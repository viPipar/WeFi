# Official Android Wi-Fi Scan & Suggestion Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan fitur pemindaian Wi-Fi resmi (`WifiManager.startScan()`) dan koneksi manual berdialog persetujuan OS resmi (`WifiNetworkSuggestion` API 29+ & `ConnectivityManager.NetworkCallback`) pada tab **Around Check**, tanpa loop otomatis, tanpa bypass, dan tanpa penyimpanan plaintext password.

**Architecture:** Mengikuti Clean Architecture & MVVM Compose:
- **Domain Layer:** `WifiScanItem`, `WifiSecurityType`, `WifiScanState`, `WifiConnectState`, `WifiConnectStatus`, serta interface `WifiScanner` dan `WifiConnector`.
- **Data Layer:** `WifiScannerImpl` (menangani `WifiManager` scan, broadcast receiver `RECEIVER_NOT_EXPORTED`, deduplikasi SSID terkuat, pengecekan lokasi aktif) dan `WifiConnectorImpl` (menangani `WifiNetworkSuggestion.Builder` dengan `.setIsUserInteractionRequired(true)`, `ConnectivityManager.NetworkCallback` untuk deteksi persetujuan/penolakan OS, timeout 30 detik coroutine, dan `removeNetworkSuggestions`).
- **UI Layer:** `AroundCheckViewModel` dan `AroundCheckScreen` berbasis tema Blynk IoT: tombol Scan, daftar SSID dengan badge keamanan & RSSI, dialog input password aman, status interaktif (Menunggu persetujuan user, Tersambung, Ditolak, Gagal, Timeout), tombol Batalkan, dan tombol Lupakan Jaringan.

**Tech Stack:** Kotlin Coroutines (`StateFlow`, `callbackFlow`), Android `WifiManager`, `WifiNetworkSuggestion` (API 29+), `ConnectivityManager`, Jetpack Compose (Material3, Blynk palette), JUnit 4, Kotlinx Coroutines Test.

---

## Global Constraints

- Android API: `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Permission: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `NEARBY_WIFI_DEVICES` (dengan `android:usesPermissionFlags="neverForLocation"`), `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`.
- Dialog Persetujuan OS: Wajib dipicu oleh `WifiNetworkSuggestion.Builder` dengan `.setIsUserInteractionRequired(true)` dan `.setIsAppInteractionRequired(true)`. Tidak ada otomasi atau bypass dialog OS.
- Single Connection Action: Hanya menghubungkan satu SSID per aksi user. Dilarang loop otomatis ke banyak SSID.
- Zero Plaintext Leakage: Password tidak pernah disimpan ke persistent storage (SharedPreferences/Database/File) dan tidak dicatat di Logcat.
- UI Design: Mematuhi Blynk IoT theme (Kartu Putih murni, aksen `#77ADF9`, font Plus Jakarta Sans, Material Icons).

---

### Task 1: Update AndroidManifest.xml & Domain Models

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/WifiScanItem.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/WifiConnectState.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiScanner.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiConnector.kt`

**Steps:**
- [ ] 1.1 Update `AndroidManifest.xml` dengan `usesPermissionFlags="neverForLocation"` pada `NEARBY_WIFI_DEVICES`.
- [ ] 1.2 Buat `WifiScanItem.kt` yang memuat data class scan item, enum `WifiSecurityType`, dan sealed interface `WifiScanState`.
- [ ] 1.3 Buat `WifiConnectState.kt` yang memuat enum `WifiConnectStatus` dan data class `WifiConnectState`.
- [ ] 1.4 Buat interface `WifiScanner.kt` dan `WifiConnector.kt`.
- [ ] 1.5 Jalankan `./gradlew compileDebugKotlin` untuk memastikan tidak ada syntax error.

---

### Task 2: Implementasi WifiScannerImpl & Unit Test

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerImpl.kt`
- Create: `app/src/test/java/com/wefi/analyzer/data/repository/WifiScannerImplTest.kt`

**Steps:**
- [ ] 2.1 Buat implementasi `WifiScannerImpl`:
  - Deteksi status lokasi aktif via `LocationManager.isLocationEnabled()`.
  - Pendaftaran receiver dengan `ContextCompat.RECEIVER_NOT_EXPORTED` (atau `RECEIVER_EXPORTED` sesuai versi OS).
  - Pemicu scan `WifiManager.startScan()`.
  - Pemrosesan `scanResults`: buang SSID kosong/blank, group by SSID, ambil RSSI terkuat, dan parsing security capability (WPA3, WPA2, Open).
