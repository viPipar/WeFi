package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.SpeedTestMetrics
import com.wefi.analyzer.domain.repository.SpeedTestRepository
import kotlinx.coroutines.flow.Flow

class RunSpeedTestUseCase(
    private val repository: SpeedTestRepository
) {
    fun execute(): Flow<SpeedTestMetrics> {
        return repository.runSpeedTest()
    }
}
