package com.wefi.analyzer.data.repository

import android.util.Log
import com.wefi.analyzer.domain.model.SpeedTestMetrics
import com.wefi.analyzer.domain.model.SpeedTestStage
import com.wefi.analyzer.domain.repository.SpeedTestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Mesin SpeedTest Akurasi Tinggi (Standar Benchmark Ookla / Fast.com)
 * 1. Ping & Jitter: Mengukur Transport Layer TCP Handshake RTT murni (tanpa penalti TLS handshake).
 * 2. Multi-Stream Parallelism: Menggunakan 4 coroutine stream simultan untuk menjenuhkan kapasitas bandwidth (TCP BDP saturation).
 * 3. Sliding-Window Delta Throughput Meter: Mengabaikan fase slow-start warm-up (1 detik pertama) dan menghitung
 *    throughput stabil berdasarkan selisih delta byte per interval 200ms.
 * 4. 64 KB Memory Buffers: Meminimalkan system call context-switching di kernel Android.
 * 5. Deteksi 100% Offline Lab Mode tanpa WAN.
 */
class SpeedTestRepositoryImpl(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) : SpeedTestRepository {

    override fun runSpeedTest(): Flow<SpeedTestMetrics> = flow {
        var currentMetrics = SpeedTestMetrics(
            isRunning = true,
            stage = SpeedTestStage.PING,
            progress = 0.05f
        )
        emit(currentMetrics)

        // =========================================================================
        // 1. STAGE PING & JITTER (TCP Socket Handshake + Fallback Http Probe)
        // =========================================================================
        val pingSamples = mutableListOf<Long>()
        val pingEndpoints = listOf(
            "https://www.google.com/generate_204",
            "https://1.1.1.1",
            "https://speed.cloudflare.com"
        )
        var successfulPings = 0

        // Probe awal via OkHttp untuk memastikan konektivitas internet / deteksi offline
        for (i in 1..4) {
            currentCoroutineContext().ensureActive()
            var pingSucceeded = false
            var duration = 0L

            for (url in pingEndpoints) {
                try {
                    val req = Request.Builder().url(url).head().build()
                    val start = System.currentTimeMillis()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful || res.code == 204) {
                            duration = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                            pingSucceeded = true
                            successfulPings++
                        }
                    }
                    if (pingSucceeded) break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }

            if (pingSucceeded) {
                // Ukur TCP Handshake murni ke port 443 untuk akurasi lapisan transport
                try {
                    val tcpStart = System.currentTimeMillis()
                    Socket().use { s ->
                        s.connect(InetSocketAddress("1.1.1.1", 443), 1500)
                    }
                    val tcpRtt = System.currentTimeMillis() - tcpStart
                    pingSamples.add(tcpRtt.coerceAtLeast(1L))
                } catch (ignored: Exception) {
                    // Fallback ke durasi HTTP jika socket raw dibatasi
                    pingSamples.add(duration.coerceAtLeast(1L))
                }
            }
            delay(50L)
        }

        // Deteksi kondisi 100% offline (Jaringan Lab Lokal tanpa WAN)
        if (successfulPings == 0) {
            emit(
                currentMetrics.copy(
                    isRunning = false,
                    stage = SpeedTestStage.OFFLINE_LAB_MODE,
                    progress = 1.0f,
                    errorMessage = "Jaringan Lokal Lab - Tidak Ada Akses Internet Luar (100% Offline)"
                )
            )
            return@flow
        }

        val avgPing = if (pingSamples.isNotEmpty()) pingSamples.average() else 0.0
        val jitter = if (pingSamples.size > 1) {
            var diffSum = 0.0
            for (i in 0 until pingSamples.size - 1) {
                diffSum += abs(pingSamples[i] - pingSamples[i + 1])
            }
            diffSum / (pingSamples.size - 1)
        } else 0.0

        currentMetrics = currentMetrics.copy(
            pingMs = round(avgPing * 10) / 10,
            jitterMs = round(jitter * 10) / 10,
            stage = SpeedTestStage.DOWNLOAD,
            progress = 0.25f
        )
        emit(currentMetrics)

        // =========================================================================
        // 2. STAGE DOWNLOAD THROUGHPUT (Multi-Stream + Sliding Window Delta Meter)
        // =========================================================================
        val totalBytesDownloaded = AtomicLong(0L)
        val isDownloadActive = AtomicBoolean(true)
        val downloadStreams = 4 // 4 paralel worker streams (standar Ookla)
        val downloadDurationMs = 5000L // 5 detik streaming konstan
        val downloadChunkUrl = "https://speed.cloudflare.com/__down?bytes=25000000" // 25MB per chunk

        val steadyStateDownloadSamples = mutableListOf<Double>()
        var finalDownloadSpeedMbps = 0.0

