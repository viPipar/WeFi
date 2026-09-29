package com.wefi.analyzer.ui.screens.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.ui.components.BlynkCard
import com.wefi.analyzer.ui.components.BlynkSegmentedControl
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueTint

@Composable
fun ChannelGraphScreen(
    viewModel: ChannelGraphViewModel,
    modifier: Modifier = Modifier
) {
    val apList by viewModel.displayResults.collectAsState()
    val selectedBand by viewModel.selectedBand.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val connectedBssid by viewModel.connectedBssid.collectAsState()

    val bands = listOf(2.4, 5.0, 6.0)
    val bandLabels = listOf("2.4 GHz", "5.0 GHz", "6.0 GHz")
    val selectedIndex = bands.indexOf(selectedBand).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // 1. Header Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GRAFIK KANAL",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Spektrum Frekuensi & Estimasi Jarak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pause / Freeze button
                IconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) BlynkBlueTint else MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = "Freeze/Resume Graph",
                        tint = if (isPaused) BlynkBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Scan trigger button
                IconButton(
                    onClick = { viewModel.triggerScan() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Scan Frekuensi",
                        tint = BlynkBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Band Switcher Pills
        BlynkSegmentedControl(
            items = bandLabels,
            selectedIndex = selectedIndex,
            onItemSelected = { viewModel.setBand(bands[it]) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Canvas Parabola Card Container or Wi-Fi Disabled Warning
        val isWifiEnabled by viewModel.isWifiEnabled.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current

        if (!isWifiEnabled) {
            BlynkCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                padding = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Wi-Fi Ponsel Dinonaktifkan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aktifkan koneksi Wi-Fi pada perangkat Anda untuk melihat spektrum frekuensi secara langsung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                            } catch (e: Exception) {
                                viewModel.triggerScan()
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BlynkBlue)
                    ) {
                        Text("Buka Pengaturan Wi-Fi", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            BlynkCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                padding = 8.dp
            ) {
                ChannelGraphCanvas(
                    apList = apList,
                    selectedBandGhz = selectedBand,
                    connectedBssid = connectedBssid,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Quick Summary Pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bandCount = apList.count {
                    if (selectedBand == 2.4) it.is24GHz else if (selectedBand == 5.0) it.is5GHz else it.is6GHz
                }
                Text(
                    text = "Terdeteksi: $bandCount Access Point di $selectedBand GHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPaused) {
                    Text(
                        text = "● GRAFIK DIBEKUKAN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}
