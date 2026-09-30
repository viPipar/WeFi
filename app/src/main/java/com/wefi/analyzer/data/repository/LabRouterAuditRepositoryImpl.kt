package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import com.wefi.analyzer.domain.repository.LabRouterAuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Repository audit log router laboratorium.
 * Seluruh verifikasi kredensial dan koneksi nirkabel telah dialihkan ke WifiConnector resmi.
 * Kelas ini dipertahankan khusus untuk pencatatan dan pengelolaan riwayat audit log lab.
 */
class LabRouterAuditRepositoryImpl : LabRouterAuditRepository {

    override val authorizedSsids: Set<String> = emptySet()

    private val _auditLogs = MutableStateFlow<List<LabAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<LabAuditLogEntry>> = _auditLogs.asStateFlow()

    override fun isSsidAuthorized(ssid: String): Boolean = ssid.isNotBlank()

    /**
     * Sesuai arsitektur resmi Android, verifikasi kredensial nyata dilakukan
     * melalui WifiConnectorImpl dan dialog persetujuan OS.
     * Metode ini mencatat upaya ke audit log tanpa simulasi atau hash derivation.
     */
    override fun testRouterCredential(
        target: LabAuditTarget,
        candidateKey: String
    ): Flow<LabAuditStatus> = flow {
        if (target.ssid.isBlank()) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "SSID kosong"
                )
            )
            return@flow
        }

        val candidate = candidateKey.trim()
        if (candidate.isEmpty()) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "Kredensial input kosong"
                )
            )
            return@flow
        }

        val finalStatus = LabAuditStatus.ERROR
        val notes = "Verifikasi kredensial nirkabel dialihkan ke WifiConnector resmi"

        emit(finalStatus)
        recordLog(
            LabAuditLogEntry(
                targetSsid = target.ssid,
                targetBssid = target.bssid,
                status = finalStatus,
                notes = notes
            )
        )
    }

    override fun recordLog(entry: LabAuditLogEntry) {
        val current = _auditLogs.value.toMutableList()
        current.add(0, entry)
        _auditLogs.value = if (current.size > MAX_LOG_ENTRIES) {
            current.take(MAX_LOG_ENTRIES)
        } else current
    }

    override fun clearLogs() {
        _auditLogs.value = emptyList()
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
    }
}