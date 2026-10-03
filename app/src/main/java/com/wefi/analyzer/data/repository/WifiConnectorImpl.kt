package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import java.util.UUID
import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiAuditResult
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiAuditLogger
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.util.ConnectCheckResult
import com.wefi.analyzer.domain.util.WifiConnectThrottler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implementasi resmi koneksi Wi-Fi interaktif menggunakan WifiNetworkSpecifier Android (API 29+).
 * Mengintegrasikan verifikasi dialog sistem OS, Golden Time Throttler, audit logging,
 * pengikatan soket proses ke jaringan target (bindProcessToNetwork), dan isolasi callback murni
 * tanpa gangguan dari koneksi Wi-Fi yang sedang aktif pada perangkat.
 */
class WifiConnectorImpl(
    private val context: Context? = null,
    private val wifiManager: WifiManager? = try {
        context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        null
    },
    private val connectivityManager: ConnectivityManager? = try {
        context?.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (e: Exception) {
        null
    },
    val auditLogger: WifiAuditLogger = WifiAuditLoggerImpl(),
    val throttler: WifiConnectThrottler = WifiConnectThrottler(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : WifiConnector {

    private val repositoryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    private var activeTargetSsid: String? = null
    private var activeAttemptId: String? = null
    private var activeNetworkRequestCallback: ConnectivityManager.NetworkCallback? = null

    private var approvalTimeoutJob: Job? = null

    private fun unregisterActiveRequestCallback() {
        val cm = connectivityManager ?: return
        val cb = activeNetworkRequestCallback ?: return
        try {
            cm.unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melepaskan active NetworkCallback", e)
        } finally {
            activeNetworkRequestCallback = null
        }
    }

    private fun unbindProcessNetwork() {
        try {
            connectivityManager?.bindProcessToNetwork(null)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melepaskan ikatan proses jaringan", e)
        }
    }

    override fun canConnect(ssid: String): ConnectCheckResult {
        return throttler.canConnect(ssid)
    }

    override fun remainingCooldownSeconds(ssid: String): Int {
        return throttler.remainingCooldownSecondsForSsid(ssid)
    }

    override fun isCurrentlyConnectedTo(ssid: String, bssid: String): Boolean {
        return try {
            val info = wifiManager?.connectionInfo ?: return false
            if (info.networkId == -1) return false
            val currentSsid = info.ssid?.replace("\"", "") ?: ""
            val currentBssid = info.bssid ?: ""
            if (bssid.isNotBlank() && currentBssid.isNotBlank()) {
                currentBssid.equals(bssid, ignoreCase = true)
            } else {
                currentSsid.equals(ssid, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun connect(ssid: String, password: String, securityType: WifiSecurityType, bssid: String) {
        if (sdkInt < Build.VERSION_CODES.Q) {
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Failed,
                message = "Fitur koneksi Wi-Fi resmi membutuhkan Android 10 (API 29) ke atas."
            )
            return
        }

        // Evaluasi Golden Time Throttler
        val eligibility = throttler.canConnect(ssid)
        if (eligibility is ConnectCheckResult.Blocked) {
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Cooldown(eligibility.remainingSeconds),
                message = "${eligibility.reason} (Tunggu ${eligibility.remainingSeconds}s)"
            )
            return
        }

        // Validasi protokol keamanan dan panjang kata sandi
        if (securityType == WifiSecurityType.WEP) {
            recordFailure(
                ssid = ssid,
                reason = "Protokol keamanan WEP sudah usang dan tidak didukung oleh Android 10+.",
                auditResult = WifiAuditResult.FAILED
            )
            return
        }

        if ((securityType == WifiSecurityType.WPA2 || securityType == WifiSecurityType.WPA3) && password.length !in 8..63) {
            recordFailure(
                ssid = ssid,
                reason = "Panjang kata sandi ${securityType.name} harus antara 8 dan 63 karakter.",
                auditResult = WifiAuditResult.FAILED
            )
            return
        }

        val cm = connectivityManager
        if (cm == null) {
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Failed,
                message = "Layanan sistem konektivitas tidak tersedia pada perangkat."
            )
            return
        }

        // Catat awal percobaan ke throttler
        throttler.recordAttemptStarted(ssid)
        activeTargetSsid = ssid
        val currentAttemptId = UUID.randomUUID().toString()
        activeAttemptId = currentAttemptId

        // Batalkan timer dan callback sesi sebelumnya jika ada
        cancelTimers()
        unregisterActiveRequestCallback()
        unbindProcessNetwork()

        try {
            val specBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (bssid.isNotBlank() && bssid.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
                try {
                    specBuilder.setBssid(MacAddress.fromString(bssid))
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal mengikat BSSID ke specifier: $bssid", e)
                }
            }

            when (securityType) {
                WifiSecurityType.WPA3 -> {
                    specBuilder.setWpa3Passphrase(password)
                }
                WifiSecurityType.OPEN -> {
                    // Jaringan terbuka tidak memerlukan passphrase
                }
                else -> {
                    specBuilder.setWpa2Passphrase(password)
                }
            }

            val specifier = specBuilder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val interactiveCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (activeAttemptId != currentAttemptId || activeTargetSsid != ssid) return
                    cancelTimers()
                    throttler.recordAttemptFinished(ssid, success = true)

                    try {
                        cm.bindProcessToNetwork(network)
                    } catch (e: Exception) {
                        Log.w(TAG, "Gagal mengikat proses aplikasi ke jaringan Wi-Fi target", e)
                    }

                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Connected,
                        message = "Tersambung ke $ssid"
                    )

                    auditLogger.record(
                        WifiAuditLogEntry(
                            ssid = ssid,
                            result = WifiAuditResult.CONNECTED,
                            reason = "User menyetujui koneksi OS dan berhasil terhubung ke router"
                        )
                    )
                }

                override fun onUnavailable() {
                    if (activeAttemptId != currentAttemptId || activeTargetSsid != ssid) return
                    unbindProcessNetwork()
                    recordFailure(
                        ssid = ssid,
                        reason = "Ditolak oleh user atau autentikasi router gagal",
                        auditResult = WifiAuditResult.REJECTED
                    )
                }

                override fun onLost(network: Network) {
                    if (activeAttemptId != currentAttemptId || activeTargetSsid != ssid) return
                    unbindProcessNetwork()
                    recordFailure(
                        ssid = ssid,
                        reason = "Koneksi terputus dari jaringan $ssid",
                        auditResult = WifiAuditResult.FAILED
                    )
                }
            }

            activeNetworkRequestCallback = interactiveCallback
            cm.requestNetwork(request, interactiveCallback)

            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.WaitingApproval,
                message = "Menunggu persetujuan user..."
            )

            // Timeout keselamatan persetujuan user (30 detik)
            approvalTimeoutJob = repositoryScope.launch {
                delay(APPROVAL_TIMEOUT_MS)
                if (_connectState.value.status == WifiConnectStatus.WaitingApproval && activeTargetSsid == ssid) {
                    recordFailure(
                        ssid = ssid,
                        reason = "Timeout menunggu persetujuan (30 detik)",
                        auditResult = WifiAuditResult.TIMEOUT
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai proses koneksi nirkabel", e)
            val friendlyReason = when (e) {
                is SecurityException -> "Izin sistem koneksi jaringan (CHANGE_NETWORK_STATE) belum diberikan."
                is IllegalArgumentException -> "Parameter SSID atau panjang password (8-63 karakter) tidak valid."
                is IllegalStateException -> "Layanan konektivitas sistem sedang sibuk. Silakan coba sesaat lagi."
                else -> "Gagal berkomunikasi dengan layanan jaringan sistem."
            }
            recordFailure(
                ssid = ssid,
                reason = friendlyReason,
                auditResult = WifiAuditResult.FAILED
            )
        }
    }

    private fun recordFailure(ssid: String, reason: String, auditResult: WifiAuditResult) {
        cancelTimers()
        unregisterActiveRequestCallback()
        unbindProcessNetwork()
        throttler.recordAttemptFinished(ssid, success = false)

        val finalStatus = when (auditResult) {
            WifiAuditResult.REJECTED -> WifiConnectStatus.Rejected
            WifiAuditResult.TIMEOUT -> WifiConnectStatus.Timeout
            else -> WifiConnectStatus.Failed
        }

        _connectState.value = WifiConnectState(
            targetSsid = ssid,
            status = finalStatus,
            message = reason
        )

        auditLogger.record(
            WifiAuditLogEntry(
                ssid = ssid,
                result = auditResult,
                reason = reason
            )
        )
    }

    override fun cancel() {
        val ssid = activeTargetSsid
        if (ssid != null) {
            throttler.recordAttemptFinished(ssid, success = false)
        }
        cancelTimers()
        unregisterActiveRequestCallback()
        unbindProcessNetwork()
        activeTargetSsid = null
        activeAttemptId = null

        _connectState.value = WifiConnectState(
            targetSsid = null,
            status = WifiConnectStatus.Idle,
            message = "Koneksi dibatalkan"
        )
    }

    override fun forgetNetwork(ssid: String) {
        if (activeTargetSsid == ssid) {
            throttler.recordAttemptFinished(ssid, success = false)
            cancelTimers()
            unregisterActiveRequestCallback()
            unbindProcessNetwork()
            activeTargetSsid = null
            activeAttemptId = null
        }
        unbindProcessNetwork()
        _connectState.value = WifiConnectState(
            targetSsid = ssid,
            status = WifiConnectStatus.Idle,
            message = "Jaringan dilupakan"
        )
    }

    private fun cancelTimers() {
        approvalTimeoutJob?.cancel()
        approvalTimeoutJob = null
    }

    override fun teardown() {
        cancel()
        repositoryScope.cancel()
    }

    companion object {
        private const val TAG = "WifiConnectorImpl"
        const val APPROVAL_TIMEOUT_MS = 30_000L // 30 detik timeout persetujuan user
    }
}
