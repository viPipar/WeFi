package com.wefi.analyzer.domain.usecase

import kotlin.math.max
import kotlin.math.min

/**
 * Menghitung skor kualitas Wi-Fi (0-100%) secara pasif tanpa perlu login
 * menggabungkan kekuatan sinyal RSSI, generasi PHY, dan kebersihan kanal.
 */
class CalculateWifiQualityScoreUseCase {

    fun execute(rssi: Int, maxPhyRate: Int, channelPenalty: Double = 0.0): Int {
        // Bobot 1: Kekuatan sinyal RSSI (50%)
        val signalScore = when {
            rssi >= -50 -> 100
            rssi <= -90 -> 10
            else -> ((rssi + 90) * 100) / 40
        }

        // Bobot 2: Kapasitas modulasi radio PHY Rate (25%)
        val phyScore = when {
            maxPhyRate >= 1200 -> 100 // WiFi 6 / 7
            maxPhyRate >= 866 -> 85   // WiFi 5 80MHz
            maxPhyRate >= 300 -> 70   // WiFi 4 40MHz
            maxPhyRate >= 144 -> 50   // WiFi 4 20MHz
            else -> 30                // Legacy 802.11a/b/g
        }

        // Bobot 3: Kebersihan kanal dari interferensi tetangga (25%)
        val cleanScore = max(10, (100 - (channelPenalty * 10)).toInt())

        val composite = (signalScore * 0.50) + (phyScore * 0.25) + (cleanScore * 0.25)
        return max(1, min(100, composite.toInt()))
    }
}
