package com.wefi.analyzer.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedNetworkInfoTest {

    @Test
    fun `default ConnectedNetworkInfo has safe zero values and null AP`() {
        val info = ConnectedNetworkInfo()

        assertNull(info.accessPoint)
        assertEquals(0, info.linkSpeedMbps)
        assertEquals("0.0.0.0", info.ipAddress)
        assertEquals("0.0.0.0", info.gatewayIp)
        assertEquals("0.0.0.0", info.dns1)
    }
}
