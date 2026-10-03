package com.wefi.analyzer.domain.model

/**
 * Domain model yang merepresentasikan sebuah Access Point Wi-Fi di sekitar.
 */
data class WifiAccessPoint(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val channelWidthMhz: Int = 20,
    val distanceMeters: Double = 0.0,
    val maxPhyRateMbps: Int = 144,
    val security: String = "WPA2",
    val capabilities: String = "",
    val qualityScore: Int = 50,
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displaySsid: String
        get() = if (ssid.isBlank()) "(SSID Tersembunyi)" else ssid

    val formattedDistance: String
        get() = "~${distanceMeters}m"

    val is5GHz: Boolean
        get() = frequencyMhz in 5000..5899

    val is6GHz: Boolean
        get() = frequencyMhz >= 5900

    val is24GHz: Boolean
        get() = frequencyMhz < 3000
}
