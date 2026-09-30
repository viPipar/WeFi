package com.wefi.analyzer.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiScanner
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import com.wefi.analyzer.domain.util.WifiScanThrottler
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Implementasi pemindaian Wi-Fi resmi yang menerapkan strategi adaptif Golden Time (20s-30s)
 * dan mematuhi batas maksimal 4 scan per 2 menit dari framework Android.
 */
class WifiScannerImpl(
    private val context: Context? = null,
    private val wifiManager: WifiManager? = try {
        context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        null
    },
    private val locationManager: LocationManager? = try {
        context?.applicationContext?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (e: Exception) {
        null
    },
    private val throttler: WifiScanThrottler = WifiScanThrottler(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : WifiScanner {

    private val repositoryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    override val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    private val _lastScanTimestamp = MutableStateFlow(0L)
    override val lastScanTimestamp: StateFlow<Long> = _lastScanTimestamp.asStateFlow()

    private val _remainingScanCooldownSeconds = MutableStateFlow(0)
    override val remainingScanCooldownSeconds: StateFlow<Int> = _remainingScanCooldownSeconds.asStateFlow()

    override val isThrottleEnabledOnDevice: Boolean
        get() {
            val ctx = context ?: return true
            return try {
                Settings.Global.getInt(ctx.contentResolver, "wifi_scan_throttle_enabled", 1) == 1
            } catch (e: Exception) {
                true
            }
        }

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
        // Muat data awal dari cache OS (tidak di-throttle)
        refreshFromCache()
    }

    private fun registerScanReceiver() {
        val ctx = context ?: return
        if (isReceiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(scanReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan scan receiver", e)
        }
    }

    fun updateCooldown() {
        _remainingScanCooldownSeconds.value = throttler.remainingCooldownSeconds()
    }

    fun hasRequiredPermissions(): Boolean {
        val ctx = context ?: return true
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearby = ContextCompat.checkSelfPermission(ctx, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            return hasNearby || hasFine
        }
        return hasFine
    }

    override fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return true
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    override fun toggleLabScanThrottle(enable: Boolean): Boolean {
        val ctx = context ?: return false
        return try {
            Settings.Global.putInt(
                ctx.contentResolver,
                "wifi_scan_throttle_enabled",
                if (enable) 1 else 0
            )
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "Tidak dapat mengubah scan throttle (membutuhkan izin WRITE_SECURE_SETTINGS): ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error saat mencoba toggle scan throttle", e)
            false
        }
    }

    override fun startScan(): Boolean {
        if (!hasRequiredPermissions()) {
            _scanState.value = WifiScanState.PermissionMissing
            return false
        }

        if (!isLocationEnabled()) {
            _scanState.value = WifiScanState.LocationDisabled
            return false
        }

        val wm = wifiManager
        if (wm == null) {
            _scanState.value = WifiScanState.Error("WifiManager tidak tersedia pada perangkat.")
            return false
        }

        if (!wm.isWifiEnabled) {
            _scanState.value = WifiScanState.Error("Wi-Fi perangkat dalam kondisi nonaktif.")
            return false
        }

        val now = System.currentTimeMillis()

        // Periksa apakah sedang dalam masa cooldown rate limit
        if (!throttler.canScan(now)) {
            val cooldown = throttler.remainingCooldownSeconds(now)
            _remainingScanCooldownSeconds.value = cooldown
            val currentItems = processRawScanResults(wm.scanResults ?: emptyList())
            _scanState.value = WifiScanState.Throttled(currentItems, cooldown)
            return false
        }

        _scanState.value = WifiScanState.Scanning

        val scanTriggered = try {
            wm.startScan()
        } catch (e: Exception) {
            Log.w(TAG, "Exception saat memanggil startScan()", e)
            false
        }

        // Catat hasil ke throttler: jika false, otomatis berlakukan backoff 10s tambahan (T+30s)
        throttler.recordScanAttempt(success = scanTriggered, now = now)
        val cooldown = throttler.remainingCooldownSeconds(now)
        _remainingScanCooldownSeconds.value = cooldown

        if (scanTriggered) {
            _lastScanTimestamp.value = now
        } else {
            // Jika OS menolak/throttle, baca hasil cache terkini tanpa menunda dan laporkan Throttled
            val currentItems = processRawScanResults(wm.scanResults ?: emptyList())
            _scanState.value = WifiScanState.Throttled(currentItems, cooldown)
        }

        return scanTriggered
    }

    override fun refreshFromCache() {
        readScanResults(success = false)
    }

    private fun readScanResults(success: Boolean) {
        val rawResults = try {
            wifiManager?.scanResults ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Exception saat membaca scanResults dari cache", e)
            emptyList()
        }

        val processedItems = processRawScanResults(rawResults)
        val cooldown = _remainingScanCooldownSeconds.value
        _scanState.value = if (success || cooldown == 0) {
            WifiScanState.Success(processedItems)
        } else {
            WifiScanState.Throttled(processedItems, cooldown)
        }
    }

    override fun teardown() {
        val ctx = context
        if (ctx != null && isReceiverRegistered) {
            try {
                ctx.unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepaskan scanReceiver", e)
            }
        }
        repositoryScope.cancel()
    }

    companion object {
        private const val TAG = "WifiScannerImpl"

        fun parseSecurityType(capabilities: String?): WifiSecurityType {
            val caps = capabilities?.uppercase() ?: return WifiSecurityType.UNKNOWN
            return when {
                // Pure WPA3 Personal (SAE only, tanpa fallback PSK WPA2)
                (caps.contains("SAE") || caps.contains("WPA3")) && !caps.contains("PSK") -> WifiSecurityType.WPA3
                // WPA2 Personal atau mode transisi (Mixed WPA2/WPA3 dengan PSK)
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
