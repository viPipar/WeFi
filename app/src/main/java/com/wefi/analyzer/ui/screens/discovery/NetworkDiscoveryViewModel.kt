package com.wefi.analyzer.ui.screens.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.PortStatus
import com.wefi.analyzer.domain.model.ServiceInfo
import com.wefi.analyzer.domain.repository.NetworkDiscoveryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class NetworkDiscoveryViewModel(
    private val repository: NetworkDiscoveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkDiscoveryUiState())
    val uiState: StateFlow<NetworkDiscoveryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val commonPorts = listOf(22, 23, 53, 80, 443, 554, 8000, 8080, 8443, 3702, 37777, 5000, 8888)

    fun startDiscovery() {
        if (_uiState.value.isScanning) return

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                phase = ScanPhase.DISCOVERING_SUBNET,
                isScanning = true,
                progress = 0.05f,
                statusMessage = "Mendeteksi konfigurasi subnet laboratorium...",
                hosts = emptyList(),
                errorMessage = null,
                exportedReportText = null,
                report = null
            )
        }

        scanJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            val subnet = repository.getSubnetInfo()
            if (!isActive) return@launch
            if (subnet == null) {
                _uiState.update {
                    it.copy(
                        phase = ScanPhase.ERROR,
                        isScanning = false,
                        errorMessage = "Tidak dapat mendeteksi subnet Wi-Fi. Pastikan terhubung ke AP/Router lab.",
                        statusMessage = "Gagal menginisialisasi subnet."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    subnetInfo = subnet,
                    phase = ScanPhase.PING_SWEEP,
                    progress = 0.15f,
                    statusMessage = "Menjalankan Ping Sweep & Hybrid Discovery pada subnet ${subnet.baseIp}/24..."
                )
            }

            val discoveredMap = ConcurrentHashMap<String, DiscoveredHost>()
            val discoveredServices = ConcurrentLinkedQueue<ServiceInfo>()

            // 1. Jalankan SSDP & mDNS discovery secara paralel dengan Ping Sweep
            val ssdpJob = async {
                try {
                    repository.discoverSsdp(3000L).collect { service ->
                        discoveredServices.add(service)
                    }
                } catch (ignored: Exception) {}
            }

            val mdnsJob = async {
                try {
                    repository.discoverMdns(4000L).collect { service ->
                        discoveredServices.add(service)
                    }
                } catch (ignored: Exception) {}
            }

            // 2. Ping sweep pada seluruh host di subnet
            try {
                repository.pingSweep(subnet).collect { host ->
                    discoveredMap[host.ip] = host
                    _uiState.update { state ->
                        state.copy(
                            hosts = discoveredMap.values.toList().sortedByDescending { it.isGateway },
                            statusMessage = "Menemukan host aktif: ${host.ip} (${host.vendor})"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Ignore sweep interruption
            }

            if (!isActive) return@launch

            ssdpJob.await()
            mdnsJob.await()

            if (!isActive) return@launch

            // Gabungkan host dari SSDP dan mDNS jika belum tercatat di sweep
            discoveredServices.forEach { srv ->
                val existing = discoveredMap[srv.ip]
                if (existing != null) {
                    discoveredMap[srv.ip] = existing.copy(
                        services = existing.services + srv
                    )
                } else if (srv.ip.startsWith(subnet.baseIp.substringBeforeLast("."))) {
                    discoveredMap[srv.ip] = DiscoveredHost(
                        ip = srv.ip,
                        vendor = "UPnP / mDNS Device",
                        services = listOf(srv),
                        isGateway = (srv.ip == subnet.gatewayIp)
                    )
                }
            }

            val liveHosts = discoveredMap.values.toList()
            if (liveHosts.isEmpty()) {
                val report = repository.generateReport(emptyList(), System.currentTimeMillis() - startTime, subnet.baseIp)
                _uiState.update {
                    it.copy(
                        phase = ScanPhase.FINISHED,
                        isScanning = false,
                        progress = 1.0f,
                        hosts = emptyList(),
                        report = report,
                        isClientIsolationSuspected = true,
                        statusMessage = "Tidak ada host terdeteksi. Kemungkinan Client Isolation AP lab aktif."
                    )
                }
                return@launch
            }

            // 3. Port Scan pada setiap host yang hidup
            _uiState.update {
                it.copy(
                    phase = ScanPhase.PORT_SCAN,
                    progress = 0.50f,
                    statusMessage = "Melakukan TCP Connect Scan pada ${liveHosts.size} host aktif..."
                )
            }

            val hostsWithPorts = mutableListOf<DiscoveredHost>()
            liveHosts.forEachIndexed { index, host ->
                _uiState.update {
                    it.copy(
                        currentHostScanned = host.ip,
                        progress = 0.50f + (0.30f * (index.toFloat() / liveHosts.size)),
                        statusMessage = "Memindai port lab pada ${host.ip} (${index + 1}/${liveHosts.size})..."
                    )
                }

                val openPorts = mutableListOf<PortResult>()
                try {
                    repository.scanPorts(host.ip, commonPorts).collect { portResult ->
                        if (portResult.status == PortStatus.OPEN) {
                            openPorts.add(portResult)
                            _uiState.update { state ->
                                val updatedList = state.hosts.map { h ->
                                    if (h.ip == host.ip) h.copy(openPorts = openPorts.toList()) else h
                                }
                                state.copy(hosts = updatedList)
                            }
                        }
                    }
                } catch (ignored: Exception) {}

                val updatedHost = host.copy(openPorts = openPorts)
                hostsWithPorts.add(updatedHost)
                _uiState.update { state ->
                    val updatedList = state.hosts.map { h ->
                        if (h.ip == host.ip) updatedHost else h
                    }
                    state.copy(hosts = updatedList)
                }
            }

            // 4. Banner Grabbing & CVE Matching (Read-Only)
            _uiState.update {
                it.copy(
                    phase = ScanPhase.BANNER_GRAB,
                    progress = 0.85f,
                    statusMessage = "Mengumpulkan banner servis & korelasi referensi CVE..."
                )
            }

            val fullyAuditedHosts = mutableListOf<DiscoveredHost>()
            hostsWithPorts.forEachIndexed { index, host ->
                _uiState.update {
                    it.copy(
                        currentHostScanned = host.ip,
                        progress = 0.85f + (0.12f * (index.toFloat() / hostsWithPorts.size)),
                        statusMessage = "Mengidentifikasi servis & CVE pada ${host.ip} (${index + 1}/${hostsWithPorts.size})..."
                    )
                }

                val primaryPort = host.openPorts.firstOrNull { it.port in listOf(554, 80, 8080, 3702, 8000) }?.port ?: 80
                val banner = try {
                    repository.grabBanner(host.ip, primaryPort)
                } catch (e: Exception) {
                    null
                }

                val deviceType = repository.identifyDeviceType(
                    openPorts = host.openPorts.map { it.port },
                    banner = banner,
                    services = host.services
                )

                // Match offline CVE based on vendor and banner info
                val vendorToMatch = if (host.vendor != "Tidak Diketahui") host.vendor else (banner?.onvifManufacturer ?: "")
                val modelToMatch = banner?.onvifModel ?: ""
                val firmwareToMatch = banner?.onvifFirmware ?: ""
                val cveMatches = repository.matchCve(vendorToMatch, modelToMatch, firmwareToMatch)

                val auditedHost = host.copy(
                    banner = banner,
                    probableDeviceType = deviceType,
                    cveMatches = cveMatches
                )
                fullyAuditedHosts.add(auditedHost)
                _uiState.update { state ->
                    val updatedList = state.hosts.map { h ->
                        if (h.ip == host.ip) auditedHost else h
                    }
                    state.copy(hosts = updatedList)
                }
            }

            if (!isActive) return@launch

            val duration = System.currentTimeMillis() - startTime
            val finalReport = repository.generateReport(fullyAuditedHosts, duration, subnet.baseIp)

            if (!isActive) return@launch

            _uiState.update {
                it.copy(
                    phase = ScanPhase.FINISHED,
                    isScanning = false,
                    progress = 1.0f,
                    hosts = fullyAuditedHosts.sortedByDescending { h -> h.isGateway },
                    report = finalReport,
                    statusMessage = "Audit selesai. Menemukan ${fullyAuditedHosts.size} host dalam ${duration / 1000}s."
                )
            }
        }
    }

    fun cancelDiscovery() {
        if (_uiState.value.isScanning || scanJob?.isActive == true) {
            scanJob?.cancel()
            _uiState.update {
                it.copy(
                    phase = ScanPhase.CANCELLED,
                    isScanning = false,
                    statusMessage = "Pemindaian dibatalkan oleh pengguna."
                )
            }
        }
    }

    fun prepareExport() {
        val report = _uiState.value.report ?: return
        val textReport = repository.exportReportText(report)
        _uiState.update {
            it.copy(exportedReportText = textReport)
        }
    }

    fun getExportJson(): String {
        val report = _uiState.value.report ?: return "{}"
        return repository.exportReportJson(report)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        repository.teardown()
    }
}
