package com.wefi.analyzer.domain.model

import java.util.UUID

/**
 * Status verifikasi kredensial router lab terotorisasi.
 */
enum class LabAuditStatus {
    UNTESTED,
    TESTING,
    MATCHED,
    FAILED,
    UNAUTHORIZED,
    ERROR
}

/**
 * Target router yang dievaluasi pada tab Around Check.
 */
data class LabAuditTarget(
    val ssid: String,
    val bssid: String,
    val isAuthorized: Boolean,
    val status: LabAuditStatus = LabAuditStatus.UNTESTED,
    val rssi: Int = 0,
    val channel: Int = 1,
    val frequencyMhz: Int = 2412
) {
    val displaySsid: String
        get() = ssid.ifBlank { "Hidden Network ($bssid)" }
}

/**
 * Riwayat pencatatan audit pengetesan (tanpa menyimpan plaintext password).
 */
data class LabAuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val targetSsid: String,
    val targetBssid: String,
    val status: LabAuditStatus,
    val notes: String = ""
)
