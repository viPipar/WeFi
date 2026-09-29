package com.wefi.analyzer.ui.screens.aplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ApSortOption {
    SIGNAL_STRENGTH,
    DISTANCE,
    SSID_NAME
}

class ApListViewModel(
    private val scannerRepository: WifiScannerRepository
) : ViewModel() {

    val selectedPreset = scannerRepository.selectedPreset

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(ApSortOption.SIGNAL_STRENGTH)
    val sortOption: StateFlow<ApSortOption> = _sortOption.asStateFlow()

    val filteredAps: StateFlow<List<WifiAccessPoint>> = combine(
        scannerRepository.scanResults,
        _searchQuery,
        _sortOption
    ) { results, query, sort ->
        val filtered = if (query.isBlank()) {
            results
        } else {
            results.filter { it.ssid.contains(query, ignoreCase = true) || it.bssid.contains(query, ignoreCase = true) }
        }

        when (sort) {
            ApSortOption.SIGNAL_STRENGTH -> filtered.sortedByDescending { it.rssi }
            ApSortOption.DISTANCE -> filtered.sortedBy { it.distanceMeters }
            ApSortOption.SSID_NAME -> filtered.sortedBy { it.ssid.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnvironmentPreset(preset: EnvironmentPreset) {
        scannerRepository.setEnvironmentPreset(preset)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: ApSortOption) {
        _sortOption.value = option
    }

    fun triggerScan() {
        scannerRepository.startScan()
    }
}
