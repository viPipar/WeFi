package com.wefi.analyzer.domain.model

/**
 * Tipe keamanan nirkabel yang didukung.
 */
enum class WifiSecurityType(val label: String) {
    OPEN("Open"),
    WEP("WEP"),
    WPA2("WPA2"),
    WPA3("WPA3"),
    UNKNOWN("Unknown")
}

/**
 * Representasi item jaringan Wi-Fi hasil pemindaian resmi.
 */
data class WifiScanItem(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val security: WifiSecurityType,
    val frequencyMhz: Int,
    val channel: Int
)

/**
 * Status siklus hidup pemindaian Wi-Fi.
 */
sealed interface WifiScanState {
    data object Idle : WifiScanState
    data object Scanning : WifiScanState
    data class Success(val items: List<WifiScanItem>) : WifiScanState
    data class Throttled(
        val items: List<WifiScanItem>,
        val remainingCooldownSeconds: Int
    ) : WifiScanState
    data object PermissionMissing : WifiScanState
    data object LocationDisabled : WifiScanState
    data class Error(
        val message: String,
        val isLocationDisabled: Boolean = false
    ) : WifiScanState
}
