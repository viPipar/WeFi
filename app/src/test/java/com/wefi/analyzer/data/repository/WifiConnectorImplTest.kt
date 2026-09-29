package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiSecurityType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiConnectorImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var connector: WifiConnectorImpl

    @Before
    fun setUp() {
        connector = WifiConnectorImpl(
            context = null,
            wifiManager = null,
            connectivityManager = null,
            mainDispatcher = testDispatcher
        )
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(WifiConnectStatus.Idle, connector.connectState.value.status)
    }

    @Test
    fun cancel_resetsToIdle() {
        connector.cancel()
        assertEquals(WifiConnectStatus.Idle, connector.connectState.value.status)
    }

    @Test
    fun forgetNetwork_resetsToIdleWithMessage() {
        connector.forgetNetwork("Test-SSID")
        assertEquals(WifiConnectStatus.Idle, connector.connectState.value.status)
        assertEquals("Test-SSID", connector.connectState.value.targetSsid)
    }

    @Test
    fun connect_whenServicesNull_returnsFailedStatus() = runTest(testDispatcher) {
        connector.connect("Test-SSID", "password123", WifiSecurityType.WPA2)
        assertEquals(WifiConnectStatus.Failed, connector.connectState.value.status)
    }
}
