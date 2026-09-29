package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.repository.WifiAuditLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementasi in-memory logger audit koneksi Wi-Fi lab.
 * Tidak menyimpan password ke persistent storage atau log sistem.
 */
class WifiAuditLoggerImpl(
    private val maxEntries: Int = 100
) : WifiAuditLogger {

    private val _auditLogs = MutableStateFlow<List<WifiAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<WifiAuditLogEntry>> = _auditLogs.asStateFlow()

    override fun record(entry: WifiAuditLogEntry) {
        val current = _auditLogs.value.toMutableList()
        current.add(0, entry)
        _auditLogs.value = if (current.size > maxEntries) {
            current.take(maxEntries)
        } else {
            current
        }
    }

    override fun clear() {
        _auditLogs.value = emptyList()
    }
}
