package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.ChannelRating
import com.wefi.analyzer.domain.model.WifiAccessPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Menghitung tingkat interferensi sinyal ko-kanal dan saluran bersebelahan
 * untuk memberikan rating bintang 1-10 pada setiap kanal frekuensi.
 */
class CalculateChannelRatingUseCase {

    fun execute(apList: List<WifiAccessPoint>, bandGhz: Double): List<ChannelRating> {
        val channels = if (bandGhz == 2.4) {
            (1..13).toList()
        } else {
            listOf(36, 40, 44, 48, 149, 153, 157, 161)
        }

        val apsInBand = apList.filter {
            if (bandGhz == 2.4) it.frequencyMhz < 3000 else it.frequencyMhz >= 5000
        }

        val ratings = channels.map { ch ->
            var penalty = 0.0
            var count = 0

            apsInBand.forEach { ap ->
                val chDiff = abs(ap.channel - ch)
                val signalWeight = (100.0 - abs(ap.rssi.toDouble())) / 10.0

                if (bandGhz == 2.4) {
                    // Pada 2.4GHz, kanal bersebelahan tumpang-tindih spektral jika selisih <= 4
                    if (chDiff <= 4) {
                        val overlapFactor = (5.0 - chDiff) / 5.0
                        penalty += signalWeight * overlapFactor
                        if (chDiff == 0) count++
                    }
                } else {
                    // Pada 5GHz, kanal 20MHz bersifat ortogonal (non-overlapping).
                    // Tumpang tindih hanya terjadi jika ko-kanal (chDiff == 0) atau kanal lebar (40/80MHz)
                    val isCoveredByWideChannel = ap.channelWidthMhz > 20 && chDiff < (ap.channelWidthMhz / 5)
                    if (chDiff == 0) {
                        penalty += signalWeight
                        count++
                    } else if (isCoveredByWideChannel) {
                        penalty += signalWeight * 0.5
                    }
                }
            }

            val stars = max(1, min(10, 10 - penalty.toInt()))
            val reason = when {
                stars >= 9 -> "Sangat bersih, minim interferensi tetangga"
                stars >= 7 -> "Cukup baik, interferensi rendah"
                stars >= 5 -> "Sedang, terdeteksi $count router di kanal ini"
                else -> "Padat! Banyak router tumpang tindih ($count AP)"
            }

            ChannelRating(
                channel = ch,
                frequencyMhz = if (bandGhz == 2.4) 2407 + (ch * 5) else 5000 + (ch * 5),
                stars = stars,
                apCount = count,
                isRecommended = false,
                reason = reason
            )
        }

        val maxStars = ratings.maxOfOrNull { it.stars } ?: 10
        return ratings.map {
            if (it.stars == maxStars) it.copy(isRecommended = true) else it
        }
    }
}
