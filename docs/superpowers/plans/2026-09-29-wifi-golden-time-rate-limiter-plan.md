# Wi-Fi Golden Time Strategy & Rate Limiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan strategi "Golden Time" pada tab Around Check: kepatuhan rate limit scan resmi Android (adaptif 20s-30s, max 4x/2 menit), golden time koneksi manual (cooldown 5s SSID sama, 3s SSID berbeda, max 3x/menit per SSID), shared NetworkCallback dengan dual timeout (30s approval, 15s handshake), audit logger tanpa password, dan UI real-time countdown.

**Architecture:**
- **Throttling Layer:** `WifiScanThrottler` (tracking 4x/120s sliding window, backoff 20s->30s jika false) dan `WifiConnectThrottler` (tracking cooldown 5s/3s dan sliding window 3x/60s).
- **Audit Layer:** `WifiAuditLogger` (menyimpan riwayat koneksi dengan timestamp, status, alasan penolakan, tanpa password).
- **Data Layer:** `WifiScannerImpl` (integrasi throttler, timestamp cache, deteksi throttle setting) dan `WifiConnectorImpl` (shared callback, dual timeout 30s/15s, pencatatan audit log).
- **UI Layer:** `AroundCheckViewModel` (ticker countdown 1s, distinctUntilChanged, debounce password) dan `AroundCheckScreen` (countdown tombol, badge status, bottom sheet audit log, stable keys).

**Tech Stack:** Kotlin Coroutines (`StateFlow`, `distinctUntilChanged`, `Flow`), Android `WifiManager`, `ConnectivityManager`, Jetpack Compose Material3, JUnit 4.

---

### Task 1: Domain Models & Throttlers

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/WifiAuditLogEntry.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/util/WifiScanThrottler.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/util/WifiConnectThrottler.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiAuditLogger.kt`
- Create: `app/src/test/java/com/wefi/analyzer/domain/util/WifiScanThrottlerTest.kt`
- Create: `app/src/test/java/com/wefi/analyzer/domain/util/WifiConnectThrottlerTest.kt`

**Steps:**
- [x] 1.1 Buat `WifiAuditLogEntry.kt` dan interface `WifiAuditLogger.kt`.
- [x] 1.2 Buat `WifiScanThrottler.kt` dengan aturan 20s/30s dan 4x per 120s sliding window.
- [x] 1.3 Buat unit test `WifiScanThrottlerTest.kt`.
- [x] 1.4 Buat `WifiConnectThrottler.kt` dengan aturan jeda 5s sama, 3s berbeda, 3x per 60s per SSID, dan 5s post-failure.
- [x] 1.5 Buat unit test `WifiConnectThrottlerTest.kt`.
- [x] 1.6 Jalankan `./gradlew testDebugUnitTest` untuk memverifikasi throttler logic.

---

### Task 2: Integrasi Scanner & Connector dengan Throttler

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerImpl.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/WifiAuditLoggerImpl.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/data/repository/WifiScannerImplTest.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt`

**Steps:**
- [x] 2.1 Buat `WifiAuditLoggerImpl.kt`.
- [x] 2.2 Update `WifiScannerImpl.kt`:
  - Integrasikan `WifiScanThrottler`.
  - Simpan `lastScanTimestamp` dan paparkan waktu sejak scan terakhir.
  - Implementasikan deteksi `Settings.Global.WIFI_SCAN_THROTTLE_ENABLED`.
  - Pastikan `scanResults` cache selalu tersedia tanpa menunggu throttle.
- [x] 2.3 Update `WifiConnectorImpl.kt`:
  - Integrasikan `WifiConnectThrottler` dan `WifiAuditLogger`.
  - Shared `ConnectivityManager.NetworkCallback`.
  - Dual timeout: 30s untuk user approval, 15s untuk handshake.
  - Log audit setiap hasil koneksi (Tersambung, Ditolak, Timeout, Gagal).
- [x] 2.4 Jalankan `./gradlew testDebugUnitTest` untuk memverifikasi scanner & connector.

---

### Task 3: ViewModel, UI & MainActivity Integration

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Steps:**
- [x] 3.1 Update `AroundCheckViewModel`:
  - UI countdown dynamic via pure throttler.
  - Expose status audit log dan dialog audit history.
- [x] 3.2 Update `AroundCheckScreen`:
  - Tampilkan label "Data dari X detik lalu" dengan real-time LaunchedEffect ticker.
  - Tampilkan countdown pada tombol scan saat cooldown (misal: "SCAN (14s)").
  - Tampilkan countdown pada tombol connect saat cooldown SSID.
  - Bottom sheet riwayat audit koneksi.
- [x] 3.3 Update `MainActivity.kt` untuk meneruskan logger dan throttler.
- [x] 3.4 Update `AroundCheckViewModelTest.kt` dan jalankan seluruh unit tests lokal.

---

### Task 4: Verifikasi, Build APK & GitHub Release

**Files:**
- All modified and created files.

**Steps:**
- [x] 4.1 Jalankan `./gradlew testDebugUnitTest` lokal (100% pass).
- [x] 4.2 Jalankan `./gradlew assembleDebug` lokal.
- [ ] 4.3 Git commit dan push ke `origin/main`.
- [ ] 4.4 Verifikasi pipeline GitHub Actions CI/CD sukses dan rilis APK `v1.0.0` terbarui.
