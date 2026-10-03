package com.wefi.analyzer.domain.model

enum class HealthScoreGrade(val label: String, val minScore: Int) {
    A_PLUS("Sempurna (A+)", 90),
    A("Sangat Baik (A)", 80),
    B("Baik (B)", 65),
    C("Perlu Perhatian (C)", 50),
    CRITICAL("Kritis & Berisiko (D/F)", 0);

    companion object {
        fun fromScore(score: Int): HealthScoreGrade = when {
            score >= 90 -> A_PLUS
            score >= 80 -> A
            score >= 65 -> B
            score >= 50 -> C
            else -> CRITICAL
        }
    }
}

enum class HealthPillarType(val title: String) {
    PHYSICAL_RF("Sinyal & Kualitas Fisik RF"),
    CHANNEL_INTERFERENCE("Kepadatan & Interferensi Kanal"),
    GATEWAY_TRANSPORT("Konektivitas Gateway & DNS"),
    WIRELESS_SECURITY("Protokol Keamanan Wi-Fi"),
    SUBNET_EXPOSURE("Paparan Subnet & Port Lab")
}

enum class PillarStatus {
    OPTIMAL,
    FAIR,
    WARNING,
    DANGER
}

data class HealthPillarScore(
    val type: HealthPillarType,
    val score: Int,
    val weight: Double,
    val status: PillarStatus,
    val summary: String,
    val metrics: Map<String, String> = emptyMap()
)

enum class RemediationImpact {
    HIGH,
    MEDIUM,
    LOW
}

data class ActionableRemediation(
    val id: String,
    val title: String,
    val description: String,
    val impact: RemediationImpact,
    val targetTabRoute: String? = null
)

data class NetworkHealthReport(
    val timestamp: Long,
    val ssid: String,
    val bssid: String,
    val overallScore: Int,
    val grade: HealthScoreGrade,
    val pillars: List<HealthPillarScore>,
    val remediations: List<ActionableRemediation>,
    val isInternetReachable: Boolean
)
