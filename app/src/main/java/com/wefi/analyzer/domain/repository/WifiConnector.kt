package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiSecurityType
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface koneksi Wi-Fi manual menggunakan WifiNetworkSuggestion resmi Android.
 * Setiap percobaan koneksi memunculkan dialog persetujuan OS ke user.
 */
interface WifiConnector {
    val connectState: StateFlow<WifiConnectState>
    fun connect(ssid: String, password: String, securityType: WifiSecurityType)
    fun cancel()
    fun forgetNetwork(ssid: String)
    fun teardown()
}
