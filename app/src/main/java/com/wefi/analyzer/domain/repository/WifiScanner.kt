package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiScanState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface pemindaian nirkabel resmi menggunakan WifiManager Android.
 */
interface WifiScanner {
    val scanState: StateFlow<WifiScanState>
    fun isLocationEnabled(): Boolean
    fun startScan()
    fun teardown()
}
