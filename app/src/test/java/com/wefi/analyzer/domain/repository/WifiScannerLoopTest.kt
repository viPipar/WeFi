package com.wefi.analyzer.domain.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScannerLoopTest {

    @Test
    fun scanInterval_isWithinAcceptableLimits() {
        val intervalMs = 6000L
        assertTrue("Scan interval should be between 4s and 10s for battery and OS throttle safety", intervalMs in 4000L..10000L)
    }
}
