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

enum class HostRiskLevel(val label: String) {
    SAFE("Aman"),
    LOW("Rendah"),
    MEDIUM("Menengah"),
    HIGH("Tinggi"),
    CRITICAL("Kritis")
}

data class HostRiskProfile(
    val level: HostRiskLevel = HostRiskLevel.SAFE,
    val score: Int = 0,
    val highlights: List<String> = emptyList(),
    val summary: String = "",
    val recommendations: List<String> = emptyList()
)

enum class AssetCategory(val label: String) {
    GATEWAY_ROUTER("Router / Gateway"),
    SURVEILLANCE_CCTV("Kamera CCTV / NVR"),
    STORAGE_NAS("NAS / File Server"),
    IOT_BROKER("IoT Broker / Bridge"),
    WORKSTATION("Host / Komputer"),
    UNKNOWN("Perangkat Lain")
}

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
    val isGateway: Boolean = false,
    val riskProfile: HostRiskProfile = HostRiskProfile(),
    val assetCategory: AssetCategory = AssetCategory.UNKNOWN,
    val hasCleartextManagement: Boolean = false
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

enum class AuthStatus(val label: String) {
    UNPROTECTED_EXPOSURE("Terbuka Bebas (Tanpa Password)"),
    PROTECTED_CREDENTIALS("Terlindungi (Memerlukan Kredensial)"),
    CONNECTION_REFUSED("Koneksi Ditolak (Port Tertutup)"),
    TIMEOUT("Waktu Habis (Timeout)"),
    UNKNOWN("Status Tidak Dikenal")
}

data class AuthPostureResult(
    val ip: String,
    val port: Int,
    val protocol: String, // "HTTP", "HTTPS", "RTSP"
    val status: AuthStatus,
    val httpStatusCode: Int? = null,
    val authHeader: String? = null,
    val serverBanner: String? = null,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
