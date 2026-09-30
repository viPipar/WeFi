package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.util.ConnectCheckResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            mainDispatcher = testDispatcher,
            sdkInt = 29
        )
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(WifiConnectStatus.Idle, connector.connectState.value.status)
        assertTrue(connector.auditLogger.auditLogs.value.isEmpty())
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
    fun connect_whenServicesNull_returnsFailedStatusAndLogsAudit() = runTest(testDispatcher) {
        connector.connect("Test-SSID", "password123", WifiSecurityType.WPA2)
        assertEquals(WifiConnectStatus.Failed, connector.connectState.value.status)
    }

    @Test
    fun canConnect_delegatesToThrottler() {
        val check = connector.canConnect("Lab-SSID")
        assertTrue(check is ConnectCheckResult.Allowed)
        assertEquals(0, connector.remainingCooldownSeconds("Lab-SSID"))
    }

    @Test
    fun connect_whenWpa2PasswordTooShort_failsImmediatelyWithoutCrashing() = runTest(testDispatcher) {
        connector.connect("Lab-AP", "short", WifiSecurityType.WPA2)
        assertEquals(WifiConnectStatus.Failed, connector.connectState.value.status)
        assertTrue(connector.connectState.value.message.contains("8"))
    }

    @Test
    fun connect_whenWepNetwork_failsWithDeprecatedMessage() = runTest(testDispatcher) {
        connector.connect("Legacy-AP", "12345", WifiSecurityType.WEP)
        assertEquals(WifiConnectStatus.Failed, connector.connectState.value.status)
        assertTrue(connector.connectState.value.message.contains("WEP"))
    }
}
