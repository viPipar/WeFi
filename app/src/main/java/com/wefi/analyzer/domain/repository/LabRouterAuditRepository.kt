package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface repository untuk pengujian router laboratorium terotorisasi.
 */
interface LabRouterAuditRepository {
    val auditLogs: StateFlow<List<LabAuditLogEntry>>
    val authorizedSsids: Set<String>

    fun isSsidAuthorized(ssid: String): Boolean
    fun testRouterCredential(target: LabAuditTarget, candidateKey: String): Flow<LabAuditStatus>
    fun recordLog(entry: LabAuditLogEntry)
    fun clearLogs()
}
