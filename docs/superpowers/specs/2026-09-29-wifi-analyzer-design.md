# Design Specification: weFi - Modern Mobile Wi-Fi Analyzer & Spectrum Diagnostics

- **Project Name:** weFi (Mobile Wi-Fi Analyzer)
- **Course / Purpose:** Tugas Besar Komunikasi Data dan Jaringan (Computer Networks & Data Communication)
- **Author:** viPipar (rafifilmanyy@gmail.com)
- **Date:** 2026-09-29
- **Target Platform:** Android Native (Kotlin + Jetpack Compose)
- **Architecture:** Clean Architecture + MVVM + Kotlin Coroutines / StateFlow
- **License:** MIT (Open Source)

---

## 1. Problem Statement & Background

Dalam studi Komunikasi Data dan Jaringan, pemahaman tentang propagasi gelombang radio (Layer 1 Physical OSI), alokasi spektrum frekuensi, interferensi kanal (Layer 2 Data Link / IEEE 802.11 MAC), serta throughput internet (Layer 4 Transport / TCP) sering kali hanya dipelajari secara teoritis di buku teks.

Proyek **weFi** dirancang untuk menyediakan instrumen diagnostik spektrum nirkabel mobile yang:
1. Memvisualisasikan kurva parabola spektrum frekuensi 2.4 GHz, 5.0 GHz, dan 6.0 GHz secara real-time.
2. Mengestimasi jarak fisik perangkat dari setiap Access Point (AP) di sekitar menggunakan formulasi matematis propagasi gelombang radio (*Log-Distance Path Loss Model*).
3. Memberikan pembeda visual tegas bagi jaringan Wi-Fi yang sedang terhubung (*Active Connection Plumb-Line*) untuk komparasi kualitas instan.
4. Menganalisis potensi kecepatan radio (*Theoretical Max PHY Rate*) dan kepadatan interferensi seluruh AP sekitar tanpa memerlukan autentikasi/koneksi.
5. Menyediakan pengujian performa aktif (*Active Speedtest: Ping, Jitter, Download, Upload*) khusus untuk jaringan yang sedang tersambung.
6. Mengintegrasikan floating help assistant dinamis (`?` icon) dengan animasi morphing juicy (ease-in-ease-out) yang dapat mengecil menjadi edge drawer saat idle dan membesar saat navigasi tab/scroll untuk membuka panduan kontekstual.
7. Mengadopsi antarmuka visual **Blynk.io Style (Putih - Biru `#77ADF9`)** yang minimalistik, modern, rapi, dan ergonomis bagi mata (*Eye-Comfort*).

---

## 2. Analisis Teori Komunikasi Data & Jaringan

### 2.1 Mengapa Uji Kecepatan Internet Riil Membutuhkan Koneksi?
Terdapat perbedaan fundamental antara **Kapasitas Link Fisik (PHY Rate)** dan **Throughput Internet (Bandwidth Internet)**:

```
[ OSI Reference Layer Interaction ]
Layer 7 (Application): Speedtest Engine (HTTP Download/Upload) ───┐ [Wajib Terkoneksi & Ber-IP]
Layer 4 (Transport)  : TCP/UDP Sockets & Streams               ───┤ Handshake WPA2/WPA3
Layer 3 (Network)    : IP Routing & DHCP Gateway               ───┘
───────────────────────────────────────────────────────────────────────────── [Batas Pasif]
Layer 2 (Data Link)  : 802.11 Beacon Frames, SSID, BSSID, IEs  ───┐ [Bisa Dibaca Tanpa Koneksi]
Layer 1 (Physical)   : Frekuensi (MHz), RSSI (dBm), Lebar Kanal ───┘
```

1. **Enkripsi & Asosiasi (4-Way Handshake):** Access Point yang terlindungi sandi WPA2/WPA3 mengenkripsi frame data menggunakan *Pairwise Transient Key (PTK)*. Perangkat tanpa kredensial tidak dapat mengirim paket data ke router; router akan langsung membuang (*drop*) frame tersebut.
2. **Ketiadaan Routing IP (DHCP):** Perangkat yang belum terhubung tidak memiliki IP address lokal dan gerbang NAT (*gateway*) menuju internet publik.
3. **Keterbatasan Hardware Smartphone (Station Mode):** Radio Wi-Fi smartphone terkunci pada mode *Station (STA)* yang hanya dapat berasosiasi dengan satu BSSID pada satu waktu.

