package com.wefi.analyzer.domain.model

import java.util.UUID

/**
 * Hasil dari upaya koneksi manual Wi-Fi untuk audit lab.
 */
enum class WifiAuditResult(val label: String) {
    CONNECTED("Tersambung"),
    REJECTED("Ditolak"),
    FAILED("Gagal"),
    TIMEOUT("Timeout")
}

/**
 * Entri log audit koneksi (tanpa pernah menyimpan password/kredensial).
 */
data class WifiAuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val ssid: String,
    val result: WifiAuditResult,
    val reason: String = ""
)
