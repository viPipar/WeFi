package com.wefi.analyzer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessSecurityAuditTest {

    @Test
    fun testParsePmfMode_detectsRequiredAndCapableAndNone() {
        assertEquals(PmfMode.REQUIRED, WirelessSecurityEvaluator.parsePmfMode("[WPA2-PSK-CCMP][PMF-R]"))
        assertEquals(PmfMode.REQUIRED, WirelessSecurityEvaluator.parsePmfMode("[WPA3-SAE-CCMP][MFPR]"))
        assertEquals(PmfMode.CAPABLE, WirelessSecurityEvaluator.parsePmfMode("[WPA3-SAE-CCMP][MFPC]"))
        assertEquals(PmfMode.CAPABLE, WirelessSecurityEvaluator.parsePmfMode("[WPA3-SAE-CCMP]"))
        assertEquals(PmfMode.NONE, WirelessSecurityEvaluator.parsePmfMode("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun testHasWps_detectsWpsToken() {
        assertTrue(WirelessSecurityEvaluator.hasWps("[WPA2-PSK-CCMP][WPS][ESS]"))
        assertFalse(WirelessSecurityEvaluator.hasWps("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun testHasInsecureCipher_detectsTkipAndWep() {
        assertTrue(WirelessSecurityEvaluator.hasInsecureCipher("[WPA-PSK-TKIP][WPA2-PSK-CCMP+TKIP]"))
        assertTrue(WirelessSecurityEvaluator.hasInsecureCipher("[WEP][ESS]"))
        assertFalse(WirelessSecurityEvaluator.hasInsecureCipher("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun testEvaluateAuditItem_whenWpsAndTkip_returnsCriticalOrHighRisk() {
        val ap = WifiAccessPoint(
            bssid = "00:11:22:33:44:55",
            ssid = "Lab_Router_Old",
            rssi = -65,
            frequencyMhz = 2412,
            channel = 1,
            security = "WPA",
            capabilities = "[WPA-PSK-TKIP][WPS][ESS]"
        )

        val result = WirelessSecurityEvaluator.evaluateAuditItem(ap, listOf(ap))
        assertTrue(result.hasWps)
        assertTrue(result.hasInsecureCipher)
        assertEquals(PmfMode.NONE, result.pmfMode)
        assertTrue(result.riskLevel == HostRiskLevel.HIGH || result.riskLevel == HostRiskLevel.CRITICAL)
        assertTrue(result.riskHighlights.isNotEmpty())
    }

    @Test
    fun testEvaluateAuditItem_whenTwinSsidWithMismatchedSecurity_flagsRogueCandidate() {
        val ap1 = WifiAccessPoint(
            bssid = "00:11:22:33:44:55",
            ssid = "OfficialLab",
            rssi = -60,
            frequencyMhz = 2412,
            channel = 1,
            security = "WPA2",
            capabilities = "[WPA2-PSK-CCMP][ESS]"
        )
        val ap2 = WifiAccessPoint(
            bssid = "AA:BB:CC:11:22:33",
            ssid = "OfficialLab",
            rssi = -40,
            frequencyMhz = 2412,
            channel = 1,
            security = "Open",
            capabilities = "[ESS]"
        )

        val result = WirelessSecurityEvaluator.evaluateAuditItem(ap2, listOf(ap1, ap2))
        assertTrue(result.isRogueTwinCandidate)
        assertTrue(result.riskLevel == HostRiskLevel.CRITICAL)
    }
}
