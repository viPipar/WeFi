package com.wefi.analyzer.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import com.wefi.analyzer.domain.usecase.CalculateDistanceUseCase
import com.wefi.analyzer.domain.usecase.CalculateWifiQualityScoreUseCase
import com.wefi.analyzer.domain.usecase.ParsePhyCapabilitiesUseCase
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiScannerRepositoryImpl(
    private val context: Context,
    private val calculateDistanceUseCase: CalculateDistanceUseCase = CalculateDistanceUseCase(),
    private val parsePhyCapabilitiesUseCase: ParsePhyCapabilitiesUseCase = ParsePhyCapabilitiesUseCase(),
    private val calculateWifiQualityScoreUseCase: CalculateWifiQualityScoreUseCase = CalculateWifiQualityScoreUseCase()
) : WifiScannerRepository {

    private val wifiManager = try {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        Log.w(TAG, "Gagal mendapatkan WifiManager", e)
        null
    }

    private val _scanResults = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    override val scanResults: StateFlow<List<WifiAccessPoint>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedPreset = MutableStateFlow(EnvironmentPreset.INDOOR)
    override val selectedPreset: StateFlow<EnvironmentPreset> = _selectedPreset.asStateFlow()

    private val _isWifiEnabled = MutableStateFlow(checkIsWifiEnabled())
    override val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    private var isReceiverRegistered = false
    private var lastScanTriggerTime = 0L
    private val MIN_SCAN_INTERVAL_MS = 20_000L // 20 detik (Golden Time: mematuhi batas 4 scan per 120s framework Android)
    private var periodicScanJob: Job? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    _isScanning.value = false
                    _isWifiEnabled.value = checkIsWifiEnabled()
                    processScanResults()
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    _isWifiEnabled.value = checkIsWifiEnabled()
                    if (!_isWifiEnabled.value) {
                        _scanResults.value = emptyList()
                        _isScanning.value = false
                    }
                }
            }
        }
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                wifiScanReceiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan BroadcastReceiver wifi scan", e)
        }
        // Initial fetch from system cache
        processScanResults()
        startPeriodicScan()
    }

    fun startPeriodicScan() {
        periodicScanJob?.cancel()
        periodicScanJob = repositoryScope.launch {
            while (isActive) {
                delay(MIN_SCAN_INTERVAL_MS)
                if (_isWifiEnabled.value) {
                    startScan()
                }
            }
        }
    }

    fun stopPeriodicScan() {
        periodicScanJob?.cancel()
        periodicScanJob = null
    }

    private fun checkIsWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun startScan() {
        _isWifiEnabled.value = checkIsWifiEnabled()
        if (!_isWifiEnabled.value) {
            _isScanning.value = false
            return
        }

        if (_isScanning.value) return

        val now = System.currentTimeMillis()
        if (now - lastScanTriggerTime < MIN_SCAN_INTERVAL_MS) {
            // Dalam masa cooldown: tetap perbarui data dari cache terkini
            processScanResults()
            return
        }

        _isScanning.value = true
        lastScanTriggerTime = now

        val success = try {
            wifiManager?.startScan() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "wifiManager.startScan() dilempar exception oleh OS", e)
            false
        }

        if (!success) {
            // OS scan throttle atau failure: segera perbarui dari cache tanpa macet
            _isScanning.value = false
            processScanResults()
        }
    }

    override fun setEnvironmentPreset(preset: EnvironmentPreset) {
        _selectedPreset.value = preset
        processScanResults()
    }

    override fun teardown() {
        stopPeriodicScan()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepaskan wifiScanReceiver", e)
            }
        }
    }

    private fun processScanResults() {
        repositoryScope.launch {
            val rawResults = try {
                wifiManager?.scanResults ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "wifiManager.scanResults dilempar exception", e)
                emptyList()
            }

            val preset = _selectedPreset.value
            val mappedList = rawResults.mapNotNull { scan ->
                try {
                    mapScanResultToDomain(scan, preset)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.rssi }

            _scanResults.value = mappedList
        }
    }

    private fun mapScanResultToDomain(scan: ScanResult, preset: EnvironmentPreset): WifiAccessPoint {
        val channel = ChannelFrequencyUtils.toChannel(scan.frequency)
        val distance = calculateDistanceUseCase.execute(
            rssi = scan.level,
            frequencyMhz = scan.frequency,
            preset = preset
        )

        val widthMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (scan.channelWidth) {
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                else -> 20
            }
        } else {
            20
        }

        val capabilities = scan.capabilities ?: ""
        val phyRate = parsePhyCapabilitiesUseCase.execute(capabilities, widthMhz)
        val score = calculateWifiQualityScoreUseCase.execute(scan.level, phyRate)

        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            scan.wifiSsid?.toString()?.replace("\"", "") ?: scan.SSID ?: ""
        } else {
            scan.SSID ?: ""
        }

        val security = when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }

        return WifiAccessPoint(
            bssid = scan.BSSID ?: "00:00:00:00:00:00",
            ssid = ssid,
            rssi = scan.level,
            frequencyMhz = scan.frequency,
            channel = channel,
            channelWidthMhz = widthMhz,
            distanceMeters = distance,
            maxPhyRateMbps = phyRate,
            security = security,
            qualityScore = score
        )
    }

    companion object {
        private const val TAG = "WifiScannerRepo"
    }
}
