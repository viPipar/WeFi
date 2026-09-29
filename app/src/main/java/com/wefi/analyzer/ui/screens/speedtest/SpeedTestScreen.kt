package com.wefi.analyzer.ui.screens.speedtest

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.wefi.analyzer.domain.model.SpeedTestStage
import com.wefi.analyzer.ui.components.BlynkCard
import com.wefi.analyzer.ui.components.BlynkMetricTile
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueDark
import com.wefi.analyzer.ui.theme.BlynkBlueTint
import com.wefi.analyzer.ui.theme.QualityGreen

@Composable
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel,
    modifier: Modifier = Modifier
) {
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val connectedAp = connectionInfo.accessPoint

    val animatedProgress by animateFloatAsState(
        targetValue = metrics.progress,
        label = "test_progress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Header Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SPEEDTEST AKTIF",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Throughput, Latensi & Stabilitas Jaringan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BlynkBlueTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = BlynkBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Connected Network Details Card
        BlynkCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (connectedAp != null) BlynkBlue else Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = connectedAp?.displaySsid ?: "Tidak Ada Wi-Fi Terhubung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (connectedAp != null) "IP: ${connectionInfo.ipAddress} • Gateway: ${connectionInfo.gatewayIp}" else "Silakan hubungkan perangkat ke Wi-Fi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (connectedAp != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BlynkMetricTile(
                        label = "Hardware Link Rate",
                        value = "${connectionInfo.linkSpeedMbps}",
                        unit = "Mbps"
                    )
                    BlynkMetricTile(
                        label = "Kekuatan Sinyal",
                        value = "${connectedAp.rssi}",
                        unit = "dBm"
                    )
                    BlynkMetricTile(
                        label = "Kanal Aktif",
                        value = "Ch ${connectedAp.channel}",
                        unit = if (connectedAp.is5GHz) "5GHz" else "2.4GHz"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Central Speedometer Gauge Card
        BlynkCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 24.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier.fillMaxSize(),
                        color = BlynkBlue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        strokeWidth = 14.dp,
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displaySpeed = if (metrics.stage == SpeedTestStage.UPLOAD) {
                            metrics.uploadMbps
                        } else {
                            metrics.downloadMbps
                        }
                        Text(
                            text = if (displaySpeed > 0) "$displaySpeed" else "0.0",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mbps",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = BlynkBlueTint
                        ) {
                            Text(
                                text = when (metrics.stage) {
                                    SpeedTestStage.IDLE -> "SIAP UJI"
                                    SpeedTestStage.PING -> "UJI PING & JITTER"
                                    SpeedTestStage.DOWNLOAD -> "MENGUNDUH (DOWNLOAD)"
                                    SpeedTestStage.UPLOAD -> "MENGUNGGAH (UPLOAD)"
                                    SpeedTestStage.FINISHED -> "SELESAI"
                                    SpeedTestStage.ERROR -> "GAGAL"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlynkBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Ping & Jitter Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BlynkMetricTile(
                        label = "Latensi (Ping)",
                        value = "${metrics.pingMs}",
                        unit = "ms",
                        valueColor = if (metrics.pingMs > 0 && metrics.pingMs < 40) QualityGreen else MaterialTheme.colorScheme.onSurface
                    )
                    BlynkMetricTile(
                        label = "Jitter",
                        value = "${metrics.jitterMs}",
                        unit = "ms"
                    )
                    BlynkMetricTile(
                        label = "Upload Riil",
                        value = "${metrics.uploadMbps}",
                        unit = "Mbps"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Action Start Button
        Button(
            onClick = { viewModel.startSpeedTest() },
            enabled = !metrics.isRunning && connectedAp != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BlynkBlue,
                disabledContainerColor = BlynkBlue.copy(alpha = 0.4f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.NetworkCheck,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (metrics.isRunning) "SEDANG MENGUJI JARINGAN..." else "MULAI UJI JARINGAN",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
