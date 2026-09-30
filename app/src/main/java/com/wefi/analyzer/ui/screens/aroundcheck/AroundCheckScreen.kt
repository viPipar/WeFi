package com.wefi.analyzer.ui.screens.aroundcheck

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.AroundCheckMode
import com.wefi.analyzer.domain.model.DfsParseResult
import com.wefi.analyzer.domain.model.HybridRouterStatus
import com.wefi.analyzer.domain.model.VerifiedLabRouter
import com.wefi.analyzer.domain.util.DfsPasswordSanitizer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val topPasswordInput by viewModel.topPasswordInput.collectAsState()
    val isTopPasswordVisible by viewModel.isTopPasswordVisible.collectAsState()
    val isSequentialTesting by viewModel.isSequentialTesting.collectAsState()
    val currentCandidateIndex by viewModel.currentCandidateIndex.collectAsState()
    val sequentialTestMessage by viewModel.sequentialTestMessage.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val showAuditSheet by viewModel.showAuditBottomSheet.collectAsState()

    // BFS, DFS & Hybrid Traversal States
    val selectedMode by viewModel.selectedMode.collectAsState()
    val dfsCsvInput by viewModel.dfsCsvInput.collectAsState()
    val isDfsCsvVisible by viewModel.isDfsCsvVisible.collectAsState()
    val dfsTargetItem by viewModel.dfsTargetItem.collectAsState()
    val dfsParsedStats by viewModel.dfsParsedStats.collectAsState()
    val hybridRouterStatuses by viewModel.hybridRouterStatuses.collectAsState()
    val hybridCsvInput by viewModel.hybridCsvInput.collectAsState()
    val isHybridCsvVisible by viewModel.isHybridCsvVisible.collectAsState()
    val traversalTotalCount by viewModel.traversalTotalCount.collectAsState()
    val goalFoundRouter by viewModel.goalFoundRouter.collectAsState()
    val showCircuitBreakerDialog by viewModel.showCircuitBreakerDialog.collectAsState()
    val verifiedRouters by viewModel.verifiedRouters.collectAsState()
    val isControlPanelExpanded by viewModel.isControlPanelExpanded.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    val scanItems = remember(scanState) {
        when (val s = scanState) {
            is WifiScanState.Success -> s.items
            is WifiScanState.Throttled -> s.items
            else -> emptyList()
        }.filter { it.ssid.isNotBlank() }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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

    // Dialog Input Password Manual (Masked + Toggle)
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
                        text = "Sambungkan Wi-Fi Lab",
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
                            keyboardController?.hide()
                            viewModel.submitConnect()
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlynkBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    val isPasswordLengthValid = targetItem.security == WifiSecurityType.OPEN || passwordInput.length in 8..63
                    val isPasswordTooShort = targetItem.security != WifiSecurityType.OPEN && passwordInput.isNotEmpty() && passwordInput.length < 8

                    if (isPasswordTooShort) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Minimal 8 karakter (standar WPA2/WPA3)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Dialog resmi OS Android akan muncul meminta persetujuan. Password tidak pernah disimpan.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.submitConnect()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                    enabled = targetItem.security == WifiSecurityType.OPEN || passwordInput.length in 8..63
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

    // Dialog Circuit Breaker (Proteksi Anti-Blacklist MAC Router Lab)
    if (showCircuitBreakerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeCircuitBreaker(continueTraversal = false) },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = QualityAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Proteksi Router Lab",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "10 password berturut-turut gagal pada router ini.",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF92400E)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Untuk mencegah penguncian MAC / pemblokiran keamanan oleh router lab (MikroTik / Aruba), traversal dijeda otomatis.\n\nApakah Anda ingin melanjutkan pengujian sisa password?",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acknowledgeCircuitBreaker(continueTraversal = true) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QualityAmber)
                ) {
                    Text("Lanjutkan Pengujian", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.acknowledgeCircuitBreaker(continueTraversal = false) }
                ) {
                    Text("Hentikan Traversal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Unified Top Card: Collapsible Accordion (BFS vs DFS) & Expansive Viewport
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Bar (Clickable to Expand / Collapse)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleControlPanelExpanded() }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(BlynkBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = BlynkBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pencarian Lab",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (selectedMode) {
                                    AroundCheckMode.BFS -> BlynkBlueTint
                                    AroundCheckMode.DFS -> Color(0xFFFEF3C7)
                                    AroundCheckMode.HYBRID -> Color(0xFFE0E7FF)
                                }
                            ) {
                                Text(
                                    text = when (selectedMode) {
                                        AroundCheckMode.BFS -> "MODE BFS"
                                        AroundCheckMode.DFS -> "MODE DFS"
                                        AroundCheckMode.HYBRID -> "MODE HYBRID"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (selectedMode) {
                                        AroundCheckMode.BFS -> BlynkBlueDark
                                        AroundCheckMode.DFS -> Color(0xFF92400E)
                                        AroundCheckMode.HYBRID -> Color(0xFF3730A3)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSequentialTesting) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = QualityAmber
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (selectedMode) {
                                                AroundCheckMode.BFS -> "BFS AKTIF"
                                                AroundCheckMode.DFS -> "DFS AKTIF"
                                                AroundCheckMode.HYBRID -> "HYBRID AKTIF"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            IconButton(
                                onClick = { viewModel.toggleControlPanelExpanded() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isControlPanelExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = if (isControlPanelExpanded) "Ciutkan Panel" else "Perluas Panel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Collapsed Compact Strip (ketika panel diciutkan agar daftar router lega)
                    AnimatedVisibility(
                        visible = !isControlPanelExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (selectedMode) {
                                    AroundCheckMode.BFS -> {
                                        Text(
                                            text = if (topPasswordInput.isBlank()) "Password BFS belum diatur" else "Passphrase: •••••••• (${topPasswordInput.length} kar)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSequentialTesting) {
                                            Button(
                                                onClick = { viewModel.cancelTraversal() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = QualityRed),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Batal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.startBfsTraversal() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Cari BFS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    AroundCheckMode.DFS -> {
                                        Text(
                                            text = if (dfsTargetItem == null) "Pilih target di list bawah" else "${dfsTargetItem?.ssid} (${dfsParsedStats.validPasswords.size} pwd)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSequentialTesting) {
                                            Button(
                                                onClick = { viewModel.cancelTraversal() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = QualityRed),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Batal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.startDfsTraversal() },
                                                enabled = dfsTargetItem != null && dfsParsedStats.validPasswords.isNotEmpty(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Uji DFS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    AroundCheckMode.HYBRID -> {
                                        val hybridParsed = remember(hybridCsvInput) { DfsPasswordSanitizer.parse(hybridCsvInput) }
                                        Text(
                                            text = "Hybrid: ${hybridParsed.validPasswords.size} pwd • ${scanItems.size} router",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSequentialTesting) {
                                            Button(
                                                onClick = { viewModel.cancelTraversal() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = QualityRed),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Batal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.startHybridTraversal() },
                                                enabled = scanItems.isNotEmpty() && hybridParsed.validPasswords.isNotEmpty(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Mulai Hybrid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expanded Controls (seluruh kontrol input ketika panel dibuka)
                    AnimatedVisibility(
                        visible = isControlPanelExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                    // Segmented Control Tab (Mode BFS vs Mode DFS)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Mode BFS Tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedMode == AroundCheckMode.BFS) BlynkBlue else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable(enabled = !isSequentialTesting) {
                                        viewModel.setMode(AroundCheckMode.BFS)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = if (selectedMode == AroundCheckMode.BFS) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Mode BFS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMode == AroundCheckMode.BFS) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }

                            // Mode DFS Tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedMode == AroundCheckMode.DFS) BlynkBlue else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable(enabled = !isSequentialTesting) {
                                        viewModel.setMode(AroundCheckMode.DFS)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Key,
                                        contentDescription = null,
                                        tint = if (selectedMode == AroundCheckMode.DFS) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Mode DFS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMode == AroundCheckMode.DFS) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }

                            // Mode Hybrid Tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedMode == AroundCheckMode.HYBRID) BlynkBlue else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable(enabled = !isSequentialTesting) {
                                        viewModel.setMode(AroundCheckMode.HYBRID)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Hub,
                                        contentDescription = null,
                                        tint = if (selectedMode == AroundCheckMode.HYBRID) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Mode Hybrid",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMode == AroundCheckMode.HYBRID) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Content Panel Berdasarkan Mode Terpilih
                    if (selectedMode == AroundCheckMode.BFS) {
                        // --- PANEL MODE BFS (1 Password -> Banyak Router) ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = topPasswordInput,
                                onValueChange = { viewModel.setTopPasswordInput(it) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Password Uji BFS", fontSize = 12.sp) },
                                placeholder = { Text("1 passphrase untuk semua router...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = BlynkBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.toggleTopPasswordVisibility() }) {
                                        Icon(
                                            imageVector = if (isTopPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                            contentDescription = "Toggle password",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (isTopPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                    if (!isSequentialTesting) viewModel.startBfsTraversal()
                                }),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BlynkBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                enabled = !isSequentialTesting
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isSequentialTesting) {
                                Button(
                                    onClick = { viewModel.cancelTraversal() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = QualityRed),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hentikan",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.startBfsTraversal()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Cari",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cari", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Menguji 1 password ke semua router lab terdeteksi secara berurutan.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (selectedMode == AroundCheckMode.DFS) {
                        // --- PANEL MODE DFS (Banyak Password CSV -> 1 Router Target) ---
                        // Target Selector Banner
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (dfsTargetItem != null) BlynkBlueTint else Color(0xFFFEF3C7),
                            border = BorderStroke(
                                1.dp,
                                if (dfsTargetItem != null) BlynkBlue.copy(alpha = 0.5f) else QualityAmber.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        tint = if (dfsTargetItem != null) BlynkBlue else QualityAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (dfsTargetItem != null) {
                                        Column {
                                            Text(
                                                text = "Target: ${dfsTargetItem!!.ssid}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = BlynkBlueDark
                                            )
                                            Text(
                                                text = "BSSID: ${dfsTargetItem!!.bssid} • CH ${dfsTargetItem!!.channel} • ${dfsTargetItem!!.rssi} dBm",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Pilih target router dari daftar di bawah (klik kartu router)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }

                                if (dfsTargetItem != null && !isSequentialTesting) {
                                    TextButton(
                                        onClick = { viewModel.selectDfsTargetItem(null) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Ganti", fontSize = 11.sp, color = BlynkBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Kata Sandi CSV",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BlynkBlueTint,
                                border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isSequentialTesting) {
                                        viewModel.applyDfsPracticumTemplate()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        tint = BlynkBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Tempel Template Modul (56)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlue
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Multi-line CSV Input TextField
                        OutlinedTextField(
                            value = dfsCsvInput,
                            onValueChange = { viewModel.setDfsCsvInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Daftar Password (CSV / Titik Koma ';')", fontSize = 12.sp) },
                            placeholder = { Text("lab123komputer; labkomputer123; ilmukomputeripb;", fontSize = 12.sp) },
                            minLines = 2,
                            maxLines = 4,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.toggleDfsCsvVisibility() }) {
                                    Icon(
                                        imageVector = if (isDfsCsvVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle CSV visibility",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (isDfsCsvVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlynkBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            enabled = !isSequentialTesting
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sanitizer Stats Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (dfsParsedStats.validPasswords.isNotEmpty()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "${dfsParsedStats.validPasswords.size} valid",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dfsParsedStats.validPasswords.isNotEmpty()) Color(0xFF15803D) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (dfsParsedStats.skippedTooShortCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "${dfsParsedStats.skippedTooShortCount} diabaikan (<8 char)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (dfsParsedStats.duplicateCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = "${dfsParsedStats.duplicateCount} duplikat",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // DFS Action Button
                        if (isSequentialTesting) {
                            Button(
                                onClick = { viewModel.cancelTraversal() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QualityRed)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hentikan Traversal DFS", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.startDfsTraversal()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = dfsTargetItem != null && dfsParsedStats.validPasswords.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (dfsTargetItem == null) {
                                        "Pilih Target Wi-Fi Dulu"
                                    } else {
                                        "Mulai Traversal DFS (${dfsParsedStats.validPasswords.size} Pwd)"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // --- PANEL MODE HYBRID (Banyak Password ke Banyak Router - DFS + BFS) ---
                        val hybridParseResult = remember(hybridCsvInput) { DfsPasswordSanitizer.parse(hybridCsvInput) }
                        val hybridValidPasswords = hybridParseResult.validPasswords

                        // Header Ringkasan: Passphrase & Router Target
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BlynkBlueTint,
                            border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Hub,
                                        contentDescription = null,
                                        tint = BlynkBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Mode Hybrid (DFS + BFS Otomatis)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = BlynkBlueDark
                                        )
                                        Text(
                                            text = "${hybridValidPasswords.size} Kata Sandi • ${scanItems.size} Router di Sekitar",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Quick template button
                                if (!isSequentialTesting) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.applyHybridPracticumTemplate() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                                contentDescription = null,
                                                tint = BlynkBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Muat Modul",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BlynkBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Input CSV Passphrase Hybrid
                        OutlinedTextField(
                            value = hybridCsvInput,
                            onValueChange = { viewModel.setHybridCsvInput(it) },
                            label = { Text("Daftar Passphrase Lab (CSV)", fontSize = 12.sp) },
                            placeholder = { Text("Contoh: 12345678; admin123; ilmukomputeripb", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.toggleHybridCsvVisibility() }) {
                                    Icon(
                                        imageVector = if (isHybridCsvVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle CSV visibility",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (isHybridCsvVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlynkBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            enabled = !isSequentialTesting
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sanitizer Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (hybridValidPasswords.isNotEmpty()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "${hybridValidPasswords.size} valid",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hybridValidPasswords.isNotEmpty()) Color(0xFF15803D) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (hybridParseResult.skippedTooShortCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "${hybridParseResult.skippedTooShortCount} (<8 char)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (hybridParseResult.duplicateCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = "${hybridParseResult.duplicateCount} duplikat",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Button
                        if (isSequentialTesting) {
                            Button(
                                onClick = { viewModel.cancelTraversal() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QualityRed)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hentikan Traversal Hybrid", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.startHybridTraversal()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = scanItems.isNotEmpty() && hybridValidPasswords.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Hub,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (scanItems.isEmpty()) {
                                        "Daftar Wi-Fi Kosong (Scan Terlebih Dahulu)"
                                    } else if (hybridValidPasswords.isEmpty()) {
                                        "Masukkan Passphrase Minimal 1"
                                    } else {
                                        "Mulai Traversal Hybrid (${hybridValidPasswords.size} Pwd ke ${scanItems.size} Router)"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

            // 2. Goal Ditemukan Celebration Card
            AnimatedVisibility(
                visible = goalFoundRouter != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (goalFoundRouter != null) {
                    val goal = goalFoundRouter!!
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.5.dp, QualityGreen),
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = QualityGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "🎉 GOAL DITEMUKAN!",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissGoalFound() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Tutup",
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Router ${goal.ssid} terverifikasi dan tersimpan ke Vault!",
                                    fontSize = 12.sp,
                                    color = Color(0xFF166534)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.95f),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = goal.ssid,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF14532D)
                                            )
                                            Text(
                                                text = "Password: ${goal.workingPassword}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF15803D)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(goal.workingPassword))
                                                viewModel.sendSnackbar("Password untuk ${goal.ssid} berhasil disalin!")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = QualityGreen),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Live Traversal Progress Banner
            AnimatedVisibility(
                visible = isSequentialTesting || sequentialTestMessage.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSequentialTesting -> Color(0xFFEFF6FF)
                            sequentialTestMessage.contains("Berhasil") || sequentialTestMessage.contains("Goal") -> Color(0xFFDCFCE7)
                            else -> Color(0xFFF8FAFC)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSequentialTesting -> BlynkBlue.copy(alpha = 0.4f)
                                sequentialTestMessage.contains("Berhasil") || sequentialTestMessage.contains("Goal") -> QualityGreen.copy(alpha = 0.4f)
                                else -> Color(0xFFE2E8F0)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSequentialTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = BlynkBlue,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else if (sequentialTestMessage.contains("Berhasil") || sequentialTestMessage.contains("Goal")) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = QualityGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Text(
                                        text = sequentialTestMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isSequentialTesting -> BlynkBlueDark
                                            sequentialTestMessage.contains("Berhasil") || sequentialTestMessage.contains("Goal") -> Color(0xFF15803D)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }

                                if (isSequentialTesting && traversalTotalCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BlynkBlueTint
                                    ) {
                                        Text(
                                            text = "${minOf(currentCandidateIndex + 1, traversalTotalCount)} / $traversalTotalCount",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlynkBlueDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (isSequentialTesting && traversalTotalCount > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val progressRatio = (currentCandidateIndex + 1).toFloat() / traversalTotalCount.toFloat()
                                LinearProgressIndicator(
                                    progress = { progressRatio.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = BlynkBlue,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Header Toolbar: Title, Timestamp, Audit Log, and SCAN Button
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

                    val isScanCoolingDown = remainingScanCooldown > 0
                    Button(
                        onClick = { viewModel.triggerSmartRefresh() },
                        enabled = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanCoolingDown) MaterialTheme.colorScheme.surfaceVariant else BlynkBlue,
                            contentColor = if (isScanCoolingDown) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanCoolingDown) Icons.Rounded.HourglassBottom else Icons.Rounded.Refresh,
                            contentDescription = if (isScanCoolingDown) "Refresh Cache" else "Scan Wi-Fi",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanCoolingDown) "REFRESH (${remainingScanCooldown}s)" else "SCAN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Warning Banners (Lokasi / Izin)
            val isLocDisabled = scanState is WifiScanState.LocationDisabled ||
                (scanState is WifiScanState.Error && (scanState as WifiScanState.Error).isLocationDisabled)

            if (isLocDisabled) {
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sistem Android mewajibkan layanan lokasi aktif untuk memindai jaringan Wi-Fi sekitar.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = Color(0xFF78350F)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

            if (scanState is WifiScanState.PermissionMissing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, QualityRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = QualityRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Izin Pemindaian Belum Diberikan",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF991B1B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aplikasi membutuhkan izin ACCESS_FINE_LOCATION atau NEARBY_WIFI_DEVICES.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            color = Color(0xFF7F1D1D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QualityRed),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Buka Pengaturan Aplikasi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (scanState is WifiScanState.Scanning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BlynkBlueTint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BlynkBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Memindai frekuensi jaringan sekitar...",
                            style = MaterialTheme.typography.bodySmall,
                            color = BlynkBlueDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4. Daftar Hasil Pemindaian Wi-Fi (Modern Blynk Tile Style)
            if (scanItems.isEmpty() && scanState !is WifiScanState.Scanning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
                                text = "Tekan tombol SCAN di atas untuk memindai router di sekitar Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    items(
                        items = scanItems,
                        key = { it.bssid.ifBlank { it.ssid } }
                    ) { item ->
                        val isTarget = connectState.targetSsid == item.ssid
                        // Hanya tampilkan tombol countdown jika kartu ini adalah target aktif / yang baru saja dicoba
                        val cooldownSec = if (isTarget) viewModel.getRemainingCooldownForSsid(item.ssid) else 0
                        val isWaitingApproval = viewModel.isItemWaitingApproval(item.ssid)

                        val isDfsTarget = (selectedMode == AroundCheckMode.DFS && dfsTargetItem?.bssid == item.bssid)
                        val verifiedItem = verifiedRouters.firstOrNull {
                            if (item.bssid.isNotBlank() && it.bssid.isNotBlank()) {
                                it.bssid.equals(item.bssid, ignoreCase = true)
                            } else {
                                it.ssid.isNotBlank() && it.ssid == item.ssid
                            }
                        }

                        WifiScanItemCard(
                            item = item,
                            connectState = connectState,
                            cooldownSeconds = cooldownSec,
                            isWaitingApproval = isWaitingApproval,
                            isDfsMode = selectedMode == AroundCheckMode.DFS,
                            isDfsTarget = isDfsTarget,
                            hybridStatus = hybridRouterStatuses[item.bssid.ifBlank { item.ssid }],
                            isVerified = verifiedItem != null,
                            verifiedPassword = verifiedItem?.workingPassword,
                            onSelectDfsTarget = { viewModel.selectDfsTargetItem(item) },
                            onConnectClick = { viewModel.openPasswordDialog(item) },
                            onCancelClick = { viewModel.cancelConnect() },
                            onForgetClick = { viewModel.forgetNetwork(item.ssid) },
                            onCooldownClick = {
                                viewModel.sendSnackbar("Tunggu jeda router ${cooldownSec}s untuk ${item.ssid}")
                            },
                            onCopyVerifiedPassword = { pwd ->
                                clipboardManager.setText(AnnotatedString(pwd))
                                viewModel.sendSnackbar("Password untuk ${item.ssid} berhasil disalin!")
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Visualizer Indikator Bar Sinyal (4 Segmen Berwarna)
 */
@Composable
private fun SignalBarsIndicator(rssi: Int) {
    val level = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    val barColor = when {
        rssi >= -65 -> QualityGreen
        rssi >= -80 -> QualityAmber
        else -> QualityRed
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((3 + i * 2.8).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= level) barColor else Color(0xFFE2E8F0))
            )
        }
    }
}

@Composable
private fun WifiScanItemCard(
    item: WifiScanItem,
    connectState: com.wefi.analyzer.domain.model.WifiConnectState,
    cooldownSeconds: Int,
    isWaitingApproval: Boolean,
    isDfsMode: Boolean = false,
    isDfsTarget: Boolean = false,
    hybridStatus: HybridRouterStatus? = null,
    isVerified: Boolean = false,
    verifiedPassword: String? = null,
    onSelectDfsTarget: (() -> Unit)? = null,
    onConnectClick: () -> Unit,
    onCancelClick: () -> Unit,
    onForgetClick: () -> Unit,
    onCooldownClick: () -> Unit,
    onCopyVerifiedPassword: ((String) -> Unit)? = null
) {
    val isTarget = connectState.targetSsid == item.ssid
    val status = if (isTarget) connectState.status else WifiConnectStatus.Idle
    val isCoolingDown = cooldownSeconds > 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isDfsTarget || hybridStatus is HybridRouterStatus.Testing) 2.dp else if (isTarget) 1.5.dp else 1.dp,
            color = if (isDfsTarget || hybridStatus is HybridRouterStatus.Testing) BlynkBlue else if (isTarget) BlynkBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        shadowElevation = if (isDfsTarget || isTarget || hybridStatus is HybridRouterStatus.Testing) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDfsMode) {
                    Modifier.clickable { onSelectDfsTarget?.invoke() }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row Atas: Ikon Sinyal + Nama SSID + Badges
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hybridStatus is HybridRouterStatus.Found || hybridStatus is HybridRouterStatus.VerifiedFromVault -> QualityGreen.copy(alpha = 0.15f)
                                    isVerified -> QualityGreen.copy(alpha = 0.15f)
                                    status == WifiConnectStatus.Connected -> QualityGreen.copy(alpha = 0.15f)
                                    status == WifiConnectStatus.WaitingApproval -> QualityAmber.copy(alpha = 0.15f)
                                    status == WifiConnectStatus.Rejected || status == WifiConnectStatus.Failed -> QualityRed.copy(alpha = 0.15f)
                                    hybridStatus is HybridRouterStatus.NotFound -> Color(0xFFF1F5F9)
                                    else -> BlynkBlueTint
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                hybridStatus is HybridRouterStatus.Found || hybridStatus is HybridRouterStatus.VerifiedFromVault -> Icons.Rounded.CheckCircle
                                isVerified -> Icons.Rounded.CheckCircle
                                status == WifiConnectStatus.Connected -> Icons.Rounded.CheckCircle
                                status == WifiConnectStatus.Rejected || status == WifiConnectStatus.Failed -> Icons.Rounded.Close
                                hybridStatus is HybridRouterStatus.NotFound -> Icons.Rounded.RemoveCircleOutline
                                else -> Icons.Rounded.Wifi
                            },
                            contentDescription = null,
                            tint = when {
                                hybridStatus is HybridRouterStatus.Found || hybridStatus is HybridRouterStatus.VerifiedFromVault -> QualityGreen
                                isVerified -> QualityGreen
                                status == WifiConnectStatus.Connected -> QualityGreen
                                status == WifiConnectStatus.WaitingApproval -> QualityAmber
                                status == WifiConnectStatus.Rejected || status == WifiConnectStatus.Failed -> QualityRed
                                hybridStatus is HybridRouterStatus.NotFound -> Color(0xFF64748B)
                                else -> BlynkBlue
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.ssid,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isDfsTarget) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BlynkBlueTint
                                ) {
                                    Text(
                                        text = "TARGET DFS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BlynkBlueDark,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Visualizer Bar Sinyal + dBm + CH + Band
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SignalBarsIndicator(rssi = item.rssi)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = if (item.frequencyMhz > 4900) "5 GHz" else "2.4 GHz",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "CH ${item.channel}",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // Badges di Kanan Atas: TERVERIFIKASI / Security
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVerified) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "TERVERIFIKASI",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
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
            }

            // Banner Password Terverifikasi (Jika Tersimpan di Vault)
            if (isVerified && !verifiedPassword.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Password: $verifiedPassword",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF166534)
                            )
                        }

                        IconButton(
                            onClick = { onCopyVerifiedPassword?.invoke(verifiedPassword) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Salin Password",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Status Traversal Hybrid (Badge Khusus Sesi Hybrid: Found / NotFound / Testing)
            if (hybridStatus != null && hybridStatus !is HybridRouterStatus.Idle) {
                Spacer(modifier = Modifier.height(8.dp))
                when (hybridStatus) {
                    is HybridRouterStatus.Found -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cocok: ${hybridStatus.workingPassword}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }

                                if (hybridStatus.workingPassword.isNotBlank()) {
                                    IconButton(
                                        onClick = { onCopyVerifiedPassword?.invoke(hybridStatus.workingPassword) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Salin Password",
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is HybridRouterStatus.VerifiedFromVault -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Terverifikasi (Vault): ${hybridStatus.workingPassword}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }

                                if (hybridStatus.workingPassword.isNotBlank()) {
                                    IconButton(
                                        onClick = { onCopyVerifiedPassword?.invoke(hybridStatus.workingPassword) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Salin Password",
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is HybridRouterStatus.NotFound -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${hybridStatus.testedCount} Passphrase Tidak Cocok",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                    is HybridRouterStatus.Testing -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BlynkBlueTint,
                            border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.5.dp,
                                    color = BlynkBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sedang Diuji [${hybridStatus.currentPasswordIndex}/${hybridStatus.totalPasswords}]...",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlynkBlueDark
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Connection Status Banner jika target aktif
            if (isTarget && status != WifiConnectStatus.Idle) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (status) {
                        WifiConnectStatus.Connected -> Color(0xFFDCFCE7)
                        WifiConnectStatus.WaitingApproval -> Color(0xFFFEF3C7)
                        WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> Color(0xFFFEE2E2)
                        WifiConnectStatus.Timeout -> Color(0xFFFFEDD5)
                        is WifiConnectStatus.Cooldown -> Color(0xFFFEF3C7)
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
                            when (status) {
                                WifiConnectStatus.WaitingApproval -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = QualityAmber
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                WifiConnectStatus.Connected -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = QualityGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = QualityRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                WifiConnectStatus.Timeout, is WifiConnectStatus.Cooldown -> {
                                    Icon(
                                        imageVector = Icons.Rounded.HourglassBottom,
                                        contentDescription = null,
                                        tint = QualityAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                else -> {}
                            }
                            Text(
                                text = when (status) {
                                    WifiConnectStatus.WaitingApproval -> "Menunggu konfirmasi dialog OS..."
                                    WifiConnectStatus.Connected -> "Tersambung ke jaringan"
                                    WifiConnectStatus.Rejected -> "Ditolak / Batal di dialog OS"
                                    WifiConnectStatus.Failed -> connectState.message.ifBlank { "Gagal tersambung" }
                                    WifiConnectStatus.Timeout -> "Timeout persetujuan (30 detik)"
                                    is WifiConnectStatus.Cooldown -> "Cooldown: tunggu ${status.remainingSeconds}s"
                                    else -> ""
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = when (status) {
                                    WifiConnectStatus.Connected -> Color(0xFF15803D)
                                    WifiConnectStatus.WaitingApproval -> Color(0xFF92400E)
                                    WifiConnectStatus.Rejected, WifiConnectStatus.Failed -> Color(0xFFB91C1C)
                                    WifiConnectStatus.Timeout -> Color(0xFFC2410C)
                                    is WifiConnectStatus.Cooldown -> Color(0xFF92400E)
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

            // Row Bawah: Info Frekuensi / Kanal di kiri, Aksi Forget + Connect di kanan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.frequencyMhz > 4900) "Kanal ${item.channel} • 5 GHz" else "Kanal ${item.channel} • 2.4 GHz",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDfsMode && !isDfsTarget) {
                        TextButton(
                            onClick = { onSelectDfsTarget?.invoke() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Pilih Target", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlynkBlue)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = onForgetClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Lupakan Jaringan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Tombol Connect dengan penanganan Cooldown yang responsif
                    Button(
                        onClick = {
                            if (isCoolingDown) {
                                onCooldownClick()
                            } else if (!isWaitingApproval) {
                                onConnectClick()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCoolingDown) QualityAmber else BlynkBlue,
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
                                isWaitingApproval -> "Memproses..."
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
