package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiScannerImplTest {

    @Test
    fun parseSecurityType_identifiesSecurityCorrectly() {
        assertEquals(
            WifiSecurityType.WPA3,
            WifiScannerImpl.parseSecurityType("[WPA3-SAE-CCMP][RSN-SAE-CCMP][ESS]")
        )
        assertEquals(
            WifiSecurityType.WPA2,
            WifiScannerImpl.parseSecurityType("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]")
        )
        assertEquals(
            WifiSecurityType.WEP,
            WifiScannerImpl.parseSecurityType("[WEP][ESS]")
        )
        assertEquals(
            WifiSecurityType.OPEN,
            WifiScannerImpl.parseSecurityType("[ESS]")
        )
        assertEquals(
            WifiSecurityType.UNKNOWN,
            WifiScannerImpl.parseSecurityType(null)
        )
    }

    @Test
    fun parseSecurityType_mixedModeWpa2Wpa3_returnsWpa2ForSpecifierCompatibility() {
        val mixedCaps = "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"
        val result = WifiScannerImpl.parseSecurityType(mixedCaps)
        assertEquals(WifiSecurityType.WPA2, result)
    }

    @Test
    fun processRawScanResults_emptyList_returnsEmpty() {
        val result = WifiScannerImpl.processRawScanResults(emptyList())
        org.junit.Assert.assertTrue(result.isEmpty())
    }

    @Test
    fun startScan_whenWifiManagerNull_setsErrorStateAndReturnsFalse() {
        val scanner = WifiScannerImpl(context = null, wifiManager = null)
        val started = scanner.startScan()
        org.junit.Assert.assertFalse(started)
        org.junit.Assert.assertTrue(scanner.scanState.value is com.wefi.analyzer.domain.model.WifiScanState.Error)
    }

    @Test
    fun toggleLabScanThrottle_whenContextNull_returnsFalse() {
        val scanner = WifiScannerImpl(context = null, wifiManager = null)
        org.junit.Assert.assertFalse(scanner.toggleLabScanThrottle(true))
    }
}
