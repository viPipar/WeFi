package com.wefi.analyzer.data.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pengatur laju keamanan jaringan (Safety Throttler & Circuit Breaker)
 * Sesuai klausul keselamatan:
 * 1. Batas maksimal 200 paket per detik di seluruh jenis pemindaian (Token Bucket / Delay pacing).
 * 2. Cooldown per-host: 30 detik sebelum mengizinkan pemindaian ulang pada IP yang sama.
 * 3. Circuit breaker: memantau persentase kegagalan respons. Jika >50% host tidak merespons,
 *    mengindikasikan client isolation aktif atau jaringan lab terputus.
 */
class NetworkSafetyThrottler(
    private val maxPacketsPerSecond: Int = 200,
    private val hostCooldownMs: Long = 30_000L
) {
    private val mutex = Mutex()
    private var availableTokens = maxPacketsPerSecond.toDouble()
    private var lastRefillTimestamp = System.currentTimeMillis()
    
    // Riwayat pemindaian terakhir per host untuk cooldown 30 detik
    private val hostLastScanMap = ConcurrentHashMap<String, Long>()
    
    // Pelacakan status circuit breaker
    private val totalProbesAttempted = AtomicInteger(0)
    private val totalProbesUnresponsive = AtomicInteger(0)

    /**
     * Membatasi laju pengiriman paket agar tidak melampaui [maxPacketsPerSecond] (200 pkt/s).
     */
    suspend fun throttlePacket() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val timePassedSec = (now - lastRefillTimestamp) / 1000.0
            lastRefillTimestamp = now

            // Tambahkan token sesuai waktu yang berlalu
            availableTokens = (availableTokens + timePassedSec * maxPacketsPerSecond)
                .coerceAtMost(maxPacketsPerSecond.toDouble())

            if (availableTokens < 1.0) {
                val waitTimeMs = ((1.0 - availableTokens) / maxPacketsPerSecond * 1000.0).toLong().coerceAtLeast(5L)
                delay(waitTimeMs)
                availableTokens = 0.0
                lastRefillTimestamp = System.currentTimeMillis()
            } else {
                availableTokens -= 1.0
            }
        }
    }

    /**
     * Memeriksa apakah host berada dalam masa pendinginan (cooldown 30s).
     * @return true jika host boleh dipindai, false jika masih dalam periode cooldown.
     */
    fun canScanHost(hostIp: String): Boolean {
        val lastScan = hostLastScanMap[hostIp] ?: return true
        return (System.currentTimeMillis() - lastScan) >= hostCooldownMs
    }

    /**
     * Mencatat bahwa host baru saja dipindai untuk memulai cooldown 30s.
     */
    fun recordHostScanned(hostIp: String) {
        hostLastScanMap[hostIp] = System.currentTimeMillis()
    }

    /**
     * Mencatat hasil probe host untuk pemantauan Circuit Breaker.
     */
    fun recordProbeResult(isResponsive: Boolean) {
        totalProbesAttempted.incrementAndGet()
        if (!isResponsive) {
            totalProbesUnresponsive.incrementAndGet()
        }
    }

    /**
     * Memeriksa apakah Circuit Breaker terpicu (>50% kegagalan setelah minimal 8 host diuji).
     */
    fun isCircuitBreakerTriggered(): Boolean {
        val attempted = totalProbesAttempted.get()
        if (attempted < 8) return false
        val unresponsive = totalProbesUnresponsive.get()
        return (unresponsive.toDouble() / attempted.toDouble()) > 0.50
    }

    /**
     * Mengatur ulang statistik circuit breaker untuk sesi pemindaian baru.
     */
    fun resetCircuitBreaker() {
        totalProbesAttempted.set(0)
        totalProbesUnresponsive.set(0)
    }

    /**
     * Membersihkan riwayat cooldown dalam memori.
     */
    fun clearCooldowns() {
        hostLastScanMap.clear()
    }
}
