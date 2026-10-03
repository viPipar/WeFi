# Smart Network Health & Security Doctor Design Specification

## Executive Summary

Fitur **Smart Network Health & Security Doctor** adalah sistem diagnostik eksekutif satu sentuhan (*1-Click Network Audit*) yang mengorkestrasikan telemetri nirkabel RF, interferensi kanal, konektivitas routing gateway, keamanan autentikasi, dan paparan kerentanan subnet ke dalam **Skor Kesehatan Jaringan 0–100%**, grade mutu (`A+` hingga `Kritis`), kartu rincian 5 pilar, dan daftar rekomendasi perbaikan instan (*actionable remediation*).

---

## 1. Domain Models

### Data Classes & Enums (`com.wefi.analyzer.domain.model.NetworkHealthModels.kt`)

```kotlin
enum class HealthScoreGrade(val label: String, val minScore: Int) {
    A_PLUS("Sempurna (A+)", 90),
    A("Sangat Baik (A)", 80),
    B("Baik (B)", 65),
    C("Perlu Perhatian (C)", 50),
    CRITICAL("Kritis & Berisiko (D/F)", 0)
}

enum class HealthPillarType(val title: String) {
    PHYSICAL_RF("Sinyal & Kualitas Fisik RF"),
    CHANNEL_INTERFERENCE("Kepadatan & Interferensi Kanal"),
    GATEWAY_TRANSPORT("Konektivitas Gateway & DNS"),
    WIRELESS_SECURITY("Protokol Keamanan Wi-Fi"),
    SUBNET_EXPOSURE("Paparan Subnet & Port Lab")
}

enum class PillarStatus {
    OPTIMAL,
    FAIR,
    WARNING,
    DANGER
}

data class HealthPillarScore(
    val type: HealthPillarType,
    val score: Int, // 0 - 100
    val weight: Double, // 0.15 - 0.25 (Total = 1.0)
    val status: PillarStatus,
    val summary: String,
    val metrics: Map<String, String> = emptyMap()
)

enum class RemediationImpact {
    HIGH,
    MEDIUM,
    LOW
}

data class ActionableRemediation(
    val id: String,
    val title: String,
    val description: String,
    val impact: RemediationImpact,
    val targetTabRoute: String? = null // e.g. "rating", "discovery", "around_check"
)

data class NetworkHealthReport(
    val timestamp: Long,
    val ssid: String,
    val bssid: String,
    val overallScore: Int, // 0 - 100
    val grade: HealthScoreGrade,
    val pillars: List<HealthPillarScore>,
    val remediations: List<ActionableRemediation>,
    val isInternetReachable: Boolean
)
```

---

## 2. 5 Pillars Scoring System (Weighted Calculation)

Total Skor Kesehatan dihitung berdasarkan pembobotan 5 pilar:
$$\text{Total Score} = \sum (\text{Pillar Score} \times \text{Weight})$$

| Pilar | Bobot | Metrik Evaluasi | Skala Penalti / Skor |
| :--- | :---: | :--- | :--- |
| **1. Fisik & Sinyal RF** | 20% | RSSI (dBm), Frekuensi Band (2.4/5/6 GHz), Link Speed (Mbps), Wi-Fi Standard | RSSI > -60dBm: 100; -60 to -70dBm: 85; -70 to -80dBm: 60; < -80dBm: 30. Band 5/6GHz bonus +10. |
| **2. Interferensi Kanal** | 20% | Jumlah AP co-channel pada kanal yang sama, adjacent channel bleed | 0 AP tetangga: 100; 1-2 AP: 80; 3-5 AP: 60; >5 AP: 40. Co-channel 2.4GHz non-1/6/11 penalti berat. |
| **3. Transport & Gateway** | 25% | Ping RTT ke Gateway lokal, Ping RTT ke DNS Publik (1.1.1.1 / 8.8.8.8) | Gateway < 10ms: 100; 10-30ms: 85; > 50ms: 60; Timeout/RTO: 10. |
| **4. Keamanan Wi-Fi** | 20% | Enkripsi AP (Open, WEP, WPA-TKIP, WPA2-Personal, WPA3-SAE, WPA3-Enterprise) | WPA3: 100; WPA2-AES: 90; WPA-TKIP: 50; WEP/Open: 15. |
| **5. Subnet & Port Exposure** | 15% | Port berisiko terbuka di router/gateway (23 Telnet, 21 FTP, 445 SMB, 8291 Winbox) | Tanpa port berisiko: 100; Telnet terbuka: penalti -40; SMB terbuka: penalti -30; Winbox: penalti -20. |

---

## 3. Domain UseCase & Orchestration

### `EvaluateNetworkHealthUseCase`
* Terhubung ke:
  * `CurrentConnectionRepository`: Mengambil SSID, BSSID, RSSI, Link Speed, Gateway IP, Frekuensi.
  * `WifiScannerRepository`: Mengambil data scan AP di sekitar untuk menghitung interferensi kanal.
  * `NetworkDiscoveryRepository`: Menguji respons gateway & mendeteksi port terbuka pada gateway IP.
* Asinkron & Non-Blocking: Menggunakan coroutine worker `withContext(Dispatchers.IO)` dengan timeout batas aman per probe (maksimal 3000ms).

---

## 4. UI/UX Design (`clean-ui-procedural` Standard)

### Header Integration
* Di `MainActivity.kt` / `HardwareStateBanner.kt`, terdapat tombol cepat **"Doctor Audit" 🩺** dengan chip skor kesehatan terkini atau tombol "Periksa Jaringan".

### `NetworkDoctorBottomSheet`
* **Animated Circular Arc Gauge**:
  * Menampilkan skor 0–100% di tengah dengan grade badge dinamis (`A+`, `A`, `B`, `C`, `KRITIS`).
  * Warna adaptif: Hijau (#22C55E) untuk >= 80, Biru (#77ADF9) untuk 65-79, Amber (#F59E0B) untuk 50-64, Merah (#EF4444) untuk < 50.
* **Kartu 5 Pilar Grid / Kolom**:
  * Setiap pilar menampilkan ikon, nama pilar, skor persentase, dan bar progress mini.
  * Klik pilar menampilkan detail metrik teknis.
* **Daftar Rekomendasi Solusi (1-Tap Remediation)**:
  * Mengurutkan aksi dari dampak tertinggi (`HIGH IMPACT FIRST`).
  * Tombol navigasi cepat (misal: "Buka Tab Rating Saluran" atau "Buka Audit Port").
* **Aksi Berbagi & Salin**:
  * Tombol salin laporan ringkas teks ke clipboard atau share intent.

---

## 5. Rencana Pengujian
* **Unit Tests**:
  * `EvaluateNetworkHealthUseCaseTest`: Menguji kalkulasi matematis skor masing-masing pilar, penentuan grade, dan pembentukan rekomendasi.
  * `NetworkDoctorViewModelTest`: Menguji perubahan status `isAuditing`, pembaruan StateFlow `healthReport`, dan penanganan error/offline.
* **Verifikasi Build**:
  * `.\gradlew.bat testDebugUnitTest --no-daemon` (100% pass)
  * `.\gradlew.bat assembleDebug --no-daemon` (BUILD SUCCESSFUL)