### 2.2 Metode Evaluasi Kualitas Wi-Fi Pasif (Tanpa Koneksi)
Aplikasi membaca **Beacon Frames 802.11** yang dipancarkan secara publik oleh seluruh router sekitar untuk mengekstrak:
1. **Theoretical Maximum PHY Rate:** Dihitung dari standar generasi (802.11b/g/n/ac/ax) dan lebar kanal (`channelWidth` 20/40/80/160 MHz) serta kapabilitas MIMO yang tertera pada *Information Elements (IEs)*.
2. **Received Signal Strength Indication (RSSI):** Kekuatan sinyal dalam satuan $-20$ hingga $-100\text{ dBm}$.
3. **Channel Congestion & Co-Channel Interference (CCI):** Tingkat tumpang tindih kanal di frekuensi yang sama.
4. **Formula Pasif "WiFi Quality Score" (0–100%):**
   $$\text{Score} = (0.50 \times S_{\text{RSSI}}) + (0.25 \times S_{\text{Gen}}) + (0.25 \times S_{\text{Interference}})$$

---

## 3. Formulasi Matematika & Algoritma Jaringan

### 3.1 Model Estimasi Jarak (Log-Distance Path Loss Model)
Hubungan antara daya terima ($\text{RSSI}$) dan jarak tempuh ($d$) di udara dimodelkan dengan:

$$d = 10^{\frac{A_0(f) - \text{RSSI}}{10 \cdot n}}$$

Di mana:
- $\text{RSSI}$: Kuat sinyal yang diterima perangkat (dBm).
- $A_0(f)$: Daya sinyal pada jarak acuan $1\text{ meter}$ ($d_0 = 1\text{m}$):
  - Frekuensi 2.4 GHz: $A_0 \approx -40\text{ dBm}$
  - Frekuensi 5.0 GHz: $A_0 \approx -45\text{ dBm}$
  - Frekuensi 6.0 GHz: $A_0 \approx -47\text{ dBm}$
- $n$ (*Path Loss Exponent*): Eksponen redaman media perambatan:
  - *Outdoor (Free Space):* $n = 2.0$
  - *Indoor Normal (Rumah / Kantor):* $n = 2.8$ (Default)
  - *Dense Concrete (Gedung Beton / Lab Jaringan):* $n = 3.5$
- Format Tampilan: `~X.Xm` (misal `~14m`).

### 3.2 Pemetaan Frekuensi ke Nomor Kanal (IEEE 802.11)
- **2.4 GHz:**
  $$\text{Ch} = \frac{f - 2407}{5} \quad (\text{untuk } f \le 2472\text{ MHz}); \quad \text{Ch 14} = 2484\text{ MHz}$$
- **5 GHz:**
  $$\text{Ch} = \frac{f - 5000}{5} \quad (\text{Rentang } 5170 - 5825\text{ MHz})$$
- **6 GHz:**
  $$\text{Ch} = \frac{f - 5940}{5} + 1 \quad (\text{Rentang } 5945 - 7105\text{ MHz})$$

### 3.3 Algoritma Rating Kanal Terbaik (Channel Optimizer)
Menghitung penalti akumulatif untuk setiap kandidat kanal $c$:

$$\text{Penalty}(c) = \sum_{i \in \text{APs}} \left( \frac{100 - |\text{RSSI}_i|}{10} \right) \cdot \text{OverlapWeight}(c, c_i)$$

Kanal dengan penalti paling rendah mendapatkan skor rating bintang 10/10 dan direkomendasikan kepada pengguna.

---

## 4. Desain Sistem & Arsitektur Perangkat Lunak

