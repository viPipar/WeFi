package com.wefi.analyzer.ui.screens.aplist

import com.wefi.analyzer.domain.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ApListSortingTest {

    private val ap1 = WifiAccessPoint(
        bssid = "01",
        ssid = "Zeta",
        rssi = -70,
        frequencyMhz = 2412,
        channel = 1,
        distanceMeters = 15.0
    )
    private val ap2 = WifiAccessPoint(
        bssid = "02",
        ssid = "Alpha",
        rssi = -40,
        frequencyMhz = 2412,
        channel = 1,
        distanceMeters = 2.0
    )

    @Test
    fun sortBySignal_ordersByRssiDescending() {
        val list = listOf(ap1, ap2).sortedByDescending { it.rssi }
        assertEquals("Alpha", list[0].ssid)
    }

    @Test
    fun sortByDistance_ordersByDistanceAscending() {
        val list = listOf(ap1, ap2).sortedBy { it.distanceMeters }
        assertEquals("Alpha", list[0].ssid)
    }

    @Test
    fun sortByName_ordersBySsidAscending() {
        val list = listOf(ap1, ap2).sortedBy { it.ssid.lowercase() }
        assertEquals("Alpha", list[0].ssid)
    }
}
