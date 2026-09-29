package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiConnector
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
 * Setiap koneksi manual mewajibkan persetujuan pengguna via dialog sistem OS.
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
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : WifiConnector {

    private val repositoryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private val _connectState = MutableStateFlow(WifiConnectState())
    override val connectState: StateFlow<WifiConnectState> = _connectState.asStateFlow()

    private var activeSuggestion: WifiNetworkSuggestion? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var timeoutJob: Job? = null

    override fun connect(ssid: String, password: String, securityType: WifiSecurityType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Failed,
                message = "Fitur WifiNetworkSuggestion membutuhkan Android 10 (API 29) ke atas."
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

        // Batalkan sesi koneksi sebelumnya jika ada
        cleanupCurrentSession()

        try {
            val suggestion = buildNetworkSuggestion(ssid, password, securityType)
            activeSuggestion = suggestion

            // Panggilan API resmi Android: addNetworkSuggestions
            // OS akan memvalidasi dan memunculkan dialog persetujuan ke user
            val status = wm.addNetworkSuggestions(listOf(suggestion))
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                _connectState.value = WifiConnectState(
                    targetSsid = ssid,
                    status = WifiConnectStatus.Failed,
                    message = "Gagal mendaftarkan saran jaringan ke sistem OS (Kode: $status)"
                )
                return
            }

            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.WaitingApproval,
                message = "Menunggu persetujuan user..."
            )

            // Daftarkan NetworkCallback untuk memantau apakah user menerima / menolak dialog OS
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    timeoutJob?.cancel()
                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Connected,
                        message = "Tersambung ke $ssid"
                    )
                }

                override fun onUnavailable() {
                    timeoutJob?.cancel()
                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Rejected,
                        message = "Koneksi ditolak oleh user atau jaringan tidak tersedia"
                    )
                }

                override fun onLost(network: Network) {
                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Failed,
                        message = "Koneksi terputus dari $ssid"
                    )
                }
            }

            networkCallback = callback
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mendaftarkan registerNetworkCallback", e)
            }

            // Timeout 30 detik untuk menunggu dialog approval dari user
            timeoutJob = repositoryScope.launch {
                delay(APPROVAL_TIMEOUT_MS)
                if (_connectState.value.status == WifiConnectStatus.WaitingApproval) {
                    cleanupCurrentSession()
                    _connectState.value = WifiConnectState(
                        targetSsid = ssid,
                        status = WifiConnectStatus.Timeout,
                        message = "Timeout menunggu persetujuan (30 detik)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai proses koneksi saran jaringan", e)
            _connectState.value = WifiConnectState(
                targetSsid = ssid,
                status = WifiConnectStatus.Failed,
                message = "Terjadi kesalahan saat memulai koneksi: ${e.message}"
            )
        }
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
            // setIsUserInteractionRequired(true) WAJIB dipanggil agar sistem operasi
            // Android menampilkan dialog notifikasi / konfirmasi persetujuan ke user.
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
        cleanupCurrentSession()
        _connectState.value = WifiConnectState(
            targetSsid = null,
            status = WifiConnectStatus.Idle,
            message = "Koneksi dibatalkan"
        )
    }

    override fun forgetNetwork(ssid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = activeSuggestion
            if (suggestion != null) {
                try {
                    wifiManager?.removeNetworkSuggestions(listOf(suggestion))
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menghapus network suggestion", e)
                }
            }
        }
        cleanupCurrentSession()
        _connectState.value = WifiConnectState(
            targetSsid = ssid,
            status = WifiConnectStatus.Idle,
            message = "Jaringan dilupakan"
        )
    }

    private fun cleanupCurrentSession() {
        timeoutJob?.cancel()
        timeoutJob = null

        val cb = networkCallback
        if (cb != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal unregister network callback", e)
            }
            networkCallback = null
        }

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

    override fun teardown() {
        cleanupCurrentSession()
        repositoryScope.cancel()
    }

    companion object {
        private const val TAG = "WifiConnectorImpl"
        const val APPROVAL_TIMEOUT_MS = 30_000L
    }
}
