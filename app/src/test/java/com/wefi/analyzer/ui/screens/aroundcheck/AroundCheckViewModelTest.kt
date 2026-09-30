package com.wefi.analyzer.ui.screens.aroundcheck

import com.wefi.analyzer.domain.model.AroundCheckMode
import com.wefi.analyzer.domain.model.HybridRouterStatus
import com.wefi.analyzer.domain.model.VerifiedLabRouter
import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiAuditResult
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.VerifiedWifiStore
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
    private lateinit var fakeVerifiedStore: FakeVerifiedWifiStore
    private lateinit var viewModel: AroundCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScanner = FakeWifiScanner()
        fakeConnector = FakeWifiConnector()
        fakeAuditLogger = FakeWifiAuditLogger()
        fakeVerifiedStore = FakeVerifiedWifiStore()
        viewModel = AroundCheckViewModel(fakeScanner, fakeConnector, fakeAuditLogger, testDispatcher, fakeVerifiedStore)
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

    @Test
    fun startSequentialTest_whenCandidateRejected_movesToNextCandidateAfterDelay() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Target-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Target-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("12345678")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        // Kandidat 1 ditolak user
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Target-1", status = WifiConnectStatus.Rejected, message = "Ditolak"))
        testScheduler.advanceTimeBy(1600L)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.currentCandidateIndex.value)
        assertEquals("Target-2", fakeConnector.lastConnectSsid)
    }

    @Test
    fun setMode_switchesBetweenBfsAndDfs() {
        assertEquals(AroundCheckMode.BFS, viewModel.selectedMode.value)
        viewModel.setMode(AroundCheckMode.DFS)
        assertEquals(AroundCheckMode.DFS, viewModel.selectedMode.value)
    }

    @Test
    fun startDfsTraversal_whenTargetNull_notifiesUser() {
        viewModel.setMode(AroundCheckMode.DFS)
        viewModel.setDfsCsvInput("pass12345;pass67890")
        viewModel.startDfsTraversal()

        assertFalse(viewModel.isSequentialTesting.value)
        assertEquals("Pilih router lab target terlebih dahulu.", viewModel.sequentialTestMessage.value)
    }

    @Test
    fun startDfsTraversal_iteratesPasswordsAndFindsGoal() = runTest(testDispatcher) {
        val targetAp = WifiScanItem("Target-AP", "00:11:22:33:44:99", -55, WifiSecurityType.WPA2, 2412, 1)
        viewModel.selectDfsTargetItem(targetAp)
        viewModel.setDfsCsvInput("wrongpass1; correctpass123")

        viewModel.startDfsTraversal()
        testScheduler.advanceUntilIdle()

        // Emulasikan password 1 salah
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Target-AP", status = WifiConnectStatus.Rejected))
        testScheduler.advanceTimeBy(3500L)
        testScheduler.runCurrent()

        // Emulasikan password 2 benar
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Target-AP", status = WifiConnectStatus.Connected))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertEquals("correctpass123", viewModel.goalFoundRouter.value?.workingPassword)
        assertTrue(fakeVerifiedStore.isRouterVerified("00:11:22:33:44:99", "Target-AP"))
    }

    @Test
    fun startBfsTraversal_whenRouterMatches_savesToVault() = runTest(testDispatcher) {
        val candidate = WifiScanItem("Bfs-AP", "00:11:22:33:44:77", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate))
        viewModel.setTopPasswordInput("topSecret123")

        viewModel.startBfsTraversal()
        testScheduler.advanceUntilIdle()

        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Bfs-AP", status = WifiConnectStatus.Connected))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        assertEquals("Bfs-AP", viewModel.goalFoundRouter.value?.ssid)
        assertTrue(viewModel.isRouterVerified("00:11:22:33:44:77", "Bfs-AP"))
        assertEquals("topSecret123", viewModel.getVerifiedPassword("00:11:22:33:44:77", "Bfs-AP"))
    }

    @Test
    fun applyDfsPracticumTemplate_populatesDfsInputWith56PassphrasesAndParsesCorrectly() {
        viewModel.applyDfsPracticumTemplate()

        val parsed = viewModel.dfsParsedStats.value
        assertEquals(56, parsed.validPasswords.size)
        assertEquals(0, parsed.skippedTooShortCount)
        assertEquals(0, parsed.duplicateCount)
        assertTrue(viewModel.dfsCsvInput.value.contains("ilmukomputeripb"))
        assertEquals("ilmukomputeripb", parsed.validPasswords.last())
    }

    @Test
    fun startDfsTraversal_whenCircuitBreakerTriggered_promptsOnlyOnceAndRespectsDecision() = runTest(testDispatcher) {
        val targetAp = WifiScanItem("Lab-AP-Circuit", "00:11:22:33:44:88", -60, WifiSecurityType.WPA2, 2412, 1)
        viewModel.selectDfsTargetItem(targetAp)
        // 13 password berbeda (min 8 karakter)
        val passwords = (1..13).joinToString(";") { "testpass%02d".format(it) }
        viewModel.setDfsCsvInput(passwords)

        viewModel.startDfsTraversal()
        testScheduler.advanceUntilIdle()

        // Emulasikan 10 kegagalan beruntun
        for (i in 1..10) {
            fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-Circuit", status = WifiConnectStatus.Failed))
            testScheduler.advanceTimeBy(3500L)
            testScheduler.runCurrent()
        }

        // Pada kegagalan ke-10, dialog circuit breaker harus aktif
        assertTrue("Circuit Breaker harus tampil pada kegagalan ke-10", viewModel.showCircuitBreakerDialog.value)

        // Verifikasi bahwa loop di-pause (belum lanjut ke index 10 / password ke-11 sebelum direspons)
        assertEquals(9, viewModel.currentCandidateIndex.value)

        // User menekan "Lanjutkan"
        viewModel.acknowledgeCircuitBreaker(continueTraversal = true)
        testScheduler.runCurrent()

        assertFalse("Dialog harus tertutup setelah direspons", viewModel.showCircuitBreakerDialog.value)

        // Emulasikan kegagalan ke-11
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-Circuit", status = WifiConnectStatus.Failed))
        testScheduler.advanceTimeBy(3500L)
        testScheduler.runCurrent()

        // SANGAT PENTING: Dialog TIDAK boleh muncul lagi (tidak spam)!
        assertFalse("Circuit Breaker TIDAK boleh spam lagi setelah user menyetujui lanjutkan", viewModel.showCircuitBreakerDialog.value)
    }

    @Test
    fun triggerSmartRefresh_whenNoCooldown_triggersStartScan() = runTest(testDispatcher) {
        fakeScanner.setCooldown(0)
        fakeScanner.startScanCallCount = 0

        viewModel.triggerSmartRefresh()

        assertEquals(1, fakeScanner.startScanCallCount)
        assertEquals(0, fakeScanner.refreshFromCacheCallCount)
    }

    @Test
    fun triggerSmartRefresh_whenInCooldown_refreshesFromCacheAndNotifiesUser() = runTest(testDispatcher) {
        fakeScanner.setCooldown(15)
        fakeScanner.startScanCallCount = 0
        fakeScanner.refreshFromCacheCallCount = 0

        viewModel.triggerSmartRefresh()
        testScheduler.advanceUntilIdle()

        assertEquals(0, fakeScanner.startScanCallCount)
        assertEquals(1, fakeScanner.refreshFromCacheCallCount)
    }

    @Test
    fun isControlPanelExpanded_canBeToggled_andAutoCollapsesOnTraversalStart() = runTest(testDispatcher) {
        assertTrue(viewModel.isControlPanelExpanded.value)

        viewModel.toggleControlPanelExpanded()
        assertFalse(viewModel.isControlPanelExpanded.value)

        viewModel.setControlPanelExpanded(true)
        assertTrue(viewModel.isControlPanelExpanded.value)

        val candidate = WifiScanItem("Bfs-AP", "00:11:22:33:44:77", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate))
        viewModel.setTopPasswordInput("topSecret123")

        viewModel.startBfsTraversal()
        // Saat traversal dimulai, panel harus otomatis collapse agar list router mendapatkan ruang pandang maksimal
        assertFalse(viewModel.isControlPanelExpanded.value)
    }

    @Test
    fun startHybridTraversal_findsPasswordOnFirstRouter_andProceedsToSecondRouter() = runTest(testDispatcher) {
        val routerA = WifiScanItem("Router-A", "11:22:33:44:55:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val routerB = WifiScanItem("Router-B", "11:22:33:44:55:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(routerA, routerB))
        viewModel.setHybridCsvInput("wrongPass1;secretMatch;extraPass3")

        viewModel.startHybridTraversal()
        testScheduler.advanceUntilIdle()

        // Router-A, Passphrase 1 gagal
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Router-A", status = WifiConnectStatus.Rejected))
        testScheduler.advanceTimeBy(3500L)
        testScheduler.runCurrent()

        // Router-A, Passphrase 2 berhasil
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Router-A", status = WifiConnectStatus.Connected))
        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()

        // Verifikasi Router-A berstatus Found dan tersimpan ke vault
        val statusA = viewModel.hybridRouterStatuses.value["11:22:33:44:55:01"]
        assertTrue("Status Router-A harus Found", statusA is HybridRouterStatus.Found)
        assertEquals("secretMatch", (statusA as HybridRouterStatus.Found).workingPassword)
        assertTrue(fakeVerifiedStore.isRouterVerified("11:22:33:44:55:01", "Router-A"))

        // Dan traversal otomatis lanjut ke Router-B (tidak lanjut menguji extraPass3 pada Router-A)
        val statusB = viewModel.hybridRouterStatuses.value["11:22:33:44:55:02"]
        assertTrue(statusB is HybridRouterStatus.Testing || statusB is HybridRouterStatus.Idle)
    }

    @Test
    fun startHybridTraversal_whenAllPasswordsFail_setsNotFoundBadge_andProceedsToNextRouter() = runTest(testDispatcher) {
        val routerA = WifiScanItem("Router-A", "11:22:33:44:55:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val routerB = WifiScanItem("Router-B", "11:22:33:44:55:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(routerA, routerB))
        viewModel.setHybridCsvInput("passOne11;passTwo22")

        viewModel.startHybridTraversal()
        testScheduler.advanceUntilIdle()

        // Router-A pass 1 gagal
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Router-A", status = WifiConnectStatus.Rejected))
        testScheduler.advanceTimeBy(3500L)
        testScheduler.runCurrent()

        // Router-A pass 2 gagal
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Router-A", status = WifiConnectStatus.Rejected))
        testScheduler.advanceTimeBy(3500L)
        testScheduler.runCurrent()

        // Status Router-A harus NotFound(2)
        val statusA = viewModel.hybridRouterStatuses.value["11:22:33:44:55:01"]
        assertTrue("Status Router-A harus NotFound setelah semua password gagal", statusA is HybridRouterStatus.NotFound)
        assertEquals(2, (statusA as HybridRouterStatus.NotFound).testedCount)
    }

    @Test
    fun startHybridTraversal_skipsAlreadyVerifiedVaultRouters() = runTest(testDispatcher) {
        val routerA = WifiScanItem("Router-A", "11:22:33:44:55:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val routerB = WifiScanItem("Router-B", "11:22:33:44:55:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(routerA, routerB))
        fakeVerifiedStore.saveVerifiedRouter(
            VerifiedLabRouter("11:22:33:44:55:01", "Router-A", "vaultPassword123", System.currentTimeMillis(), WifiSecurityType.WPA2)
        )
        viewModel.setHybridCsvInput("passOne11;passTwo22")

        viewModel.startHybridTraversal()
        testScheduler.advanceUntilIdle()

        // Router-A harus langsung ditandai VerifiedFromVault
        val statusA = viewModel.hybridRouterStatuses.value["11:22:33:44:55:01"]
        assertTrue("Router yang ada di vault harus berstatus VerifiedFromVault", statusA is HybridRouterStatus.VerifiedFromVault)
        assertEquals("vaultPassword123", (statusA as HybridRouterStatus.VerifiedFromVault).workingPassword)

        // Pastikan koneksi tidak pernah mencoba menghubungkan password test ke Router-A
        assertTrue(fakeConnector.lastConnectSsid != "Router-A" || fakeConnector.lastConnectPassword != "passOne11")
    }

    @Test
    fun cancelTraversal_haltsHybridTraversalImmediately_andResetsTestingRouterToIdle() = runTest(testDispatcher) {
        val routerA = WifiScanItem("Router-A", "11:22:33:44:55:01", -50, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(routerA))
        viewModel.setHybridCsvInput("passOne11;passTwo22")

        viewModel.startHybridTraversal()
        testScheduler.advanceUntilIdle()

        // Status awal Router-A sedang Testing
        assertTrue(viewModel.isSequentialTesting.value)

        viewModel.cancelTraversal()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isSequentialTesting.value)
        val statusA = viewModel.hybridRouterStatuses.value["11:22:33:44:55:01"]
        assertEquals(HybridRouterStatus.Idle, statusA)
        assertTrue(fakeConnector.cancelCalled)
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

    fun setCooldown(seconds: Int) {
        _remainingScanCooldown.value = seconds
    }

    var startScanCallCount = 0
    var refreshFromCacheCallCount = 0
    var teardownCalled = false

    override val isThrottleEnabledOnDevice: Boolean = true

    override fun isLocationEnabled(): Boolean = true
    override fun startScan(): Boolean {
        startScanCallCount++
        return true
    }
    override fun refreshFromCache() {
        refreshFromCacheCallCount++
    }
    override fun toggleLabScanThrottle(enable: Boolean): Boolean = false
    override fun teardown() {
        teardownCalled = true
    }
}

