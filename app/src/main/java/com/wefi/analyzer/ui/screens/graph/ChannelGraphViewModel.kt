package com.wefi.analyzer.ui.screens.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.CurrentConnectionRepository
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChannelGraphViewModel(
    private val scannerRepository: WifiScannerRepository,
    private val connectionRepository: CurrentConnectionRepository
) : ViewModel() {

    private val _selectedBand = MutableStateFlow(2.4)
    val selectedBand: StateFlow<Double> = _selectedBand.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    val isWifiEnabled: StateFlow<Boolean> = scannerRepository.isWifiEnabled

    private var frozenResults: List<WifiAccessPoint> = emptyList()

    val displayResults: StateFlow<List<WifiAccessPoint>> = combine(
        scannerRepository.scanResults,
        _isPaused
    ) { results, paused ->
        if (paused) {
            frozenResults
        } else {
            frozenResults = results
            results
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedBssid: StateFlow<String?> = connectionRepository.connectionInfo
        .map { it.accessPoint?.bssid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setBand(band: Double) {
        _selectedBand.value = band
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun triggerScan() {
        if (!_isPaused.value) {
            scannerRepository.startScan()
        }
    }
}
