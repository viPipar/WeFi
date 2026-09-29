package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import com.wefi.analyzer.domain.repository.LabRouterAuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Implementasi repository pengujian dan audit router laboratorium.
 * Mendukung integrasi scan nirkabel dan validasi kredensial (via lab mock API atau constant-time hash matcher).
 */
class LabRouterAuditRepositoryImpl(
    initialAuthorizedSsids: Set<String> = DEFAULT_AUTHORIZED_SSIDS,
    private val httpClient: OkHttpClient = defaultHttpClient
) : LabRouterAuditRepository {

    private val _authorizedSsids = initialAuthorizedSsids.toMutableSet()
    override val authorizedSsids: Set<String>
        get() = _authorizedSsids

    private val authorizedPrefixRegex = Regex("^(?i)(Lab-|IPB-|RouterLab|TPLINK).*")

    private val _auditLogs = MutableStateFlow<List<LabAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<LabAuditLogEntry>> = _auditLogs.asStateFlow()

    @Volatile
    private var labApiEndpoint: String? = null

    /**
     * Daftarkan SSID tambahan ke dalam scope pengujian laboratorium.
     */
    fun addAuthorizedSsid(ssid: String) {
        if (ssid.isNotBlank()) {
            _authorizedSsids.add(ssid.trim())
        }
    }

    /**
     * Konfigurasi endpoint mock/simulator server lab untuk validasi nyata via HTTP (opsional).
     * Contoh: "http://localhost:3000/api/verify" atau "http://10.0.2.2:3000/api/verify"
     */
    fun setLabApiEndpoint(url: String?) {
        labApiEndpoint = url?.takeIf { it.isNotBlank() }
    }

    override fun isSsidAuthorized(ssid: String): Boolean {
        if (ssid.isBlank()) return false
        val trimmed = ssid.trim()
        val inExplicitList = _authorizedSsids.any { it.equals(trimmed, ignoreCase = true) }
        val matchesPrefix = trimmed.matches(authorizedPrefixRegex)
        return inExplicitList || matchesPrefix
    }

    override fun testRouterCredential(
        target: LabAuditTarget,
        candidateKey: String
    ): Flow<LabAuditStatus> = flow {
        if (!isSsidAuthorized(target.ssid)) {
            emit(LabAuditStatus.UNAUTHORIZED)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.UNAUTHORIZED,
                    notes = "SSID di luar scope lab yang diotorisasi"
                )
            )
            return@flow
        }

        val candidate = candidateKey.trim()
        if (candidate.isEmpty()) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "Kredensial input kosong"
                )
            )
            return@flow
        }

        if (candidate.length > MAX_CANDIDATE_LENGTH) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "Panjang kredensial melebihi batas ($MAX_CANDIDATE_LENGTH karakter)"
                )
            )
            return@flow
        }

        emit(LabAuditStatus.TESTING)
        delay(SIMULATED_LATENCY_MS)

        // Validasi: Coba verifikasi ke endpoint lab jika tersedia, atau fallback ke pencocokan aman internal
        val isMatch = verifyWithLabEndpoint(target.ssid, target.bssid, candidate)
            ?: isCredentialMatched(target.ssid, candidate)

        val finalStatus = if (isMatch) LabAuditStatus.MATCHED else LabAuditStatus.FAILED
        val notes = if (isMatch) "Kredensial cocok (MATCH)" else "Kredensial tidak cocok (MISMATCH)"

        emit(finalStatus)
        recordLog(
            LabAuditLogEntry(
                targetSsid = target.ssid,
                targetBssid = target.bssid,
                status = finalStatus,
                notes = notes
            )
        )
    }

    private suspend fun verifyWithLabEndpoint(ssid: String, bssid: String, candidate: String): Boolean? {
        val endpoint = labApiEndpoint ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = """{"ssid":"$ssid","bssid":"$bssid","candidate":"$candidate"}"""
                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun isCredentialMatched(ssid: String, candidate: String): Boolean {
        val base = ssid.trim()
        if (base.isEmpty()) return false

        val expected = buildSet {
            add(base)
            add("${base}123")
            add("${base}2026")
            add("lab$base")
            add("${base}_lab")
            add("${base}@lab")
            add("admin$base")
            add("labkomputer123")
            add("ipbjuara")
            add("iotlab2026")
            add("admin1234")
            add("halo1234")
            add("routerlab123")
        }

        val candidateHash = sha256(candidate)
        return expected.any { constantTimeEquals(sha256(it), candidateHash) }
    }

    override fun recordLog(entry: LabAuditLogEntry) {
        val current = _auditLogs.value.toMutableList()
        current.add(0, entry)
        _auditLogs.value = if (current.size > MAX_LOG_ENTRIES) {
            current.take(MAX_LOG_ENTRIES)
        } else current
    }

    override fun clearLogs() {
        _auditLogs.value = emptyList()
    }

    companion object {
        val DEFAULT_AUTHORIZED_SSIDS = setOf(
            "ilmukomputeripb",
            "Lab-IoT-01",
            "Lab-Jaringan-A",
            "Lab-Riset-Wifi",
            "RouterLab",
            "TPLINK406",
            "Halo"
        )

        private const val MAX_LOG_ENTRIES = 100
        private const val MAX_CANDIDATE_LENGTH = 128
        private const val SIMULATED_LATENCY_MS = 350L

        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
        }

        fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
            return diff == 0
        }
    }
}