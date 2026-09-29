# weFi 📶
> **Modern Mobile Wi-Fi Spectrum Analyzer & Network Diagnostics**  
> *Developed for Computer Networks & Data Communication (Komunikasi Data dan Jaringan)*

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_Native-3DDC84.svg?logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Style](https://img.shields.io/badge/Theme-Blynk.io_Clean_Slate-77ADF9.svg)](https://blynk.io)

---

## 📖 Ringkasan Proyek

**weFi** adalah aplikasi mobile Android native berbasis **Kotlin & Jetpack Compose** yang dirancang sebagai instrumen laboratorium dan diagnostik spektrum frekuensi nirkabel (*Wi-Fi*). Aplikasi ini menjembatani teori **Komunikasi Data dan Jaringan** (propagasi gelombang radio, interferensi ko-kanal, estimasi jarak, modulasi fisik PHY, dan throughput transport) ke dalam antarmuka instrumen bergerak (*mobile telemetry*).

Mengusung filosofi desain **Blynk.io IoT Clean Slate (Putih - Biru `#77ADF9`)**, antarmuka dirancang minimalis, compact, terstruktur, ramah mata (*Eye-Comfort Anti-Glare*), dan bebas dari elemen *AI-Slop*.

---

## ✨ Fitur Utama

### 1. 📊 Grafik Kanal Parabola Spektrum (Channel Graph)
- **Multi-Band Support:** Mendukung pemindaian frekuensi **2.4 GHz** (Ch 1–14), **5.0 GHz** (Ch 36–165), dan **6.0 GHz** (Wi-Fi 6E).
- **Custom Canvas Bezier Engine:** Kurva parabola kuadratik halus yang merepresentasikan sebaran daya sinyal ($-20$ hingga $-100\text{ dBm}$) dan lebar kanal ($20$, $40$, $80$, $160\text{ MHz}$).
- **Connected AP Center Plumb-Line:** Garis vertikal putus-putus (*dashed line*) di tengah kurva Wi-Fi yang sedang aktif tersambung dengan badge `● TERHUBUNG (JARINGAN SAYA)`. Memudahkan analisis komparasi kekuatan sinyal dan interferensi terhadap router tetangga.

### 2. 📏 Estimasi Jarak Matematis (Log-Distance Path Loss)
Menggunakan model fisika perambatan gelombang radio standar IEEE 802.11:

$$d = 10^{\frac{A_0(f) - \text{RSSI}}{10 \cdot n}}$$

- $A_0(f)$: Daya acuan pada jarak 1 meter ($-40\text{ dBm}$ pada 2.4 GHz, $-45\text{ dBm}$ pada 5.0 GHz).
- **Preset Lingkungan Adaptif:**
  - 🌳 **Outdoor (Ruang Terbuka):** $n = 2.0$ (*Free Space Path Loss*)
  - 🏢 **Indoor (Rumah / Kantor):** $n = 2.8$ (*Default*)
  - 🧱 **Dense Obstacles (Tembok Beton / Lab):** $n = 3.5$

### 3. 🔍 Analisis Kualitas Pasif (Tanpa Login / Sandi)
Membaca *Beacon Information Elements (IEs)* dari udara untuk mengekstrak:
- **Theoretical Max PHY Rate:** Kapasitas pipa transmisi radio router (Wi-Fi 4 s/d Wi-Fi 7).
- **WiFi Quality Score (0–100%):** Skor komposit berdasarkan RSSI, generasi standar nirkabel, dan kebersihan kanal.

### 4. ⚡ Speedtest Aktif & Diagnostik Koneksi
Khusus untuk jaringan yang sedang terhubung ke perangkat:
- **Latensi Ping & Jitter:** Pengukuran kestabilan waktu tempuh paket (*Round-Trip Time*).
- **Download & Upload Throughput (Mbps):** Uji transfer aliran data riil dengan gauge speedometer animasi Blynk.
- **Network Telemetry:** IP Lokal, Gateway Router, DNS Server, dan Hardware Negotiated Link Speed.

### 5. 💡 Juicy Morphing Floating Help Assistant
- Tombol bantuan dinamis (`?`) melayang dengan animasi *juicy ease-in-ease-out* (`FastOutSlowInEasing`).
- Membesar saat perpindahan tab atau scroll, dan otomatis mengecil (*auto-dock*) ke tepi bezel layar saat idle selama 3 detik.
- Mengetuk tombol membuka **Contextual Help Drawer** yang berisi panduan praktis dan tips troubleshooting untuk tab yang sedang aktif.

---

## 🏗️ Arsitektur Perangkat Lunak (Clean Architecture + MVVM)

```
com.wefi.analyzer
├── data
│   ├── model/         # Entity data mentah scan result & speedtest
│   └── repository/    # Implementasi hardware WifiManager & engine network
├── domain
│   ├── model/         # Immutable domain entities (WifiAccessPoint, EnvironmentPreset)
│   ├── repository/    # Interface repository
│   └── usecase/       # Pure Kotlin use cases (CalculateDistance, ParsePhyRate, etc.)
└── ui
    ├── components/    # Reusable widgets (BlynkCard, MorphingHelpFab, Canvas, dll)
    ├── navigation/    # Bottom navigation bar & routing
    ├── screens/       # 4 Tab Views (ChannelGraph, ApList, Rating, Speedtest)
    └── theme/         # Blynk.io Blue Design System (#77ADF9)
```

---

## 🛠️ Prasyarat & Kompilasi

- **Android Studio:** Hedgehog (2023.1.1) atau versi lebih baru
- **JDK:** Java 17
- **Android SDK:** Min SDK 26 (Android 8.0 Oreo), Target SDK 34 (Android 14)

### Menjalankan Proyek:
```bash
# Clone repositori
git clone https://github.com/viPipar/weFi.git
cd weFi

# Jalankan Unit Tests Domain
./gradlew testDebugUnitTest

# Rakit APK Debug
./gradlew assembleDebug
```

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi terbuka [MIT License](LICENSE).  
Dibuat dengan dedikasi untuk mata kuliah Komunikasi Data dan Jaringan oleh **viPipar** (`rafifilmanyy@gmail.com`).
