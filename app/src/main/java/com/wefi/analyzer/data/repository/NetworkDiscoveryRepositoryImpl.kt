package com.wefi.analyzer.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.wefi.analyzer.data.util.NetworkSafetyThrottler
import com.wefi.analyzer.domain.model.AssetCategory
import com.wefi.analyzer.domain.model.AuthPostureResult
import com.wefi.analyzer.domain.model.AuthStatus
import com.wefi.analyzer.domain.model.BannerInfo
import com.wefi.analyzer.domain.model.CveMatch
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.HostRiskLevel
import com.wefi.analyzer.domain.model.HostRiskProfile
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.PortStatus
import com.wefi.analyzer.domain.model.ServiceInfo
import com.wefi.analyzer.domain.model.SubnetInfo
import com.wefi.analyzer.domain.repository.NetworkDiscoveryRepository
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class NetworkDiscoveryRepositoryImpl(
    private val context: Context? = null,
    private val throttler: NetworkSafetyThrottler = NetworkSafetyThrottler()
) : NetworkDiscoveryRepository {

    private val tag = "NetworkDiscoveryRepo"
    private val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val nsdManager = context?.applicationContext?.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var multicastLock: WifiManager.MulticastLock? = null

    // Cache database OUI & snapshot CVE offline
    private val ouiCache = ConcurrentHashMap<String, String>()
    private val cveList = mutableListOf<CveCatalogEntry>()

    init {
        loadOuiDatabase()
        loadCveCatalog()
    }

    private data class CveCatalogEntry(
        val cveId: String,
        val vendor: String,
        val affectedModels: String,
        val affectedFirmware: String,
        val cvss: Double,
        val severity: String,
        val description: String
    )

    private fun loadOuiDatabase() {
        try {
            val ctx = context ?: return
            ctx.assets.open("oui_database.csv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split(",", limit = 2)
                        if (parts.size == 2) {
                            val prefix = parts[0].trim().uppercase()
                            val vendor = parts[1].trim()
                            ouiCache[prefix] = vendor
                        }
                    }
                }
            }
            Log.d(tag, "Loaded ${ouiCache.size} OUI database entries")
        } catch (e: Exception) {
            Log.w(tag, "Gagal memuat oui_database.csv dari assets: ${e.message}")
        }
    }

    private fun loadCveCatalog() {
        try {
            val ctx = context ?: return
            val jsonString = ctx.assets.open("cve_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                cveList.add(
                    CveCatalogEntry(
                        cveId = obj.optString("cveId"),
                        vendor = obj.optString("vendor"),
                        affectedModels = obj.optString("affectedModels"),
                        affectedFirmware = obj.optString("affectedFirmware"),
                        cvss = obj.optDouble("cvss", 7.0),
                        severity = obj.optString("severity", "HIGH"),
                        description = obj.optString("description")
                    )
                )
            }
            Log.d(tag, "Loaded ${cveList.size} CVE catalog entries")
        } catch (e: Exception) {
            Log.w(tag, "Gagal memuat cve_catalog.json dari assets: ${e.message}")
        }
    }

    override fun getSubnetInfo(): SubnetInfo? {
        try {
            val dhcp = wifiManager?.dhcpInfo
            val ipInt = dhcp?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipBytes = byteArrayOf(
                    (ipInt and 0xFF).toByte(),
                    ((ipInt shr 8) and 0xFF).toByte(),
                    ((ipInt shr 16) and 0xFF).toByte(),
                    ((ipInt shr 24) and 0xFF).toByte()
                )
                val localIp = InetAddress.getByAddress(ipBytes).hostAddress ?: "192.168.1.10"
                val gatewayInt = dhcp?.gateway ?: 0
                val gatewayIp = if (gatewayInt != 0) {
                    val gwBytes = byteArrayOf(
                        (gatewayInt and 0xFF).toByte(),
                        ((gatewayInt shr 8) and 0xFF).toByte(),
                        ((gatewayInt shr 16) and 0xFF).toByte(),
                        ((gatewayInt shr 24) and 0xFF).toByte()
                    )
                    InetAddress.getByAddress(gwBytes).hostAddress
                } else null

                // Default /24 subnet calculation
                val parts = localIp.split(".")
                if (parts.size == 4) {
                    val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                    val hosts = (1..254).map { "$subnetPrefix.$it" }
                    return SubnetInfo(
                        baseIp = "$subnetPrefix.0",
                        netmask = "255.255.255.0",
                        prefixLength = 24,
                        gatewayIp = gatewayIp,
                        hostsToScan = hosts
                    )
                }
            }

            // Fallback via NetworkInterface jika dhcpInfo 0
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.name.contains("wlan", ignoreCase = true) || nif.name.contains("eth", ignoreCase = true)) {
                    for (addr in nif.interfaceAddresses) {
                        val inet = addr.address
                        if (!inet.isLoopbackAddress && inet.hostAddress?.contains(":") == false) {
                            val ip = inet.hostAddress ?: continue
                            val parts = ip.split(".")
                            if (parts.size == 4) {
                                val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                                val hosts = (1..254).map { "$subnetPrefix.$it" }
                                return SubnetInfo(
                                    baseIp = "$subnetPrefix.0",
                                    netmask = "255.255.255.0",
                                    prefixLength = 24,
                                    gatewayIp = "$subnetPrefix.1",
                                    hostsToScan = hosts
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Gagal mendapatkan subnet: ${e.message}", e)
        }
        return null
    }

    /**
     * Fitur A: Ping Sweep dengan Hybrid Probe (ICMP echo fallback TCP connect).
     * Android non-root tidak mendukung raw ICMP socket, sehingga digunakan InetAddress.isReachable()
     * yang otomatis fallback ke TCP port 80/443/554/22.
     * Menggunakan channelFlow tanpa blocking Head-of-Line sehingga host aktif langsung di-emit saat merespons.
     * Concurrency limit: 32 parallel coroutines.
     */
    override fun pingSweep(subnet: SubnetInfo): Flow<DiscoveredHost> = channelFlow {
        val semaphore = Semaphore(32)
        throttler.resetCircuitBreaker()

        // Prioritaskan Gateway pertama kali jika ada
        val prioritizedHosts = subnet.hostsToScan.sortedByDescending { it == subnet.gatewayIp }

        prioritizedHosts.forEach { hostIp ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    // Rate limiter pacing (maksimal 200 pkt/detik)
                    throttler.throttlePacket()

                    val isGateway = (hostIp == subnet.gatewayIp)
                    val startTime = System.currentTimeMillis()
                    var isAlive = false

                    try {
                        val inet = InetAddress.getByName(hostIp)
                        // 1. Coba isReachable
                        isAlive = inet.isReachable(300)
                    } catch (ignored: Exception) {
                    }

                    // 2. Fallback TCP probe jika isReachable bernilai false (sangat umum di Android)
                    if (!isAlive) {
                        val probePorts = intArrayOf(80, 554, 443, 22, 8000)
                        for (p in probePorts) {
                            try {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(hostIp, p), 600)
                                    isAlive = true
                                }
                            } catch (e: Exception) {
                                // Jika respon Connection Refused (RST packet), host tetap hidup!
                                if (e.message?.contains("refused", ignoreCase = true) == true) {
                                    isAlive = true
                                }
                            }
                            if (isAlive) break
                        }
                    }

                    val duration = System.currentTimeMillis() - startTime
                    throttler.recordProbeResult(isAlive)

                    if (isAlive) {
                        throttler.recordHostScanned(hostIp)
                        val mac = readMacFromArp(hostIp)
                        val vendor = if (mac != null) lookupVendor(mac) else "Tidak Diketahui"
                        send(
                            DiscoveredHost(
                                ip = hostIp,
                                macAddress = mac,
                                vendor = vendor,
                                responseTimeMs = duration.coerceAtLeast(1L),
                                isGateway = isGateway
                            )
                        )
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fitur B: TCP Connect Port Scan (Bukan SYN scan untuk kepatuhan non-root).
     * Menggunakan channelFlow sehingga hasil port langsung di-emit saat selesai tanpa menunggu port lain.
     * Timeout per-port 500ms, concurrency limit 64 worker.
     */
    override fun scanPorts(hostIp: String, ports: List<Int>): Flow<PortResult> = channelFlow {
        val semaphore = Semaphore(64)

        ports.forEach { port ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    throttler.throttlePacket()
                    val startTime = System.currentTimeMillis()
                    val serviceGuess = getStaticServiceGuess(port)

                    var status = PortStatus.TIMEOUT
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(hostIp, port), 1200)
                            status = PortStatus.OPEN
                        }
                    } catch (ste: SocketTimeoutException) {
                        status = PortStatus.TIMEOUT
                    } catch (e: Exception) {
                        status = if (e.message?.contains("refused", ignoreCase = true) == true) {
                            PortStatus.CLOSED
                        } else {
                            PortStatus.TIMEOUT
                        }
                    }
                    val duration = System.currentTimeMillis() - startTime
                    send(
                        PortResult(
                            port = port,
                            status = status,
                            serviceName = serviceGuess,
                            responseTimeMs = duration
                        )
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fitur C: mDNS Discovery via Android NsdManager resmi.
     * Menggunakan antrean pekerja sekuensial untuk resolveService guna mencegah konkurensi FAILURE_ALREADY_ACTIVE (code 3).
     */
    override fun discoverMdns(timeoutMs: Long): Flow<ServiceInfo> = callbackFlow {
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val serviceTypes = listOf("_http._tcp.", "_rtsp._tcp.", "_onvif._tcp.", "_workstation._tcp.")
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val resolveChannel = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        // Coroutine pekerja sekuensial untuk resolveService: mencegah FAILURE_ALREADY_ACTIVE (code 3)
        val resolveJob = launch(Dispatchers.IO) {
            for (serviceInfo in resolveChannel) {
                try {
                    withTimeoutOrNull(2500L) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            try {
                                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                        Log.w(tag, "mDNS resolve failed: code $errorCode")
                                        if (cont.isActive) cont.resume(Unit)
                                    }

                                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                        val host = resolvedInfo?.host?.hostAddress
                                        val port = resolvedInfo?.port ?: 0
                                        val name = resolvedInfo?.serviceName ?: "Unknown Service"
                                        if (host != null) {
                                            trySend(
                                                ServiceInfo(
                                                    serviceName = name,
                                                    serviceType = resolvedInfo.serviceType ?: "",
                                                    host = host,
                                                    ip = host,
                                                    port = port,
                                                    source = "mDNS"
                                                )
                                            )
                                        }
                                        if (cont.isActive) cont.resume(Unit)
                                    }
                                })
                            } catch (e: Exception) {
                                Log.w(tag, "Exception calling resolveService: ${e.message}")
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Timeout / error resolving mDNS service: ${e.message}")
                }
            }
        }

        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(tag, "mDNS onStartDiscoveryFailed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}

                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.d(tag, "mDNS discovery started for $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo == null) return
                    resolveChannel.trySend(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            }
            listeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(tag, "Gagal memulai mDNS discovery untuk $serviceType: ${e.message}")
            }
        }

        kotlinx.coroutines.delay(timeoutMs)

        listeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (ignored: Exception) {}
        }
        resolveChannel.close()
        resolveJob.cancel()
        close()

        awaitClose {
            listeners.forEach { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (ignored: Exception) {}
            }
            resolveChannel.close()
            resolveJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fitur D: SSDP Discovery via UDP Multicast 239.255.255.250:1900.
     * Menggunakan MulticastLock untuk mencegah filter hardware Android.
     */
    override fun discoverSsdp(timeoutMs: Long): Flow<ServiceInfo> = flow {
        acquireMulticastLock()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 1000

            val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"

            val queryBytes = ssdpQuery.toByteArray()
            val multicastGroup = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(queryBytes, queryBytes.size, multicastGroup, 1900)

            // Kirim 2 paket query dengan jeda singkat
            socket.send(packet)
            throttler.throttlePacket()

            val endTime = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)

            while (System.currentTimeMillis() < endTime) {
                try {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)
                    val rawResponse = String(responsePacket.data, 0, responsePacket.length)
                    val senderIp = responsePacket.address.hostAddress ?: continue

                    // Parse SSDP Headers
                    var location = ""
                    var server = ""
                    var st = ""
                    rawResponse.lines().forEach { line ->
                        val lower = line.lowercase()
                        when {
                            lower.startsWith("location:") -> location = line.substring(9).trim()
                            lower.startsWith("server:") -> server = line.substring(7).trim()
                            lower.startsWith("st:") -> st = line.substring(3).trim()
                        }
                    }

                    emit(
                        ServiceInfo(
                            serviceName = server.ifEmpty { "UPnP/SSDP Device" },
                            serviceType = st.ifEmpty { "urn:schemas-upnp-org:device" },
                            host = location,
                            ip = senderIp,
                            port = responsePacket.port,
                            source = "SSDP"
                        )
                    )
                } catch (ste: SocketTimeoutException) {
                    // Socket timeout normal per-receive iteration
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "SSDP Discovery error: ${e.message}")
        } finally {
            socket?.close()
            releaseMulticastLock()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fitur E: MAC OUI Lookup dari database offline.
     */
    override fun lookupVendor(macAddress: String): String {
        val cleanMac = macAddress.replace("-", ":").replace(".", ":").uppercase()
        val parts = cleanMac.split(":")
        if (parts.size >= 3) {
            val prefix = "${parts[0]}:${parts[1]}:${parts[2]}"
            val matched = ouiCache[prefix]
            if (matched != null) return matched
        }
        return "Tidak Diketahui"
    }

    /**
     * Membaca tabel ARP lokal (/proc/net/arp).
     * Catatan Pembatasan Android: Sejak Android 10 (API 29+), akses ke /proc/net/arp dibatasi
     * oleh SELinux sandbox. Fungsi ini bertindak sebagai best-effort fallback.
     */
    private fun readMacFromArp(targetIp: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ memblokir pembacaan ARP cache tanpa root
            return null
        }
        try {
            val file = java.io.File("/proc/net/arp")
            if (!file.canRead()) return null
            return file.useLines { lines ->
                for (line in lines) {
                    val tokens = line.split("\\s+".toRegex())
                    if (tokens.size >= 4 && tokens[0] == targetIp) {
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00") {
                            return@useLines mac
                        }
                    }
                }
                null
            }
        } catch (ignored: Exception) {}
        return null
    }

    /**
     * Fitur F: Identifikasi Servis Statis
     */
    private fun getStaticServiceGuess(port: Int): String {
        return when (port) {
            21 -> "FTP"
            22 -> "SSH"
            23 -> "Telnet"
            25 -> "SMTP"
            53 -> "DNS"
            80 -> "HTTP Web Admin"
            110 -> "POP3"
            139 -> "NetBIOS"
            443 -> "HTTPS"
            445 -> "SMB (File Share)"
            554 -> "RTSP (CCTV Stream)"
            1883 -> "MQTT (IoT Broker)"
            3128 -> "Squid / HTTP Proxy"
            3306 -> "MySQL Database"
            3389 -> "RDP Remote Desktop"
            3702 -> "WS-Discovery / ONVIF"
            5000 -> "UPnP / Web Service"
            8000 -> "Hikvision SDK / Web"
            8080 -> "HTTP-Alt"
            8291 -> "MikroTik Winbox"
            8443 -> "HTTPS-Alt"
            8888 -> "HTTP-Proxy / Web"
            9000 -> "Portainer / Web API"
            37777 -> "Dahua DVR/NVR Media"
            else -> "Port $port"
        }
    }

    override fun evaluateHostRisk(host: DiscoveredHost): HostRiskProfile {
        val openPortNumbers = host.openPorts.map { it.port }.toSet()
        val cveMaxCvss = host.cveMatches.maxOfOrNull { it.cvssScore } ?: 0.0
        val highlights = mutableListOf<String>()

        var score = 0

        // 1. CVE Risk Evaluation
        if (cveMaxCvss >= 9.0) {
            score += 50
            highlights.add("CVE Kritis terdeteksi (CVSS $cveMaxCvss)")
        } else if (cveMaxCvss >= 7.0) {
            score += 30
            highlights.add("CVE Tinggi terdeteksi (CVSS $cveMaxCvss)")
        } else if (cveMaxCvss >= 4.0) {
            score += 15
            highlights.add("CVE Menengah terdeteksi (CVSS $cveMaxCvss)")
        }

        // 2. Open Port Risk Evaluation
        if (openPortNumbers.contains(23)) {
            score += 35
            highlights.add("Port 23 (Telnet) terbuka tanpa enkripsi")
        }
        if (openPortNumbers.contains(445) || openPortNumbers.contains(139)) {
            score += 25
            highlights.add("Port 445/139 (SMB/NetBIOS) terbuka")
        }
        if (openPortNumbers.contains(8291)) {
            score += 15
            highlights.add("Port 8291 (MikroTik Winbox) terbuka")
        }
        if (openPortNumbers.contains(21)) {
            score += 20
            highlights.add("Port 21 (FTP plaintext) terbuka")
        }
        if (openPortNumbers.contains(1883)) {
            score += 15
            highlights.add("Port 1883 (MQTT Broker) terbuka")
        }
        if (openPortNumbers.contains(80) || openPortNumbers.contains(8080)) {
            score += 10
            highlights.add("HTTP Web Admin terbuka")
        }
        if (openPortNumbers.contains(554)) {
            score += 10
            highlights.add("RTSP CCTV Stream aktif")
        }

        val level = when {
            score >= 60 || cveMaxCvss >= 9.0 -> HostRiskLevel.CRITICAL
            score >= 35 || cveMaxCvss >= 7.0 -> HostRiskLevel.HIGH
            score >= 15 || cveMaxCvss >= 4.0 -> HostRiskLevel.MEDIUM
            score > 0 -> HostRiskLevel.LOW
            else -> HostRiskLevel.SAFE
        }

        val summary = when (level) {
            HostRiskLevel.CRITICAL -> "Perangkat memiliki eksposur tingkat kritis yang membutuhkan mitigasi segera."
            HostRiskLevel.HIGH -> "Ditemukan servis sensitif atau port plaintext terbuka yang berisiko di jaringan lab."
            HostRiskLevel.MEDIUM -> "Beberapa servis aktif terdeteksi. Disarankan melakukan audit berkala."
            HostRiskLevel.LOW -> "Port terdeteksi aktif dengan konfigurasi relatif standar."
            HostRiskLevel.SAFE -> "Tidak ada kerentanan atau servis sensitif yang terekspos terbuka."
        }

        val recommendations = mutableListOf<String>()
        if (openPortNumbers.contains(23)) recommendations.add("Ganti Telnet port 23 dengan SSH (port 22) terenkripsi.")
        if (openPortNumbers.contains(21)) recommendations.add("Gunakan SFTP/FTPS sebagai pengganti FTP plaintext.")
        if (openPortNumbers.contains(80) || openPortNumbers.contains(8080)) recommendations.add("Aktifkan HTTPS dan gunakan kredensial kuat untuk Web Admin.")
        if (openPortNumbers.contains(445) || openPortNumbers.contains(139)) recommendations.add("Batasi akses SMB/NetBIOS hanya untuk subnet tepercaya.")
        if (openPortNumbers.contains(8291)) recommendations.add("Ubah port Winbox default atau batasi IP akses di firewall MikroTik.")
        if (cveMaxCvss >= 7.0) recommendations.add("Perbarui firmware vendor ke versi terbaru untuk menambal CVE.")
        if (recommendations.isEmpty() && level != HostRiskLevel.SAFE) recommendations.add("Pantau lalu lintas perangkat di jaringan lab secara berkala.")

        return HostRiskProfile(
            level = level,
            score = score.coerceAtMost(100),
            highlights = highlights,
            summary = summary,
            recommendations = recommendations
        )
    }

    /**
     * Fitur G: Banner Grabbing (Read-Only, tanpa autentikasi, tanpa payload eksploitasi).
     */
    override suspend fun grabBanner(hostIp: String, port: Int): BannerInfo? = withContext(Dispatchers.IO) {
        throttler.throttlePacket()
        return@withContext when (port) {
            80, 8080, 8443, 8000 -> grabHttpBanner(hostIp, port)
            554 -> grabRtspBanner(hostIp, port)
            3702 -> grabOnvifBanner(hostIp, port)
            else -> null
        }
    }

    private fun grabHttpBanner(hostIp: String, port: Int): BannerInfo? {
        try {
            Socket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(hostIp, port), 2000)

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                writer.write("HEAD / HTTP/1.1\r\nHost: $hostIp\r\nUser-Agent: WeFi-LabAudit/1.0\r\nConnection: close\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val sb = StringBuilder()
                var serverHeader: String? = null
                var xPoweredBy: String? = null

                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 25) {
                    sb.append(line).append("\n")
                    val lower = line.lowercase()
                    if (lower.startsWith("server:")) {
                        serverHeader = line.substring(7).trim()
                    } else if (lower.startsWith("x-powered-by:")) {
                        xPoweredBy = line.substring(13).trim()
                    }
                    if (line.isEmpty()) break
                    line = reader.readLine()
                    count++
                }

                if (serverHeader != null || xPoweredBy != null) {
                    return BannerInfo(
                        rawBanner = sb.toString().trim(),
                        server = serverHeader,
                        xPoweredBy = xPoweredBy
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        // Fallback GET / HTTP/1.1 untuk embedded router/CCTV yang menolak metode HEAD
        try {
            Socket().use { socket ->
                socket.soTimeout = 2500
                socket.connect(InetSocketAddress(hostIp, port), 2000)

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                writer.write("GET / HTTP/1.1\r\nHost: $hostIp\r\nRange: bytes=0-1024\r\nUser-Agent: WeFi-LabAudit/1.0\r\nConnection: close\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val sb = StringBuilder()
                var serverHeader: String? = null
                var xPoweredBy: String? = null

                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 25) {
                    sb.append(line).append("\n")
                    val lower = line.lowercase()
                    if (lower.startsWith("server:")) {
                        serverHeader = line.substring(7).trim()
                    } else if (lower.startsWith("x-powered-by:")) {
                        xPoweredBy = line.substring(13).trim()
                    }
                    if (line.isEmpty()) break
                    line = reader.readLine()
                    count++
                }

                return BannerInfo(
                    rawBanner = sb.toString().trim(),
                    server = serverHeader,
                    xPoweredBy = xPoweredBy
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun grabRtspBanner(hostIp: String, port: Int): BannerInfo? {
        try {
            Socket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(hostIp, port), 2000)

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                writer.write("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: WeFi-LabAudit/1.0\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val sb = StringBuilder()
                var rtspServer: String? = null

                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 20) {
                    sb.append(line).append("\n")
                    if (line.lowercase().startsWith("server:")) {
                        rtspServer = line.substring(7).trim()
                    }
                    if (line.isEmpty()) break
                    line = reader.readLine()
                    count++
                }

                return BannerInfo(
                    rawBanner = sb.toString().trim(),
                    rtspServer = rtspServer
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun grabOnvifBanner(hostIp: String, port: Int): BannerInfo? {
        try {
            val soapRequest = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                  <soap:Body>
                    <tds:GetDeviceInformation/>
                  </soap:Body>
                </soap:Envelope>
            """.trimIndent()

            Socket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(hostIp, port), 2000)

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                writer.write("POST /onvif/device_service HTTP/1.1\r\n")
                writer.write("Host: $hostIp:$port\r\n")
                writer.write("Content-Type: application/soap+xml; charset=utf-8\r\n")
                writer.write("Content-Length: ${soapRequest.toByteArray().size}\r\n")
                writer.write("Connection: close\r\n\r\n")
                writer.write(soapRequest)
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val sb = StringBuilder()
                var line = reader.readLine()
                var count = 0
                while (line != null && count < 40) {
                    sb.append(line)
                    line = reader.readLine()
                    count++
                }
                val raw = sb.toString()

                // Ekstraksi tag XML dasar secara aman
                fun extractTag(xml: String, tag: String): String? {
                    val openTag = "<$tag>"
                    val closeTag = "</$tag>"
                    val start = xml.indexOf(openTag)
                    val end = xml.indexOf(closeTag)
                    if (start != -1 && end != -1 && end > start) {
                        return xml.substring(start + openTag.length, end).trim()
                    }
                    return null
                }

                val manufacturer = extractTag(raw, "tds:Manufacturer") ?: extractTag(raw, "Manufacturer")
                val model = extractTag(raw, "tds:Model") ?: extractTag(raw, "Model")
                val firmware = extractTag(raw, "tds:FirmwareVersion") ?: extractTag(raw, "FirmwareVersion")

                return BannerInfo(
                    rawBanner = "ONVIF Device Information Probe",
                    onvifManufacturer = manufacturer,
                    onvifModel = model,
                    onvifFirmware = firmware
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Fitur H: Offline CVE Matching (Hanya informasi, tanpa kode eksploitasi).
     */
    override fun matchCve(vendor: String, model: String, firmware: String): List<CveMatch> {
        val matches = mutableListOf<CveMatch>()
        if (vendor.isBlank() && model.isBlank()) return emptyList()

        for (entry in cveList) {
            val vendorMatches = vendor.isNotEmpty() && entry.vendor.contains(vendor, ignoreCase = true)
            val modelMatches = model.isNotEmpty() && (entry.affectedModels.contains(model, ignoreCase = true) || model.contains(entry.affectedModels, ignoreCase = true))
            
            if (vendorMatches || modelMatches) {
                matches.add(
                    CveMatch(
                        cveId = entry.cveId,
                        cvssScore = entry.cvss,
                        severity = entry.severity,
                        vendor = entry.vendor,
                        affectedModel = entry.affectedModels,
                        description = entry.description
                    )
                )
            }
        }
        return matches
    }

    /**
     * Klasifikasi tipe perangkat berdasarkan port terbuka, banner, dan service.
     */
    override fun identifyDeviceType(openPorts: List<Int>, banner: BannerInfo?, services: List<ServiceInfo>): String {
        val portSet = openPorts.toSet()
        val allText = buildString {
            append(banner?.rawBanner ?: "").append(" ")
            append(banner?.server ?: "").append(" ")
            append(banner?.rtspServer ?: "").append(" ")
            append(banner?.onvifManufacturer ?: "").append(" ")
            append(banner?.onvifModel ?: "").append(" ")
            services.forEach { append(it.serviceName).append(" ").append(it.serviceType).append(" ") }
        }.lowercase()

        return when {
            portSet.contains(554) || portSet.contains(3702) || portSet.contains(37777) ||
                    allText.contains("hikvision") || allText.contains("dahua") || allText.contains("camera") || allText.contains("cctv") -> {
                "Kamera CCTV / NVR Lab"
            }
            portSet.contains(53) || allText.contains("router") || allText.contains("gateway") || allText.contains("openwrt") -> {
                "Router / Gateway Lab"
            }
            allText.contains("printer") || portSet.contains(9100) -> {
                "Printer Jaringan"
            }
            allText.contains("synology") || allText.contains("qnap") || portSet.contains(139) || portSet.contains(445) -> {
                "NAS / File Server Lab"
            }
            portSet.contains(22) || portSet.contains(23) -> {
                "Terminal Server / Linux Host"
            }
            openPorts.isNotEmpty() -> {
                "Workstation Aktif"
            }
            else -> {
                "Perangkat Jaringan"
            }
        }
    }

    override fun classifyAssetCategory(host: DiscoveredHost): AssetCategory {
        val portSet = host.openPorts.map { it.port }.toSet()
        val allText = buildString {
            append(host.banner?.rawBanner ?: "").append(" ")
            append(host.banner?.server ?: "").append(" ")
            append(host.banner?.rtspServer ?: "").append(" ")
            append(host.banner?.onvifManufacturer ?: "").append(" ")
            append(host.banner?.onvifModel ?: "").append(" ")
            host.services.forEach { append(it.serviceName).append(" ").append(it.serviceType).append(" ") }
        }.lowercase()

        return when {
            host.isGateway || portSet.contains(53) || portSet.contains(8291) || allText.contains("router") || allText.contains("gateway") -> AssetCategory.GATEWAY_ROUTER
            portSet.contains(554) || portSet.contains(3702) || portSet.contains(37777) || allText.contains("camera") || allText.contains("cctv") -> AssetCategory.SURVEILLANCE_CCTV
            portSet.contains(445) || portSet.contains(139) || allText.contains("synology") || allText.contains("qnap") -> AssetCategory.STORAGE_NAS
            portSet.contains(1883) || portSet.contains(8883) || allText.contains("mqtt") -> AssetCategory.IOT_BROKER
            portSet.isNotEmpty() -> AssetCategory.WORKSTATION
            else -> AssetCategory.UNKNOWN
        }
    }

    override fun hasCleartextManagement(host: DiscoveredHost): Boolean {
        val cleartextPorts = setOf(21, 23, 80)
        return host.openPorts.any { it.port in cleartextPorts }
    }

    /**
     * Fitur I: Kompilasi Laporan & Ekspor.
     */
    override fun generateReport(hosts: List<DiscoveredHost>, durationMs: Long, subnet: String): DiscoveryReport {
        val isCircuitBreaker = throttler.isCircuitBreakerTriggered()
        val isIsolationSuspected = isCircuitBreaker || (hosts.isEmpty())
        return DiscoveryReport(
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
            subnet = subnet,
            totalHostsScanned = 254,
            totalHostsAlive = hosts.size,
            hosts = hosts,
            isClientIsolationSuspected = isIsolationSuspected
        )
    }

    override fun exportReportJson(report: DiscoveryReport): String {
        val root = JSONObject()
        root.put("project", "WeFi Network Discovery Lab Audit")
        root.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp)))
        root.put("durationMs", report.durationMs)
        root.put("subnet", report.subnet)
        root.put("totalScanned", report.totalHostsScanned)
        root.put("totalAlive", report.totalHostsAlive)
        root.put("clientIsolationSuspected", report.isClientIsolationSuspected)

        val hostArray = JSONArray()
        report.hosts.forEach { host ->
            val hostObj = JSONObject()
            hostObj.put("ip", host.ip)
            hostObj.put("mac", host.macAddress ?: "N/A (Android 10+ Restricted)")
            hostObj.put("vendor", host.vendor)
            hostObj.put("deviceType", host.probableDeviceType)
            hostObj.put("responseTimeMs", host.responseTimeMs)
            hostObj.put("isGateway", host.isGateway)
            hostObj.put("assetCategory", host.assetCategory.name)
            hostObj.put("hasCleartextManagement", host.hasCleartextManagement)

            val portArray = JSONArray()
            host.openPorts.forEach { p ->
                val pObj = JSONObject()
                pObj.put("port", p.port)
                pObj.put("status", p.status.name)
                pObj.put("service", p.serviceName)
                portArray.put(pObj)
            }
            hostObj.put("openPorts", portArray)

            val cveArray = JSONArray()
            host.cveMatches.forEach { cve ->
                val cObj = JSONObject()
                cObj.put("cveId", cve.cveId)
                cObj.put("cvss", cve.cvssScore)
                cObj.put("severity", cve.severity)
                cObj.put("description", cve.description)
                cveArray.put(cObj)
            }
            hostObj.put("cveMatches", cveArray)

            hostArray.put(hostObj)
        }
        root.put("hosts", hostArray)

        return root.toString(2)
    }

    override fun exportReportText(report: DiscoveryReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("==================================================")
            appendLine("           LAPORAN AUDIT PENEMUAN JARINGAN LAB   ")
            appendLine("==================================================")
            appendLine("Waktu Audit   : ${dateFormat.format(Date(report.timestamp))}")
            appendLine("Subnet Lab    : ${report.subnet}")
            appendLine("Durasi Audit  : ${report.durationMs / 1000} detik")
            appendLine("Total Host Diuji : ${report.totalHostsScanned} alamat IP")
            appendLine("Host Aktif Ditemukan: ${report.totalHostsAlive}")
            if (report.isClientIsolationSuspected) {
                appendLine("CATATAN: Terdeteksi kemungkinan Client Isolation AP aktif atau jaringan terisolasi.")
            }
            appendLine("--------------------------------------------------")
            appendLine("DAFTAR PERANGKAT TERDETEKSI:")
            appendLine()

            if (report.hosts.isEmpty()) {
                appendLine("Tidak ada host aktif ditemukan. Periksa konfigurasi isolasi AP lab.")
            } else {
                report.hosts.forEachIndexed { index, host ->
                    appendLine("[${index + 1}] Alamat IP: ${host.ip} ${if (host.isGateway) "(DEFAULT GATEWAY)" else ""}")
                    appendLine("    Tipe Perangkat : ${host.probableDeviceType}")
                    if (host.assetCategory != AssetCategory.UNKNOWN) {
                        appendLine("    Kategori Aset  : ${host.assetCategory.label}")
                    }
                    if (host.hasCleartextManagement) {
                        appendLine("    PERINGATAN     : Protokol manajemen teks-terbuka (Cleartext) aktif! Rentan sniffing.")
                    }
                    appendLine("    Vendor OUI     : ${host.vendor}")
                    appendLine("    Alamat MAC     : ${host.macAddress ?: "Tidak dapat dibaca (Restriksi Android 10+)"}")
                    appendLine("    Latensi Respons: ${host.responseTimeMs} ms")

                    if (host.openPorts.isNotEmpty()) {
                        appendLine("    Port Terbuka   :")
                        host.openPorts.forEach { p ->
                            appendLine("      - Port ${p.port}/TCP: ${p.serviceName} (${p.responseTimeMs} ms)")
                        }
                    } else {
                        appendLine("    Port Terbuka   : Tidak ada port terbuka dari daftar umum")
                    }

                    if (host.banner != null) {
                        appendLine("    Banner Info    :")
                        host.banner.server?.let { appendLine("      - Server HTTP: $it") }
                        host.banner.rtspServer?.let { appendLine("      - Server RTSP: $it") }
                        host.banner.onvifManufacturer?.let { appendLine("      - ONVIF Manufacturer: $it") }
                        host.banner.onvifModel?.let { appendLine("      - ONVIF Model: $it") }
                        host.banner.onvifFirmware?.let { appendLine("      - ONVIF Firmware: $it") }
                    }

                    if (host.cveMatches.isNotEmpty()) {
                        appendLine("    Potensi Kerentanan (Referensi Edukasi Offline):")
                        host.cveMatches.forEach { cve ->
                            appendLine("      * [${cve.cveId}] CVSS ${cve.cvssScore} (${cve.severity}): ${cve.description}")
                        }
                    }
                    appendLine()
                }
            }
            appendLine("==================================================")
            appendLine("Laporan ini dihasilkan untuk kebutuhan edukasi laboratorium.")
            appendLine("Tidak ada muatan eksploitasi yang digunakan dalam pengujian ini.")
        }
    }

    override suspend fun testAuthPosture(
        hostIp: String,
        port: Int,
        protocolHint: String?
    ): AuthPostureResult = withContext(Dispatchers.IO) {
        val isRtsp = (port == 554 || protocolHint?.equals("RTSP", ignoreCase = true) == true)
        val isHttps = (port == 443 || port == 8443 || protocolHint?.equals("HTTPS", ignoreCase = true) == true)

        if (isRtsp) {
            testRtspAuthPosture(hostIp, port)
        } else {
            testHttpAuthPosture(hostIp, port, isHttps)
        }
    }

    private fun testRtspAuthPosture(hostIp: String, port: Int): AuthPostureResult {
        try {
            Socket().use { socket ->
                socket.soTimeout = 3000
                socket.connect(InetSocketAddress(hostIp, port), 2500)

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                writer.write("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: WeFi-LabAudit/1.0\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val firstLine = reader.readLine() ?: ""
                var authHeader: String? = null
                var serverHeader: String? = null

                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 25) {
                    val lower = line.lowercase()
                    if (lower.startsWith("www-authenticate:")) {
                        authHeader = line.substring(17).trim()
                    } else if (lower.startsWith("server:")) {
                        serverHeader = line.substring(7).trim()
                    }
                    if (line.isEmpty()) break
                    line = reader.readLine()
                    count++
                }

                val isOptions200 = firstLine.contains("200 OK")
                var describeStatusCode = 200

                if (isOptions200) {
                    try {
                        writer.write("DESCRIBE rtsp://$hostIp:$port/ RTSP/1.0\r\nCSeq: 2\r\nAccept: application/sdp\r\nUser-Agent: WeFi-LabAudit/1.0\r\n\r\n")
                        writer.flush()
                        val describeLine = reader.readLine() ?: ""
                        if (describeLine.contains("401") || describeLine.contains("Unauthorized")) {
                            describeStatusCode = 401
                        }
                    } catch (ignored: Exception) {
                    }
                }

                return when {
                    firstLine.contains("401") || describeStatusCode == 401 -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = "RTSP",
                            status = AuthStatus.PROTECTED_CREDENTIALS,
                            httpStatusCode = 401,
                            authHeader = authHeader ?: "Digest/Basic realm",
                            serverBanner = serverHeader,
                            description = "Stream RTSP terkunci. Kamera meminta kredensial resmi sebelum mengizinkan pemutaran video."
                        )
                    }
                    isOptions200 -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = "RTSP",
                            status = AuthStatus.UNPROTECTED_EXPOSURE,
                            httpStatusCode = 200,
                            serverBanner = serverHeader,
                            description = "PERINGATAN KRITIS: Stream RTSP terbuka bebas tanpa autentikasi! Feed kamera dapat diputar langsung di jaringan lokal."
                        )
                    }
                    else -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = "RTSP",
                            status = AuthStatus.UNKNOWN,
                            description = "Respon RTSP: ${firstLine.take(60)}"
                        )
                    }
                }
            }
        } catch (ste: SocketTimeoutException) {
            return AuthPostureResult(
                ip = hostIp,
                port = port,
                protocol = "RTSP",
                status = AuthStatus.TIMEOUT,
                description = "Waktu koneksi RTSP habis (Timeout). Kamera mungkin memblokir port atau tidak merespons."
            )
        } catch (e: Exception) {
            val isRefused = e.message?.contains("refused", ignoreCase = true) == true
            return AuthPostureResult(
                ip = hostIp,
                port = port,
                protocol = "RTSP",
                status = if (isRefused) AuthStatus.CONNECTION_REFUSED else AuthStatus.UNKNOWN,
                description = if (isRefused) "Koneksi ditolak (Port RTSP $port ditutup)." else "Gagal menguji RTSP: ${e.message}"
            )
        }
    }

    private fun createInsecureSslSocket(hostIp: String, port: Int, timeoutMs: Int): Socket {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val socket = sslContext.socketFactory.createSocket()
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(hostIp, port), timeoutMs)
        return socket
    }

    private fun testHttpAuthPosture(hostIp: String, port: Int, isHttps: Boolean): AuthPostureResult {
        val protocol = if (isHttps) "HTTPS" else "HTTP"
        try {
            val socket = if (isHttps) {
                createInsecureSslSocket(hostIp, port, 3000)
            } else {
                Socket().apply {
                    soTimeout = 3000
                    connect(InetSocketAddress(hostIp, port), 2500)
                }
            }

            socket.use { s ->
                val writer = OutputStreamWriter(s.getOutputStream(), "UTF-8")
                writer.write("GET / HTTP/1.1\r\nHost: $hostIp\r\nUser-Agent: Mozilla/5.0 (Android; WeFi-LabAudit)\r\nAccept: */*\r\nConnection: close\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val statusLine = reader.readLine() ?: ""
                var authHeader: String? = null
                var serverHeader: String? = null
                var locationHeader: String? = null

                val bodyPreview = StringBuilder()
                var line: String? = reader.readLine()
                var isBody = false
                var count = 0

                while (line != null && count < 60) {
                    if (!isBody) {
                        if (line.isEmpty()) {
                            isBody = true
                        } else {
                            val lower = line.lowercase()
                            if (lower.startsWith("www-authenticate:")) {
                                authHeader = line.substring(17).trim()
                            } else if (lower.startsWith("server:")) {
                                serverHeader = line.substring(7).trim()
                            } else if (lower.startsWith("location:")) {
                                locationHeader = line.substring(9).trim()
                            }
                        }
                    } else {
                        bodyPreview.append(line).append(" ")
                    }
                    line = reader.readLine()
                    count++
                }

                val bodyLower = bodyPreview.toString().lowercase()
                val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull()

                return when {
                    statusCode == 401 -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = protocol,
                            status = AuthStatus.PROTECTED_CREDENTIALS,
                            httpStatusCode = 401,
                            authHeader = authHeader,
                            serverBanner = serverHeader,
                            description = "Terlindungi: Memerlukan Autentikasi HTTP 401 (${authHeader ?: "Basic/Digest challenge"})."
                        )
                    }
                    statusCode == 403 -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = protocol,
                            status = AuthStatus.PROTECTED_CREDENTIALS,
                            httpStatusCode = 403,
                            serverBanner = serverHeader,
                            description = "Akses ditolak (HTTP 403 Forbidden). Web portal dilindungi oleh kebijakan otorisasi/login."
                        )
                    }
                    statusCode in 300..399 -> {
                        val isLoginRedirect = locationHeader?.lowercase()?.let {
                            it.contains("login") || it.contains("auth") || it.contains("signin")
                        } == true
                        if (isLoginRedirect) {
                            AuthPostureResult(
                                ip = hostIp,
                                port = port,
                                protocol = protocol,
                                status = AuthStatus.PROTECTED_CREDENTIALS,
                                httpStatusCode = statusCode,
                                serverBanner = serverHeader,
                                description = "Dialihkan ke halaman autentikasi/login resmi (${locationHeader ?: "/login"})."
                            )
                        } else {
                            AuthPostureResult(
                                ip = hostIp,
                                port = port,
                                protocol = protocol,
                                status = AuthStatus.UNPROTECTED_EXPOSURE,
                                httpStatusCode = statusCode,
                                serverBanner = serverHeader,
                                description = "Dialihkan langsung ke dashboard internal (${locationHeader ?: "/"}) tanpa tantangan login."
                            )
                        }
                    }
                    statusCode in 200..299 -> {
                        val hasPasswordForm = bodyLower.contains("type=\"password\"") ||
                                bodyLower.contains("type='password'") ||
                                bodyLower.contains("loginform") ||
                                bodyLower.contains("name=\"password\"") ||
                                bodyLower.contains("masuk ke router")
                        if (hasPasswordForm) {
                            AuthPostureResult(
                                ip = hostIp,
                                port = port,
                                protocol = protocol,
                                status = AuthStatus.PROTECTED_CREDENTIALS,
                                httpStatusCode = statusCode,
                                serverBanner = serverHeader,
                                description = "Web form login terdeteksi. Portal terlindungi oleh form autentikasi kredensial."
                            )
                        } else {
                            AuthPostureResult(
                                ip = hostIp,
                                port = port,
                                protocol = protocol,
                                status = AuthStatus.UNPROTECTED_EXPOSURE,
                                httpStatusCode = statusCode,
                                serverBanner = serverHeader,
                                description = "PERINGATAN KRITIS: Web portal terbuka langsung tanpa form login atau proteksi password (HTTP 200 OK)!"
                            )
                        }
                    }
                    else -> {
                        AuthPostureResult(
                            ip = hostIp,
                            port = port,
                            protocol = protocol,
                            status = AuthStatus.UNKNOWN,
                            httpStatusCode = statusCode,
                            serverBanner = serverHeader,
                            description = "Respon server: ${statusLine.ifEmpty { "Koneksi selesai tanpa respon status" }}"
                        )
                    }
                }
            }
        } catch (ste: SocketTimeoutException) {
            return AuthPostureResult(
                ip = hostIp,
                port = port,
                protocol = protocol,
                status = AuthStatus.TIMEOUT,
                description = "Waktu koneksi $protocol habis (Timeout). Port mungkin difilter oleh firewall."
            )
        } catch (e: Exception) {
            val isRefused = e.message?.contains("refused", ignoreCase = true) == true
            return AuthPostureResult(
                ip = hostIp,
                port = port,
                protocol = protocol,
                status = if (isRefused) AuthStatus.CONNECTION_REFUSED else AuthStatus.UNKNOWN,
                description = if (isRefused) "Koneksi ditolak (Port $port ditutup)." else "Gagal menguji $protocol: ${e.message}"
            )
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("wefi_multicast_lock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(tag, "Gagal mengakuisisi multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Gagal melepaskan multicast lock: ${e.message}")
        }
    }

    override fun teardown() {
        releaseMulticastLock()
        throttler.clearCooldowns()
    }
}
