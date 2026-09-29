package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.DeviceHardwareState
import kotlinx.coroutines.flow.StateFlow

interface DeviceHardwareRepository {
    val hardwareState: StateFlow<DeviceHardwareState>
    fun refresh()
    fun teardown()
}
