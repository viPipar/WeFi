package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiScanThrottlerTest {

    private lateinit var throttler: WifiScanThrottler

    @Before
    fun setUp() {
        throttler = WifiScanThrottler()
    }

    @Test
    fun initialScan_isAllowed() {
        assertTrue(throttler.canScan(now = 100_000L))
        assertEquals(0, throttler.remainingCooldownSeconds(now = 100_000L))
    }

    @Test
    fun successfulScan_enforces20sCooldown() {
        val t0 = 100_000L
        throttler.recordScanAttempt(success = true, now = t0)

        // Di T+10s: Masih cooldown
        assertFalse(throttler.canScan(now = t0 + 10_000L))
        assertEquals(10, throttler.remainingCooldownSeconds(now = t0 + 10_000L))

        // Di T+19s: Masih cooldown (1s)
        assertFalse(throttler.canScan(now = t0 + 19_000L))
        assertEquals(1, throttler.remainingCooldownSeconds(now = t0 + 19_000L))

        // Di T+20s: Boleh scan
        assertTrue(throttler.canScan(now = t0 + 20_000L))
        assertEquals(0, throttler.remainingCooldownSeconds(now = t0 + 20_000L))
    }

    @Test
    fun failedScan_enforces30sBackoff() {
        val t0 = 100_000L
        throttler.recordScanAttempt(success = false, now = t0)

        // Di T+20s: Masih cooldown karena backoff 30 detik
        assertFalse(throttler.canScan(now = t0 + 20_000L))
        assertEquals(10, throttler.remainingCooldownSeconds(now = t0 + 20_000L))

        // Di T+30s: Boleh coba scan lagi
        assertTrue(throttler.canScan(now = t0 + 30_000L))
        assertEquals(0, throttler.remainingCooldownSeconds(now = t0 + 30_000L))
    }

    @Test
    fun max4ScansPer2Minutes_isStrictlyEnforced() {
        var time = 1_000_000L

        // Lakukan 4x scan yang berhasil dengan jarak aman
        for (i in 1..4) {
            assertTrue(throttler.canScan(now = time))
            throttler.recordScanAttempt(success = true, now = time)
            time += 21_000L // maju 21 detik
        }

        assertEquals(4, throttler.getRecentScansInWindow(now = time))

        // Percobaan ke-5 dalam jendela 120s harus diblokir
        assertFalse(throttler.canScan(now = time))
        assertTrue(throttler.remainingCooldownSeconds(now = time) > 0)

        // Maju melampaui 120 detik dari scan pertama (1_000_000 + 120_001 = 1_120_001)
        val afterWindow = 1_000_000L + 120_001L
        assertTrue(throttler.canScan(now = afterWindow))
    }
}
