# Network Security Assessment & Surface Reconnaissance Suite Design Specification

## 1. Executive Summary

Fitur **Network Security Assessment & Surface Reconnaissance Suite** adalah sistem analisis postur keamanan jaringan dua lapis (*Dual-Layer Security Reconnaissance*) yang dirancang untuk mempercepat auditor keamanan jaringan dan teknisi lab dalam mendeteksi titik lemah konfigurasi pada perimeter Wi-Fi serta memetakan aset bernilai tinggi (*High-Value Assets*) dan protokol tanpa enkripsi (*Cleartext Services*) di dalam subnet LAN.

Sistem ini beroperasi 100% pada Android non-root, mematuhi standar Clean Architecture, coroutine Kotlin thread-safe, dan panduan antarmuka `clean-ui-procedural`.

---

## 2. Arsitektur & Domain Models

### A. Model Keamanan Nirkabel (`com.wefi.analyzer.domain.model.WirelessSecurityAudit.kt`)

```kotlin
package com.wefi.analyzer.domain.model

enum class PmfMode(val label: String) {
    REQUIRED("Wajib (PMF-R)"),
    CAPABLE("Opsional (PMF-C)"),
    NONE("Tidak Aktif (Rentan Spoofing)")
}

data class WirelessSecurityAuditItem(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val capabilities: String,
    val hasWps: Boolean,
    val pmfMode: PmfMode,
    val hasInsecureCipher: Boolean,
    val isRogueTwinCandidate: Boolean,
    val riskLevel: HostRiskLevel,
    val riskHighlights: List<String>
)

enum class AssetCategory(val label: String) {
    GATEWAY_ROUTER("Router / Gateway"),
    SURVEILLANCE_CCTV("Kamera CCTV / NVR"),
    STORAGE_NAS("NAS / File Server"),
    IOT_BROKER("IoT Broker / Bridge"),
    WORKSTATION("Host / Komputer"),
    UNKNOWN("Perangkat Lain")
}
```

### B. Ekstensi Model `WifiAccessPoint`
Menambahkan properti:
* `capabilities: String` (raw beacon capabilities)
* `hasWps: Boolean` (deteksi token `[WPS]`)
* `pmfMode: PmfMode` (deteksi token PMF)
* `hasInsecureCipher: Boolean` (deteksi WEP atau TKIP)

---

## 3. Logika Evaluasi & Algoritma Heuristik

### A. Heuristik Keamanan Wi-Fi (RF Layer)
1. **WPS Detection:**
   Jika `capabilities.contains("WPS", ignoreCase = true)` -> `hasWps = true`.
2. **PMF (Protected Management Frames) Detection:**
   * Jika `capabilities.contains("PMF-R")` atau `capabilities.contains("MFPR")` -> `PmfMode.REQUIRED`.
   * Jika `capabilities.contains("PMF-C")` atau `capabilities.contains("MFPC")` atau `capabilities.contains("SAE")` -> `PmfMode.CAPABLE`.
   * Selain itu -> `PmfMode.NONE` (AP rentan terhadap deauth/disassociation injection).
3. **Insecure Cipher Detection:**
   Jika `capabilities.contains("TKIP")` atau `capabilities.contains("WEP")` -> `hasInsecureCipher = true`.
4. **Rogue AP / Twin SSID Heuristics:**
   Jika terdapat >= 2 AP di scan results yang memiliki SSID sama (dan bukan blank/hidden), namun memiliki BSSID dengan prefix vendor OUI yang berbeda secara mencurigakan atau tipe enkripsi yang tidak seragam (misal AP 1 menggunakan WPA2 tapi AP 2 menggunakan Open), tandai sebagai `isRogueTwinCandidate = true`.

### B. Klasifikasi Aset Bernilai Tinggi & Port Cleartext (LAN Layer)
1. **Port Cleartext Sensitif:**
   * Port 23 (Telnet), Port 21 (FTP), Port 80 (HTTP Admin) ditandai sebagai `CLEARTEXT_MANAGEMENT` dengan tingkat risiko tinggi.
2. **Pemetaan Kategori Aset:**
   * Port 554/3702/37777 -> `SURVEILLANCE_CCTV`
   * Port 445/139 -> `STORAGE_NAS`
   * Port 8291/Port 53/Gateway -> `GATEWAY_ROUTER`
   * Port 1883/8883 -> `IOT_BROKER`

---

## 4. Antarmuka Pengguna (`clean-ui-procedural` Standard)

### A. Tampilan Tab Ganda di Modul Audit (`NetworkDiscoveryScreen.kt`)
1. **Tab 1: LAN Attack Surface & Aset Kritis:**
   * Menampilkan host terdeteksi, ringkasan port, kartu risiko host, tombol deep scan port, dan filter aset bernilai tinggi.
2. **Tab 2: Wireless Recon Matrix (Permukaan Serangan Wi-Fi):**
   * Menampilkan daftar AP di sekitar yang telah diaudit keamanannya.
   * Chip penanda: `WPS AKTIF` (Merah/Amber), `NO PMF` (Amber), `TKIP USANG` (Merah), `DUPLIKAT/ROGUE` (Merah).
   * Filter cepat: *"Hanya AP Rentan"*.

### B. Bar Ringkasan Cepat (Executive Recon Stat Bar):
* Menampilkan jumlah AP dengan WPS aktif, jumlah AP tanpa PMF, jumlah port cleartext di LAN, dan total host kritis.

### C. Ekspor Laporan Komprehensif:
* Laporan Markdown & JSON mencakup seluruh temuan pre-auth (Wi-Fi) dan post-auth (LAN) dengan stempel waktu dan rekomendasi perbaikan.

---

## 5. Rencana Pengujian
* **Unit Tests**:
  * Pengujian parsing capabilities (WPS, PMF, TKIP) dari string Android ScanResult.
  * Pengujian deteksi duplikat SSID / Rogue AP candidate.
  * Pengujian klasifikasi kategori aset LAN dan identifikasi port cleartext.
* **Verifikasi Build**:
  * `.\gradlew.bat testDebugUnitTest --no-daemon`
  * `.\gradlew.bat assembleDebug --no-daemon`
