package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import com.wefi.analyzer.domain.repository.LabRouterAuditRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class LabRouterAuditRepositoryImpl : LabRouterAuditRepository {

    override val authorizedSsids: Set<String> = setOf(
        "ilmukomputeripb",
        "Lab-IoT-01",
        "Lab-Jaringan-A",
        "Lab-Riset-Wifi",
        "RouterLab",
        "TPLINK406",
        "Halo"
    )

    private val authorizedPrefixRegex = Regex("^(?i)(Lab-|IPB-|RouterLab|TPLINK).*")

    private val _auditLogs = MutableStateFlow<List<LabAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<LabAuditLogEntry>> = _auditLogs.asStateFlow()

    private val mockValidCredentials: Map<String, List<String>> = mapOf(
        "ilmukomputeripb" to listOf("ilmukomputeripb", "labkomputer123", "ipbjuara"),
        "Lab-IoT-01" to listOf("Lab-IoT-01", "iotlab2026"),
        "Lab-Jaringan-A" to listOf("Lab-Jaringan-A", "jaringanA123"),
        "Lab-Riset-Wifi" to listOf("Lab-Riset-Wifi", "risetwifi2026"),
        "RouterLab" to listOf("RouterLab", "routerlab123"),
        "TPLINK406" to listOf("TPLINK406", "admin1234"),
        "Halo" to listOf("Halo", "halo1234")
    )

    override fun isSsidAuthorized(ssid: String): Boolean {
        if (ssid.isBlank()) return false
        val inExplicitList = authorizedSsids.any { it.equals(ssid, ignoreCase = true) }
        val matchesPrefix = ssid.matches(authorizedPrefixRegex)
        return inExplicitList || matchesPrefix
    }

    override fun testRouterCredential(
        target: LabAuditTarget,
        candidateKey: String
    ): Flow<LabAuditStatus> = flow {
        if (!isSsidAuthorized(target.ssid)) {
            emit(LabAuditStatus.UNAUTHORIZED)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.UNAUTHORIZED,
                    notes = "SSID di luar whitelist lab"
                )
            )
            return@flow
        }

        if (candidateKey.isBlank()) {
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

        emit(LabAuditStatus.TESTING)
        delay(350) // Simulasi waktu respon handshake / endpoint lab

        val isMatch = isCredentialMatched(target.ssid, candidateKey)
        val finalStatus = if (isMatch) LabAuditStatus.MATCHED else LabAuditStatus.FAILED
        val notes = if (isMatch) {
            "Verifikasi kredensial berhasil (MATCH)"
        } else {
            "Kredensial tidak cocok (MISMATCH)"
        }

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

    private fun isCredentialMatched(ssid: String, candidateKey: String): Boolean {
        val normalizedSsid = ssid.trim()
        val validList = mockValidCredentials.entries
            .firstOrNull { it.key.equals(normalizedSsid, ignoreCase = true) }
            ?.value ?: listOf(normalizedSsid, "labkomputer123")

        return validList.any { it.equals(candidateKey.trim(), ignoreCase = false) } ||
                candidateKey.trim().equals(normalizedSsid, ignoreCase = true)
    }

    override fun recordLog(entry: LabAuditLogEntry) {
        val current = _auditLogs.value.toMutableList()
        // Sisipkan log baru di urutan paling atas, batasi 100 entri terakhir
        current.add(0, entry)
        if (current.size > 100) {
            _auditLogs.value = current.take(100)
        } else {
            _auditLogs.value = current
        }
    }

    override fun clearLogs() {
        _auditLogs.value = emptyList()
    }
}
