package com.wefi.analyzer.ui.screens.aroundcheck

import com.wefi.analyzer.data.repository.LabRouterAuditRepositoryImpl
import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.WifiScannerRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AroundCheckViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeScannerRepo: FakeWifiScannerRepository
    private lateinit var auditRepo: LabRouterAuditRepositoryImpl
    private lateinit var viewModel: AroundCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScannerRepo = FakeWifiScannerRepository()
        auditRepo = LabRouterAuditRepositoryImpl()
        viewModel = AroundCheckViewModel(fakeScannerRepo, auditRepo, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setQuery_updatesQueryState() {
        viewModel.setQuery("ilmukomputeripb")
        assertEquals("ilmukomputeripb", viewModel.query.value)
    }

    @Test
    fun setConsentGiven_togglesConsentState() {
        assertFalse(viewModel.isAuthorizedConsentGiven.value)
        viewModel.setConsentGiven(true)
        assertTrue(viewModel.isAuthorizedConsentGiven.value)
    }

    @Test
    fun startAuditSearch_withoutConsent_doesNotStart() = runTest(testDispatcher) {
        viewModel.setQuery("ilmukomputeripb")
        viewModel.setConsentGiven(false)
        viewModel.startAuditSearch()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isTestingInProgress.value)
    }

    @Test
    fun startAuditSearch_withConsent_executesAndMatchesAuthorizedTarget() = runTest(testDispatcher) {
        viewModel.setQuery("ilmukomputeripb")
        viewModel.setConsentGiven(true)

        viewModel.startAuditSearch()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isTestingInProgress.value)
        val targets = viewModel.targets.value
        val labAp = targets.firstOrNull { it.ssid == "ilmukomputeripb" }
        assertTrue(labAp != null)
        assertEquals(LabAuditStatus.MATCHED, labAp?.status)

        val publicAp = targets.firstOrNull { it.ssid == "Public-Cafe-WiFi" }
        assertTrue(publicAp != null)
        assertEquals(LabAuditStatus.UNAUTHORIZED, publicAp?.status)
    }

    @Test
    fun stopAudit_cancelsTesting() = runTest(testDispatcher) {
        viewModel.setQuery("ilmukomputeripb")
        viewModel.setConsentGiven(true)
        viewModel.startAuditSearch()

        viewModel.stopAudit()
        assertFalse(viewModel.isTestingInProgress.value)
    }
}

private class FakeWifiScannerRepository : WifiScannerRepository {
    private val _scanResults = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    override val scanResults: StateFlow<List<WifiAccessPoint>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedPreset = MutableStateFlow(EnvironmentPreset.INDOOR)
    override val selectedPreset: StateFlow<EnvironmentPreset> = _selectedPreset.asStateFlow()

    private val _isWifiEnabled = MutableStateFlow(true)
    override val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    override fun startScan() {}
    override fun setEnvironmentPreset(preset: EnvironmentPreset) {
        _selectedPreset.value = preset
    }
}
