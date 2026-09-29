package com.wefi.analyzer.domain.usecase

/**
 * Menghitung Theoretical Maximum PHY Data Rate (Kapasitas Radio Maksimum)
 * berdasarkan standar generasi IEEE 802.11 dan lebar kanal (MHz).
 */
class ParsePhyCapabilitiesUseCase {

    fun execute(standard: String, widthMhz: Int): Int {
        val std = standard.uppercase()
        return when {
            std.contains("BE") || std.contains("WIFI 7") || std.contains("7") -> {
                when {
                    widthMhz >= 320 -> 4800
                    widthMhz >= 160 -> 2400
                    widthMhz >= 80 -> 1200
                    else -> 600
                }
            }
            std.contains("AX") || std.contains("WIFI 6") || std.contains("6") -> {
                when {
                    widthMhz >= 160 -> 2402
                    widthMhz >= 80 -> 1201
                    widthMhz >= 40 -> 574
                    else -> 287
                }
            }
            std.contains("AC") || std.contains("WIFI 5") || std.contains("5") -> {
                when {
                    widthMhz >= 160 -> 1733
                    widthMhz >= 80 -> 867
                    widthMhz >= 40 -> 433
                    else -> 200
                }
            }
            std.contains("N") || std.contains("WIFI 4") || std.contains("4") -> {
                if (widthMhz >= 40) 300 else 144
            }
            std.contains("G") || std.contains("A") -> 54
            std.contains("B") -> 11
            else -> 144
        }
    }
}
