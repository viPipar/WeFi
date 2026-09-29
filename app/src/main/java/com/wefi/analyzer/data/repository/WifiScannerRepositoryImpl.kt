package com.wefi.analyzer.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiScannerRepositoryImpl(
    private val context: Context,
    private val calculateDistanceUseCase: CalculateDistanceUseCase = CalculateDistanceUseCase(),
    private val parsePhyCapabilitiesUseCase: ParsePhyCapabilitiesUseCase = ParsePhyCapabilitiesUseCase(),
    private val calculateWifiQualityScoreUseCase: CalculateWifiQualityScoreUseCase = CalculateWifiQualityScoreUseCase()
) : WifiScannerRepository {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _scanResults = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    override val scanResults: StateFlow<List<WifiAccessPoint>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedPreset = MutableStateFlow(EnvironmentPreset.INDOOR)
    override val selectedPreset: StateFlow<EnvironmentPreset> = _selectedPreset.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                _isScanning.value = false
                processScanResults()
            }
        }
    }

    init {
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            ContextCompat.registerReceiver(
                context,
                wifiScanReceiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            // Guard against any OEM receiver registration exception
        }
        // Initial fetch from system cache
        processScanResults()
    }

    override fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true

        val success = try {
            wifiManager?.startScan() ?: false
        } catch (e: Exception) {
            false
        }

        if (!success) {
            // Throttled by Android OS or failure, process existing cached results
            _isScanning.value = false
            processScanResults()
        }
    }

    override fun setEnvironmentPreset(preset: EnvironmentPreset) {
        _selectedPreset.value = preset
        processScanResults()
    }

    private fun processScanResults() {
        repositoryScope.launch {
            val rawResults = try {
                wifiManager?.scanResults ?: emptyList()
            } catch (e: Exception) {
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
}
