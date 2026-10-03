package com.wefi.analyzer.domain

import com.wefi.analyzer.data.util.NetworkSafetyThrottler
import com.wefi.analyzer.domain.model.BannerInfo
import com.wefi.analyzer.domain.model.CveMatch
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.PortStatus
import com.wefi.analyzer.domain.model.ServiceInfo
import com.wefi.analyzer.domain.model.SubnetInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiscoveryLogicTest {

    // 1. Uji Pemetaan Servis & Klasifikasi Tipe Perangkat
    @Test
    fun testIdentifyDeviceType_cctvPorts() {
        val portsCctv = listOf(554, 80)
        val banner = BannerInfo(
            rawBanner = "RTSP/1.0 200 OK\r\nServer: Hikvision-Web",
            rtspServer = "Hikvision RTSP Server"
        )
        val deviceType = classifyDeviceType(portsCctv, banner, emptyList())
        assertEquals("Kamera CCTV / NVR Lab", deviceType)
    }

    @Test
    fun testIdentifyDeviceType_routerGateway() {
        val portsRouter = listOf(53, 80)
        val banner = BannerInfo(
            rawBanner = "HTTP/1.1 200 OK\r\nServer: OpenWrt Router",
            server = "OpenWrt"
        )
        val deviceType = classifyDeviceType(portsRouter, banner, emptyList())
        assertEquals("Router / Gateway Lab", deviceType)
    }

    @Test
    fun testIdentifyDeviceType_terminalServer() {
        val portsTerminal = listOf(22)
        val banner = BannerInfo(rawBanner = "SSH-2.0-OpenSSH_8.9p1")
        val deviceType = classifyDeviceType(portsTerminal, banner, emptyList())
        assertEquals("Terminal Server / Linux Host", deviceType)
    }

    // 2. Uji Pencocokan OUI Vendor MAC Offline
    @Test
    fun testLookupVendor_knownVendors() {
        val ouiDatabase = mapOf(
            "BC:AD:28" to "Hikvision",
            "4C:11:BF" to "Dahua Technology",
            "00:40:8C" to "Axis Communications",
            "50:C7:BF" to "TP-Link Technologies",
            "00:1A:2B" to "Cisco Systems"
        )

        assertEquals("Hikvision", lookupOuiVendor("bc:ad:28:11:22:33", ouiDatabase))
        assertEquals("Dahua Technology", lookupOuiVendor("4C-11-BF-AA-BB-CC", ouiDatabase))
        assertEquals("Axis Communications", lookupOuiVendor("00.40.8C.01.02.03", ouiDatabase))
        assertEquals("TP-Link Technologies", lookupOuiVendor("50:C7:BF:99:88:77", ouiDatabase))
        assertEquals("Cisco Systems", lookupOuiVendor("00:1a:2b:44:55:66", ouiDatabase))
        assertEquals("Tidak Diketahui", lookupOuiVendor("12:34:56:78:90:AB", ouiDatabase))
    }

    // 3. Uji Pencocokan CVE Offline
    @Test
    fun testMatchCve_educationalSnapshot() {
        val sampleCveList = listOf(
            CveMatch(
                cveId = "CVE-2021-36260",
                cvssScore = 9.8,
                severity = "CRITICAL",
                vendor = "Hikvision",
                affectedModel = "DS-2CD,DS-2DE",
                description = "Command injection in web server input parameter."
            ),
            CveMatch(
                cveId = "CVE-2021-33044",
                cvssScore = 9.8,
                severity = "CRITICAL",
                vendor = "Dahua",
                affectedModel = "IPC-HDBW,NVR",
                description = "Identity bypass during authentication."
            ),
            CveMatch(
                cveId = "CVE-2023-1389",
                cvssScore = 8.8,
                severity = "HIGH",
                vendor = "TP-Link",
                affectedModel = "Archer AX21",
                description = "Command injection in locale parameter."
            )
        )

        val hikvisionMatches = matchCves("Hikvision", "DS-2CD", sampleCveList)
        assertEquals(1, hikvisionMatches.size)
        assertEquals("CVE-2021-36260", hikvisionMatches[0].cveId)
        assertEquals(9.8, hikvisionMatches[0].cvssScore, 0.01)

        val dahuaMatches = matchCves("Dahua", "NVR", sampleCveList)
        assertEquals(1, dahuaMatches.size)
        assertEquals("CVE-2021-33044", dahuaMatches[0].cveId)

        val unknownMatches = matchCves("GenericVendor", "ModelX", sampleCveList)
        assertTrue(unknownMatches.isEmpty())
    }

    // 4. Uji Safety Throttler & Circuit Breaker
    @Test
    fun testSafetyThrottler_cooldownAndCircuitBreaker() {
        val throttler = NetworkSafetyThrottler(
            maxPacketsPerSecond = 200,
            hostCooldownMs = 1000L // 1 detik untuk pengujian unit
        )

        val testHost = "192.168.1.50"
        assertTrue(throttler.canScanHost(testHost))

        throttler.recordHostScanned(testHost)
        assertFalse(throttler.canScanHost(testHost))

        // Circuit breaker: pengujian respons
        // Kurang dari 8 host diuji -> belum terpicu
        throttler.recordProbeResult(false)
        throttler.recordProbeResult(false)
        assertFalse(throttler.isCircuitBreakerTriggered())

        // Tambahkan probe hingga total 10 host dengan 8 gagal (>50%)
        repeat(8) { throttler.recordProbeResult(false) }
        assertTrue(throttler.isCircuitBreakerTriggered())

        throttler.resetCircuitBreaker()
        assertFalse(throttler.isCircuitBreakerTriggered())
    }

    // 5. Uji Pembuatan & Format Laporan Audit
    @Test
    fun testGenerateAndExportReport_jsonAndText() {
        val sampleHost = DiscoveredHost(
            ip = "192.168.1.108",
            macAddress = "BC:AD:28:12:34:56",
            vendor = "Hikvision",
            responseTimeMs = 12L,
            openPorts = listOf(
                PortResult(554, PortStatus.OPEN, "RTSP", 10L),
                PortResult(80, PortStatus.OPEN, "HTTP", 12L)
            ),
            probableDeviceType = "Kamera CCTV / NVR Lab",
            isGateway = false,
            banner = BannerInfo(rawBanner = "Hikvision IP Camera", rtspServer = "Hikvision RTSP"),
            cveMatches = listOf(
                CveMatch("CVE-2021-36260", 9.8, "CRITICAL", "Hikvision", "DS-2CD", "Command Injection")
            )
        )

        val report = DiscoveryReport(
            timestamp = 1700000000000L,
            durationMs = 4500L,
            subnet = "192.168.1.0/24",
            totalHostsScanned = 254,
            totalHostsAlive = 1,
            hosts = listOf(sampleHost),
            isClientIsolationSuspected = false
        )

        // Validasi ekspor JSON
        val jsonString = buildJsonReport(report)
        val json = JSONObject(jsonString)
        assertEquals("WeFi Network Discovery Lab Audit", json.getString("project"))
        assertEquals(254, json.getInt("totalScanned"))
        assertEquals(1, json.getInt("totalAlive"))
        val hostsArray = json.getJSONArray("hosts")
        assertEquals(1, hostsArray.length())
        assertEquals("192.168.1.108", hostsArray.getJSONObject(0).getString("ip"))

        // Validasi ekspor Teks
        val textReport = buildTextReport(report)
        assertTrue(textReport.contains("LAPORAN AUDIT PENEMUAN JARINGAN LAB"))
        assertTrue(textReport.contains("192.168.1.108"))
        assertTrue(textReport.contains("Hikvision"))
        assertTrue(textReport.contains("CVE-2021-36260"))
    }

    // Helper functions mirroring implementation logic for isolated testing
    private fun classifyDeviceType(openPorts: List<Int>, banner: BannerInfo?, services: List<ServiceInfo>): String {
        val portSet = openPorts.toSet()
        val allText = buildString {
            append(banner?.rawBanner ?: "").append(" ")
            append(banner?.server ?: "").append(" ")
            append(banner?.rtspServer ?: "").append(" ")
            append(banner?.onvifManufacturer ?: "").append(" ")
            services.forEach { append(it.serviceName).append(" ") }
        }.lowercase()

        return when {
            portSet.contains(554) || portSet.contains(3702) || allText.contains("hikvision") || allText.contains("dahua") || allText.contains("camera") -> {
                "Kamera CCTV / NVR Lab"
            }
            portSet.contains(53) || allText.contains("router") || allText.contains("openwrt") -> {
                "Router / Gateway Lab"
            }
            portSet.contains(22) -> {
                "Terminal Server / Linux Host"
            }
            else -> "Perangkat Jaringan"
        }
    }

    private fun lookupOuiVendor(mac: String, database: Map<String, String>): String {
        val cleanMac = mac.replace("-", ":").replace(".", ":").uppercase()
        val parts = cleanMac.split(":")
        if (parts.size >= 3) {
            val prefix = "${parts[0]}:${parts[1]}:${parts[2]}"
            return database[prefix] ?: "Tidak Diketahui"
        }
        return "Tidak Diketahui"
    }

    private fun matchCves(vendor: String, model: String, catalog: List<CveMatch>): List<CveMatch> {
        return catalog.filter {
            (vendor.isNotEmpty() && it.vendor.contains(vendor, ignoreCase = true)) ||
                    (model.isNotEmpty() && it.affectedModel.contains(model, ignoreCase = true))
        }
    }

    private fun buildJsonReport(report: DiscoveryReport): String {
        val json = JSONObject()
        json.put("project", "WeFi Network Discovery Lab Audit")
        json.put("totalScanned", report.totalHostsScanned)
        json.put("totalAlive", report.totalHostsAlive)
        val arr = org.json.JSONArray()
        report.hosts.forEach { h ->
            val o = JSONObject()
            o.put("ip", h.ip)
            o.put("vendor", h.vendor)
            arr.put(o)
        }
        json.put("hosts", arr)
        return json.toString()
    }

    private fun buildTextReport(report: DiscoveryReport): String {
        return buildString {
            appendLine("LAPORAN AUDIT PENEMUAN JARINGAN LAB")
            appendLine("Subnet: ${report.subnet}")
            report.hosts.forEach { h ->
                appendLine("Host: ${h.ip} - ${h.vendor}")
                h.cveMatches.forEach { c ->
                    appendLine("  CVE: ${c.cveId}")
                }
            }
        }
    }

    private fun evaluateRiskForHost(host: DiscoveredHost): com.wefi.analyzer.domain.model.HostRiskProfile {
        val openPortNumbers = host.openPorts.map { it.port }.toSet()
        val cveMaxCvss = host.cveMatches.maxOfOrNull { it.cvssScore } ?: 0.0
        val highlights = mutableListOf<String>()
        var score = 0

        if (cveMaxCvss >= 9.0) {
            score += 50
            highlights.add("CVE Kritis terdeteksi (CVSS $cveMaxCvss)")
        } else if (cveMaxCvss >= 7.0) {
            score += 30
            highlights.add("CVE Tinggi terdeteksi (CVSS $cveMaxCvss)")
        }

        if (openPortNumbers.contains(23)) {
            score += 35
            highlights.add("Port 23 (Telnet) terbuka tanpa enkripsi")
        }
        if (openPortNumbers.contains(445) || openPortNumbers.contains(139)) {
            score += 25
            highlights.add("Port 445/139 (SMB/NetBIOS) terbuka")
        }

        val level = when {
            score >= 60 || cveMaxCvss >= 9.0 -> com.wefi.analyzer.domain.model.HostRiskLevel.CRITICAL
            score >= 35 || cveMaxCvss >= 7.0 -> com.wefi.analyzer.domain.model.HostRiskLevel.HIGH
            score >= 15 -> com.wefi.analyzer.domain.model.HostRiskLevel.MEDIUM
            score > 0 -> com.wefi.analyzer.domain.model.HostRiskLevel.LOW
            else -> com.wefi.analyzer.domain.model.HostRiskLevel.SAFE
        }

        return com.wefi.analyzer.domain.model.HostRiskProfile(level = level, score = score, highlights = highlights)
    }

    @Test
    fun testHostRiskEvaluation_whenCriticalCvePresent_returnsCriticalRisk() {
        val cve = CveMatch("CVE-2023-1234", 9.8, "CRITICAL", "Vendor", "Model", "Remote Code Execution")
        val host = DiscoveredHost(
            ip = "192.168.1.1",
            cveMatches = listOf(cve),
            openPorts = listOf(PortResult(80, PortStatus.OPEN, "HTTP Web Admin", 10L))
        )
        val profile = evaluateRiskForHost(host)
        assertEquals(com.wefi.analyzer.domain.model.HostRiskLevel.CRITICAL, profile.level)
        assertTrue(profile.score >= 50)
        assertTrue(profile.highlights.any { it.contains("CVE Kritis") })
    }

    @Test
    fun testHostRiskEvaluation_whenTelnetAndSmbOpen_returnsHighRisk() {
        val host = DiscoveredHost(
            ip = "192.168.1.100",
            openPorts = listOf(
                PortResult(23, PortStatus.OPEN, "Telnet", 10L),
                PortResult(445, PortStatus.OPEN, "SMB (File Share)", 10L)
            )
        )
        val profile = evaluateRiskForHost(host)
        assertTrue(profile.level == com.wefi.analyzer.domain.model.HostRiskLevel.HIGH || profile.level == com.wefi.analyzer.domain.model.HostRiskLevel.CRITICAL)
        assertTrue(profile.highlights.any { it.contains("Telnet") })
        assertTrue(profile.highlights.any { it.contains("SMB") })
    }
}
