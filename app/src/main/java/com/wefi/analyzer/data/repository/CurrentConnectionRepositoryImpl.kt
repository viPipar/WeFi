package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.ConnectedNetworkInfo
import com.wefi.analyzer.domain.repository.CurrentConnectionRepository
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class CurrentConnectionRepositoryImpl(
    private val context: Context
) : CurrentConnectionRepository {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _connectionInfo = MutableStateFlow(ConnectedNetworkInfo())
    override val connectionInfo: StateFlow<ConnectedNetworkInfo> = _connectionInfo.asStateFlow()

    init {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager?.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshConnectionInfo()
                }

                override fun onLost(network: Network) {
                    _connectionInfo.value = ConnectedNetworkInfo()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    refreshConnectionInfo()
                }
            }
        )

        refreshConnectionInfo()
    }

    override fun refreshConnectionInfo() {
        val wifiInfo: WifiInfo? = wifiManager?.connectionInfo

        if (wifiInfo == null || wifiInfo.bssid == null || wifiInfo.bssid == "02:00:00:00:00:00") {
            _connectionInfo.value = ConnectedNetworkInfo()
            return
        }

        val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Connected Wi-Fi"
        val bssid = wifiInfo.bssid ?: ""
        val rssi = wifiInfo.rssi
        val linkSpeed = wifiInfo.linkSpeed
        val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else 2412
        val channel = ChannelFrequencyUtils.toChannel(frequency)

        val dhcp = wifiManager.dhcpInfo
        val ip = dhcp?.let { formatIpAddress(it.ipAddress) } ?: "192.168.1.100"
        val gateway = dhcp?.let { formatIpAddress(it.gateway) } ?: "192.168.1.1"
        val dns1 = dhcp?.let { formatIpAddress(it.dns1) } ?: "8.8.8.8"

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
    }

    private fun formatIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF)
    }
}
