package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.EnvironmentPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateDistanceUseCaseTest {
    private val useCase = CalculateDistanceUseCase()

    @Test
    fun `when RSSI equals reference power at 1 meter then distance should be 1 meter`() {
        // 2.4 GHz reference power A0 is -40 dBm
        val distance = useCase.execute(rssi = -40, frequencyMhz = 2412, preset = EnvironmentPreset.INDOOR)
        assertEquals(1.0, distance, 0.05)
    }

    @Test
    fun `when RSSI drops by 10 times n then distance increases tenfold`() {
        // At n = 2.0 (Outdoor), every 20 dBm drop multiplies distance by 10
        val d1 = useCase.execute(rssi = -40, frequencyMhz = 2412, preset = EnvironmentPreset.OUTDOOR)
        val d2 = useCase.execute(rssi = -60, frequencyMhz = 2412, preset = EnvironmentPreset.OUTDOOR)
        assertEquals(1.0, d1, 0.05)
        assertEquals(10.0, d2, 0.5)
    }

    @Test
    fun `when frequency is 5 GHz reference power is calibrated at minus 45 dBm`() {
        val distance = useCase.execute(rssi = -45, frequencyMhz = 5180, preset = EnvironmentPreset.OUTDOOR)
        assertEquals(1.0, distance, 0.05)
    }
}
