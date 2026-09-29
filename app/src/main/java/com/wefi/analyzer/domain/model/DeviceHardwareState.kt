package com.wefi.analyzer.domain.model

/**
 * Model status kesiapan perangkat keras (Wi-Fi, Layanan Lokasi GPS, dan Izin).
 * Diperlukan agar pengguna mendapat panduan jelas jika GPS atau Wi-Fi mati.
 */
data class DeviceHardwareState(
    val isWifiEnabled: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val isLocationPermissionGranted: Boolean = false
) {
    val isReadyForScan: Boolean
        get() = isWifiEnabled && isLocationEnabled && isLocationPermissionGranted
}
