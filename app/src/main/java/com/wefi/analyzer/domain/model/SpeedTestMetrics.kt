package com.wefi.analyzer.domain.model

enum class SpeedTestStage {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    FINISHED,
    ERROR
}

/**
 * Metrik hasil active speedtest jaringan.
 */
data class SpeedTestMetrics(
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val isRunning: Boolean = false,
    val stage: SpeedTestStage = SpeedTestStage.IDLE,
    val progress: Float = 0f,
    val errorMessage: String? = null
)
