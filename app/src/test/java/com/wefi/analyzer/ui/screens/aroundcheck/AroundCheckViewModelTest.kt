package com.wefi.analyzer.ui.screens.aroundcheck

import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiAuditResult
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiAuditLogger
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.repository.WifiScanner
import com.wefi.analyzer.domain.util.ConnectCheckResult
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
    private lateinit var fakeAuditLogger: FakeWifiAuditLogger
    private lateinit var viewModel: AroundCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScanner = FakeWifiScanner()
        fakeConnector = FakeWifiConnector()
        fakeAuditLogger = FakeWifiAuditLogger()
        viewModel = AroundCheckViewModel(fakeScanner, fakeConnector, fakeAuditLogger, testDispatcher)
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

    @Test
    fun auditLogs_exposesLoggedEntries() {
        fakeAuditLogger.record(
            WifiAuditLogEntry(
                ssid = "Lab-Wifi-1",
                result = WifiAuditResult.CONNECTED,
                reason = "Success"
            )
        )
        assertEquals(1, viewModel.auditLogs.value.size)
        assertEquals("Lab-Wifi-1", viewModel.auditLogs.value[0].ssid)
    }

    @Test
    fun canConnectToSsid_delegatesToConnector() {
        assertTrue(viewModel.canConnectToSsid("Lab-Wifi"))
        fakeConnector.allowConnect = false
        assertFalse(viewModel.canConnectToSsid("Lab-Wifi"))
    }

    @Test
    fun isItemWaitingApproval_returnsTrueWhenTargetAndStatusMatch() {
        assertFalse(viewModel.isItemWaitingApproval("Lab-Wifi"))
        fakeConnector.setWaitingApproval("Lab-Wifi")
        assertTrue(viewModel.isItemWaitingApproval("Lab-Wifi"))
        assertFalse(viewModel.isItemWaitingApproval("Other-Wifi"))
    }
    @Test
    fun setTopPasswordInput_updatesValue() {
        viewModel.setTopPasswordInput("labpass123")
        assertEquals("labpass123", viewModel.topPasswordInput.value)
    }

    @Test
    fun toggleTopPasswordVisibility_togglesBoolean() {
        assertFalse(viewModel.isTopPasswordVisible.value)
        viewModel.toggleTopPasswordVisibility()
        assertTrue(viewModel.isTopPasswordVisible.value)
        viewModel.toggleTopPasswordVisibility()
        assertFalse(viewModel.isTopPasswordVisible.value)
    }

    @Test
    fun startSequentialTest_withEmptyCandidates_setsMessageAndDoesNotStart() = runTest(testDispatcher) {
        fakeScanner.setScanItems(emptyList())
        viewModel.setTopPasswordInput("pass123")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertEquals("Daftar Wi-Fi kosong. Silakan scan terlebih dahulu.", viewModel.sequentialTestMessage.value)
        assertNull(fakeConnector.lastConnectSsid)
    }

    @Test
    fun startSequentialTest_initiatesConnectOnFirstCandidate() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Lab-AP-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.isSequentialTesting.value)
        assertEquals(0, viewModel.currentCandidateIndex.value)
        assertEquals("Lab-AP-1", fakeConnector.lastConnectSsid)
        assertEquals("labSecret", fakeConnector.lastConnectPassword)
    }

    @Test
    fun startSequentialTest_whenCandidateSucceeds_stopsTestingWithSuccessMessage() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Connected))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertTrue(viewModel.sequentialTestMessage.value.contains("Berhasil tersambung"))
    }

    @Test
    fun startSequentialTest_whenFirstCandidateFails_advancesToSecondCandidateAfterCooldown() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Lab-AP-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.currentCandidateIndex.value)
        assertEquals("Lab-AP-1", fakeConnector.lastConnectSsid)

        // Simulasikan user menolak atau kegagalan koneksi di AP 1
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Rejected))
        testScheduler.advanceUntilIdle()

        // Harus berpindah ke AP 2
        assertEquals(1, viewModel.currentCandidateIndex.value)
        assertEquals("Lab-AP-2", fakeConnector.lastConnectSsid)
        assertEquals("labSecret", fakeConnector.lastConnectPassword)
        assertTrue(viewModel.isSequentialTesting.value)
    }

    @Test
    fun cancelSequentialTest_stopsLoopAndCallsConnectorCancel() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.isSequentialTesting.value)
        viewModel.cancelSequentialTest()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertTrue(fakeConnector.cancelCalled)
        assertEquals("Pengujian dihentikan.", viewModel.sequentialTestMessage.value)
    }

    @Test
    fun startSequentialTest_whenCooldownActive_waitsAndRecoversWithoutDeadlock() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Lab-AP-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        // Emulasikan kegagalan kandidat 1
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Failed, message = "Ditolak"))
        testScheduler.advanceUntilIdle()

        // Harus melanjutkan ke AP 2 tanpa menggantung di Cooldown
        assertEquals(1, viewModel.currentCandidateIndex.value)
        assertEquals("Lab-AP-2", fakeConnector.lastConnectSsid)
    }

    @Test
    fun startSequentialTest_whenConnectorEmitsCooldown_handlesWithoutHanging() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        // Emulasikan connector mengembalikan Cooldown(2)
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Cooldown(2)))
        testScheduler.advanceTimeBy(3000L)
        testScheduler.advanceUntilIdle()

        // Kemudian emulasikan berhasil
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Connected))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertTrue(viewModel.sequentialTestMessage.value.contains("Berhasil tersambung"))
    }
}

private class FakeWifiScanner : WifiScanner {
    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    override val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    fun setScanItems(items: List<WifiScanItem>) {
        _scanState.value = WifiScanState.Success(items)
    }

    private val _lastScanTimestamp = MutableStateFlow(0L)
    override val lastScanTimestamp: StateFlow<Long> = _lastScanTimestamp.asStateFlow()

    private val _remainingScanCooldown = MutableStateFlow(0)
    override val remainingScanCooldownSeconds: StateFlow<Int> = _remainingScanCooldown.asStateFlow()

    override val isThrottleEnabledOnDevice: Boolean = true

    override fun isLocationEnabled(): Boolean = true
    override fun startScan(): Boolean = true
    override fun refreshFromCache() {}
    override fun toggleLabScanThrottle(enable: Boolean): Boolean = false
    override fun teardown() {}
}

private class FakeWifiConnector : WifiConnector {
    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    var lastConnectSsid: String? = null
    var lastConnectPassword: String? = null
    var cancelCalled = false
    var lastForgottenSsid: String? = null
    var allowConnect = true

    fun setWaitingApproval(ssid: String) {
        _connectState.value = WifiConnectState(targetSsid = ssid, status = WifiConnectStatus.WaitingApproval)
    }

    fun emitConnectState(state: WifiConnectState) {
        _connectState.value = state
    }

    override fun canConnect(ssid: String): ConnectCheckResult {
        return if (allowConnect) ConnectCheckResult.Allowed else ConnectCheckResult.Blocked("Cooldown", 5)
    }

    override fun remainingCooldownSeconds(ssid: String): Int = if (allowConnect) 0 else 5

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

private class FakeWifiAuditLogger : WifiAuditLogger {
    private val _auditLogs = MutableStateFlow<List<WifiAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<WifiAuditLogEntry>> = _auditLogs.asStateFlow()

    override fun record(entry: WifiAuditLogEntry) {
        _auditLogs.value = listOf(entry) + _auditLogs.value
    }

    override fun clear() {
        _auditLogs.value = emptyList()
    }
}
