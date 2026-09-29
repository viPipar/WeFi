package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.ConnectedNetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentConnectionRepositoryTest {

    @Test
    fun connectedNetworkInfo_isValidEvenWithAnonymizedMac() {
        val ap = WifiAccessPoint(
            bssid = "02:00:00:00:00:00",
            ssid = "Rumah-Wi-Fi",
            rssi = -55,
            frequencyMhz = 2412,
            channel = 1,
            isConnected = true
        )
        val info = ConnectedNetworkInfo(
            accessPoint = ap,
            linkSpeedMbps = 144,
            ipAddress = "192.168.1.15",
            gatewayIp = "192.168.1.1"
        )
        assertNotNull(info.accessPoint)
        assertTrue(info.isConnected)
        assertEquals("Rumah-Wi-Fi", info.accessPoint?.ssid)
        assertEquals("192.168.1.15", info.ipAddress)
    }

    @Test
    fun connectedNetworkInfo_handlesUnknownSsidGracefully() {
        val ap = WifiAccessPoint(
            bssid = "02:00:00:00:00:00",
            ssid = "Wi-Fi Terhubung",
            rssi = -60,
            frequencyMhz = 5180,
            channel = 36,
            isConnected = true
        )
        val info = ConnectedNetworkInfo(
            accessPoint = ap,
            linkSpeedMbps = 866,
            ipAddress = "10.0.0.42",
            gatewayIp = "10.0.0.1"
        )
        assertTrue(info.isConnected)
        assertEquals("Wi-Fi Terhubung", info.accessPoint?.displaySsid)
    }
}
