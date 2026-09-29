package com.wefi.analyzer.ui.screens.aroundcheck

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiAuditResult
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.ui.components.BlynkCard
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueDark
import com.wefi.analyzer.ui.theme.BlynkBlueTint
import com.wefi.analyzer.ui.theme.QualityAmber
import com.wefi.analyzer.ui.theme.QualityGreen
import com.wefi.analyzer.ui.theme.QualityRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AroundCheckScreen(
    viewModel: AroundCheckViewModel,
    modifier: Modifier = Modifier
) {
    val scanState by viewModel.scanState.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val lastScanTime by viewModel.lastScanTimestamp.collectAsState()
    val remainingScanCooldown by viewModel.remainingScanCooldownSeconds.collectAsState()
    val selectedItemForDialog by viewModel.selectedItemForPasswordDialog.collectAsState()
    val passwordInput by viewModel.passwordInput.collectAsState()
    val isPasswordVisible by viewModel.isPasswordVisible.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val showAuditSheet by viewModel.showAuditBottomSheet.collectAsState()
    var currentTickerSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            currentTickerSeconds = System.currentTimeMillis() / 1000
        }
    }

    val context = LocalContext.current

    // Modal Bottom Sheet untuk Audit Log Riwayat Koneksi Lab
    if (showAuditSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowAuditBottomSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            AuditLogSheetContent(
                logs = auditLogs,
                onClearClick = { viewModel.clearAuditLogs() },
                onCloseClick = { viewModel.setShowAuditBottomSheet(false) }
            )
        }
    }

    // Dialog Input Password (Masked + Toggle)
    if (selectedItemForDialog != null) {
        val targetItem = selectedItemForDialog!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissPasswordDialog() },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = BlynkBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sambungkan Wi-Fi",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "SSID: ${targetItem.ssid}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Keamanan: ${targetItem.security.label} • Sinyal: ${targetItem.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { viewModel.setPasswordInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password Wi-Fi") },
                        placeholder = { Text("Masukkan passphrase...") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.submitConnect()
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlynkBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sistem Android akan menampilkan dialog persetujuan OS. Password tidak disimpan.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitConnect() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                    enabled = targetItem.security == WifiSecurityType.OPEN || passwordInput.isNotBlank()
                ) {
                    Text("Sambungkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPasswordDialog() }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AROUND CHECK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Timestamp Data dari X detik lalu
                val relativeTimeText = if (lastScanTime > 0L) {
                    val diffSec = maxOf(0L, currentTickerSeconds - (lastScanTime / 1000))
                    "Data dari $diffSec detik lalu"
                } else {
                    "Memuat data cache..."
                }

                Text(
                    text = relativeTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tombol Audit Log
                IconButton(onClick = { viewModel.setShowAuditBottomSheet(true) }) {
                    Box {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Audit Log",
                            tint = BlynkBlue
                        )
                        if (auditLogs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(QualityGreen)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Tombol SCAN dengan indikator Cooldown Golden Time
                val isScanCoolingDown = remainingScanCooldown > 0
                Button(
                    onClick = { viewModel.startScan() },
                    enabled = !isScanCoolingDown,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlynkBlue,
                        disabledContainerColor = BlynkBlue.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isScanCoolingDown) Icons.Rounded.HourglassBottom else Icons.Rounded.Refresh,
                        contentDescription = "Scan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanCoolingDown) "SCAN (${remainingScanCooldown}s)" else "SCAN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Banner Peringatan Lokasi Nonaktif
        if (scanState is WifiScanState.Error && (scanState as WifiScanState.Error).isLocationDisabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, QualityAmber.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOff,
                            contentDescription = null,
                            tint = QualityAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Layanan Lokasi Nonaktif",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF92400E)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sistem Android mewajibkan layanan lokasi aktif untuk memindai jaringan Wi-Fi sekitar.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = Color(0xFF78350F)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QualityAmber),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Buka Pengaturan Lokasi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Indikator Status Pemindaian
        when (val state = scanState) {
            is WifiScanState.Scanning -> {
                BlynkCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BlynkBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sedang memindai jaringan sekitar...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            is WifiScanState.Error -> {
                if (!state.isLocationDisabled) {
                    BlynkCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = QualityRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = QualityRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            else -> {}
        }

        // Daftar Hasil Pemindaian Wi-Fi dengan Stable Key
        val scanItems = (scanState as? WifiScanState.Success)?.items ?: emptyList()

        if (scanItems.isEmpty() && scanState !is WifiScanState.Scanning) {
            BlynkCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum Ada Jaringan Terdeteksi",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tekan tombol SCAN di atas untuk memindai jaringan sekitar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = scanItems,
                    key = { it.bssid.ifBlank { it.ssid } }
                ) { item ->
                    val cooldownSec = viewModel.getRemainingCooldownForSsid(item.ssid)
                    val isWaitingApproval = viewModel.isItemWaitingApproval(item.ssid)

                    WifiScanItemCard(
                        item = item,
                        connectState = connectState,
                        cooldownSeconds = cooldownSec,
                        isWaitingApproval = isWaitingApproval,
                        onConnectClick = { viewModel.openPasswordDialog(item) },
                        onCancelClick = { viewModel.cancelConnect() },
                        onForgetClick = { viewModel.forgetNetwork(item.ssid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiScanItemCard(
    item: WifiScanItem,
    connectState: com.wefi.analyzer.domain.model.WifiConnectState,
    cooldownSeconds: Int,
    isWaitingApproval: Boolean,
    onConnectClick: () -> Unit,
    onCancelClick: () -> Unit,
    onForgetClick: () -> Unit
) {
    val isTarget = connectState.targetSsid == item.ssid
    val status = if (isTarget) connectState.status else WifiConnectStatus.Idle
    val isCoolingDown = cooldownSeconds > 0

    BlynkCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (status) {
                                    WifiConnectStatus.Connected -> QualityGreen.copy(alpha = 0.15f)
                                    WifiConnectStatus.WaitingApproval -> QualityAmber.copy(alpha = 0.15f)
                                    WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> QualityRed.copy(alpha = 0.15f)
                                    else -> BlynkBlueTint
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (status) {
                                WifiConnectStatus.Connected -> Icons.Rounded.CheckCircle
                                WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> Icons.Rounded.Close
                                else -> Icons.Rounded.Wifi
                            },
                            contentDescription = null,
                            tint = when (status) {
                                WifiConnectStatus.Connected -> QualityGreen
                                WifiConnectStatus.WaitingApproval -> QualityAmber
                                WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> QualityRed
                                else -> BlynkBlue
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.ssid,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kanal ${item.channel} (${item.frequencyMhz} MHz)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Security Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (item.security) {
                        WifiSecurityType.WPA3 -> Color(0xFFF3E8FF)
                        WifiSecurityType.WPA2 -> BlynkBlueTint
                        WifiSecurityType.OPEN -> Color(0xFFDCFCE7)
                        else -> Color(0xFFF3F4F6)
                    }
                ) {
                    Text(
                        text = item.security.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.security) {
                            WifiSecurityType.WPA3 -> Color(0xFF7E22CE)
                            WifiSecurityType.WPA2 -> BlynkBlueDark
                            WifiSecurityType.OPEN -> Color(0xFF15803D)
                            else -> Color(0xFF4B5563)
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Connection Status Banner
            if (isTarget && status != WifiConnectStatus.Idle) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (status) {
                        WifiConnectStatus.Connected -> Color(0xFFDCFCE7)
                        WifiConnectStatus.WaitingApproval -> Color(0xFFFEF3C7)
                        WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> Color(0xFFFEE2E2)
                        WifiConnectStatus.Timeout -> Color(0xFFFFEDD5)
                        else -> Color.Transparent
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (status == WifiConnectStatus.WaitingApproval) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = QualityAmber
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = when (status) {
                                    WifiConnectStatus.WaitingApproval -> "Menunggu persetujuan user..."
                                    WifiConnectStatus.Connected -> "Tersambung"
                                    WifiConnectStatus.Rejected -> "Ditolak oleh user"
                                    WifiConnectStatus.Failed -> connectState.message.ifBlank { "Gagal tersambung" }
                                    WifiConnectStatus.Timeout -> "Timeout persetujuan"
                                    else -> ""
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = when (status) {
                                    WifiConnectStatus.Connected -> Color(0xFF15803D)
                                    WifiConnectStatus.WaitingApproval -> Color(0xFF92400E)
                                    WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> Color(0xFFB91C1C)
                                    WifiConnectStatus.Timeout -> Color(0xFFC2410C)
                                    else -> Color.Unspecified
                                }
                            )
                        }

                        if (status == WifiConnectStatus.WaitingApproval) {
                            TextButton(
                                onClick = onCancelClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Batalkan", color = QualityRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onForgetClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Lupakan Jaringan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tombol Connect dengan status Golden Time Cooldown
                val isButtonEnabled = !isWaitingApproval && !isCoolingDown
                Button(
                    onClick = onConnectClick,
                    enabled = isButtonEnabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlynkBlue,
                        disabledContainerColor = BlynkBlue.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isCoolingDown) Icons.Rounded.Timer else Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            status == WifiConnectStatus.Connected -> "Tersambung"
                            isCoolingDown -> "Tunggu (${cooldownSeconds}s)"
                            else -> "Connect"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditLogSheetContent(
    logs: List<WifiAuditLogEntry>,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUDIT LOG KONEKSI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Riwayat hasil percobaan tanpa menyimpan password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClearClick) {
                        Text("Hapus", color = QualityRed, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onCloseClick) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Tutup")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada riwayat audit koneksi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.ssid,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (log.result) {
                                        WifiAuditResult.CONNECTED -> Color(0xFFDCFCE7)
                                        WifiAuditResult.REJECTED -> Color(0xFFFEE2E2)
                                        WifiAuditResult.TIMEOUT -> Color(0xFFFFEDD5)
                                        WifiAuditResult.FAILED -> Color(0xFFFEE2E2)
                                    }
                                ) {
                                    Text(
                                        text = log.result.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (log.result) {
                                            WifiAuditResult.CONNECTED -> Color(0xFF15803D)
                                            WifiAuditResult.REJECTED, WifiAuditResult.FAILED -> Color(0xFFB91C1C)
                                            WifiAuditResult.TIMEOUT -> Color(0xFFC2410C)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (log.reason.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.reason,
                                        style = MaterialTheme.typography.bodySmall,
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
        Spacer(modifier = Modifier.height(16.dp))
    }
}
