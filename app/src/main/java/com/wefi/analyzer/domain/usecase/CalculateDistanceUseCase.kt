package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.EnvironmentPreset
import kotlin.math.pow
import kotlin.math.round

/**
 * Menghitung estimasi jarak fisik perangkat dari Access Point menggunakan
 * Log-Distance Path Loss Model:
 * d = 10 ^ ((A0(f) - RSSI) / (10 * n))
 */
class CalculateDistanceUseCase {
    fun execute(
        rssi: Int,
        frequencyMhz: Int,
        preset: EnvironmentPreset = EnvironmentPreset.INDOOR
    ): Double {
        // Daya referensi A0 pada jarak 1 meter terkalibrasi:
        val a0 = if (frequencyMhz >= 5000) -45.0 else -40.0
        val exponent = (a0 - rssi.toDouble()) / (10.0 * preset.exponent)
        val rawDistance = 10.0.pow(exponent)
        // Dibulatkan ke 1 desimal presisi
        return round(rawDistance * 10.0) / 10.0
    }
}
