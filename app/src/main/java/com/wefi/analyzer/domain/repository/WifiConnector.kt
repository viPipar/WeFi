package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.util.ConnectCheckResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface koneksi Wi-Fi manual menggunakan WifiNetworkSuggestion resmi Android.
 * Mengintegrasikan strategi Golden Time, audit logging, dan verifikasi persetujuan OS.
 */
interface WifiConnector {
    val connectState: StateFlow<WifiConnectState>

    fun canConnect(ssid: String): ConnectCheckResult
    fun remainingCooldownSeconds(ssid: String): Int
    fun connect(ssid: String, password: String, securityType: WifiSecurityType)
    fun cancel()
    fun forgetNetwork(ssid: String)
    fun teardown()
}
