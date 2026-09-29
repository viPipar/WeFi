package com.wefi.analyzer.domain.util

/**
 * Hasil pengecekan kelaikan koneksi berdasarkan strategi Golden Time.
 */
sealed interface ConnectCheckResult {
    data object Allowed : ConnectCheckResult
    data class Blocked(
        val reason: String,
        val remainingSeconds: Int
    ) : ConnectCheckResult
}

/**
 * Throttler koneksi manual Wi-Fi untuk kepatuhan golden time praktikum lab:
 * - Jeda minimal 5 detik antar percobaan ke SSID yang sama.
 * - Jeda minimal 3 detik antar percobaan ke SSID berbeda.
 * - Maksimal 3 kali percobaan per SSID per menit (sliding window 60s).
 * - Cooldown 5 detik pasca kegagalan/penolakan.
 * - Eksklusif satu SSID pada satu waktu (mencegah loop/race condition).
 */
class WifiConnectThrottler(
    private val sameSsidIntervalMs: Long = SAME_SSID_INTERVAL_MS,
    private val diffSsidIntervalMs: Long = DIFF_SSID_INTERVAL_MS,
    private val ssidWindowMs: Long = SSID_WINDOW_MS,
    private val maxAttemptsPerSsidWindow: Int = MAX_ATTEMPTS_PER_SSID_WINDOW,
    private val postFailureCooldownMs: Long = POST_FAILURE_COOLDOWN_MS
) {

    private var activeConnectingSsid: String? = null
    var lastAttemptSsid: String? = null
        private set
    var lastAttemptTimestamp: Long = 0L
        private set

    private val ssidAttemptsMap = mutableMapOf<String, MutableList<Long>>()

    @Synchronized
    fun canConnect(ssid: String, now: Long = System.currentTimeMillis()): ConnectCheckResult {
        // Aturan: Hanya satu SSID pada satu waktu
        val active = activeConnectingSsid
        if (active != null && active != ssid) {
            return ConnectCheckResult.Blocked(
                reason = "Sedang menunggu koneksi ke $active",
                remainingSeconds = 0
            )
        }

        // Aturan: Jeda global antar percobaan (5s jika SSID sama, 3s jika SSID berbeda)
        val lastSsid = lastAttemptSsid
        if (lastAttemptTimestamp > 0L) {
            val requiredInterval = if (lastSsid == ssid) sameSsidIntervalMs else diffSsidIntervalMs
            val elapsed = now - lastAttemptTimestamp
            if (elapsed < requiredInterval) {
                val remainingSec = (((requiredInterval - elapsed) + 999) / 1000).toInt()
                return ConnectCheckResult.Blocked(
                    reason = if (lastSsid == ssid) "Jeda 5s antar percobaan SSID sama" else "Jeda 3s antar SSID berbeda",
                    remainingSeconds = remainingSec
                )
            }
        }

        // Aturan: Maksimal 3 percobaan per SSID per 1 menit (sliding window)
        val timestamps = ssidAttemptsMap.getOrPut(ssid) { mutableListOf() }
        timestamps.removeAll { (now - it) > ssidWindowMs }

        if (timestamps.size >= maxAttemptsPerSsidWindow) {
            val oldest = timestamps.first()
            val waitMs = ssidWindowMs - (now - oldest)
            val remainingSec = (((waitMs) + 999) / 1000).toInt()
            return ConnectCheckResult.Blocked(
                reason = "Maks 3x percobaan per menit untuk SSID ini",
                remainingSeconds = remainingSec
            )
        }

        return ConnectCheckResult.Allowed
    }

    @Synchronized
    fun remainingCooldownSecondsForSsid(ssid: String, now: Long = System.currentTimeMillis()): Int {
        val result = canConnect(ssid, now)
        return when (result) {
            is ConnectCheckResult.Allowed -> 0
            is ConnectCheckResult.Blocked -> result.remainingSeconds
        }
    }

    @Synchronized
    fun recordAttemptStarted(ssid: String, now: Long = System.currentTimeMillis()) {
        activeConnectingSsid = ssid
        lastAttemptSsid = ssid
        lastAttemptTimestamp = now

        val timestamps = ssidAttemptsMap.getOrPut(ssid) { mutableListOf() }
        timestamps.removeAll { (now - it) > ssidWindowMs }
        timestamps.add(now)
    }

    @Synchronized
    fun recordAttemptFinished(ssid: String, success: Boolean, now: Long = System.currentTimeMillis()) {
        if (activeConnectingSsid == ssid) {
            activeConnectingSsid = null
        }
        lastAttemptSsid = ssid
        // Jika gagal/ditolak, perbarui timestamp ke waktu selesai untuk memastikan cooldown 5s
        if (!success) {
            lastAttemptTimestamp = now
        }
    }

    @Synchronized
    fun clear() {
        activeConnectingSsid = null
        lastAttemptSsid = null
        lastAttemptTimestamp = 0L
        ssidAttemptsMap.clear()
    }

    companion object {
        const val SAME_SSID_INTERVAL_MS = 5_000L // 5 detik antar percobaan SSID sama
        const val DIFF_SSID_INTERVAL_MS = 3_000L // 3 detik antar SSID berbeda
        const val SSID_WINDOW_MS = 60_000L // 1 menit window
        const val MAX_ATTEMPTS_PER_SSID_WINDOW = 3 // Maksimal 3x percobaan per menit
        const val POST_FAILURE_COOLDOWN_MS = 5_000L // 5 detik pasca gagal
    }
}
