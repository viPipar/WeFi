package com.wefi.analyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.BlynkBlueTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualHelpDrawer(
    tabId: Int,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (tabId) {
                0 -> ChannelGraphHelpContent()
                1 -> ApListHelpContent()
                2 -> ChannelRatingHelpContent()
                3 -> SpeedTestHelpContent()
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChannelGraphHelpContent() {
    HelpSectionHeader(
        title = "Panduan Grafik Kanal & Spektrum",
        subtitle = "Visualisasi sebaran gelombang radio & interferensi kanal"
    )

    HelpCard(
        title = "📈 Membaca Sumbu X & Sumbu Y",
        description = "• Sumbu Horizontal (X): Menunjukkan nomor kanal frekuensi (Ch 1 s/d 14 di 2.4 GHz, Ch 36 s/d 165 di 5 GHz).\n• Sumbu Vertikal (Y): Menunjukkan kekuatan daya sinyal terima (RSSI) dalam dBm (-20 dBm sangat kuat, -90 dBm sangat lemah)."
    )

    HelpCard(
        title = "📡 Mengapa Bentuknya Parabola?",
        description = "Sinyal Wi-Fi tidak berupa garis lurus melainkan menyebar selebar bandwidth-nya (20 MHz, 40 MHz, atau 80 MHz). Puncak lengkungan adalah titik frekuensi pusat dan daya sinyal terkuat router."
    )

    HelpCard(
        title = "🔵 Garis Vertikal Penanda (Connected AP Plumb-Line)",
        description = "Garis putus-putus biru yang menembus puncak parabola menunjukkan router Wi-Fi yang sedang aktif terhubung ke HP Anda. Anda dapat langsung membandingkan apakah kurva router Anda lebih tinggi dari tetangga atau sedang tertimpa interferensi."
    )

    HelpCard(
        title = "📏 Rumus Jarak (~meter)",
        description = "Estimasi jarak dihitung secara real-time menggunakan Log-Distance Path Loss Model:\nd = 10 ^ ((A0 - RSSI) / (10 * n))\nDi mana A0 adalah daya acuan jarak 1 meter (-40 dBm di 2.4 GHz, -45 dBm di 5 GHz)."
    )
}

@Composable
private fun ApListHelpContent() {
    HelpSectionHeader(
        title = "Panduan Radar & Daftar Access Point",
        subtitle = "Identifikasi seluruh router nirkabel di sekitar Anda"
    )

    HelpCard(
        title = "🔍 Max PHY Rate vs Kecepatan Internet",
        description = "Max PHY Rate adalah kapasitas teoritis pipa radio nirkabel antara HP dan router (misal Wi-Fi 5 up to 866 Mbps). Ini BUKAN kecepatan paket internet ISP, karena kecepatan internet hanya dapat diuji saat Anda terhubung."
    )

    HelpCard(
        title = "⚙️ Kalibrasi Redaman Lingkungan (n)",
        description = "Gunakan tombol selektor di bagian atas:\n• Outdoor (n=2.0): Ruang terbuka bebas hambatan.\n• Indoor (n=2.8): Ruangan rumah/kantor dengan partisi ringan.\n• Beton (n=3.5): Gedung tebal/lab dengan dinding beton masif."
    )

    HelpCard(
        title = "🛡️ Keamanan & BSSID",
        description = "BSSID adalah alamat MAC kartu jaringan radio router. WPA2/WPA3 menunjukkan enkripsi kata sandi yang melindungi jaringan."
    )
}

@Composable
private fun ChannelRatingHelpContent() {
    HelpSectionHeader(
        title = "Panduan Rating Kanal (Channel Optimizer)",
        subtitle = "Analisis kepadatan frekuensi dan rekomendasi kanal terbaik"
    )

    HelpCard(
        title = "⭐ Sistem Rating 1 - 10 Bintang",
        description = "Semakin banyak bintang (hingga 10/10), semakin bersih kanal tersebut dari interferensi tetangga. Kanal dengan label 'Direkomendasikan' adalah kanal paling ideal untuk disetel pada router Anda."
    )

    HelpCard(
        title = "⚠️ Co-Channel & Adjacent-Channel Interference",
        description = "Di frekuensi 2.4 GHz, hanya ada 3 kanal yang tidak saling tumpang tindih: Kanal 1, 6, dan 11. Jika router tetangga menumpuk di kanal 6, memindahkan router Anda ke kanal 1 atau 11 akan menghilangkan tabrakan antrean paket (CSMA/CA)."
    )
}

@Composable
private fun SpeedTestHelpContent() {
    HelpSectionHeader(
        title = "Panduan Uji Kecepatan Aktif (Speedtest)",
        subtitle = "Diagnostik performa internet pada jaringan yang tersambung"
    )

    HelpCard(
        title = "⚡ Download & Upload Throughput",
        description = "Menguji laju transfer paket data riil end-to-end dari server pengujian ke smartphone Anda dalam satuan Megabit per second (Mbps)."
    )

    HelpCard(
        title = "⏱️ Ping (Latensi) & Jitter",
        description = "• Ping: Waktu tempuh bolak-balik (Round-Trip Time) paket data (ms). Di bawah 30ms sangat ideal untuk game & video call.\n• Jitter: Variasi kestabilan latensi. Semakin kecil angka jitter, semakin stabil koneksi Anda."
    )
}

@Composable
private fun HelpSectionHeader(title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BlynkBlueTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = BlynkBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpCard(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}
