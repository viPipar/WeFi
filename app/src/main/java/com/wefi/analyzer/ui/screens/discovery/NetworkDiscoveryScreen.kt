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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.CheckCircle
import com.wefi.analyzer.domain.model.AuthPostureResult
import com.wefi.analyzer.domain.model.AuthStatus
import com.wefi.analyzer.domain.model.RtspProbePath
import com.wefi.analyzer.ui.components.CctvLivePlayerDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.AssetCategory
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.HostRiskLevel
import com.wefi.analyzer.domain.model.PmfMode
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.WirelessSecurityAuditItem
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
    val authPostureState by viewModel.authPostureState.collectAsState()
    val isTestingAuth by viewModel.isTestingAuth.collectAsState()
    val rtspProbeResults by viewModel.rtspProbeResults.collectAsState()
    val isProbingRtsp by viewModel.isProbingRtsp.collectAsState()
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

        item {
            ExecutiveReconStatBar(summary = uiState.reconStatSummary)
        }

        item {
            ReconTabSwitcher(
                selectedTab = uiState.selectedTab,
                hostCount = uiState.hosts.size,
                wirelessCount = uiState.wirelessAuditItems.size,
                onTabSelected = { viewModel.setReconTab(it) }
            )
        }

        if (uiState.selectedTab == SecurityReconTab.LAN_SURFACE) {
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
        } else {
            // Wireless Recon Matrix Tab
            item {
                val vulnerableCount = uiState.wirelessAuditItems.count { it.riskLevel != HostRiskLevel.SAFE }
                val displayCount = if (uiState.wirelessFilterOnlyVulnerable) vulnerableCount else uiState.wirelessAuditItems.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wireless Recon Matrix ($displayCount)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        modifier = Modifier.clickable {
                            viewModel.setWirelessFilterOnlyVulnerable(!uiState.wirelessFilterOnlyVulnerable)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.wirelessFilterOnlyVulnerable) QualityAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (uiState.wirelessFilterOnlyVulnerable) "Hanya AP Rentan ✓" else "Filter Rentan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.wirelessFilterOnlyVulnerable) QualityAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            val filteredItems = if (uiState.wirelessFilterOnlyVulnerable) {
                uiState.wirelessAuditItems.filter { it.riskLevel != HostRiskLevel.SAFE }
            } else {
                uiState.wirelessAuditItems
            }

            if (filteredItems.isEmpty()) {
                item {
                    WirelessEmptyCard(isFiltered = uiState.wirelessFilterOnlyVulnerable)
                }
            } else {
                items(filteredItems, key = { it.bssid }) { auditItem ->
                    WirelessReconCard(auditItem = auditItem)
                }
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
            authPostureState = authPostureState,
            isTestingAuth = isTestingAuth,
            rtspProbeResults = rtspProbeResults,
            isProbingRtsp = isProbingRtsp,
            onDismiss = { viewModel.selectHostForDetail(null) },
            onDeepScan = { viewModel.scanHostDeep(selectedHost!!) },
            onTestAuthPosture = { port, proto -> viewModel.testHostAuthPosture(selectedHost!!, port, proto) },
            onProbeRtspPaths = { port -> viewModel.probeHostRtspPaths(selectedHost!!, port) }
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
                            if (host.assetCategory != AssetCategory.UNKNOWN) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BlynkBlueTint
                                ) {
                                    Text(
                                        text = host.assetCategory.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (host.hasCleartextManagement) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = QualityAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "CLEARTEXT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = QualityAmber,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
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
    authPostureState: Map<String, AuthPostureResult>,
    isTestingAuth: Boolean,
    rtspProbeResults: Map<String, List<RtspProbePath>>,
    isProbingRtsp: Boolean,
    onDismiss: () -> Unit,
    onDeepScan: () -> Unit,
    onTestAuthPosture: (port: Int, protocolHint: String?) -> Unit,
    onProbeRtspPaths: (port: Int) -> Unit
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

    val webPort = host.openPorts.firstOrNull { it.port in listOf(80, 443, 8080, 8443, 8000) }?.port
    var showWebAdminDialog by remember { mutableStateOf(false) }
    var showCctvDialog by remember { mutableStateOf(false) }
    var showInAppPlayer by remember { mutableStateOf(false) }
    var activeInAppStreamUrl by remember { mutableStateOf<String?>(null) }
    var selectedAuthPort by remember { mutableStateOf(webPort ?: if (host.openPorts.any { it.port == 554 }) 554 else 80) }
    var customWebPortInput by remember { mutableStateOf(webPort?.toString() ?: "80") }
    var customScheme by remember { mutableStateOf(if (webPort == 443 || webPort == 8443) "https" else "http") }

    var cctvChannelPreset by remember { mutableStateOf("Generic") }
    var cctvCustomPath by remember { mutableStateOf("live/ch0") }
    var cctvUsername by remember { mutableStateOf("") }
    var cctvPassword by remember { mutableStateOf("") }

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
                            if (host.assetCategory != AssetCategory.UNKNOWN) {
                                Surface(shape = RoundedCornerShape(4.dp), color = BlynkBlueTint) {
                                    Text(
                                        text = host.assetCategory.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (host.hasCleartextManagement) {
                                Surface(shape = RoundedCornerShape(4.dp), color = QualityAmber.copy(alpha = 0.15f)) {
                                    Text(
                                        text = "CLEARTEXT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = QualityAmber,
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

                // Web Admin Button (Always Accessible with Port Selector)
                OutlinedButton(
                    onClick = { showWebAdminDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BlynkBlueDark),
                    border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Web Admin", fontSize = 12.sp)
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

            // Uji Keterbukaan Autentikasi (Auth Posture Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = BlynkBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Uji Keterbukaan Akses (Auth Posture)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Verifikasi pasif apakah portal/stream terbuka tanpa autentikasi (unauthenticated exposure) atau dilindungi autentikasi resmi.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Port selector chips
                    val testablePorts = (host.openPorts.map { it.port } + listOf(80, 443, 554, 8000)).distinct().sorted()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        testablePorts.take(6).forEach { p ->
                            Surface(
                                modifier = Modifier.clickable { selectedAuthPort = p },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedAuthPort == p) BlynkBlue else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (p == 554) "Port 554 (RTSP)" else "Port $p",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedAuthPort == p) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Test Action Button
                    Button(
                        onClick = {
                            val proto = if (selectedAuthPort == 554) "RTSP" else if (selectedAuthPort in listOf(443, 8443)) "HTTPS" else "HTTP"
                            onTestAuthPosture(selectedAuthPort, proto)
                        },
                        enabled = !isTestingAuth,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingAuth) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Menguji Handshake Autentikasi...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Uji Keterbukaan Port $selectedAuthPort", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Result display if any
                    val currentResult = authPostureState["${host.ip}:$selectedAuthPort"]
                    if (currentResult != null) {
                        val isUnprotected = currentResult.status == AuthStatus.UNPROTECTED_EXPOSURE
                        val isProtected = currentResult.status == AuthStatus.PROTECTED_CREDENTIALS
                        val cardBg = when {
                            isUnprotected -> QualityRed.copy(alpha = 0.12f)
                            isProtected -> QualityGreen.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val borderColor = when {
                            isUnprotected -> QualityRed
                            isProtected -> QualityGreen
                            else -> MaterialTheme.colorScheme.outline
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (isUnprotected) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = if (isUnprotected) QualityRed else if (isProtected) QualityGreen else BlynkBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when {
                                            isUnprotected -> "PERINGATAN: TERBUKA TANPA PASSWORD"
                                            isProtected -> "TERLINDUNGI DENGAN PASSWORD"
                                            else -> currentResult.status.label
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnprotected) QualityRed else if (isProtected) QualityGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = currentResult.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                currentResult.serverBanner?.let { srv ->
                                    Text(
                                        text = "Server Banner: $srv",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Kartu Akses & Monitoring CCTV Lab (Isolasi)
            val isCctvDevice = host.probableDeviceType.contains("CCTV", ignoreCase = true) ||
                    host.assetCategory == AssetCategory.SURVEILLANCE_CCTV ||
                    host.openPorts.any { it.port in listOf(554, 3702, 37777, 8000) }

            if (isCctvDevice) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = QualityAmber.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Rounded.Videocam, contentDescription = null, tint = QualityAmber, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = "Akses & Monitoring CCTV Lab",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Monitoring feed RTSP & konsol kamera di lab pribadi",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // RTSP Posture Status if already checked
                        val rtspResult = authPostureState["${host.ip}:554"]
                        if (rtspResult != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) QualityRed.copy(alpha = 0.1f) else QualityGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) QualityRed else QualityGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) QualityRed else QualityGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) "RTSP Port 554: Stream Terbuka Bebas!" else "RTSP Port 554: Memerlukan Kredensial",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (rtspResult.status == AuthStatus.UNPROTECTED_EXPOSURE) QualityRed else QualityGreen
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onTestAuthPosture(554, "RTSP") },
                                enabled = !isTestingAuth,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = QualityAmber),
                                border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.6f))
                            ) {
                                Icon(imageVector = Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Uji RTSP", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showCctvDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QualityAmber, contentColor = Color.White)
                            ) {
                                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Live Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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

    if (showWebAdminDialog) {
        val parsedPort = customWebPortInput.toIntOrNull() ?: 80
        val isHttps = customScheme == "https" || parsedPort == 443 || parsedPort == 8443
        val currentScheme = if (isHttps) "https" else "http"
        val portSuffix = if ((currentScheme == "http" && parsedPort == 80) || (currentScheme == "https" && parsedPort == 443)) "" else ":$parsedPort"
        val previewUrl = "$currentScheme://${host.ip}$portSuffix"

        AlertDialog(
            onDismissRequest = { showWebAdminDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.Language, contentDescription = null, tint = BlynkBlue)
                    Text(text = "Akses Web Admin Lab", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Target Host: ${host.ip}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(text = "Pilih / Masukkan Port Web Admin:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("80", "443", "8080", "8000", "8443").forEach { pStr ->
                            Surface(
                                modifier = Modifier.clickable {
                                    customWebPortInput = pStr
                                    customScheme = if (pStr == "443" || pStr == "8443") "https" else "http"
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (customWebPortInput == pStr) BlynkBlue else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = when (pStr) {
                                        "80" -> "80 (HTTP)"
                                        "443" -> "443 (HTTPS)"
                                        "8080" -> "8080 (Alt)"
                                        "8000" -> "8000 (CCTV/Hik)"
                                        "8443" -> "8443 (S-Alt)"
                                        else -> pStr
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (customWebPortInput == pStr) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customWebPortInput,
                        onValueChange = { customWebPortInput = it.filter { char -> char.isDigit() }.take(5) },
                        label = { Text("Port Kustom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "URL: $previewUrl",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = BlynkBlueDark,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl))
                            context.startActivity(intent)
                            showWebAdminDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membuka browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue)
                ) {
                    Text("Buka Browser", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onTestAuthPosture(parsedPort, if (isHttps) "HTTPS" else "HTTP")
                    showWebAdminDialog = false
                    Toast.makeText(context, "Menguji keterbukaan port $parsedPort...", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Uji Auth Dahulu", color = BlynkBlueDark)
                }
            }
        )
    }

    if (showCctvDialog) {
        val finalPath = when (cctvChannelPreset) {
            "Generic" -> "live/ch0"
            "Hikvision" -> "Streaming/Channels/101"
            "Dahua" -> "cam/realmonitor?channel=1&subtype=0"
            else -> cctvCustomPath
        }
        val authPart = if (cctvUsername.isNotBlank()) {
            if (cctvPassword.isNotBlank()) "${cctvUsername}:${cctvPassword}@" else "${cctvUsername}@"
        } else ""
        val previewRtsp = "rtsp://$authPart${host.ip}:554/$finalPath"

        AlertDialog(
            onDismissRequest = { showCctvDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.Videocam, contentDescription = null, tint = QualityAmber)
                    Text(text = "Monitoring Feed CCTV (RTSP)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Kamera IP: ${host.ip} (${host.vendor})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(text = "Pilih Preset Format Stream Kamera:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Generic", "Hikvision", "Dahua", "Kustom").forEach { preset ->
                            Surface(
                                modifier = Modifier.clickable { cctvChannelPreset = preset },
                                shape = RoundedCornerShape(8.dp),
                                color = if (cctvChannelPreset == preset) QualityAmber else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (cctvChannelPreset == preset) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Smart Auto-Probe RTSP Paths for OEM / Generic Cameras
                    val detectedPaths = rtspProbeResults[host.ip] ?: emptyList()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Auto-Probe RTSP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Deteksi otomatis path CCTV OEM / Generic",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onProbeRtspPaths(554) },
                                    enabled = !isProbingRtsp,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = QualityAmber)
                                ) {
                                    if (isProbingRtsp) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = QualityAmber)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Memindai...", fontSize = 11.sp)
                                    } else {
                                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Scan Path", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            if (isProbingRtsp) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = QualityAmber
                                )
                            }

                            if (detectedPaths.isNotEmpty()) {
                                Text(
                                    text = "Path Ditemukan (${detectedPaths.size} channel):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = QualityGreen
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    detectedPaths.forEach { probePath ->
                                        val isSelected = (cctvChannelPreset == "Kustom" && cctvCustomPath == probePath.path)
                                        Surface(
                                            modifier = Modifier.clickable {
                                                cctvChannelPreset = "Kustom"
                                                cctvCustomPath = probePath.path
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) QualityGreen else if (probePath.requiresAuth) QualityAmber.copy(alpha = 0.15f) else QualityGreen.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, if (isSelected) QualityGreen else if (probePath.requiresAuth) QualityAmber else QualityGreen)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (probePath.requiresAuth) Icons.Rounded.Lock else Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else if (probePath.requiresAuth) QualityAmber else QualityGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${probePath.path} (${if (probePath.requiresAuth) "401 Sandi" else "Bebas"})",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (cctvChannelPreset == "Kustom") {
                        OutlinedTextField(
                            value = cctvCustomPath,
                            onValueChange = { cctvCustomPath = it },
                            label = { Text("Path RTSP Stream (misal: h264/ch1/main)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cctvUsername,
                            onValueChange = { cctvUsername = it },
                            label = { Text("Username (opsional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cctvPassword,
                            onValueChange = { cctvPassword = it },
                            label = { Text("Password (opsional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = previewRtsp,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(previewRtsp), "video/*")
                                }
                                context.startActivity(Intent.createChooser(intent, "Buka Feed Video Kamera"))
                                showCctvDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak ada aplikasi pemutar RTSP eksternal: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("VLC / Eksternal", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            activeInAppStreamUrl = previewRtsp
                            showInAppPlayer = true
                            showCctvDialog = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QualityAmber)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tonton di Aplikasi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCctvDialog = false }) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showInAppPlayer && activeInAppStreamUrl != null) {
        CctvLivePlayerDialog(
            rtspUrl = activeInAppStreamUrl!!,
            cameraTitle = "${host.vendor ?: "Kamera"} (${host.ip})",
            onDismiss = {
                showInAppPlayer = false
                activeInAppStreamUrl = null
            }
        )
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

@Composable
private fun ExecutiveReconStatBar(
    summary: ReconStatSummary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReconStatPill(
            label = "Host Kritis",
            count = summary.criticalHosts,
            alertColor = QualityRed,
            modifier = Modifier.weight(1f)
        )
        ReconStatPill(
            label = "Cleartext",
            count = summary.cleartextPortsCount,
            alertColor = QualityAmber,
            modifier = Modifier.weight(1f)
        )
        ReconStatPill(
            label = "WPS Aktif",
            count = summary.wpsEnabledApsCount,
            alertColor = QualityAmber,
            modifier = Modifier.weight(1f)
        )
        ReconStatPill(
            label = "Tanpa PMF",
            count = summary.noPmfApsCount,
            alertColor = QualityAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReconStatPill(
    label: String,
    count: Int,
    alertColor: Color,
    modifier: Modifier = Modifier
) {
    val isAlert = count > 0
    val bgColor = if (isAlert) alertColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (isAlert) alertColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReconTabSwitcher(
    selectedTab: SecurityReconTab,
    hostCount: Int,
    wirelessCount: Int,
    onTabSelected: (SecurityReconTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isLan = selectedTab == SecurityReconTab.LAN_SURFACE
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(SecurityReconTab.LAN_SURFACE) },
                shape = RoundedCornerShape(9.dp),
                color = if (isLan) BlynkBlue else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAN Surface ($hostCount)",
                        fontSize = 12.sp,
                        fontWeight = if (isLan) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLan) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isWireless = selectedTab == SecurityReconTab.WIRELESS_RECON
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(SecurityReconTab.WIRELESS_RECON) },
                shape = RoundedCornerShape(9.dp),
                color = if (isWireless) BlynkBlue else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wireless Recon ($wirelessCount)",
                        fontSize = 12.sp,
                        fontWeight = if (isWireless) FontWeight.Bold else FontWeight.Medium,
                        color = if (isWireless) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WirelessReconCard(
    auditItem: WirelessSecurityAuditItem
) {
    var isExpanded by remember { mutableStateOf(false) }

    val riskColor = when (auditItem.riskLevel) {
        HostRiskLevel.CRITICAL -> QualityRed
        HostRiskLevel.HIGH -> QualityRed
        HostRiskLevel.MEDIUM -> QualityAmber
        HostRiskLevel.LOW -> BlynkBlue
        HostRiskLevel.SAFE -> QualityGreen
    }

    val riskLabel = when (auditItem.riskLevel) {
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
            if (auditItem.isRogueTwinCandidate) QualityRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: SSID, BSSID, Signal & Risk
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = riskColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (auditItem.riskLevel == HostRiskLevel.CRITICAL || auditItem.riskLevel == HostRiskLevel.HIGH) Icons.Rounded.Warning else Icons.Rounded.Security,
                                contentDescription = null,
                                tint = riskColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (auditItem.ssid.isBlank()) "(SSID Tersembunyi)" else auditItem.ssid,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
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
                            text = "${auditItem.bssid} • Ch ${auditItem.channel} • ${auditItem.security}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${auditItem.rssi} dBm",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (auditItem.rssi > -65) QualityGreen else QualityAmber
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Security posture badges row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (auditItem.hasWps) {
                    PostureBadge(label = "WPS AKTIF", color = QualityAmber)
                }
                when (auditItem.pmfMode) {
                    PmfMode.REQUIRED -> PostureBadge(label = "PMF WAJIB", color = QualityGreen)
                    PmfMode.CAPABLE -> PostureBadge(label = "PMF OPSIONAL", color = BlynkBlue)
                    PmfMode.NONE -> PostureBadge(label = "TANPA PMF", color = QualityAmber)
                }
                if (auditItem.hasInsecureCipher) {
                    PostureBadge(label = "CIPHER USANG", color = QualityRed)
                }
                if (auditItem.isRogueTwinCandidate) {
                    PostureBadge(label = "ROGUE/KEMBAR", color = QualityRed)
                }
            }

            // Highlights
            if (auditItem.riskHighlights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    auditItem.riskHighlights.forEach { highlight ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(riskColor, CircleShape)
                            )
                            Text(
                                text = highlight,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Expandable capabilities details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Beacon Capabilities: ${auditItem.capabilities}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PostureBadge(
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun WirelessEmptyCard(isFiltered: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = BlynkBlue,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isFiltered) "Tidak Ada AP Rentan Ditemukan" else "Belum Ada Data Pemindaian Wi-Fi",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isFiltered) "Semua Access Point di sekitar memiliki konfigurasi aman (WPS nonaktif & PMF aktif)." else "Nyalakan Wi-Fi dan lakukan pemindaian di menu Radar AP untuk menganalisis keamanan nirkabel.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