        coroutineScope {
            // A. Peluncuran 4 Coroutine Worker Pengunduh
            val workers = (1..downloadStreams).map { workerId ->
                launch(Dispatchers.IO) {
                    val buffer = ByteArray(65536) // 64 KB buffer memori optimal
                    while (isDownloadActive.get()) {
                        try {
                            val request = Request.Builder().url(downloadChunkUrl).build()
                            client.newCall(request).execute().use { response ->
                                val source = response.body?.source()
                                if (source != null && response.isSuccessful) {
                                    var bytesRead = 0
                                    while (isDownloadActive.get() && source.read(buffer).also { bytesRead = it } != -1) {
                                        totalBytesDownloaded.addAndGet(bytesRead.toLong())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Download worker $workerId exception: ${e.message}")
                            break
                        }
                    }
                }
            }

            // B. Sampler Throughput Berbasis Sliding Window (200ms per irisan)
            val startTime = System.currentTimeMillis()
            var lastSampleTime = startTime
            var lastSampleBytes = 0L

            while (System.currentTimeMillis() - startTime < downloadDurationMs) {
                delay(200L)
                currentCoroutineContext().ensureActive()

                val now = System.currentTimeMillis()
                val currentBytes = totalBytesDownloaded.get()
                val deltaBytes = currentBytes - lastSampleBytes
                val deltaTimeSec = (now - lastSampleTime) / 1000.0

                if (deltaTimeSec > 0.05 && deltaBytes > 0) {
                    val instantaneousMbps = (deltaBytes * 8.0) / (deltaTimeSec * 1_000_000.0)

                    // Abaikan 1 detik pertama (Warm-up / TCP Slow-Start discard)
                    val elapsedTotal = now - startTime
                    if (elapsedTotal >= 1000L) {
                        steadyStateDownloadSamples.add(instantaneousMbps)
                    }

                    val displayMbps = if (steadyStateDownloadSamples.isNotEmpty()) {
                        // Rata-rata dari sampel kondisi stabil (steady-state window)
                        steadyStateDownloadSamples.takeLast(6).average()
                    } else {
                        instantaneousMbps
                    }

                    val progressFraction = (elapsedTotal.toFloat() / downloadDurationMs.toFloat()).coerceIn(0f, 1f)
                    val currentProgress = 0.25f + (progressFraction * 0.45f)

                    emit(
                        currentMetrics.copy(
                            downloadMbps = round(displayMbps * 10) / 10,
                            progress = currentProgress
                        )
                    )

                    lastSampleBytes = currentBytes
                    lastSampleTime = now
                }
            }

            // Hentikan worker download
            isDownloadActive.set(false)
            workers.forEach { it.cancel() }
        }

        // Kalkulasi nilai akhir download: top 90th percentile atau average kondisi stabil
        finalDownloadSpeedMbps = if (steadyStateDownloadSamples.isNotEmpty()) {
            val sorted = steadyStateDownloadSamples.sorted()
            val percentile90 = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
            round(percentile90 * 10) / 10
        } else {
            0.0
        }

        currentMetrics = currentMetrics.copy(
            downloadMbps = finalDownloadSpeedMbps,
            stage = SpeedTestStage.UPLOAD,
            progress = 0.70f
        )
        emit(currentMetrics)

        // =========================================================================
        // 3. STAGE UPLOAD THROUGHPUT (Multi-Stream + Sliding Window)
        // =========================================================================
        val totalBytesUploaded = AtomicLong(0L)
        val isUploadActive = AtomicBoolean(true)
        val uploadStreams = 2 // 2 paralel upload streams
        val uploadDurationMs = 4000L // 4 detik streaming unggah
        val steadyStateUploadSamples = mutableListOf<Double>()
        var finalUploadSpeedMbps = 0.0

        coroutineScope {
            val uploadChunk = ByteArray(65536) // 64 KB chunk
            val uploadBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    while (isUploadActive.get()) {
                        sink.write(uploadChunk)
                        totalBytesUploaded.addAndGet(uploadChunk.size.toLong())
                    }
                }
            }

            // A. Peluncuran Worker Unggah
            val uploadWorkers = (1..uploadStreams).map {
                launch(Dispatchers.IO) {
                    while (isUploadActive.get()) {
                        try {
                            val request = Request.Builder()
                                .url("https://speed.cloudflare.com/__up")
                                .post(uploadBody)
                                .build()
                            client.newCall(request).execute().use { }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            break
                        }
                    }
                }
            }

            // B. Sampler Throughput Unggah (Sliding Window 200ms)
            val startTime = System.currentTimeMillis()
            var lastSampleTime = startTime
            var lastSampleBytes = 0L

            while (System.currentTimeMillis() - startTime < uploadDurationMs) {
                delay(200L)
                currentCoroutineContext().ensureActive()

                val now = System.currentTimeMillis()
                val currentBytes = totalBytesUploaded.get()
                val deltaBytes = currentBytes - lastSampleBytes
                val deltaTimeSec = (now - lastSampleTime) / 1000.0

                if (deltaTimeSec > 0.05 && deltaBytes > 0) {
                    val instantaneousMbps = (deltaBytes * 8.0) / (deltaTimeSec * 1_000_000.0)

                    val elapsedTotal = now - startTime
                    if (elapsedTotal >= 800L) {
                        steadyStateUploadSamples.add(instantaneousMbps)
                    }

                    val displayMbps = if (steadyStateUploadSamples.isNotEmpty()) {
                        steadyStateUploadSamples.takeLast(5).average()
                    } else {
                        instantaneousMbps
                    }

                    val progressFraction = (elapsedTotal.toFloat() / uploadDurationMs.toFloat()).coerceIn(0f, 1f)
                    val currentProgress = 0.70f + (progressFraction * 0.30f)

                    emit(
                        currentMetrics.copy(
                            uploadMbps = round(displayMbps * 10) / 10,
                            progress = currentProgress
                        )
                    )

                    lastSampleBytes = currentBytes
                    lastSampleTime = now
                }
            }

            isUploadActive.set(false)
            uploadWorkers.forEach { it.cancel() }
        }

        finalUploadSpeedMbps = if (steadyStateUploadSamples.isNotEmpty()) {
            val sorted = steadyStateUploadSamples.sorted()
            val p90 = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
            round(p90 * 10) / 10
        } else {
            0.0
        }

        currentMetrics = currentMetrics.copy(
            uploadMbps = finalUploadSpeedMbps,
            stage = SpeedTestStage.FINISHED,
            progress = 1.0f,
            isRunning = false
        )
        emit(currentMetrics)
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "SpeedTestRepo"
    }
}
