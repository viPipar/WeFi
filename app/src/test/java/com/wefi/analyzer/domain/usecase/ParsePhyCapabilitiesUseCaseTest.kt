package com.wefi.analyzer.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ParsePhyCapabilitiesUseCaseTest {
    private val useCase = ParsePhyCapabilitiesUseCase()

    @Test
    fun `wifi 4 802_11n theoretical rate based on width`() {
        assertEquals(144, useCase.execute("802.11n", widthMhz = 20))
        assertEquals(300, useCase.execute("802.11n", widthMhz = 40))
    }

    @Test
    fun `wifi 5 802_11ac theoretical rate based on width`() {
        assertEquals(433, useCase.execute("802.11ac", widthMhz = 40))
        assertEquals(867, useCase.execute("802.11ac", widthMhz = 80))
    }

    @Test
    fun `wifi 6 802_11ax theoretical rate based on width`() {
        assertEquals(574, useCase.execute("802.11ax", widthMhz = 40))
        assertEquals(1201, useCase.execute("802.11ax", widthMhz = 80))
    }

    @Test
    fun `wifi 7 802_11be theoretical rate based on width`() {
        assertEquals(2400, useCase.execute("802.11be", widthMhz = 160))
    }
}
