package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * Informasi jaringan Wi-Fi yang saat ini sedang aktif terhubung ke perangkat.
 */
data class ConnectedNetworkInfo(
    val accessPoint: WifiAccessPoint? = null,
    val linkSpeedMbps: Int = 0,
    val ipAddress: String = "0.0.0.0",
    val gatewayIp: String = "0.0.0.0",
    val dns1: String = "0.0.0.0"
) {
    val isConnected: Boolean
        get() = accessPoint != null && (accessPoint.isConnected || ipAddress != "0.0.0.0")
}

interface CurrentConnectionRepository {
    val connectionInfo: StateFlow<ConnectedNetworkInfo>
    fun refreshConnectionInfo()
    fun teardown() {}
}
