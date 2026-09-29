package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiScanState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface pemindaian nirkabel resmi menggunakan WifiManager Android dengan strategi Golden Time.
 */
interface WifiScanner {
    val scanState: StateFlow<WifiScanState>
    val lastScanTimestamp: StateFlow<Long>
    val remainingScanCooldownSeconds: StateFlow<Int>
    val isThrottleEnabledOnDevice: Boolean

    fun isLocationEnabled(): Boolean
    fun startScan(): Boolean
    fun refreshFromCache()
    fun toggleLabScanThrottle(enable: Boolean): Boolean
    fun teardown()
}
