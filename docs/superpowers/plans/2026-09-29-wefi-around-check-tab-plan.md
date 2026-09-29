# Around Check (Lab Router Connection Testing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menambahkan tab baru ke-5 bernama **"Around Check"** pada aplikasi WeFi untuk pengujian dan audit koneksi router laboratorium terotorisasi, dilengkapi whitelist scope lab, input query kredensial ter-masking, status indikator lengkap (Belum diuji, Sedang diuji, Cocok, Gagal, Tidak diizinkan, Error), konfirmasi otorisasi, dan audit log riwayat pengujian.

**Architecture:** Menerapkan Clean Architecture & MVVM Compose: `LabRouterAuditRepository` mengelola whitelist SSID lab yang sah, mengeksekusi simulasi/verifikasi koneksi bertahap yang terlindungi coroutine, serta mencatat `LabAuditLogEntry` tanpa membocorkan plaintext password. `AroundCheckViewModel` mengonsumsi scan results dari `WifiScannerRepository` dan status verifikasi, menyajikannya ke `AroundCheckScreen` berbasis tema Blynk IoT (Putih, Biru `#77ADF9`, Plus Jakarta Sans, dan Material vector icons).

**Tech Stack:** Kotlin 1.9, Jetpack Compose Material 3, StateFlow/Coroutines, JUnit 4.

---

## Global Constraints
- **Scope Restriction:** Hanya SSID yang terdaftar dalam whitelist laboratorium yang diizinkan untuk diuji. SSID di luar whitelist wajib ditandai `TIDAK DIIZINKAN`.
- **Zero Plaintext Leak:** Password atau query kredensial tidak boleh ditampilkan dalam plaintext di UI (default masked `••••••••`) dan tidak boleh dicatat dalam log mentah.
- **Wajib Konfirmasi Otorisasi:** Tombol uji hanya aktif jika operator mencentang konfirmasi izin resmi laboratorium.
- **Standar Visual Blynk IoT:** Palet Putih Bersih (`#FFFFFF`), kanvas `#F8FAFC`, aksen Biru Blynk (`#77ADF9`), font Plus Jakarta Sans, dan icon Material vector murni (tanpa emotikon/ASCII).

---

### Task 1: Domain Model & Interface Audit Router Lab

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/LabRouterAudit.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/LabRouterAuditRepository.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/repository/LabRouterAuditTest.kt`

**Interfaces:**
- `enum class LabAuditStatus { UNTESTED, TESTING, MATCHED, FAILED, UNAUTHORIZED, ERROR }`
- `data class LabAuditTarget(val ssid: String, val bssid: String, val isAuthorized: Boolean, val status: LabAuditStatus = LabAuditStatus.UNTESTED, val rssi: Int = 0)`
- `data class LabAuditLogEntry(val timestamp: Long, val targetSsid: String, val status: LabAuditStatus, val notes: String)`
- `interface LabRouterAuditRepository { val auditLogs: StateFlow<List<LabAuditLogEntry>>; fun isSsidAuthorized(ssid: String): Boolean; fun testRouterCredential(target: LabAuditTarget, candidateKey: String): Flow<LabAuditStatus>; fun recordLog(entry: LabAuditLogEntry) }`

- [x] **Step 1: Tulis unit test untuk verifikasi whitelist dan state audit**
- [x] **Step 2: Jalankan test dan pastikan gagal (kompilasi)**
- [x] **Step 3: Buat data class & enum di LabRouterAudit.kt**
- [x] **Step 4: Buat interface LabRouterAuditRepository.kt**
- [x] **Step 5: Jalankan test dan pastikan lulus**
- [x] **Step 6: Commit Task 1**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/LabRouterAudit.kt \
        app/src/main/java/com/wefi/analyzer/domain/repository/LabRouterAuditRepository.kt \
        app/src/test/java/com/wefi/analyzer/domain/repository/LabRouterAuditTest.kt
git commit -m "feat(audit): define LabRouterAudit domain models and repository interface"
```

---

### Task 2: Implementasi Repository Audit & Simulasi Kredensial Lab

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/LabRouterAuditRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/LabRouterAuditRepositoryImplTest.kt`

**Interfaces:**
- Menerapkan whitelist bawaan (`ilmukomputeripb`, `Lab-IoT-01`, `Lab-Jaringan-A`, `Lab-Riset-Wifi`, prefix `Lab-*`).
- Menjalankan eksekusi verifikasi bertahap dengan masking password.
- Mencatat riwayat audit log terenkripsi/ter-masking.

- [x] **Step 1: Tulis unit test untuk repository implementation**
- [x] **Step 2: Jalankan test dan pastikan gagal**
- [x] **Step 3: Implementasikan LabRouterAuditRepositoryImpl.kt**
- [x] **Step 4: Jalankan test dan pastikan lulus**
- [x] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/LabRouterAuditRepositoryImpl.kt \
        app/src/test/java/com/wefi/analyzer/data/repository/LabRouterAuditRepositoryImplTest.kt
git commit -m "feat(audit): implement LabRouterAuditRepository with strict whitelist boundary"
```

