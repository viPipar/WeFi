package com.wefi.analyzer.ui.screens.aroundcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import com.wefi.analyzer.domain.repository.LabRouterAuditRepository
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel untuk tab Around Check.
 * Menghubungkan pemindaian hardware AP dengan audit kredensial lab terotorisasi.
 */
class AroundCheckViewModel(
    private val scannerRepository: WifiScannerRepository,
    private val auditRepository: LabRouterAuditRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    private val _isAuthorizedConsentGiven = MutableStateFlow(false)
    val isAuthorizedConsentGiven: StateFlow<Boolean> = _isAuthorizedConsentGiven.asStateFlow()

    private val _isTestingInProgress = MutableStateFlow(false)
    val isTestingInProgress: StateFlow<Boolean> = _isTestingInProgress.asStateFlow()

    private val _activeTestingTargetBssid = MutableStateFlow<String?>(null)
    val activeTestingTargetBssid: StateFlow<String?> = _activeTestingTargetBssid.asStateFlow()

    private val _targetStatuses = MutableStateFlow<Map<String, LabAuditStatus>>(emptyMap())

    private val _showLogBottomSheet = MutableStateFlow(false)
    val showLogBottomSheet: StateFlow<Boolean> = _showLogBottomSheet.asStateFlow()

    val auditLogs: StateFlow<List<LabAuditLogEntry>> = auditRepository.auditLogs

    // Lab mock default APs agar selalu ada router lab yang siap diuji dalam simulasi
    private val defaultLabTargets = listOf(
        LabAuditTarget("ilmukomputeripb", "00:1A:2B:3C:4D:01", isAuthorized = true, rssi = -52, channel = 6),
        LabAuditTarget("Lab-IoT-01", "00:1A:2B:3C:4D:02", isAuthorized = true, rssi = -60, channel = 1),
        LabAuditTarget("Lab-Jaringan-A", "00:1A:2B:3C:4D:03", isAuthorized = true, rssi = -68, channel = 11),
        LabAuditTarget("RouterLab", "00:1A:2B:3C:4D:04", isAuthorized = true, rssi = -55, channel = 36),
        LabAuditTarget("TPLINK406", "00:1A:2B:3C:4D:05", isAuthorized = true, rssi = -63, channel = 40),
        LabAuditTarget("Halo", "00:1A:2B:3C:4D:06", isAuthorized = true, rssi = -72, channel = 44),
        LabAuditTarget("Public-Cafe-WiFi", "00:1A:2B:3C:4D:99", isAuthorized = false, status = LabAuditStatus.UNAUTHORIZED, rssi = -82, channel = 1)
    )

    val targets: StateFlow<List<LabAuditTarget>> = combine(
        scannerRepository.scanResults,
        _targetStatuses
    ) { scanList, statusMap ->
        val mergedList = mutableListOf<LabAuditTarget>()
        val seenBssids = mutableSetOf<String>()

        // 1. Tambahkan hasil scan perangkat sekitar
        for (ap in scanList) {
            val isAuthorized = auditRepository.isSsidAuthorized(ap.ssid)
            val baseStatus = if (isAuthorized) LabAuditStatus.UNTESTED else LabAuditStatus.UNAUTHORIZED
            val currentStatus = statusMap[ap.bssid] ?: baseStatus

            mergedList.add(
                LabAuditTarget(
                    ssid = ap.ssid,
                    bssid = ap.bssid,
                    isAuthorized = isAuthorized,
                    status = currentStatus,
                    rssi = ap.rssi,
                    channel = ap.channel,
                    frequencyMhz = ap.frequencyMhz
                )
            )
            seenBssids.add(ap.bssid)
        }

        // 2. Sertakan default lab targets jika belum ada di scan list
        for (defaultAp in defaultLabTargets) {
            if (!seenBssids.contains(defaultAp.bssid)) {
                val currentStatus = statusMap[defaultAp.bssid] ?: defaultAp.status
                mergedList.add(defaultAp.copy(status = currentStatus))
                seenBssids.add(defaultAp.bssid)
            }
        }

        // Urutkan: Terotorisasi lab di atas, lalu berdasarkan RSSI tertinggi
        mergedList.sortedWith(
            compareByDescending<LabAuditTarget> { it.isAuthorized }
                .thenByDescending { it.rssi }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultLabTargets)

    private var auditJob: Job? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun setConsentGiven(consent: Boolean) {
        _isAuthorizedConsentGiven.value = consent
    }

    fun setShowLogBottomSheet(show: Boolean) {
        _showLogBottomSheet.value = show
    }

    fun clearAuditLogs() {
        auditRepository.clearLogs()
    }

    fun startAuditSearch() {
        if (!_isAuthorizedConsentGiven.value) return
        if (_query.value.isBlank()) return
        if (_isTestingInProgress.value) return

        auditJob?.cancel()
        auditJob = viewModelScope.launch {
            _isTestingInProgress.value = true

            val currentTargets = targets.value
            for (target in currentTargets) {
                // Jangan uji SSID yang tidak terotorisasi
                if (!target.isAuthorized) {
                    updateTargetStatus(target.bssid, LabAuditStatus.UNAUTHORIZED)
                    continue
                }

                _activeTestingTargetBssid.value = target.bssid
                updateTargetStatus(target.bssid, LabAuditStatus.TESTING)

                auditRepository.testRouterCredential(target, _query.value).collect { status ->
                    updateTargetStatus(target.bssid, status)
                }
            }

            _activeTestingTargetBssid.value = null
            _isTestingInProgress.value = false
        }
    }

    fun stopAudit() {
        auditJob?.cancel()
        auditJob = null
        _activeTestingTargetBssid.value = null
        _isTestingInProgress.value = false

        // Kembalikan target yang sedang 'TESTING' ke 'UNTESTED'
        val currentMap = _targetStatuses.value.toMutableMap()
        for ((bssid, status) in currentMap) {
            if (status == LabAuditStatus.TESTING) {
                currentMap[bssid] = LabAuditStatus.UNTESTED
            }
        }
        _targetStatuses.value = currentMap
    }

    fun resetAuditStatuses() {
        _targetStatuses.value = emptyMap()
    }

    private fun updateTargetStatus(bssid: String, status: LabAuditStatus) {
        val current = _targetStatuses.value.toMutableMap()
        current[bssid] = status
        _targetStatuses.value = current
    }

    override fun onCleared() {
        super.onCleared()
        auditJob?.cancel()
    }
}
