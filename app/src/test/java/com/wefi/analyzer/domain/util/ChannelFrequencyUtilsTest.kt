package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFrequencyUtilsTest {
    @Test
    fun `verify 2_4 GHz channel mapping`() {
        assertEquals(1, ChannelFrequencyUtils.toChannel(2412))
        assertEquals(6, ChannelFrequencyUtils.toChannel(2437))
        assertEquals(11, ChannelFrequencyUtils.toChannel(2462))
        assertEquals(13, ChannelFrequencyUtils.toChannel(2472))
        assertEquals(14, ChannelFrequencyUtils.toChannel(2484))
    }

    @Test
    fun `verify 5 GHz channel mapping`() {
        assertEquals(36, ChannelFrequencyUtils.toChannel(5180))
        assertEquals(40, ChannelFrequencyUtils.toChannel(5200))
        assertEquals(149, ChannelFrequencyUtils.toChannel(5745))
        assertEquals(165, ChannelFrequencyUtils.toChannel(5825))
    }

    @Test
    fun `verify 6 GHz channel mapping`() {
        assertEquals(1, ChannelFrequencyUtils.toChannel(5945))
        assertEquals(5, ChannelFrequencyUtils.toChannel(5965))
    }

    @Test
    fun `verify channel to center frequency reverse mapping`() {
        assertEquals(2412, ChannelFrequencyUtils.toFrequency(1, is24GHz = true))
        assertEquals(2437, ChannelFrequencyUtils.toFrequency(6, is24GHz = true))
        assertEquals(5180, ChannelFrequencyUtils.toFrequency(36, is24GHz = false))
    }

    @Test
    fun `verify invalid and edge-case frequencies return 0`() {
        assertEquals(0, ChannelFrequencyUtils.toChannel(0))
        assertEquals(0, ChannelFrequencyUtils.toChannel(-2412))
        assertEquals(0, ChannelFrequencyUtils.toChannel(99999))
        assertEquals(0, ChannelFrequencyUtils.toFrequency(0, is24GHz = true))
        assertEquals(0, ChannelFrequencyUtils.toFrequency(-1, is24GHz = false))
    }
}