### 4.1 Struktur Modul (Clean Architecture)
```
com.wefi.analyzer
├── data
│   ├── model
│   │   ├── WifiAccessPointEntity.kt
│   │   └── SpeedTestResultEntity.kt
│   ├── repository
│   │   ├── WifiScannerRepositoryImpl.kt
│   │   └── SpeedTestRepositoryImpl.kt
│   └── service
│       └── AndroidWifiBroadcastReceiver.kt
├── domain
│   ├── model
│   │   ├── WifiAccessPoint.kt
│   │   ├── EnvironmentPreset.kt
│   │   ├── ChannelRating.kt
│   │   └── SpeedTestMetrics.kt
│   ├── repository
│   │   ├── WifiScannerRepository.kt
│   │   └── SpeedTestRepository.kt
│   └── usecase
│       ├── CalculateDistanceUseCase.kt
│       ├── CalculateChannelRatingUseCase.kt
│       ├── CalculateWifiQualityScoreUseCase.kt
│       ├── ParsePhyCapabilitiesUseCase.kt
│       └── RunSpeedTestUseCase.kt
├── ui
│   ├── components
│   │   ├── BlynkCard.kt
│   │   ├── BlynkSegmentedControl.kt
│   │   ├── BlynkMetricTile.kt
│   │   ├── ChannelGraphCanvas.kt
│   │   ├── MorphingHelpFab.kt
│   │   └── ContextualHelpDrawer.kt
│   ├── navigation
│   │   ├── BottomNavBar.kt
│   │   └── Screen.kt
│   ├── screens
│   │   ├── graph
│   │   │   ├── ChannelGraphScreen.kt
│   │   │   └── ChannelGraphViewModel.kt
│   │   ├── aplist
│   │   │   ├── ApListScreen.kt
│   │   │   └── ApListViewModel.kt
│   │   ├── rating
│   │   │   ├── ChannelRatingScreen.kt
│   │   │   └── ChannelRatingViewModel.kt
│   │   └── speedtest
│   │       ├── SpeedTestScreen.kt
│   │       └── SpeedTestViewModel.kt
│   └── theme
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── MainActivity.kt
```

### 4.2 Spesifikasi Build & Dependensi Android
- **Min SDK:** 26 (Android 8.0 Oreo - mencakup >95% smartphone aktif di pasaran).
- **Target & Compile SDK:** 34 (Android 14) / 35 (Android 15).
- **Kotlin & Compose BOM:** Kotlin 2.0+ / Compose BOM `2024.06.00+`.
- **Core Libraries:**
  - `androidx.core:core-ktx:1.13.1`
  - `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4`
  - `androidx.navigation:navigation-compose:2.7.7`
  - `com.squareup.okhttp3:okhttp:4.12.0` (Untuk streaming multi-thread engine Speedtest aktif)
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`


---

## 5. UI/UX Specification: Blynk.io Aesthetic & Eye Comfort

### 5.1 Palet Warna & Token Desain
- **Primary:** `#77ADF9` (Azure Blue Blynk)
- **Primary Light / Container:** `#EAF2FE` (Soft Blue tint)
- **Background Utama:** `#F5F8FC` (Soft Cloud Slate - anti-glare, ramah retina)
- **Dark Mode Background:** `#0F172A` (Deep Slate - mode malam minim emisi cahaya)
- **Surface Card:** `#FFFFFF` (Pristine White, border 1dp `#E2E8F0`, corner radius 16dp)
- **Text Primary:** `#1E293B` (Slate 800)
- **Text Secondary:** `#64748B` (Slate 500)
- **Curve Accent Harmonies:**
  - Blue (`#77ADF9`)
  - Emerald Mint (`#34D399`)
  - Soft Amber (`#FBBF24`)
  - Purple Lavender (`#A78BFA`)
  - Rose Coral (`#F87171`)

### 5.2 Fitur Visual Khusus: "Connected AP Center Plumb-Line"
1. **Garis Vertikal Putus-Putus (Dashed Plumb-Line):**
   - Menembus dari batas atas kanvas grafik, melalui titik puncak kurva router yang sedang terhubung, hingga ke sumbu X.
   - Warna garis: Aksen `#77ADF9` dengan ketebalan 2dp dan pola *dash-gap* (8dp dash, 4dp gap).
2. **Pill Header di Puncak Garis:**
   - Badge mengapung: `● TERHUBUNG (JARINGAN SAYA)`.
3. **Tujuan Analisis:**
   - Mempermudah pengguna melihat posisi tinggi sinyalnya dibanding tetangga.
   - Memperjelas apakah ada kurva router tetangga yang menembus garis frekuensi router sendiri (indikator langsung *co-channel interference*).

### 5.3 Juicy Morphing Floating Help Assistant & Contextual Help Drawer
Fitur bantuan dinamis dengan animasi mikro (*micro-interactions*) yang halus dan responsif:

1. **State 1: Expanded Active FAB (Bulatan Besar + Ikon `?`)**
   - Diameter 56dp, melayang di kanan bawah layar.
   - Dipicu otomatis saat berpindah tab atau saat layar di-scroll oleh pengguna.
   - **Animasi Juicy:** Meluncur keluar dari pinggir bezel layar (*slide-in*), bertransformasi dari bulatan kecil menjadi bulatan besar dengan efek pegas/pantulan lembut (*scale-up overshoot* dengan `FastOutSlowInEasing`).
