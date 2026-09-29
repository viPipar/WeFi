package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.SpeedTestMetrics
import kotlinx.coroutines.flow.Flow

interface SpeedTestRepository {
    fun runSpeedTest(): Flow<SpeedTestMetrics>
}
