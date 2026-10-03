# Network Security Assessment & Surface Reconnaissance Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan fitur mutakhir **Network Security Assessment & Surface Reconnaissance Suite** yang memberikan kemampuan pemetaan postur keamanan nirkabel (WPS, PMF 802.11w, TKIP, Rogue AP) serta pemetaan aset bernilai tinggi (*High-Value Assets*) dan protokol manajemen tanpa enkripsi (*Cleartext Services*) di dalam subnet LAN.

**Architecture:** Menggunakan Clean Architecture berbasis Kotlin Coroutines & StateFlow. Lapisan domain membedah kapabilitas beacon frame dan mengklasifikasikan aset subnet. Lapisan presentasi mengadopsi tampilan tab ganda (LAN Surface vs Wireless Recon) dengan bar ringkasan eksekutif dan ekspor laporan terpadu sesuai standar `clean-ui-procedural`.

**Tech Stack:** Kotlin 1.9+, Android SDK 34, Jetpack Compose, Material 3, Coroutines, StateFlow, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-03-security-assessment-suite-design.md`

## Global Constraints
- Android Min SDK 24, Target SDK 34.
- 100% Kotlin coroutines idiomatic, thread-safe, non-blocking.
- Desain UI/UX mematuhi `clean-ui-procedural`: zero neon borders, tipografi kontras tinggi, palet Blynk IoT (#77ADF9, #F8FAFC, #FFFFFF), safe-zone padding.
- Bahasa UI: Bahasa Indonesia profesional, jelas, dan rapi.
- Verifikasi wajib sebelum klaim selesai: Unit tests pass (`testDebugUnitTest`) dan APK assemble pass (`assembleDebug`).

---

### Task 1: Buat Model Keamanan Nirkabel & Parser Heuristik

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/WirelessSecurityAudit.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/WifiAccessPoint.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/model/WirelessSecurityAuditTest.kt`

**Interfaces:**
- Produces: `PmfMode`, `WirelessSecurityAuditItem`, fungsi pembantu `evaluateWirelessSecurity(capabilities: String, ssid: String, bssid: String, allAps: List<WifiAccessPoint>)`.

- [x] **Step 1: Tulis unit test untuk verifikasi parser kapabilitas keamanan**
- [x] **Step 2: Jalankan test untuk memverifikasi kegagalan awal (TDD)**
- [x] **Step 3: Implementasikan model & parser di `WirelessSecurityAudit.kt`**
- [x] **Step 4: Tambahkan properti kapabilitas pada `WifiAccessPoint.kt`**
- [x] **Step 5: Jalankan test untuk memverifikasi kelulusan**
- [x] **Step 6: Commit perubahan (`a9ef828`)**

---

### Task 2: Integrasikan Ekstraksi Kapabilitas di `WifiScannerRepositoryImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt`

**Interfaces:**
- Memetakan `capabilities`, `hasWps`, `pmfMode`, `hasInsecureCipher` langsung saat mapping `ScanResult` ke `WifiAccessPoint`.

- [x] **Step 1: Update `mapScanResultToDomain` di `WifiScannerRepositoryImpl.kt`**
- [x] **Step 2: Jalankan unit test `WifiScannerRepositoryImplTest`**
- [x] **Step 3: Commit perubahan (`71918c8`)**

---

### Task 3: Implementasikan Klasifikasi Aset Kritis & Deteksi Port Cleartext di `NetworkDiscoveryRepositoryImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/NetworkDiscoveryLogicTest.kt`

**Interfaces:**
- Produces: `AssetCategory`, properti `assetCategory: AssetCategory` dan `hasCleartextManagement: Boolean` di `DiscoveredHost`.

- [x] **Step 1: Tambahkan enum `AssetCategory` dan properti di `DiscoveredHost`**
- [x] **Step 2: Implementasikan penentuan `AssetCategory` & `hasCleartextManagement` di `NetworkDiscoveryRepositoryImpl.kt`**
- [x] **Step 3: Tulis unit test di `NetworkDiscoveryLogicTest.kt`**
- [x] **Step 4: Jalankan unit test dan commit (`c8b0a73`)**

---

### Task 4: Integrasikan Security Recon State di `NetworkDiscoveryViewModel`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryUiState.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`

**Interfaces:**
- Consumes: `WifiScannerRepository` (opsional atau dipassingkan) / scan data.
- Produces: `selectedTab: StateFlow<SecurityReconTab>`, `reconStatSummary: StateFlow<ReconStatSummary>`, filter aksi.

- [x] **Step 1: Tulis unit test untuk ViewModel**
- [x] **Step 2: Implementasikan state & filter di `NetworkDiscoveryViewModel.kt`**
- [x] **Step 3: Jalankan unit test dan commit (`641ca67`)**

---

### Task 5: Implementasikan Tampilan Matriks Recon di `NetworkDiscoveryScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`

**Interfaces:**
- Menampilkan Tab Switcher: "LAN Attack Surface" vs "Wireless Recon Matrix".
- Bar Ringkasan Statistik Eksekutif (WPS, No PMF, Cleartext, Host Kritis).
- Kartu Recon Nirkabel dengan badge indikator severitas tinggi.
- Desain bersih sesuai `clean-ui-procedural`.

- [x] **Step 1: Implementasikan komponen UI di `NetworkDiscoveryScreen.kt`**
- [x] **Step 2: Kompilasi dan verifikasi dengan unit test**
- [x] **Step 3: Commit perubahan (`adbde48`)**

---

### Task 6: Verifikasi Menyeluruh & Finalisasi (/goal)

**Files:**
- Seluruh modul proyek.

- [x] **Step 1: Jalankan seluruh rangkaian unit test proyek**
  Run: `.\gradlew.bat testDebugUnitTest --no-daemon` -> Result: PASS 100%.
- [x] **Step 2: Kompilasi APK debug**
  Run: `.\gradlew.bat assembleDebug --no-daemon` -> Result: BUILD SUCCESSFUL.
- [x] **Step 3: Periksa git status dan finalisasi commit**
- [x] **Step 4: Sampaikan laporan akhir dan sertakan penanda penyelesaian `/goal`**