2. **State 2: Docked Mini Tab / Edge Drawer (Bulatan Kecil saat Idle)**
   - Jika pengguna diam (*idle* tanpa scroll atau perpindahan tab selama 3 detik), tombol otomatis mundur (*slide-docking*) ke tepi bezel layar dan mengecil (*scale-down*) menjadi bulatan kecil berdiameter ~24dp.
   - Desain ini menjaga tampilan tetap bersih (*unobtrusive*), tidak menutupi grafik parabola atau teks data.
3. **State 3: Tap Interaction & Contextual Help Drawer**
   - Mengetuk bulatan kecil di tepi layar akan membesarkannya kembali secara instan dan membuka **Contextual Help Drawer / Bottom Sheet**:
     - **Tab Grafik:** Panduan membaca sumbu frekuensi & dBm, arti lengkung parabola, formula Log-Distance Path Loss untuk estimasi jarak, dan fungsi garis vertikal AP terkoneksi.
     - **Tab Daftar AP:** Panduan membaca BSSID MAC, arti RSSI, perbedaan kapasitas link radio (PHY Rate) vs internet ISP, serta enkripsi keamanan.
     - **Tab Rating:** Panduan pemilihan kanal non-overlapping (1, 6, 11) dan rekomendasi menghindari interferensi.
     - **Tab Speedtest:** Panduan evaluasi throughput internet (Mbps), latensi (ping), dan kestabilan koneksi (jitter).

### 5.4 Widget Kontrol Kalibrasi Lingkungan Adaptif (Environment Preset Selector)
Widget segmented pill compact yang diletakkan di header Tab Daftar AP dan Pengaturan:
- **Pilihan Preset:** `[ 🌳 Outdoor (n=2.0) ]  [ 🏢 Indoor (n=2.8) ]  [ 🧱 Beton (n=3.5) ]`
- Mengubah konstanta eksponen redaman $n$ secara instan pada `CalculateDistanceUseCase`.
- Nilai estimasi jarak `~meter` di seluruh kurva parabola dan kartu daftar AP diperbarui secara real-time dan reaktif via StateFlow.

---

## 6. Manajemen Hardware Android & Permissions

### 6.1 Deklarasi Izin Android (`AndroidManifest.xml`)
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.NEARBY_WIFI_DEVICES` (Android 13+ / API 33)
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.CHANGE_WIFI_STATE`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

### 6.2 Penanganan Wi-Fi Scan Throttling
1. Menggunakan data caching di `WifiScannerRepository` untuk memastikan grafik parabola tidak pernah berkedip (*flicker-free*).
2. Menyediakan tombol pemicu manual (*Pull-to-refresh* / *Scan Button*) dengan rate-limiter debounced.
3. Menyertakan petunjuk pengembang di menu pengaturan untuk mematikan *Wi-Fi scan throttling* di *Developer Options* smartphone guna demonstrasi live tanpa batas jeda.

---

## 7. Rencana Repositori GitHub & Lisensi Open Source

Sebelum eksekusi implementasi kode:
1. Inisialisasi Git lokal di `c:\my_project\project_weFi`.
2. Menyusun `README.md` berstandar internasional dengan deskripsi teknis, diagram alur arsitektur, dan panduan kompilasi.
3. Menyiapkan lisensi `LICENSE` (MIT License).
4. Membuat repositori publik di GitHub pengguna (`viPipar/weFi` atau nama yang disetujui) dan menghubungkan remote origin.

---

## 8. Strategi Verifikasi & Testing

1. **Unit Testing Domain Models:**
   - Uji matematis rumus `CalculateDistanceUseCase`: Verifikasi hasil jarak pada berbagai input RSSI ($-40\text{ dBm} \rightarrow 1.0\text{m}$, $-70\text{ dBm} \rightarrow \approx 11.8\text{m}$ pada $n=2.8$).
   - Uji pemetaan kanal frekuensi (2412 MHz $\rightarrow$ Ch 1, 5180 MHz $\rightarrow$ Ch 36).
   - Uji algoritma penalti rating kanal.
2. **UI & Canvas Rendering Verification:**
   - Memastikan kurva Bezier ter-render mulus tanpa memory leak pada recomposition tinggi.
   - Memastikan plumb-line vertikal hanya muncul pada AP yang sedang terkoneksi.
   - Memastikan tema warna putih-biru `#77ADF9` konsisten di seluruh layar.
3. **Hardware Integration Testing:**
   - Menghubungkan ke perangkat fisik Android (atau emulator dengan Wi-Fi scan mock) untuk memverifikasi penerimaan `BroadcastReceiver` dan penanganan izin runtime.
