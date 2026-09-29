package com.wefi.analyzer.ui.screens.aroundcheck

import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.repository.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AroundCheckViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeScanner: FakeWifiScanner
    private lateinit var fakeConnector: FakeWifiConnector
    private lateinit var viewModel: AroundCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScanner = FakeWifiScanner()
        fakeConnector = FakeWifiConnector()
        viewModel = AroundCheckViewModel(fakeScanner, fakeConnector, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openPasswordDialog_setsTargetAndResetsPasswordInput() {
        val item = WifiScanItem("Lab-Wifi", "00:11:22:33:44:55", -60, WifiSecurityType.WPA2, 2412, 1)
        viewModel.openPasswordDialog(item)

        assertEquals(item, viewModel.selectedItemForPasswordDialog.value)
        assertEquals("", viewModel.passwordInput.value)
        assertFalse(viewModel.isPasswordVisible.value)
    }

    @Test
    fun dismissPasswordDialog_clearsTargetAndInput() {
        val item = WifiScanItem("Lab-Wifi", "00:11:22:33:44:55", -60, WifiSecurityType.WPA2, 2412, 1)
        viewModel.openPasswordDialog(item)
        viewModel.setPasswordInput("secret123")
        viewModel.dismissPasswordDialog()

        assertNull(viewModel.selectedItemForPasswordDialog.value)
        assertEquals("", viewModel.passwordInput.value)
    }

    @Test
    fun submitConnect_invokesConnectorAndClearsDialog() = runTest(testDispatcher) {
        val item = WifiScanItem("Lab-Wifi", "00:11:22:33:44:55", -60, WifiSecurityType.WPA2, 2412, 1)
        viewModel.openPasswordDialog(item)
        viewModel.setPasswordInput("pass12345")
        viewModel.submitConnect()
        testScheduler.advanceUntilIdle()

        assertEquals("Lab-Wifi", fakeConnector.lastConnectSsid)
        assertEquals("pass12345", fakeConnector.lastConnectPassword)
        assertNull(viewModel.selectedItemForPasswordDialog.value)
        assertEquals("", viewModel.passwordInput.value)
    }

    @Test
    fun openPasswordDialog_forOpenNetwork_triggersConnectImmediately() = runTest(testDispatcher) {
        val openItem = WifiScanItem("Free-Lab-Wifi", "00:11:22:33:44:66", -50, WifiSecurityType.OPEN, 2412, 1)
        viewModel.openPasswordDialog(openItem)

        assertEquals("Free-Lab-Wifi", fakeConnector.lastConnectSsid)
        assertEquals("", fakeConnector.lastConnectPassword)
        assertNull(viewModel.selectedItemForPasswordDialog.value)
    }

    @Test
    fun cancelConnect_callsConnectorCancel() {
        viewModel.cancelConnect()
        assertTrue(fakeConnector.cancelCalled)
    }

    @Test
    fun forgetNetwork_callsConnectorForget() {
        viewModel.forgetNetwork("Lab-Wifi")
        assertEquals("Lab-Wifi", fakeConnector.lastForgottenSsid)
    }
}

private class FakeWifiScanner : WifiScanner {
    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    override val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()
    var startScanCalled = false

    override fun isLocationEnabled(): Boolean = true
    override fun startScan() {
        startScanCalled = true
    }
    override fun teardown() {}
}

private class FakeWifiConnector : WifiConnector {
    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    var lastConnectSsid: String? = null
    var lastConnectPassword: String? = null
    var cancelCalled = false
    var lastForgottenSsid: String? = null

    override fun connect(ssid: String, password: String, securityType: WifiSecurityType) {
        lastConnectSsid = ssid
        lastConnectPassword = password
        _connectState.value = WifiConnectState(
            targetSsid = ssid,
            status = WifiConnectStatus.WaitingApproval,
            message = "Menunggu persetujuan user..."
        )
    }

    override fun cancel() {
        cancelCalled = true
        _connectState.value = WifiConnectState(status = WifiConnectStatus.Idle)
    }

    override fun forgetNetwork(ssid: String) {
        lastForgottenSsid = ssid
        _connectState.value = WifiConnectState(targetSsid = ssid, status = WifiConnectStatus.Idle)
    }

    override fun teardown() {}
}
