package com.wefi.analyzer.ui.screens.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.SpeedTestMetrics
import com.wefi.analyzer.domain.repository.ConnectedNetworkInfo
import com.wefi.analyzer.domain.repository.CurrentConnectionRepository
import com.wefi.analyzer.domain.usecase.RunSpeedTestUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedTestViewModel(
    private val runSpeedTestUseCase: RunSpeedTestUseCase,
    private val connectionRepository: CurrentConnectionRepository
) : ViewModel() {

    val connectionInfo: StateFlow<ConnectedNetworkInfo> = connectionRepository.connectionInfo

    private val _metrics = MutableStateFlow(SpeedTestMetrics())
    val metrics: StateFlow<SpeedTestMetrics> = _metrics.asStateFlow()

    private var testJob: Job? = null

    fun startSpeedTest() {
        if (_metrics.value.isRunning) return

        testJob?.cancel()
        testJob = viewModelScope.launch {
            runSpeedTestUseCase.execute().collect { updatedMetrics ->
                _metrics.value = updatedMetrics
            }
        }
    }
}
