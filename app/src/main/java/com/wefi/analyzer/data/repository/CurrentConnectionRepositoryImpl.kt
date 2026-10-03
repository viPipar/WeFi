package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.ConnectedNetworkInfo
import com.wefi.analyzer.domain.repository.CurrentConnectionRepository
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CurrentConnectionRepositoryImpl(
    private val context: Context
) : CurrentConnectionRepository {

    private val connectivityManager = try {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (e: Exception) {
        Log.w(TAG, "Gagal mendapatkan ConnectivityManager", e)
        null
    }

    private val wifiManager = try {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        Log.w(TAG, "Gagal mendapatkan WifiManager", e)
        null
    }

    private val _connectionInfo = MutableStateFlow(ConnectedNetworkInfo())
    override val connectionInfo: StateFlow<ConnectedNetworkInfo> = _connectionInfo.asStateFlow()

    private var activeWifiInfoFromCapabilities: WifiInfo? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshConnectionInfo()
                }

                override fun onLost(network: Network) {
                    activeWifiInfoFromCapabilities = null
                    _connectionInfo.value = ConnectedNetworkInfo()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val transport = capabilities.transportInfo
                        if (transport is WifiInfo) {
                            activeWifiInfoFromCapabilities = transport
                        }
                    }
                    refreshConnectionInfo()
                }
            }

            connectivityManager?.registerNetworkCallback(networkRequest, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan network callback", e)
        }

        refreshConnectionInfo()
    }

    override fun teardown() {
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepas network callback", e)
            } finally {
                networkCallback = null
            }
        }
    }

    override fun refreshConnectionInfo() {
        try {
            // Prioritaskan WifiInfo modern dari NetworkCapabilities (Android 10+ / 12+)
            val wifiInfo: WifiInfo? = activeWifiInfoFromCapabilities ?: try {
                wifiManager?.connectionInfo
            } catch (e: Exception) {
                null
            }

            // Periksa apakah perangkat sedang aktif tersambung ke jaringan Wi-Fi
            val isWifiTransport = try {
                val activeNet = connectivityManager?.activeNetwork
                val caps = connectivityManager?.getNetworkCapabilities(activeNet)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            } catch (e: Exception) {
                false
            }

            val dhcp = try { wifiManager?.dhcpInfo } catch (e: Exception) { null }
            val ip = dhcp?.let { formatIpAddress(it.ipAddress) } ?: "0.0.0.0"
            val gateway = dhcp?.let { formatIpAddress(it.gateway) } ?: "0.0.0.0"
            val dns1 = dhcp?.let { formatIpAddress(it.dns1) } ?: "0.0.0.0"

            val isDisconnectedDummy = wifiInfo == null || wifiInfo.networkId == -1 || wifiInfo.ssid == "<unknown ssid>"
            val hasValidConnection = isWifiTransport && (ip != "0.0.0.0" || !isDisconnectedDummy)

            if (!hasValidConnection) {
                _connectionInfo.value = ConnectedNetworkInfo()
                return
            }

            val rawSsid = wifiInfo?.ssid
            val ssid = rawSsid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() } ?: "Wi-Fi Terhubung"
            val bssid = wifiInfo?.bssid?.takeIf { it.isNotBlank() } ?: "02:00:00:00:00:00"
            val rssi = wifiInfo?.rssi ?: -65
            val linkSpeed = wifiInfo?.linkSpeed?.coerceAtLeast(0) ?: 144
            val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try { wifiInfo?.frequency ?: 2412 } catch (e: Exception) { 2412 }
            } else {
                2412
            }
            val channel = ChannelFrequencyUtils.toChannel(frequency)

            val ap = WifiAccessPoint(
                bssid = bssid,
                ssid = ssid,
                rssi = rssi,
                frequencyMhz = frequency,
                channel = channel,
                maxPhyRateMbps = linkSpeed,
                isConnected = true
            )

            _connectionInfo.value = ConnectedNetworkInfo(
                accessPoint = ap,
                linkSpeedMbps = linkSpeed,
                ipAddress = ip,
                gatewayIp = gateway,
                dns1 = dns1
            )
        } catch (e: Exception) {
            Log.w(TAG, "Kesalahan saat membaca koneksi wifi aktif", e)
            _connectionInfo.value = ConnectedNetworkInfo()
        }
    }

    private fun formatIpAddress(ip: Int): String {
        if (ip == 0) return "0.0.0.0"
        return (ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF)
    }

    companion object {
        private const val TAG = "CurrentConnectionRepo"
    }
}
