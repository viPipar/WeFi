package com.wefi.analyzer.domain.model

data class VerifiedLabRouter(
    val bssid: String,
    val ssid: String,
    val workingPassword: String,
    val discoveredTimestamp: Long = System.currentTimeMillis(),
    val securityType: WifiSecurityType = WifiSecurityType.WPA2
)
