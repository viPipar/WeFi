package com.wefi.analyzer.domain.model

enum class PmfMode(val label: String) {
    REQUIRED("Wajib (PMF-R)"),
    CAPABLE("Opsional (PMF-C)"),
    NONE("Tidak Aktif (Rentan Spoofing)")
}

data class WirelessSecurityAuditItem(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val capabilities: String,
    val hasWps: Boolean,
    val pmfMode: PmfMode,
    val hasInsecureCipher: Boolean,
    val isRogueTwinCandidate: Boolean,
    val riskLevel: HostRiskLevel,
    val riskHighlights: List<String>
)

object WirelessSecurityEvaluator {

    fun parsePmfMode(capabilities: String): PmfMode {
        val upper = capabilities.uppercase()
        return when {
            upper.contains("PMF-R") || upper.contains("MFPR") -> PmfMode.REQUIRED
            upper.contains("PMF-C") || upper.contains("MFPC") || upper.contains("SAE") || upper.contains("WPA3") -> PmfMode.CAPABLE
            else -> PmfMode.NONE
        }
    }

    fun hasWps(capabilities: String): Boolean {
        return capabilities.contains("WPS", ignoreCase = true)
    }

    fun hasInsecureCipher(capabilities: String): Boolean {
        val upper = capabilities.uppercase()
        return upper.contains("TKIP") || upper.contains("WEP")
    }

    fun evaluateAuditItem(
        ap: WifiAccessPoint,
        allAps: List<WifiAccessPoint>
    ): WirelessSecurityAuditItem {
        val hasWps = hasWps(ap.capabilities)
        val pmfMode = parsePmfMode(ap.capabilities)
        val hasInsecureCipher = hasInsecureCipher(ap.capabilities)

        // Rogue twin candidate heuristic: Same SSID (non-blank), but different BSSID and (different security or different vendor/frequency)
        val twins = allAps.filter {
            it.ssid.isNotBlank() &&
            it.ssid.equals(ap.ssid, ignoreCase = true) &&
            !it.bssid.equals(ap.bssid, ignoreCase = true)
        }
        val isRogueTwinCandidate = twins.any {
            !it.security.equals(ap.security, ignoreCase = true) ||
            (it.bssid.take(8) != ap.bssid.take(8) && Math.abs(it.rssi - ap.rssi) > 25)
        }

        val highlights = mutableListOf<String>()
        var score = 0

        if (hasWps) {
            score += 35
            highlights.add("WPS (Wi-Fi Protected Setup) aktif")
        }
        if (pmfMode == PmfMode.NONE) {
            score += 25
            highlights.add("802.11w PMF tidak aktif (Rentan deauth spoofing)")
        }
        if (hasInsecureCipher) {
            score += 30
            highlights.add("Mengizinkan cipher usang TKIP/WEP")
        }
        if (isRogueTwinCandidate) {
            score += 40
            highlights.add("Potensi Rogue AP / SSID Kembar dengan enkripsi berbeda")
        }
        if (ap.security.equals("Open", ignoreCase = true)) {
            score += 50
            highlights.add("Jaringan terbuka tanpa enkripsi")
        }

        val riskLevel = when {
            score >= 60 -> HostRiskLevel.CRITICAL
            score >= 35 -> HostRiskLevel.HIGH
            score >= 15 -> HostRiskLevel.MEDIUM
            score > 0 -> HostRiskLevel.LOW
            else -> HostRiskLevel.SAFE
        }

        return WirelessSecurityAuditItem(
            bssid = ap.bssid,
            ssid = ap.ssid,
            rssi = ap.rssi,
            channel = ap.channel,
            security = ap.security,
            capabilities = ap.capabilities,
            hasWps = hasWps,
            pmfMode = pmfMode,
            hasInsecureCipher = hasInsecureCipher,
            isRogueTwinCandidate = isRogueTwinCandidate,
            riskLevel = riskLevel,
            riskHighlights = highlights
        )
    }
}
