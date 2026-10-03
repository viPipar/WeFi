package com.wefi.analyzer.ui.screens.discovery

import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.SubnetInfo
import com.wefi.analyzer.domain.model.WirelessSecurityAuditItem

enum class ScanPhase {
    IDLE,
    DISCOVERING_SUBNET,
    PING_SWEEP,
    PORT_SCAN,
    BANNER_GRAB,
    FINISHED,
    CANCELLED,
    ERROR
}

enum class SecurityReconTab(val title: String) {
    LAN_SURFACE("LAN Attack Surface"),
    WIRELESS_RECON("Wireless Recon Matrix")
}

data class ReconStatSummary(
    val totalHosts: Int = 0,
    val criticalHosts: Int = 0,
    val cleartextPortsCount: Int = 0,
    val wpsEnabledApsCount: Int = 0,
    val noPmfApsCount: Int = 0,
    val rogueCandidatesCount: Int = 0
)

data class NetworkDiscoveryUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val subnetInfo: SubnetInfo? = null,
    val hosts: List<DiscoveredHost> = emptyList(),
    val currentHostScanned: String? = null,
    val progress: Float = 0f,
    val statusMessage: String = "Siap untuk memulai audit penemuan jaringan lab.",
    val isScanning: Boolean = false,
    val report: DiscoveryReport? = null,
    val errorMessage: String? = null,
    val exportedReportText: String? = null,
    val isClientIsolationSuspected: Boolean = false,
    val selectedTab: SecurityReconTab = SecurityReconTab.LAN_SURFACE,
    val wirelessAuditItems: List<WirelessSecurityAuditItem> = emptyList(),
    val wirelessFilterOnlyVulnerable: Boolean = false,
    val reconStatSummary: ReconStatSummary = ReconStatSummary()
)
