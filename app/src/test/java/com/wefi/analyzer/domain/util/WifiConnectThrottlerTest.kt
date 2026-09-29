package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiConnectThrottlerTest {

    private lateinit var throttler: WifiConnectThrottler

    @Before
    fun setUp() {
        throttler = WifiConnectThrottler()
    }

    @Test
    fun initialConnect_isAllowed() {
        val result = throttler.canConnect("Lab-Wifi-A", now = 100_000L)
        assertTrue(result is ConnectCheckResult.Allowed)
        assertEquals(0, throttler.remainingCooldownSecondsForSsid("Lab-Wifi-A", now = 100_000L))
    }

    @Test
    fun sameSsid_enforces5sCooldown() {
        val t0 = 100_000L
        throttler.recordAttemptStarted("Lab-Wifi-A", now = t0)
        throttler.recordAttemptFinished("Lab-Wifi-A", success = true, now = t0 + 1_000L)

        // Di T+2s: Cooldown untuk SSID yang sama (5 detik dari t0)
        val blocked = throttler.canConnect("Lab-Wifi-A", now = t0 + 2_000L)
        assertTrue(blocked is ConnectCheckResult.Blocked)
        assertEquals(3, (blocked as ConnectCheckResult.Blocked).remainingSeconds)

        // Di T+5s: Boleh mencoba lagi
        val allowed = throttler.canConnect("Lab-Wifi-A", now = t0 + 5_000L)
        assertTrue(allowed is ConnectCheckResult.Allowed)
    }

    @Test
    fun differentSsid_enforces3sCooldown() {
        val t0 = 100_000L
        throttler.recordAttemptStarted("Lab-Wifi-A", now = t0)
        throttler.recordAttemptFinished("Lab-Wifi-A", success = true, now = t0 + 500L)

        // Di T+1s: Cooldown untuk SSID berbeda (butuh 3 detik)
        val blocked = throttler.canConnect("Lab-Wifi-B", now = t0 + 1_000L)
        assertTrue(blocked is ConnectCheckResult.Blocked)
        assertEquals(2, (blocked as ConnectCheckResult.Blocked).remainingSeconds)

        // Di T+3s: Boleh mencoba SSID berbeda
        val allowed = throttler.canConnect("Lab-Wifi-B", now = t0 + 3_000L)
        assertTrue(allowed is ConnectCheckResult.Allowed)
    }

    @Test
    fun max3AttemptsPerMinutePerSsid_isEnforced() {
        var time = 100_000L
        val ssid = "Lab-Test-Router"

        for (i in 1..3) {
            val check = throttler.canConnect(ssid, now = time)
            assertTrue(check is ConnectCheckResult.Allowed)
            throttler.recordAttemptStarted(ssid, now = time)
            throttler.recordAttemptFinished(ssid, success = true, now = time + 1_000L)
            time += 6_000L // maju 6 detik (lolos jeda 5s)
        }

        // Percobaan ke-4 pada SSID yang sama dalam 1 menit harus diblokir
        val blocked = throttler.canConnect(ssid, now = time)
        assertTrue(blocked is ConnectCheckResult.Blocked)

        // Setelah jendela 60 detik berlalu dari percobaan pertama
        val afterWindow = 100_000L + 60_001L
        val allowedAfterWindow = throttler.canConnect(ssid, now = afterWindow)
        assertTrue(allowedAfterWindow is ConnectCheckResult.Allowed)
    }

    @Test
    fun postFailure_enforces5sCooldownFromFinish() {
        val t0 = 100_000L
        throttler.recordAttemptStarted("Lab-Wifi-A", now = t0)
        // Gagal pada T+10s
        val tFinish = t0 + 10_000L
        throttler.recordAttemptFinished("Lab-Wifi-A", success = false, now = tFinish)

        // Di T+12s (2s setelah gagal): Masih cooldown
        val blocked = throttler.canConnect("Lab-Wifi-A", now = tFinish + 2_000L)
        assertTrue(blocked is ConnectCheckResult.Blocked)
        assertEquals(3, (blocked as ConnectCheckResult.Blocked).remainingSeconds)

        // Di T+15s (5s setelah gagal): Boleh
        val allowed = throttler.canConnect("Lab-Wifi-A", now = tFinish + 5_000L)
        assertTrue(allowed is ConnectCheckResult.Allowed)
    }
}
