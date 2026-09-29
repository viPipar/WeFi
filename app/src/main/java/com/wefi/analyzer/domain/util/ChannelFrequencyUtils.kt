package com.wefi.analyzer.domain.util

/**
 * Utilitas konversi antara frekuensi radio (MHz) dan nomor kanal IEEE 802.11.
 */
object ChannelFrequencyUtils {

    fun toChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5945..7105 -> (frequencyMhz - 5940) / 5
            else -> 0
        }
    }

    fun toFrequency(channel: Int, is24GHz: Boolean): Int {
        if (channel <= 0) return 0
        return if (is24GHz) {
            if (channel == 14) 2484 else 2407 + (channel * 5)
        } else {
            5000 + (channel * 5)
        }
    }

    fun getChannelsForBand(bandGhz: Double): List<Int> {
        return when (bandGhz) {
            2.4 -> (1..13).toList()
            5.0 -> listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)
            6.0 -> (1..93 step 4).toList()
            else -> (1..13).toList()
        }
    }
}