---

### Task 3: AroundCheckViewModel (State, Filter, dan Flow Eksekusi)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- `query: StateFlow<String>`
- `isAuthorizedConsentGiven: StateFlow<Boolean>`
- `isTestingInProgress: StateFlow<Boolean>`
- `targets: StateFlow<List<LabAuditTarget>>`
- `auditLogs: StateFlow<List<LabAuditLogEntry>>`
- `fun setQuery(q: String)`
- `fun setConsentGiven(value: Boolean)`
- `fun startAuditSearch()`
- `fun stopAudit()`

- [x] **Step 1: Tulis unit test untuk AroundCheckViewModel**
- [x] **Step 2: Implementasikan AroundCheckViewModel.kt**
- [x] **Step 3: Jalankan test dan pastikan lulus**
- [x] **Step 4: Commit Task 3**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt \
        app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt
git commit -m "feat(aroundcheck): implement AroundCheckViewModel state and audit orchestration"
```

---

### Task 4: Desain UI AroundCheckScreen (Blynk IoT Standard)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AuditLogBottomSheet.kt`

**Komponen UI:**
1. **Header Toolbar:** Judul `AROUND CHECK`, subtitle `Verifikasi Koneksi Lab Terotorisasi`, dan tombol riwayat audit log (`Icons.Rounded.History`).
2. **Input Query Card:**
   - Label uppercase: `INPUT QUERY (KREDENSIAL UJI LAB)`
   - Field password dengan visibility toggle (`Icons.Rounded.Visibility` / `VisibilityOff`).
   - Tombol `UJI SEKARANG` berlatar Biru Blynk (`#77ADF9`).
3. **Authorization Confirmation Card:**
   - Banner amber/biru dengan checkbox persetujuan: `"Saya menyatakan memiliki izin resmi dari pengelola laboratorium untuk menguji perangkat jaringan dalam scope ini."`
4. **Target Router List:**
   - Menampilkan router sekitar.
   - Badge status:
     - `Belum diuji`: Abu-abu
     - `Sedang diuji`: Biru Blynk dengan animasi circular progress
     - `Cocok`: Hijau (`QualityGreen`) dengan `Icons.Rounded.CheckCircle`
     - `Gagal`: Merah (`QualityRed`) dengan `Icons.Rounded.Cancel`
     - `Tidak diizinkan`: Slate (`#64748B`) dengan `Icons.Rounded.Block`
     - `Error`: Amber (`QualityAmber`) dengan `Icons.Rounded.ErrorOutline`

- [x] **Step 1: Buat AroundCheckScreen.kt dan komponen card target**
- [x] **Step 2: Buat AuditLogBottomSheet.kt untuk melihat log riwayat**
- [x] **Step 3: Commit Task 4**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt \
        app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AuditLogBottomSheet.kt
git commit -m "feat(aroundcheck): build AroundCheckScreen and AuditLogBottomSheet with Blynk IoT aesthetic"
```

---

### Task 5: Integrasi Navigasi 5 Tab di MainActivity & BottomNavBar

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/navigation/BottomNavBar.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`

**Interfaces:**
- Tambahkan `data object AroundCheck : Screen("around_check", "Around", Icons.Rounded.Security, 4)` di `Screen.kt`.
- Tambahkan rute `Screen.AroundCheck -> AroundCheckScreen(viewModel = aroundCheckViewModel)` di `MainActivity.kt`.

- [x] **Step 1: Update Screen.kt dengan rute Around Check**
- [x] **Step 2: Update BottomNavBar.kt agar proporsional untuk 5 tab**
- [x] **Step 3: Inisialisasi LabRouterAuditRepository dan AroundCheckViewModel di MainActivity.kt**
- [x] **Step 4: Commit Task 5**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/navigation/Screen.kt \
        app/src/main/java/com/wefi/analyzer/ui/navigation/BottomNavBar.kt \
        app/src/main/java/com/wefi/analyzer/MainActivity.kt
git commit -m "feat(nav): integrate 5th tab Around Check into navigation and MainActivity"
```

---

### Task 6: Verifikasi Penuh CI/CD dan Rilis APK Terbaru

**Files:**
- Modify: `docs/superpowers/plans/2026-09-29-wefi-around-check-tab-plan.md`

- [ ] **Step 1: Jalankan git push ke branch main**
- [ ] **Step 2: Pantau eksekusi GitHub Actions CI Run hingga seluruh test passing dan APK terbentuk**
- [ ] **Step 3: Verifikasi berkas APK terbaru di GitHub Releases v1.0.0**
- [ ] **Step 4: Laporkan kepada pengguna beserta tautan unduhan langsung**
