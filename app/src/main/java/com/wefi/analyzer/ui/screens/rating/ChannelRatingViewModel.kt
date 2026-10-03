package com.wefi.analyzer.ui.screens.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.ChannelRating
import com.wefi.analyzer.domain.repository.WifiScannerRepository
import com.wefi.analyzer.domain.usecase.CalculateChannelRatingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ChannelRatingViewModel(
    private val scannerRepository: WifiScannerRepository,
    private val calculateChannelRatingUseCase: CalculateChannelRatingUseCase = CalculateChannelRatingUseCase()
) : ViewModel() {

    private val _selectedBand = MutableStateFlow(2.4)
    val selectedBand: StateFlow<Double> = _selectedBand.asStateFlow()

    val isWifiEnabled: StateFlow<Boolean> = scannerRepository.isWifiEnabled

    val channelRatings: StateFlow<List<ChannelRating>> = combine(
        scannerRepository.scanResults,
        _selectedBand
    ) { results, band ->
        calculateChannelRatingUseCase.execute(results, band).sortedByDescending { it.stars }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBand(band: Double) {
        _selectedBand.value = band
    }

    fun triggerScan() {
        scannerRepository.startScan()
    }
}
