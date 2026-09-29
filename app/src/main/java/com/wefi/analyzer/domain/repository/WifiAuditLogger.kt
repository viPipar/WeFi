package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface pencatatan audit koneksi nirkabel tanpa menyimpan password.
 */
interface WifiAuditLogger {
    val auditLogs: StateFlow<List<WifiAuditLogEntry>>
    fun record(entry: WifiAuditLogEntry)
    fun clear()
}
