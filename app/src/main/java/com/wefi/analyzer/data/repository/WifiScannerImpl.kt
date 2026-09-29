package com.wefi.analyzer.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiScanner
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementasi resmi pemindaian Wi-Fi menggunakan WifiManager dan BroadcastReceiver Android.
 */
class WifiScannerImpl(
    private val context: Context,
    private val wifiManager: WifiManager? = try {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        null
    },
    private val locationManager: LocationManager? = try {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (e: Exception) {
        null
    }
) : WifiScanner {

    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    override val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    private var isReceiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val isSuccess = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                readScanResults(isSuccess)
            }
        }
    }

    init {
        registerScanReceiver()
    }

    private fun registerScanReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(scanReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan scan receiver", e)
        }
    }

    override fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    override fun startScan() {
        if (!isLocationEnabled()) {
            _scanState.value = WifiScanState.Error(
                message = "Layanan lokasi perangkat mati. Aktifkan lokasi untuk memindai jaringan Wi-Fi.",
                isLocationDisabled = true
            )
            return
        }

        val wm = wifiManager
        if (wm == null) {
            _scanState.value = WifiScanState.Error("WifiManager tidak tersedia pada perangkat ini.")
            return
        }

        if (!wm.isWifiEnabled) {
            _scanState.value = WifiScanState.Error("Wi-Fi perangkat dalam kondisi nonaktif.")
            return
        }

        _scanState.value = WifiScanState.Scanning

        val scanTriggered = try {
            wm.startScan()
        } catch (e: Exception) {
            Log.w(TAG, "Exception saat memanggil startScan()", e)
            false
        }

        if (!scanTriggered) {
            // Jika OS melakukan scan-throttling, tetap baca hasil pemindaian cache terakhir
            readScanResults(success = false)
        }
    }

    private fun readScanResults(success: Boolean) {
        val rawResults = try {
            wifiManager?.scanResults ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Exception saat membaca scanResults", e)
            emptyList()
        }

        val processedItems = processRawScanResults(rawResults)
        _scanState.value = WifiScanState.Success(processedItems)
    }

    override fun teardown() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepaskan scanReceiver", e)
            }
        }
    }

    companion object {
        private const val TAG = "WifiScannerImpl"

        fun parseSecurityType(capabilities: String?): WifiSecurityType {
            val caps = capabilities?.uppercase() ?: return WifiSecurityType.UNKNOWN
            return when {
                caps.contains("SAE") || caps.contains("WPA3") -> WifiSecurityType.WPA3
                caps.contains("PSK") || caps.contains("WPA2") || caps.contains("WPA") -> WifiSecurityType.WPA2
                caps.contains("WEP") -> WifiSecurityType.WEP
                !caps.contains("WPA") && !caps.contains("WEP") && !caps.contains("EAP") -> WifiSecurityType.OPEN
                else -> WifiSecurityType.UNKNOWN
            }
        }

        fun processRawScanResults(rawList: List<ScanResult>): List<WifiScanItem> {
            return rawList
                .filter { !it.SSID.isNullOrBlank() }
                .groupBy { it.SSID }
                .mapNotNull { (ssid, list) ->
                    val strongest = list.maxByOrNull { it.level } ?: return@mapNotNull null
                    WifiScanItem(
                        ssid = ssid,
                        bssid = strongest.BSSID ?: "",
                        rssi = strongest.level,
                        security = parseSecurityType(strongest.capabilities),
                        frequencyMhz = strongest.frequency,
                        channel = ChannelFrequencyUtils.toChannel(strongest.frequency)
                    )
                }
                .sortedByDescending { it.rssi }
        }
    }
}
