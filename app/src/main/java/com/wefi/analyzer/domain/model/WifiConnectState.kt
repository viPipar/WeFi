package com.wefi.analyzer.domain.model

/**
 * Status koneksi jaringan manual dengan persetujuan sistem operasi Android.
 */
sealed interface WifiConnectStatus {
    data object Idle : WifiConnectStatus
    data object WaitingApproval : WifiConnectStatus
    data object Connected : WifiConnectStatus
    data object Rejected : WifiConnectStatus
    data object Failed : WifiConnectStatus
    data object Timeout : WifiConnectStatus
}

/**
 * State koneksi saat ini untuk pelacakan UI.
 */
data class WifiConnectState(
    val targetSsid: String? = null,
    val status: WifiConnectStatus = WifiConnectStatus.Idle,
    val message: String = ""
)
