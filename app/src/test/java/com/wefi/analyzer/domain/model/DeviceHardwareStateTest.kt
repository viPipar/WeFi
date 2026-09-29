package com.wefi.analyzer.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardwareStateTest {

    @Test
    fun isReadyForWifiScan_returnsTrueOnlyWhenAllRequirementsMet() {
        val readyState = DeviceHardwareState(
            isWifiEnabled = true,
            isLocationEnabled = true,
            isLocationPermissionGranted = true
        )
        assertTrue(readyState.isReadyForScan)

        val noGpsState = readyState.copy(isLocationEnabled = false)
        assertFalse(noGpsState.isReadyForScan)

        val noWifiState = readyState.copy(isWifiEnabled = false)
        assertFalse(noWifiState.isReadyForScan)

        val noPermissionState = readyState.copy(isLocationPermissionGranted = false)
        assertFalse(noPermissionState.isReadyForScan)
    }
}
