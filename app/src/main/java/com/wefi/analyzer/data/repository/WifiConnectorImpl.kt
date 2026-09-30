package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
 * Implementasi resmi koneksi Wi-Fi menggunakan WifiNetworkSuggestion Android (API 29+).
 * Mengintegrasikan strategi Golden Time, audit logger tanpa password, dan shared NetworkCallback
 * dengan dual timeout (30s persetujuan, 15s handshake).
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
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : WifiConnector {

    private val repositoryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    private var activeSuggestion: WifiNetworkSuggestion? = null
    private var activeTargetSsid: String? = null
    private var activeNetworkRequestCallback: ConnectivityManager.NetworkCallback? = null

    private var approvalTimeoutJob: Job? = null
    private var handshakeTimeoutJob: Job? = null
    private var isSharedCallbackRegistered = false

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

    // Satu NetworkCallback bersama untuk seluruh siklus koneksi
    private val sharedNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val ssid = activeTargetSsid ?: return
            approvalTimeoutJob?.cancel()

            // Masuk ke fase verifikasi handshake jaringan (timeout 15 detik)
            handshakeTimeoutJob?.cancel()
            handshakeTimeoutJob = repositoryScope.launch {
                delay(HANDSHAKE_TIMEOUT_MS)
                if (_connectState.value.status == WifiConnectStatus.WaitingApproval) {
                    recordFailure(ssid, "Timeout verifikasi handshake (15 detik)", WifiAuditResult.TIMEOUT)
                }
            }

            handshakeTimeoutJob?.cancel()
            throttler.recordAttemptFinished(ssid, success = true)

            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Connected,
                message = "Tersambung ke $ssid"
            )

            auditLogger.record(
                WifiAuditLogEntry(
                    ssid = ssid,
                    result = WifiAuditResult.CONNECTED,
                    reason = "User menyetujui koneksi OS dan jaringan tersedia"
                )
            )
        }

        override fun onUnavailable() {
            val ssid = activeTargetSsid ?: return
            recordFailure(
                ssid = ssid,
                reason = "Ditolak oleh user atau jaringan tidak tersedia",
                auditResult = WifiAuditResult.REJECTED
            )
        }

        override fun onLost(network: Network) {
            val ssid = activeTargetSsid ?: return
            recordFailure(
                ssid = ssid,
                reason = "Koneksi terputus dari jaringan",
                auditResult = WifiAuditResult.FAILED
            )
        }
    }

    init {
        registerSharedNetworkCallback()
    }

    private fun registerSharedNetworkCallback() {
        val cm = connectivityManager ?: return
        if (isSharedCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, sharedNetworkCallback)
            isSharedCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan shared NetworkCallback", e)
        }
    }

    override fun canConnect(ssid: String): ConnectCheckResult {
        return throttler.canConnect(ssid)
    }

    override fun remainingCooldownSeconds(ssid: String): Int {
        return throttler.remainingCooldownSecondsForSsid(ssid)
    }

    override fun connect(ssid: String, password: String, securityType: WifiSecurityType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Failed,
                message = "Fitur WifiNetworkSuggestion membutuhkan Android 10 (API 29) ke atas."
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

        val wm = wifiManager
        val cm = connectivityManager
        if (wm == null || cm == null) {
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

        // Batalkan timer sesi sebelumnya jika ada
        cancelTimers()
        unregisterActiveRequestCallback()
        removeCurrentSuggestion()

        try {
            // 1. Koneksi Interaktif via WifiNetworkSpecifier (Munculkan Dialog OS Instan)
            val specifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val specBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                when (securityType) {
                    WifiSecurityType.WPA3 -> {
                        if (password.isNotEmpty()) specBuilder.setWpa3Passphrase(password)
                    }
                    WifiSecurityType.OPEN -> {
                        // Jaringan terbuka tanpa passphrase
                    }
                    else -> {
                        if (password.isNotEmpty()) specBuilder.setWpa2Passphrase(password)
                    }
                }
                specBuilder.build()
            } else {
                null
            }

            val requestBuilder = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (specifier != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBuilder.setNetworkSpecifier(specifier)
            }
            val request = requestBuilder.build()

            val interactiveCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (activeTargetSsid != ssid) return
                    cancelTimers()
                    throttler.recordAttemptFinished(ssid, success = true)

                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Connected,
                        message = "Tersambung ke $ssid"
                    )

                    auditLogger.record(
                        WifiAuditLogEntry(
                            ssid = ssid,
                            result = WifiAuditResult.CONNECTED,
                            reason = "User menyetujui koneksi OS dan jaringan tersedia"
                        )
                    )
                }

                override fun onUnavailable() {
                    if (activeTargetSsid != ssid) return
                    recordFailure(
                        ssid = ssid,
                        reason = "Ditolak oleh user atau jaringan tidak tersedia",
                        auditResult = WifiAuditResult.REJECTED
                    )
                }

                override fun onLost(network: Network) {
                    if (activeTargetSsid != ssid) return
                    recordFailure(
                        ssid = ssid,
                        reason = "Koneksi terputus dari jaringan",
                        auditResult = WifiAuditResult.FAILED
                    )
                }
            }

            activeNetworkRequestCallback = interactiveCallback
            cm.requestNetwork(request, interactiveCallback)

            // 2. Daftarkan juga WifiNetworkSuggestion sebagai pendamping jika didukung
            try {
                val suggestion = buildNetworkSuggestion(ssid, password, securityType)
                activeSuggestion = suggestion
                wm.addNetworkSuggestions(listOf(suggestion))
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mendaftarkan suggestion pendamping", e)
            }

            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.WaitingApproval,
                message = "Menunggu persetujuan user..."
            )

            // Pastikan shared callback terdaftar
            registerSharedNetworkCallback()

            // Timeout keselamatan persetujuan user (30 detik)
            approvalTimeoutJob = repositoryScope.launch {
                delay(APPROVAL_TIMEOUT_MS)
                if (_connectState.value.status == WifiConnectStatus.WaitingApproval) {
                    recordFailure(
                        ssid = ssid,
                        reason = "Timeout menunggu persetujuan (30 detik)",
                        auditResult = WifiAuditResult.TIMEOUT
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai proses koneksi saran jaringan", e)
            recordFailure(
                ssid = ssid,
                reason = "Terjadi kesalahan saat memulai koneksi: ${e.message}",
                auditResult = WifiAuditResult.FAILED
            )
        }
    }

    private fun recordFailure(ssid: String, reason: String, auditResult: WifiAuditResult) {
        cancelTimers()
        unregisterActiveRequestCallback()
        removeCurrentSuggestion()
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildNetworkSuggestion(
        ssid: String,
        password: String,
        securityType: WifiSecurityType
    ): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setIsAppInteractionRequired(true)
            .setIsUserInteractionRequired(true)

        when (securityType) {
            WifiSecurityType.WPA3 -> {
                if (password.isNotEmpty()) {
                    builder.setWpa3Passphrase(password)
                }
            }
            WifiSecurityType.OPEN -> {
                // Jaringan terbuka tidak memerlukan passphrase
            }
            else -> {
                if (password.isNotEmpty()) {
                    builder.setWpa2Passphrase(password)
                }
            }
        }

        return builder.build()
    }

    override fun cancel() {
        val ssid = activeTargetSsid
        if (ssid != null) {
            throttler.recordAttemptFinished(ssid, success = false)
        }
        cancelTimers()
        unregisterActiveRequestCallback()
        removeCurrentSuggestion()
        activeTargetSsid = null

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
            removeCurrentSuggestion()
            activeTargetSsid = null
        }
        _connectState.value = WifiConnectState(
            targetSsid = ssid,
            status = WifiConnectStatus.Idle,
            message = "Jaringan dilupakan"
        )
    }

    private fun removeCurrentSuggestion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = activeSuggestion
            if (suggestion != null) {
                try {
                    wifiManager?.removeNetworkSuggestions(listOf(suggestion))
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menghapus saran jaringan aktif", e)
                }
                activeSuggestion = null
            }
        }
    }

    private fun cancelTimers() {
        approvalTimeoutJob?.cancel()
        approvalTimeoutJob = null
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
    }

    override fun teardown() {
        cancel()
        unregisterActiveRequestCallback()
        if (isSharedCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(sharedNetworkCallback)
                isSharedCallbackRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepaskan shared NetworkCallback", e)
            }
        }
        repositoryScope.cancel()
    }

    companion object {
        private const val TAG = "WifiConnectorImpl"
        const val APPROVAL_TIMEOUT_MS = 30_000L // 30 detik timeout persetujuan user
        const val HANDSHAKE_TIMEOUT_MS = 15_000L // 15 detik timeout handshake
    }
}
