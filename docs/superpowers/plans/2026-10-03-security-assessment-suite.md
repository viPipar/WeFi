# Network Security Assessment & Surface Reconnaissance Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan fitur mutakhir **Network Security Assessment & Surface Reconnaissance Suite** yang memberikan kemampuan pemetaan postur keamanan nirkabel (WPS, PMF 802.11w, TKIP, Rogue AP) serta pemetaan aset bernilai tinggi (*High-Value Assets*) dan protokol manajemen tanpa enkripsi (*Cleartext Services*) di dalam subnet LAN.

**Architecture:** Menggunakan Clean Architecture berbasis Kotlin Coroutines & StateFlow. Lapisan domain membedah kapabilitas beacon frame dan mengklasifikasikan aset subnet. Lapisan presentasi mengadopsi tampilan tab ganda (LAN Surface vs Wireless Recon) dengan bar ringkasan eksekutif dan ekspor laporan terpadu sesuai standar `clean-ui-procedural`.

**Tech Stack:** Kotlin 1.9+, Android SDK 34, Jetpack Compose, Material 3, Coroutines, StateFlow, JUnit 4, MockK.

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

- [ ] **Step 1: Tulis unit test untuk verifikasi parser kapabilitas keamanan**

Di `WirelessSecurityAuditTest.kt`:
Menguji deteksi `[WPS]`, `[PMF]`, `[TKIP]`, dan deteksi kembaran SSID dengan BSSID berbeda.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan awal (TDD)**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.model.WirelessSecurityAuditTest"`
Expected: FAIL.

- [ ] **Step 3: Implementasikan model & parser di `WirelessSecurityAudit.kt`**

- [ ] **Step 4: Tambahkan properti kapabilitas pada `WifiAccessPoint.kt`**

- [ ] **Step 5: Jalankan test untuk memverifikasi kelulusan**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.model.WirelessSecurityAuditTest"`
Expected: PASS.

- [ ] **Step 6: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/WirelessSecurityAudit.kt app/src/main/java/com/wefi/analyzer/domain/model/WifiAccessPoint.kt app/src/test/java/com/wefi/analyzer/domain/model/WirelessSecurityAuditTest.kt
git commit -m "feat: add wireless security audit models and capabilities parser"
```

---

### Task 2: Integrasikan Ekstraksi Kapabilitas di `WifiScannerRepositoryImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt:215-248`

**Interfaces:**
- Memetakan `capabilities`, `hasWps`, `pmfMode`, `hasInsecureCipher` langsung saat mapping `ScanResult` ke `WifiAccessPoint`.

- [ ] **Step 1: Update `mapScanResultToDomain` di `WifiScannerRepositoryImpl.kt`**

- [ ] **Step 2: Jalankan unit test `WifiScannerRepositoryImplTest`**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.data.repository.WifiScannerRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 3: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt
git commit -m "feat: extract WPS, PMF, and cipher capabilities in WifiScannerRepositoryImpl"
```

---

### Task 3: Implementasikan Klasifikasi Aset Kritis & Deteksi Port Cleartext di `NetworkDiscoveryRepositoryImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/NetworkDiscoveryLogicTest.kt`

**Interfaces:**
- Produces: `AssetCategory`, properti `assetCategory: AssetCategory` dan `hasCleartextManagement: Boolean` di `DiscoveredHost`.

- [ ] **Step 1: Tambahkan enum `AssetCategory` dan properti di `DiscoveredHost`**

- [ ] **Step 2: Implementasikan penentuan `AssetCategory` & `hasCleartextManagement` di `NetworkDiscoveryRepositoryImpl.kt`**

- [ ] **Step 3: Tulis unit test di `NetworkDiscoveryLogicTest.kt`**

- [ ] **Step 4: Jalankan unit test dan commit**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.data.repository.NetworkDiscoveryLogicTest"`
Expected: PASS.

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/NetworkDiscoveryLogicTest.kt
git commit -m "feat: add high-value asset categorization and cleartext port detection"
```

---

### Task 4: Integrasikan Security Recon State di `NetworkDiscoveryViewModel`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`

**Interfaces:**
- Consumes: `WifiScannerRepository` (opsional atau dipassingkan) / scan data.
- Produces: `activeTab: StateFlow<SecurityReconTab>`, `reconStatSummary: StateFlow<ReconStatSummary>`, filter aksi.

- [ ] **Step 1: Tulis unit test untuk ViewModel**

- [ ] **Step 2: Implementasikan state & filter di `NetworkDiscoveryViewModel.kt`**

- [ ] **Step 3: Jalankan unit test dan commit**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.discovery.NetworkDiscoveryViewModelTest"`
Expected: PASS.

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt
git commit -m "feat: integrate security recon state and filters in NetworkDiscoveryViewModel"
```

---

### Task 5: Implementasikan Tampilan Matriks Recon di `NetworkDiscoveryScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`

**Interfaces:**
- Menampilkan Tab Switcher: "LAN Attack Surface" vs "Wireless Recon Matrix".
- Bar Ringkasan Statistik Eksekutif (WPS, No PMF, Cleartext, Host Kritis).
- Kartu Recon Nirkabel dengan badge indikator severitas tinggi.
- Desain bersih sesuai `clean-ui-procedural`.

- [ ] **Step 1: Implementasikan komponen UI di `NetworkDiscoveryScreen.kt`**

- [ ] **Step 2: Kompilasi dan verifikasi dengan unit test**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt
git commit -m "feat: implement dual-layer security recon matrix and stat summary in NetworkDiscoveryScreen"
```

---

### Task 6: Verifikasi Menyeluruh & Finalisasi (/goal)

**Files:**
- Seluruh modul proyek.

- [ ] **Step 1: Jalankan seluruh rangkaian unit test proyek**
Run: `.\gradlew.bat testDebugUnitTest --no-daemon`
Expected: Seluruh unit test PASS 100%.

- [ ] **Step 2: Kompilasi APK debug**
Run: `.\gradlew.bat assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Periksa git status dan finalisasi commit**
Run: `git status`
Expected: Working tree clean.

- [ ] **Step 4: Sampaikan laporan akhir dan sertakan penanda penyelesaian `/goal`**
