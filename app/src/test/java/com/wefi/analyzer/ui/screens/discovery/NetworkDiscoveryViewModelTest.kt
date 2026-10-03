package com.wefi.analyzer.ui.screens.discovery

import com.wefi.analyzer.domain.model.BannerInfo
import com.wefi.analyzer.domain.model.CveMatch
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.PmfMode
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.PortStatus
import com.wefi.analyzer.domain.model.ServiceInfo
import com.wefi.analyzer.domain.model.SubnetInfo
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.NetworkDiscoveryRepository
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkDiscoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeNetworkDiscoveryRepository
    private lateinit var viewModel: NetworkDiscoveryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeNetworkDiscoveryRepository()
        viewModel = NetworkDiscoveryViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStartDiscovery_subnetNull_transitionsToError() = runTest {
        fakeRepo.stubSubnet = null

        viewModel.startDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanPhase.ERROR, state.phase)
        assertFalse(state.isScanning)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("subnet Wi-Fi"))
    }

    @Test
    fun testStartDiscovery_noHostsAlive_flagsClientIsolation() = runTest {
        fakeRepo.stubSubnet = SubnetInfo(
            baseIp = "192.168.1.0",
            netmask = "255.255.255.0",
            prefixLength = 24,
            gatewayIp = "192.168.1.1",
            hostsToScan = listOf("192.168.1.1", "192.168.1.100")
        )
        fakeRepo.stubPingSweepHosts = emptyList()

        viewModel.startDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanPhase.FINISHED, state.phase)
        assertFalse(state.isScanning)
        assertTrue(state.isClientIsolationSuspected)
        assertTrue(state.hosts.isEmpty())
        assertNotNull(state.report)
    }

    @Test
    fun testStartDiscovery_hostsFound_progressivePortScanAndFinish() = runTest {
        val testSubnet = SubnetInfo(
            baseIp = "192.168.1.0",
            netmask = "255.255.255.0",
            prefixLength = 24,
            gatewayIp = "192.168.1.1",
            hostsToScan = listOf("192.168.1.1", "192.168.1.50")
        )
        fakeRepo.stubSubnet = testSubnet

        val host1 = DiscoveredHost(
            ip = "192.168.1.1",
            macAddress = "00:11:22:33:44:55",
            vendor = "OpenWrt",
            responseTimeMs = 5L,
            isGateway = true
        )
        val host2 = DiscoveredHost(
            ip = "192.168.1.50",
            macAddress = "BC:AD:28:11:22:33",
            vendor = "Hikvision",
            responseTimeMs = 12L,
            isGateway = false
        )
        fakeRepo.stubPingSweepHosts = listOf(host1, host2)
        fakeRepo.stubPortResults["192.168.1.1"] = listOf(
            PortResult(53, PortStatus.OPEN, "DNS", 5L),
            PortResult(80, PortStatus.OPEN, "HTTP", 6L)
        )
        fakeRepo.stubPortResults["192.168.1.50"] = listOf(
            PortResult(554, PortStatus.OPEN, "RTSP", 10L),
            PortResult(80, PortStatus.OPEN, "HTTP", 12L)
        )

        viewModel.startDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanPhase.FINISHED, state.phase)
        assertFalse(state.isScanning)
        assertEquals(2, state.hosts.size)
        // Gateway should be sorted first
        assertTrue(state.hosts[0].isGateway)
        assertEquals("192.168.1.1", state.hosts[0].ip)
        assertEquals(2, state.hosts[0].openPorts.size)

        assertEquals("192.168.1.50", state.hosts[1].ip)
        assertEquals(2, state.hosts[1].openPorts.size)
        assertNotNull(state.report)
    }

    @Test
    fun testCancelDiscovery_updatesStateToCancelled() = runTest {
        fakeRepo.stubSubnet = SubnetInfo(
            baseIp = "192.168.1.0",
            netmask = "255.255.255.0",
            prefixLength = 24,
            gatewayIp = "192.168.1.1",
            hostsToScan = listOf("192.168.1.1")
        )

        viewModel.startDiscovery()
        viewModel.cancelDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanPhase.CANCELLED, state.phase)
        assertFalse(state.isScanning)
    }

    @Test
    fun testPrepareExport_formatsTextAndJsonReports() = runTest {
        val testSubnet = SubnetInfo(
            baseIp = "192.168.1.0",
            netmask = "255.255.255.0",
            prefixLength = 24,
            gatewayIp = "192.168.1.1",
            hostsToScan = listOf("192.168.1.1")
        )
        fakeRepo.stubSubnet = testSubnet
        fakeRepo.stubPingSweepHosts = listOf(
            DiscoveredHost(
                ip = "192.168.1.1",
                vendor = "TestRouter",
                responseTimeMs = 2L,
                isGateway = true
            )
        )

        viewModel.startDiscovery()
        advanceUntilIdle()

        viewModel.prepareExport()
        val state = viewModel.uiState.value
        assertNotNull(state.exportedReportText)
        assertTrue(state.exportedReportText!!.contains("STUB_TEXT_REPORT"))

        val json = viewModel.getExportJson()
        assertTrue(json.contains("STUB_JSON_REPORT"))
    }

    @Test
    fun testSelectHostForDetail_updatesSelectedHostState() = runTest {
        val host = DiscoveredHost(
            ip = "192.168.1.10",
            responseTimeMs = 15L
        )

        assertNull(viewModel.selectedHostForDetail.value)

        viewModel.selectHostForDetail(host)
        assertEquals("192.168.1.10", viewModel.selectedHostForDetail.value?.ip)

        viewModel.selectHostForDetail(null)
        assertNull(viewModel.selectedHostForDetail.value)
    }

    @Test
    fun testScanHostDeep_enrichesHostPortsAndUpdatesState() = runTest {
        val initialHost = DiscoveredHost(
            ip = "192.168.1.20",
            responseTimeMs = 20L
        )
        fakeRepo.stubPortResults["192.168.1.20"] = listOf(
            PortResult(8291, PortStatus.OPEN, "MikroTik Winbox", 8L),
            PortResult(80, PortStatus.OPEN, "HTTP Admin", 5L)
        )

        viewModel.selectHostForDetail(initialHost)
        viewModel.scanHostDeep(initialHost)
        advanceUntilIdle()

        assertFalse(viewModel.isDeepScanningHost.value)
        val updatedSelected = viewModel.selectedHostForDetail.value
        assertNotNull(updatedSelected)
        assertEquals("192.168.1.20", updatedSelected?.ip)
        assertEquals(2, updatedSelected?.openPorts?.size)
        assertTrue(updatedSelected?.openPorts?.any { it.port == 8291 } == true)
    }

    @Test
    fun testSetReconTab_updatesSelectedTab() = runTest {
        assertEquals(SecurityReconTab.LAN_SURFACE, viewModel.uiState.value.selectedTab)
        viewModel.setReconTab(SecurityReconTab.WIRELESS_RECON)
        assertEquals(SecurityReconTab.WIRELESS_RECON, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testSetWirelessFilterOnlyVulnerable_updatesFilter() = runTest {
        assertFalse(viewModel.uiState.value.wirelessFilterOnlyVulnerable)
        viewModel.setWirelessFilterOnlyVulnerable(true)
        assertTrue(viewModel.uiState.value.wirelessFilterOnlyVulnerable)
    }

    @Test
    fun testEvaluateWirelessAudits_computesAuditItemsAndReconStats() = runTest {
        val ap1 = WifiAccessPoint(
            bssid = "00:11:22:33:44:55",
            ssid = "Lab_Vulnerable",
            rssi = -60,
            frequencyMhz = 2437,
            channel = 6,
            security = "WPA2-PSK",
            capabilities = "[WPA2-PSK-CCMP][WPS]"
        )
        val ap2 = WifiAccessPoint(
            bssid = "AA:BB:CC:DD:EE:FF",
            ssid = "Lab_Secure",
            rssi = -55,
            frequencyMhz = 2462,
            channel = 11,
            security = "WPA3-SAE",
            capabilities = "[WPA3-SAE][PMF-R]"
        )

        viewModel.evaluateWirelessAudits(listOf(ap1, ap2))

        val state = viewModel.uiState.value
        assertEquals(2, state.wirelessAuditItems.size)
        assertEquals(1, state.reconStatSummary.wpsEnabledApsCount)
        assertEquals(1, state.reconStatSummary.noPmfApsCount)

        val item1 = state.wirelessAuditItems.first { it.ssid == "Lab_Vulnerable" }
        assertTrue(item1.hasWps)
        assertEquals(PmfMode.NONE, item1.pmfMode)

        val item2 = state.wirelessAuditItems.first { it.ssid == "Lab_Secure" }
        assertFalse(item2.hasWps)
        assertEquals(PmfMode.REQUIRED, item2.pmfMode)
    }

    @Test
    fun testWifiScannerIntegration_updatesReconStateAutomatically() = runTest {
        val fakeScanner = FakeWifiScannerRepository()
        val vmWithScanner = NetworkDiscoveryViewModel(fakeRepo, fakeScanner)

        val ap = WifiAccessPoint(
            bssid = "11:22:33:44:55:66",
            ssid = "Rogue_Twin",
            rssi = -40,
            frequencyMhz = 2412,
            channel = 1,
            security = "Open",
            capabilities = "[ESS]"
        )
        fakeScanner.emitAps(listOf(ap))
        advanceUntilIdle()

        val state = vmWithScanner.uiState.value
        assertEquals(1, state.wirelessAuditItems.size)
        assertEquals("Rogue_Twin", state.wirelessAuditItems[0].ssid)
    }
}

private class FakeNetworkDiscoveryRepository : NetworkDiscoveryRepository {
    var stubSubnet: SubnetInfo? = null
    var stubPingSweepHosts: List<DiscoveredHost> = emptyList()
    val stubPortResults = mutableMapOf<String, List<PortResult>>()
    var stubMdnsServices: List<ServiceInfo> = emptyList()
    var stubSsdpServices: List<ServiceInfo> = emptyList()

    override fun getSubnetInfo(): SubnetInfo? = stubSubnet

    override fun pingSweep(subnet: SubnetInfo): Flow<DiscoveredHost> {
        return flowOf(*stubPingSweepHosts.toTypedArray())
    }

    override fun scanPorts(hostIp: String, ports: List<Int>): Flow<PortResult> {
        val results = stubPortResults[hostIp] ?: ports.map {
            PortResult(it, PortStatus.CLOSED, "Service", 10L)
        }
        return flowOf(*results.toTypedArray())
    }

    override fun discoverMdns(timeoutMs: Long): Flow<ServiceInfo> {
        return flowOf(*stubMdnsServices.toTypedArray())
    }

    override fun discoverSsdp(timeoutMs: Long): Flow<ServiceInfo> {
        return flowOf(*stubSsdpServices.toTypedArray())
    }

    override fun lookupVendor(macAddress: String): String = "StubVendor"

    override suspend fun grabBanner(hostIp: String, port: Int): BannerInfo? {
        return BannerInfo(rawBanner = "Stub Banner", server = "StubServer")
    }

    override fun matchCve(vendor: String, model: String, firmware: String): List<CveMatch> = emptyList()

    override fun identifyDeviceType(openPorts: List<Int>, banner: BannerInfo?, services: List<ServiceInfo>): String {
        return if (openPorts.contains(554)) "Kamera CCTV / NVR Lab" else "Router / Gateway Lab"
    }

    override fun evaluateHostRisk(host: DiscoveredHost): com.wefi.analyzer.domain.model.HostRiskProfile {
        return com.wefi.analyzer.domain.model.HostRiskProfile(
            level = com.wefi.analyzer.domain.model.HostRiskLevel.LOW,
            score = 10,
            highlights = listOf("Stub risk")
        )
    }

    override fun classifyAssetCategory(host: DiscoveredHost): com.wefi.analyzer.domain.model.AssetCategory {
        return if (host.isGateway) com.wefi.analyzer.domain.model.AssetCategory.GATEWAY_ROUTER else com.wefi.analyzer.domain.model.AssetCategory.WORKSTATION
    }

    override fun hasCleartextManagement(host: DiscoveredHost): Boolean {
        return host.openPorts.any { it.port in listOf(21, 23, 80) }
    }

    override fun generateReport(hosts: List<DiscoveredHost>, durationMs: Long, subnet: String): DiscoveryReport {
        return DiscoveryReport(
            timestamp = 1000L,
            durationMs = durationMs,
            subnet = subnet,
            totalHostsScanned = 254,
            totalHostsAlive = hosts.size,
            hosts = hosts,
            isClientIsolationSuspected = hosts.isEmpty()
        )
    }

    override fun exportReportJson(report: DiscoveryReport): String = "{\"report\": \"STUB_JSON_REPORT\"}"

    override fun exportReportText(report: DiscoveryReport): String = "STUB_TEXT_REPORT"

    override fun teardown() {}
}

private class FakeWifiScannerRepository : WifiScannerRepository {
    private val _scanResults = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    override val scanResults: StateFlow<List<WifiAccessPoint>> = _scanResults.asStateFlow()
    override val isScanning: StateFlow<Boolean> = MutableStateFlow(false)
    override val selectedPreset: StateFlow<EnvironmentPreset> = MutableStateFlow(EnvironmentPreset.INDOOR)
    override val isWifiEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    fun emitAps(aps: List<WifiAccessPoint>) {
        _scanResults.value = aps
    }

    override fun startScan() {}
    override fun setEnvironmentPreset(preset: EnvironmentPreset) {}
}
