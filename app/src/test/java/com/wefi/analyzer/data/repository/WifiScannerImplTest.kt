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
}
