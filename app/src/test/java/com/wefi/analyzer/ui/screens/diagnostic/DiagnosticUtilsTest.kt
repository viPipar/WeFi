package com.wefi.analyzer.ui.screens.diagnostic

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticUtilsTest {

    @Test
    fun `formatExceptionSummary returns clean class and message`() {
        val exception = IllegalArgumentException("Invalid channel frequency")
        val summary = DiagnosticUtils.formatExceptionSummary(exception)

        assertTrue(summary.contains("IllegalArgumentException"))
        assertTrue(summary.contains("Invalid channel frequency"))
    }

    @Test
    fun `formatStackTrace returns non-empty string with cause`() {
        val rootCause = NullPointerException("WifiManager was null")
        val exception = IllegalStateException("Startup failed", rootCause)
        val trace = DiagnosticUtils.formatStackTrace(exception)

        assertNotNull(trace)
        assertTrue(trace.contains("IllegalStateException"))
        assertTrue(trace.contains("Startup failed"))
        assertTrue(trace.contains("WifiManager was null"))
    }
}
