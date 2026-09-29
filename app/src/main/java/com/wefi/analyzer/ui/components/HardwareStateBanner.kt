package com.wefi.analyzer.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.domain.model.DeviceHardwareState
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.QualityAmber

/**
 * Banner panduan perangkat keras ala Blynk.io:
 * Memberi tahu pengguna jika GPS mati, Wi-Fi mati, atau izin belum lengkap,
 * dilengkapi tombol aksi langsung ke pengaturan sistem Android.
 */
@Composable
fun HardwareStateBanner(
    hardwareState: DeviceHardwareState,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hardwareState.isReadyForScan) return

    val context = LocalContext.current

    val (title, description, buttonText, icon, onAction) = when {
        !hardwareState.isWifiEnabled -> {
            Tuple5(
                "Wi-Fi Perangkat Nonaktif",
                "Aktifkan Wi-Fi ponsel Anda untuk memindai spektrum dan daftar jaringan.",
                "Aktifkan Wi-Fi",
                Icons.Rounded.WifiOff
            ) {
                try {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        !hardwareState.isLocationEnabled -> {
            Tuple5(
                "Layanan Lokasi (GPS) Mati",
                "Android mewajibkan GPS aktif agar sistem mengizinkan pendeteksian router Wi-Fi di sekitar.",
                "Aktifkan GPS",
                Icons.Rounded.LocationOff
            ) {
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        else -> {
            Tuple5(
                "Izin Lokasi Belum Diberikan",
                "Beri izin lokasi agar aplikasi diizinkan membaca nama SSID dan kekuatan sinyal.",
                "Beri Izin",
                Icons.Rounded.Security
            ) {
                onRequestPermissions()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFFBEB), // Soft Amber background
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = QualityAmber,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = BlynkBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E
)
