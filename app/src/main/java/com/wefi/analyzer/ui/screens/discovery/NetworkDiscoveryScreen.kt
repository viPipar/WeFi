package com.wefi.analyzer.ui.screens.discovery

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.HostRiskLevel
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueDark
import com.wefi.analyzer.ui.theme.BlynkBlueTint
import com.wefi.analyzer.ui.theme.QualityAmber
import com.wefi.analyzer.ui.theme.QualityGreen
import com.wefi.analyzer.ui.theme.QualityRed

@Composable
fun NetworkDiscoveryScreen(
    viewModel: NetworkDiscoveryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedHost by viewModel.selectedHostForDetail.collectAsState()
    val isDeepScanning by viewModel.isDeepScanningHost.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isLimitationsExpanded by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ScanProgress"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HeaderSection()
        }

        item {
            ScanControlCard(
                uiState = uiState,
                progress = animatedProgress,
                onStartScan = { viewModel.startDiscovery() },
                onCancelScan = { viewModel.cancelDiscovery() }
            )
        }

        // Circuit breaker / Client isolation warning
        if (uiState.isClientIsolationSuspected) {
            item {
                ClientIsolationNoticeCard()
            }
        }

        // Expandable Android Limitation Info
        item {
            AndroidLimitationsAccordion(
                isExpanded = isLimitationsExpanded,
                onToggle = { isLimitationsExpanded = !isLimitationsExpanded }
            )
        }

        // Discovered Hosts Summary Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Perangkat Terdeteksi (${uiState.hosts.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.report != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportButtons(
                            onCopyReport = {
                                viewModel.prepareExport()
                                val text = viewModel.uiState.value.exportedReportText ?: ""
                                if (text.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Laporan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShareReport = {
                                viewModel.prepareExport()
                                val text = viewModel.uiState.value.exportedReportText ?: ""
                                if (text.isNotEmpty()) {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Laporan Audit"))
                                }
                            }
                        )
                    }
                }
            }
        }

        // Discovered Host Items
        if (uiState.hosts.isEmpty()) {
            item {
                EmptyStateCard(isScanning = uiState.isScanning)
            }
        } else {
            items(uiState.hosts, key = { it.ip }) { host ->
                DiscoveredHostCard(
                    host = host,
                    onOpenDetail = { viewModel.selectHostForDetail(host) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (selectedHost != null) {
        HostDetailBottomSheet(
            host = selectedHost!!,
            isDeepScanning = isDeepScanning,
            onDismiss = { viewModel.selectHostForDetail(null) },
            onDeepScan = { viewModel.scanHostDeep(selectedHost!!) }
        )
    }
}

@Composable
private fun HeaderSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = BlynkBlueTint,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.DeviceHub,
                        contentDescription = null,
                        tint = BlynkBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Audit Penemuan Jaringan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Lingkup Laboratorium Keamanan & CCTV Terisolasi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScanControlCard(
    uiState: NetworkDiscoveryUiState,
    progress: Float,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status Operasi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.isScanning) BlynkBlueTint else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = uiState.phase.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isScanning) BlynkBlueDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.isScanning) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BlynkBlue,
                    trackColor = BlynkBlueTint
                )
            }

            // Subnet & Safety tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Subnet: ${uiState.subnetInfo?.baseIp ?: "Auto"}/24",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BlynkBlueTint
                ) {
                    Text(
                        text = "Pacing: 200 pkt/s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = BlynkBlueDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (uiState.isScanning) {
                    OutlinedButton(
                        onClick = onCancelScan,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QualityRed),
                        border = BorderStroke(1.dp, QualityRed.copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Hentikan", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onStartScan,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue, contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Mulai Audit Jaringan Lab", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientIsolationNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = QualityAmber.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = QualityAmber,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Kemungkinan Client Isolation Aktif",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tidak ada host lain yang merespons probe. Pada router tertentu, fitur 'AP Isolation' mencegah komunikasi antar-klien nirkabel. Pastikan router lab mengizinkan komunikasi LAN.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AndroidLimitationsAccordion(
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = BlynkBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Catatan Batasan Teknis Android (Non-Root)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "1. Raw ICMP: Android non-root tidak mengizinkan socket raw ICMP. Sistem menggunakan InetAddress.isReachable() dengan fallback TCP Connect 80/443/554/22.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. Restriksi ARP Cache: Sejak Android 10+ (API 29), kernel membatasi akses /proc/net/arp. Alamat MAC diambil dari SSDP/mDNS jika tersedia.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. Throttling Aman: Laju paket dibatasi 200 pkt/s dengan cooldown 30s per-host untuk mencegah gangguan pada router lab.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveredHostCard(
    host: DiscoveredHost,
    onOpenDetail: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val riskColor = when (host.riskProfile.level) {
        HostRiskLevel.CRITICAL -> QualityRed
        HostRiskLevel.HIGH -> QualityRed
        HostRiskLevel.MEDIUM -> QualityAmber
        HostRiskLevel.LOW -> BlynkBlue
        HostRiskLevel.SAFE -> QualityGreen
    }

    val riskLabel = when (host.riskProfile.level) {
        HostRiskLevel.CRITICAL -> "KRITIS"
        HostRiskLevel.HIGH -> "TINGGI"
        HostRiskLevel.MEDIUM -> "SEDANG"
        HostRiskLevel.LOW -> "RENDAH"
        HostRiskLevel.SAFE -> "AMAN"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (host.isGateway) BlynkBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: IP, Badges, Response Time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = if (host.probableDeviceType.contains("CCTV", ignoreCase = true)) {
                        Icons.Rounded.Videocam
                    } else {
                        Icons.Rounded.DeviceHub
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (host.isGateway) BlynkBlueTint else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (host.isGateway) BlynkBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = host.ip,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (host.isGateway) {
                                Surface(shape = RoundedCornerShape(4.dp), color = BlynkBlueTint) {
                                    Text(
                                        text = "GATEWAY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = riskColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = riskLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = riskColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${host.probableDeviceType} • ${host.vendor}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${host.responseTimeMs} ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = QualityGreen
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable details: Ports, Banner, CVE
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // MAC Address
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "MAC Address", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = host.macAddress ?: "Restriksi Android 10+",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Open Ports Chip List
                    Text(
                        text = "Port Terbuka (${host.openPorts.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (host.openPorts.isEmpty()) {
                        Text(
                            text = "Tidak ada port standar terbuka yang merespons",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            host.openPorts.forEach { portResult ->
                                PortChip(portResult)
                            }
                        }
                    }

                    // Banner Info
                    if (host.banner != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = "Banner Servis:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                host.banner.server?.let {
                                    Text(text = "HTTP Server: $it", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                host.banner.rtspServer?.let {
                                    Text(text = "RTSP Server: $it", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                host.banner.onvifManufacturer?.let {
                                    Text(text = "ONVIF: $it ${host.banner.onvifModel ?: ""} (${host.banner.onvifFirmware ?: ""})", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // CVE Matches
                    if (host.cveMatches.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = QualityAmber.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = QualityAmber, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Referensi Potensi Kerentanan (${host.cveMatches.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                host.cveMatches.forEach { cve ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${cve.cveId} (CVSS ${cve.cvssScore})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cve.cvssScore >= 9.0) QualityRed else QualityAmber
                                            )
                                            Text(
                                                text = cve.description,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Host Card Footer: Risk score & Open Detail Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Skor Paparan: ${host.riskProfile.score}/100",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = riskColor
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BlynkBlueTint,
                    modifier = Modifier.clickable { onOpenDetail() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = BlynkBlueDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Detail & Audit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlynkBlueDark
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HostDetailBottomSheet(
    host: DiscoveredHost,
    isDeepScanning: Boolean,
    onDismiss: () -> Unit,
    onDeepScan: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val riskColor = when (host.riskProfile.level) {
        HostRiskLevel.CRITICAL -> QualityRed
        HostRiskLevel.HIGH -> QualityRed
        HostRiskLevel.MEDIUM -> QualityAmber
        HostRiskLevel.LOW -> BlynkBlue
        HostRiskLevel.SAFE -> QualityGreen
    }

    val riskLabel = when (host.riskProfile.level) {
        HostRiskLevel.CRITICAL -> "RISIKO KRITIS"
        HostRiskLevel.HIGH -> "RISIKO TINGGI"
        HostRiskLevel.MEDIUM -> "RISIKO SEDANG"
        HostRiskLevel.LOW -> "RISIKO RENDAH"
        HostRiskLevel.SAFE -> "AMAN / NORMAL"
    }

    val webPort = host.openPorts.firstOrNull { it.port in listOf(80, 443, 8080, 8443) }?.port

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (host.isGateway) BlynkBlueTint else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (host.probableDeviceType.contains("CCTV", ignoreCase = true)) {
                                    Icons.Rounded.Videocam
                                } else {
                                    Icons.Rounded.DeviceHub
                                },
                                contentDescription = null,
                                tint = if (host.isGateway) BlynkBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = host.ip,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (host.isGateway) {
                                Surface(shape = RoundedCornerShape(4.dp), color = BlynkBlueTint) {
                                    Text(
                                        text = "GATEWAY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${host.probableDeviceType} • ${host.vendor}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Risk Assessment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, riskColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = riskColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PROFIL RISIKO & KEAMANAN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = riskColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = riskLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = host.riskProfile.summary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (host.riskProfile.highlights.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Temuan Paparan:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            host.riskProfile.highlights.forEach { highlight ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(text = "•", fontSize = 12.sp, color = riskColor, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = highlight,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (host.riskProfile.recommendations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Langkah Mitigasi Lab:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            host.riskProfile.recommendations.forEach { rec ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(text = "→", fontSize = 12.sp, color = BlynkBlue, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = rec,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Deep Scan Button
                Button(
                    onClick = onDeepScan,
                    enabled = !isDeepScanning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue, contentColor = Color.White)
                ) {
                    if (isDeepScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Memindai...", fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Deep Scan Port", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Web Admin Button (if port open)
                if (webPort != null) {
                    OutlinedButton(
                        onClick = {
                            val scheme = if (webPort == 443 || webPort == 8443) "https" else "http"
                            val portSuffix = if (webPort == 80 || webPort == 443) "" else ":$webPort"
                            val url = "$scheme://${host.ip}$portSuffix"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlynkBlueDark),
                        border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Web Admin", fontSize = 12.sp)
                    }
                }

                // Copy Brief Button
                OutlinedButton(
                    onClick = {
                        val brief = buildString {
                            appendLine("=== AUDIT HOST LAB WEFI ===")
                            appendLine("IP: ${host.ip} (${if (host.isGateway) "Gateway" else "Host"})")
                            appendLine("Tipe: ${host.probableDeviceType}")
                            appendLine("Vendor: ${host.vendor}")
                            appendLine("MAC: ${host.macAddress ?: "Restriksi Android 10+"}")
                            appendLine("Latensi: ${host.responseTimeMs} ms")
                            appendLine("Tingkat Risiko: ${host.riskProfile.level.name} (Skor: ${host.riskProfile.score}/100)")
                            appendLine("Port Terbuka: " + if (host.openPorts.isEmpty()) "Tidak ada" else host.openPorts.joinToString { "${it.port}/${it.serviceName}" })
                            if (host.riskProfile.highlights.isNotEmpty()) {
                                appendLine("Temuan:")
                                host.riskProfile.highlights.forEach { appendLine("- $it") }
                            }
                        }
                        clipboardManager.setText(AnnotatedString(brief))
                        Toast.makeText(context, "Ringkasan host disalin", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Salin", fontSize = 12.sp)
                }
            }

            // Technical Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Parameter Teknis",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "MAC Address", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = host.macAddress ?: "Restriksi Android 10+ (Non-Root)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Waktu Respon (RTT)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${host.responseTimeMs} ms",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = QualityGreen
                        )
                    }
                }
            }

            // Open Ports Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Port & Servis Terbuka (${host.openPorts.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (host.openPorts.isNotEmpty()) {
                            Text(
                                text = "TCP Connect",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (host.openPorts.isEmpty()) {
                        Text(
                            text = "Belum ada port terbuka terdeteksi. Jalankan 'Deep Scan Port' di atas untuk memindai 24 port servis lab.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            host.openPorts.forEach { portResult ->
                                PortChip(portResult)
                            }
                        }
                    }
                }
            }

            // Banner Grabbing Section (if present)
            if (host.banner != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Banner Servis & Respon HTTP/RTSP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        host.banner.server?.let {
                            Text(text = "HTTP Server: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        host.banner.rtspServer?.let {
                            Text(text = "RTSP Server: $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        host.banner.onvifManufacturer?.let {
                            Text(text = "ONVIF: $it ${host.banner.onvifModel ?: ""} (${host.banner.onvifFirmware ?: ""})", fontSize = 11.sp)
                        }
                    }
                }
            }

            // CVE Matches Section (if present)
            if (host.cveMatches.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = QualityAmber.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = QualityAmber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Referensi Kerentanan Publik (${host.cveMatches.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        host.cveMatches.forEach { cve ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${cve.cveId} (CVSS ${cve.cvssScore})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cve.cvssScore >= 9.0) QualityRed else QualityAmber
                                )
                                Text(
                                    text = cve.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortChip(portResult: PortResult) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = BlynkBlueTint,
        border = BorderStroke(0.5.dp, BlynkBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(QualityGreen, CircleShape)
            )
            Text(
                text = "${portResult.port} (${portResult.serviceName})",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = BlynkBlueDark
            )
        }
    }
}

@Composable
private fun ExportButtons(
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable(onClick = onCopyReport)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Text(text = "Salin", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BlynkBlueTint,
            modifier = Modifier.clickable(onClick = onShareReport)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Share, contentDescription = null, tint = BlynkBlueDark, modifier = Modifier.size(13.dp))
                Text(text = "Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlynkBlueDark)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(isScanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeviceHub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isScanning) "Sedang melakukan pemindaian subnet..." else "Belum ada perangkat yang diaudit",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isScanning) {
                Text(
                    text = "Tekan 'Mulai Audit Jaringan Lab' untuk mendeteksi host, port CCTV, dan servis lab.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
