package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabRouterAuditTest {

    private val defaultWhitelist = listOf(
        "ilmukomputeripb",
        "Lab-IoT-01",
        "Lab-Jaringan-A",
        "Lab-Riset-Wifi",
        "RouterLab",
        "TPLINK406",
        "Halo"
    )

    @Test
    fun isSsidAuthorized_returnsTrueOnlyForWhitelistedSsids() {
        assertTrue(defaultWhitelist.any { it.equals("ilmukomputeripb", ignoreCase = true) })
        assertTrue(defaultWhitelist.any { it.equals("Lab-IoT-01", ignoreCase = true) })
        assertTrue(defaultWhitelist.any { it.equals("RouterLab", ignoreCase = true) })
        assertTrue(defaultWhitelist.any { it.equals("TPLINK406", ignoreCase = true) })
        assertTrue(defaultWhitelist.any { it.equals("Halo", ignoreCase = true) })
        assertFalse(defaultWhitelist.any { it.equals("Public-Cafe-WiFi", ignoreCase = true) })
    }

    @Test
    fun labAuditTarget_marksUnauthorizedCorrectly() {
        val publicAp = LabAuditTarget(
            ssid = "Tetangga-WiFi",
            bssid = "00:11:22:33:44:55",
            isAuthorized = false,
            status = LabAuditStatus.UNAUTHORIZED
        )
        assertEquals(LabAuditStatus.UNAUTHORIZED, publicAp.status)
        assertFalse(publicAp.isAuthorized)
    }

    @Test
    fun labAuditStatus_allExpectedStatesExist() {
        val states = LabAuditStatus.entries.map { it.name }
        assertTrue(states.contains("UNTESTED"))
        assertTrue(states.contains("TESTING"))
        assertTrue(states.contains("MATCHED"))
        assertTrue(states.contains("FAILED"))
        assertTrue(states.contains("UNAUTHORIZED"))
        assertTrue(states.contains("ERROR"))
    }
}
