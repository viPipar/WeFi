package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * Kontrak repository untuk pemindaian hardware Wi-Fi di sekitar.
 */
interface WifiScannerRepository {
    val scanResults: StateFlow<List<WifiAccessPoint>>
    val isScanning: StateFlow<Boolean>
    val selectedPreset: StateFlow<EnvironmentPreset>

    fun startScan()
    fun setEnvironmentPreset(preset: EnvironmentPreset)
}
