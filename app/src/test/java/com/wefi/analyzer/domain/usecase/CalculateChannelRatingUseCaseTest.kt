package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateChannelRatingUseCaseTest {
    private val useCase = CalculateChannelRatingUseCase()

    @Test
    fun `channel with no APs should have maximum 10 stars rating`() {
        val ratings = useCase.execute(emptyList(), bandGhz = 2.4)
        val channel1 = ratings.first { it.channel == 1 }
        assertEquals(10, channel1.stars)
        assertTrue(channel1.isRecommended)
    }

    @Test
    fun `channel crowded with strong APs has lower stars than clean channel`() {
        val crowdedAp = WifiAccessPoint(
            bssid = "00:11:22:33:44:55",
            ssid = "CrowdedRouter",
            rssi = -40,
            frequencyMhz = 2437, // Ch 6
            channel = 6
        )
        val ratings = useCase.execute(listOf(crowdedAp), bandGhz = 2.4)
        val ch6 = ratings.first { it.channel == 6 }
        val ch1 = ratings.first { it.channel == 1 }

        assertTrue(ch6.stars < ch1.stars)
        assertTrue(ch1.isRecommended)
    }
}
