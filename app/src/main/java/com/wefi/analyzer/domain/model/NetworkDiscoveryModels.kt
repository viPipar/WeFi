package com.wefi.analyzer.domain.model

enum class PortStatus {
    OPEN,
    CLOSED,
    TIMEOUT
}

data class PortResult(
    val port: Int,
    val status: PortStatus,
    val serviceName: String,
    val responseTimeMs: Long
)

data class BannerInfo(
    val rawBanner: String,
    val server: String? = null,
    val xPoweredBy: String? = null,
    val rtspServer: String? = null,
    val onvifManufacturer: String? = null,
    val onvifModel: String? = null,
    val onvifFirmware: String? = null
)

data class CveMatch(
    val cveId: String,
    val cvssScore: Double,
    val severity: String,
    val vendor: String,
    val affectedModel: String,
    val description: String
)

data class ServiceInfo(
    val serviceName: String,
    val serviceType: String,
    val host: String,
    val ip: String,
    val port: Int,
    val source: String // "mDNS" or "SSDP" or "PortProbe"
)

data class DiscoveredHost(
    val ip: String,
    val macAddress: String? = null,
    val vendor: String = "Tidak Diketahui",
    val responseTimeMs: Long = 0,
    val openPorts: List<PortResult> = emptyList(),
    val services: List<ServiceInfo> = emptyList(),
    val banner: BannerInfo? = null,
    val probableDeviceType: String = "Perangkat Jaringan",
    val cveMatches: List<CveMatch> = emptyList(),
    val isGateway: Boolean = false
)

data class SubnetInfo(
    val baseIp: String,
    val netmask: String,
    val prefixLength: Int,
    val gatewayIp: String?,
    val hostsToScan: List<String>
)

data class DiscoveryReport(
    val timestamp: Long,
    val durationMs: Long,
    val subnet: String,
    val totalHostsScanned: Int,
    val totalHostsAlive: Int,
    val hosts: List<DiscoveredHost>,
    val isClientIsolationSuspected: Boolean
)
