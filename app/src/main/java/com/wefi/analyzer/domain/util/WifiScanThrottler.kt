package com.wefi.analyzer.domain.util

/**
 * Throttler pemindaian Wi-Fi cerdas untuk mematuhi rate limit resmi Android.
 * - Mencoba scan adaptif di T+20s jika sukses.
 * - Mundur (backoff) ke T+30s jika OS mengembalikan false (throttled).
 * - Menjaga batas maksimal 4 scan per jendela 2 menit (120 detik).
 */
class WifiScanThrottler(
    private val baseIntervalMs: Long = BASE_INTERVAL_MS,
    private val backoffIntervalMs: Long = BACKOFF_INTERVAL_MS,
    private val windowMs: Long = WINDOW_MS,
    private val maxScansPerWindow: Int = MAX_SCANS_PER_WINDOW
) {

    private val scanTimestamps = mutableListOf<Long>()
    var lastScanTimestamp: Long = 0L
        private set
    var lastScanSuccess: Boolean = true
        private set

    @Synchronized
    fun canScan(now: Long = System.currentTimeMillis()): Boolean {
        cleanOldTimestamps(now)

        // Periksa batas maksimal 4x per 2 menit
        if (scanTimestamps.size >= maxScansPerWindow) {
            return false
        }

        // Periksa interval minimal sejak pemindaian terakhir
        val requiredInterval = if (lastScanSuccess) baseIntervalMs else backoffIntervalMs
        if (lastScanTimestamp > 0L && (now - lastScanTimestamp) < requiredInterval) {
            return false
        }

        return true
    }

    @Synchronized
    fun remainingCooldownSeconds(now: Long = System.currentTimeMillis()): Int {
        cleanOldTimestamps(now)

        var maxWaitMs = 0L

        // Cooldown berdasarkan batas frekuensi (4x per 120s)
        if (scanTimestamps.size >= maxScansPerWindow) {
            val oldestInWindow = scanTimestamps.firstOrNull() ?: 0L
            val windowWait = windowMs - (now - oldestInWindow)
            if (windowWait > maxWaitMs) {
                maxWaitMs = windowWait
            }
        }

        // Cooldown berdasarkan interval pemindaian terakhir
        val requiredInterval = if (lastScanSuccess) baseIntervalMs else backoffIntervalMs
        if (lastScanTimestamp > 0L) {
            val intervalWait = requiredInterval - (now - lastScanTimestamp)
            if (intervalWait > maxWaitMs) {
                maxWaitMs = intervalWait
            }
        }

        return if (maxWaitMs > 0L) {
            ((maxWaitMs + 999) / 1000).toInt()
        } else {
            0
        }
    }

    @Synchronized
    fun recordScanAttempt(success: Boolean, now: Long = System.currentTimeMillis()) {
        cleanOldTimestamps(now)
        lastScanTimestamp = now
        lastScanSuccess = success
        if (success) {
            scanTimestamps.add(now)
        }
    }

    @Synchronized
    fun getRecentScansInWindow(now: Long = System.currentTimeMillis()): Int {
        cleanOldTimestamps(now)
        return scanTimestamps.size
    }

    private fun cleanOldTimestamps(now: Long) {
        scanTimestamps.removeAll { timestamp ->
            (now - timestamp) > windowMs
        }
    }

    companion object {
        const val BASE_INTERVAL_MS = 20_000L // 20 detik (Golden time scan)
        const val BACKOFF_INTERVAL_MS = 30_000L // 30 detik (Backoff saat false)
        const val WINDOW_MS = 120_000L // 2 menit
        const val MAX_SCANS_PER_WINDOW = 4 // Maksimal 4 kali per 2 menit
    }
}
