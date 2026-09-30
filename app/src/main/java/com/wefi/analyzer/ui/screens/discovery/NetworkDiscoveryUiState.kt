package com.wefi.analyzer.ui.screens.discovery

import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.SubnetInfo

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
    val isClientIsolationSuspected: Boolean = false
)
