package com.wefi.analyzer.ui.screens.aplist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.ui.components.BlynkCard
import com.wefi.analyzer.ui.components.BlynkMetricTile
import com.wefi.analyzer.ui.components.EnvironmentPresetSelector
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueTint
import com.wefi.analyzer.ui.theme.QualityAmber
import com.wefi.analyzer.ui.theme.QualityGreen
import com.wefi.analyzer.ui.theme.QualityRed

@Composable
fun ApListScreen(
    viewModel: ApListViewModel,
    modifier: Modifier = Modifier
) {
    val apList by viewModel.filteredAps.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Header Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RADAR & DAFTAR AP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Total ${apList.size} Access Point terdeteksi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.triggerScan() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Scan Ulang",
                    tint = BlynkBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Adaptive Environment Preset Selector
        EnvironmentPresetSelector(
            selectedPreset = selectedPreset,
            onPresetSelected = { viewModel.setEnvironmentPreset(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari nama SSID atau BSSID...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = BlynkBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Access Point List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(apList, key = { it.bssid }) { ap ->
                ApItemCard(ap = ap)
            }
        }
    }
}

@Composable
private fun ApItemCard(ap: WifiAccessPoint) {
    val qualityColor = when {
        ap.qualityScore >= 80 -> QualityGreen
        ap.qualityScore >= 50 -> QualityAmber
        else -> QualityRed
    }

    BlynkCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (ap.isConnected) BlynkBlue else BlynkBlueTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (ap.isConnected) Color.White else BlynkBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ap.displaySsid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (ap.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BlynkBlueTint
                            ) {
                                Text(
                                    text = "TERHUBUNG",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlynkBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = ap.bssid,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quality Score Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = qualityColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${ap.qualityScore}%",
                    color = qualityColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metrics Grid (RSSI, Jarak, Kanal, PHY Rate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BlynkMetricTile(
                label = "Kekuatan Sinyal",
                value = "${ap.rssi}",
                unit = "dBm"
            )
            BlynkMetricTile(
                label = "Estimasi Jarak",
                value = ap.formattedDistance,
                valueColor = BlynkBlue
            )
            BlynkMetricTile(
                label = "Kanal & Band",
                value = "Ch ${ap.channel}",
                unit = if (ap.is24GHz) "2.4G" else if (ap.is5GHz) "5G" else "6G"
            )
            BlynkMetricTile(
                label = "Max PHY Rate",
                value = "${ap.maxPhyRateMbps}",
                unit = "Mbps"
            )
        }
    }
}
