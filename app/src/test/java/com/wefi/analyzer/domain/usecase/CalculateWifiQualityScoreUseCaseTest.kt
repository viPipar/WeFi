package com.wefi.analyzer.domain.usecase

import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateWifiQualityScoreUseCaseTest {
    private val useCase = CalculateWifiQualityScoreUseCase()

    @Test
    fun `strong signal with wifi 6 and clean channel yields high score`() {
        val score = useCase.execute(rssi = -45, maxPhyRate = 1201, channelPenalty = 0.0)
        assertTrue(score >= 85)
    }

    @Test
    fun `weak signal with legacy rate and crowded channel yields low score`() {
        val score = useCase.execute(rssi = -88, maxPhyRate = 54, channelPenalty = 8.0)
        assertTrue(score <= 45)
    }
}