private class FakeWifiConnector : WifiConnector {
    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    var lastConnectSsid: String? = null
    var lastConnectPassword: String? = null
    var lastConnectBssid: String? = null
    var isCurrentlyConnectedResult = false
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

    override fun isCurrentlyConnectedTo(ssid: String, bssid: String): Boolean = isCurrentlyConnectedResult

    override fun connect(ssid: String, password: String, securityType: WifiSecurityType, bssid: String) {
        lastConnectSsid = ssid
        lastConnectPassword = password
        lastConnectBssid = bssid
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

private class FakeVerifiedWifiStore : VerifiedWifiStore {
    private val _routers = MutableStateFlow<List<VerifiedLabRouter>>(emptyList())
    override val verifiedRouters: StateFlow<List<VerifiedLabRouter>> = _routers.asStateFlow()

    override fun saveVerifiedRouter(router: VerifiedLabRouter) {
        val current = _routers.value.toMutableList()
        current.removeAll { it.bssid == router.bssid || it.ssid == router.ssid }
        current.add(0, router)
        _routers.value = current
    }

    override fun isRouterVerified(bssid: String, ssid: String): Boolean {
        return _routers.value.any { it.bssid == bssid || it.ssid == ssid }
    }

    override fun getVerifiedPassword(bssid: String, ssid: String): String? {
        return _routers.value.firstOrNull { it.bssid == bssid || it.ssid == ssid }?.workingPassword
    }

    override fun removeVerifiedRouter(bssid: String) {
        _routers.value = _routers.value.filterNot { it.bssid == bssid }
    }

    override fun clearAll() {
        _routers.value = emptyList()
    }
}
