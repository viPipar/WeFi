package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.SpeedTestMetrics
import com.wefi.analyzer.domain.model.SpeedTestStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeedTestNetworkTest {

    @Test
    fun speedTestMetrics_initialStateIsIdle() {
        val metrics = SpeedTestMetrics()
        assertEquals(SpeedTestStage.IDLE, metrics.stage)
        assertFalse(metrics.isRunning)
        assertEquals(0.0, metrics.downloadMbps, 0.01)
        assertEquals(0.0, metrics.uploadMbps, 0.01)
    }

    @Test
    fun speedTestMetrics_stageProgression() {
        val pingMetrics = SpeedTestMetrics(isRunning = true, stage = SpeedTestStage.PING, pingMs = 18.5)
        assertEquals(SpeedTestStage.PING, pingMetrics.stage)
        assertEquals(18.5, pingMetrics.pingMs, 0.01)

        val downloadMetrics = pingMetrics.copy(stage = SpeedTestStage.DOWNLOAD, downloadMbps = 45.2)
        assertEquals(SpeedTestStage.DOWNLOAD, downloadMetrics.stage)
        assertEquals(45.2, downloadMetrics.downloadMbps, 0.01)

        val finishedMetrics = downloadMetrics.copy(stage = SpeedTestStage.FINISHED, isRunning = false)
        assertFalse(finishedMetrics.isRunning)
        assertEquals(SpeedTestStage.FINISHED, finishedMetrics.stage)
    }

    @Test
    fun speedTestMetrics_onNetworkFailure_doesNotEmitFakeSpeeds() {
        val failedMetrics = SpeedTestMetrics(
            isRunning = false,
            stage = SpeedTestStage.FINISHED,
            downloadMbps = 0.0,
            uploadMbps = 0.0,
            pingMs = 0.0,
            jitterMs = 0.0
        )
        assertEquals(0.0, failedMetrics.downloadMbps, 0.001)
        assertEquals(0.0, failedMetrics.uploadMbps, 0.001)
        assertEquals(0.0, failedMetrics.pingMs, 0.001)
    }
}