- [ ] 2.2 Buat unit test `WifiScannerImplTest.kt` untuk parsing security, grouping SSID terkuat, dan penanganan lokasi nonaktif.
- [ ] 2.3 Jalankan `./gradlew testDebugUnitTest` dan pastikan lulus.

---

### Task 3: Implementasi WifiConnectorImpl & Unit Test

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`
- Create: `app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt`

**Steps:**
- [ ] 3.1 Buat implementasi `WifiConnectorImpl`:
  - Membangun `WifiNetworkSuggestion.Builder`:
    - `.setSsid(ssid)`
    - `.setWpa2Passphrase(password)` / `.setWpa3Passphrase(password)`
    - `.setIsAppInteractionRequired(true)`
    - `.setIsUserInteractionRequired(true)` (memicu dialog konfirmasi persetujuan OS)
  - Mendaftarkan `ConnectivityManager.registerNetworkCallback` dengan transport `TRANSPORT_WIFI`.
  - Menghandle `onAvailable()` -> `Connected`, `onUnavailable()` -> `Rejected / Failed`, `onLost()` -> `Failed`.
  - Timer timeout 30 detik untuk menunggu approval user; jika lewat -> `Timeout`.
  - Implementasikan `cancel()` dan `forgetNetwork(ssid)` via `WifiManager.removeNetworkSuggestions()`.
- [ ] 3.2 Buat unit test `WifiConnectorImplTest.kt` untuk simulasi status approval, rejection, dan timeout.
- [ ] 3.3 Jalankan `./gradlew testDebugUnitTest` dan pastikan lulus.

---

### Task 4: Integrasi ViewModel AroundCheckViewModel & Unit Test

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Steps:**
- [ ] 4.1 Update `AroundCheckViewModel`:
  - Injeksi `WifiScanner` dan `WifiConnector`.
  - Expose `scanState` dan `connectState`.
  - State dialog input password untuk target SSID terpilih.
  - Action `startScan()`, `connect(ssid, password)`, `cancelConnect()`, `forgetNetwork(ssid)`.
  - Guard agar tombol connect di-disable pada item yang sedang menunggu approval OS.
- [ ] 4.2 Update `AroundCheckViewModelTest.kt` untuk menguji alur scan, open password dialog, trigger connect, dan cancel.
- [ ] 4.3 Jalankan `./gradlew testDebugUnitTest` dan pastikan lulus.

---

### Task 5: Refactor UI AroundCheckScreen & DI di MainActivity

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`

**Steps:**
- [ ] 5.1 Update `AroundCheckScreen.kt`:
  - Banner peringatan jika lokasi sistem mati dengan tombol navigasi ke `Settings.ACTION_LOCATION_SOURCE_SETTINGS`.
  - Tombol Scan untuk me-refresh daftar.
  - Kartu daftar SSID: nama SSID, badge tipe keamanan (WPA3 / WPA2 / Open), kekuatan sinyal dBm, tombol Connect, status item (Menunggu persetujuan user..., Tersambung, Ditolak, Gagal, Timeout), tombol Batalkan, dan tombol Lupakan Jaringan.
  - Dialog input password dengan kolom masked, toggle tampilkan password, tombol Batal, dan tombol Sambungkan.
- [ ] 5.2 Inisialisasi `WifiScannerImpl` dan `WifiConnectorImpl` di `MainActivity.kt` dan teruskan ke `AroundCheckViewModel`.
- [ ] 5.3 Jalankan `./gradlew testDebugUnitTest` dan `./gradlew assembleDebug` secara lokal.

---

### Task 6: Verifikasi Akhir, Git Commit & CI/CD Release

**Files:**
- All modified and created files.

**Steps:**
- [ ] 6.1 Jalankan seluruh suite tes unit lokal: `./gradlew testDebugUnitTest`.
- [ ] 6.2 Bangun debug APK lokal: `./gradlew assembleDebug`.
- [ ] 6.3 Commit dan push ke `origin/main` untuk memicu GitHub Actions CI/CD.
- [ ] 6.4 Verifikasi status GitHub Actions hingga `completed / success` dan release `v1.0.0` terbarui.
- [ ] 6.5 Siapkan penjelasan lengkap untuk user:
  - API pemicu dialog OS.
  - Perilaku saat user menolak dialog.
  - Alasan teknis mengapa loop otomatis mustahil pada API resmi Android.
