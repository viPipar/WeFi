# WeFi Around Check, Golden Time & UI/UX Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Memperbaiki bug spam dialog Circuit Breaker pada Mode DFS, mengharmonisasikan kepatuhan Golden Time di seluruh layer scanner/connector, memperbaiki keandalan tombol refresh/scan, merombak layout UI/UX tab Around Check agar daftar router memiliki ruang scroll yang lega (collapsible control panel + weighted viewport), dan menerapkan perbaikan best practices Android.

**Architecture:** Menerapkan single-prompt suspending Circuit Breaker di `AroundCheckViewModel`, menghapus singleton teardown pada `onCleared()` untuk mencegah scan receiver mati, mengaktifkan smart cache refresh pada tombol SCAN saat cooldown, menyatukan interval Golden Time adaptif, dan merefaktor `AroundCheckScreen.kt` dengan accordion compact panel agar `LazyColumn` mendapatkan alokasi ruang utama (65-80% tinggi layar).

**Tech Stack:** Kotlin Coroutines (StateFlow, CompletableDeferred), Jetpack Compose (Material3, AnimatedVisibility, LazyColumn weight), Android WifiManager, JUnit4.

## Global Constraints
- **Preserve Business Logic:** Alur pemindaian, pemilihan kandidat, pengujian BFS/DFS, dan pencatatan audit log tidak boleh dirusak.
- **UI Standard:** Mematuhi pedoman `clean-ui-procedural` dan Blynk.io Clean Slate (`#77ADF9`, soft tints, zero gaudy neon borders, zero WordArt typography).
- **Single Source of Truth:** Singleton scanner dan connector di level aplikasi tidak boleh dimatikan oleh lifecycle `ViewModel.onCleared()`.

---

### Task 1: Fix Circuit Breaker Spam di AroundCheckViewModel (Tanya 1 Kali & Suspend Loop)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Produces: `hasCircuitBreakerAcknowledged: StateFlow<Boolean>`, `acknowledgeCircuitBreaker(continueTraversal: Boolean)` dengan penyelesaian `CompletableDeferred`.

- [ ] **Step 1: Tulis unit test untuk verifikasi Circuit Breaker hanya bertanya 1 kali**
Tambahkan test `startDfsTraversal_whenCircuitBreakerTriggered_promptsOnlyOnceAndRespectsDecision()` di `AroundCheckViewModelTest.kt`.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (RED)**
Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest"`

- [ ] **Step 3: Implementasikan perbaikan Circuit Breaker di AroundCheckViewModel.kt**
1. Tambahkan variabel flag `var circuitBreakerAcknowledged = false`.
2. Gunakan `CompletableDeferred<Boolean>` saat memicu dialog agar traversal loop benar-benar di-pause (`await()`).
3. Saat user menekan "Lanjutkan", deferred selesai dengan `true`, `circuitBreakerAcknowledged = true`, dan pengujian melanjutkan sisa password tanpa pernah memunculkan dialog lagi.
4. Saat user menekan "Hentikan", deferred selesai dengan `false`, loop dihentikan via `cancelTraversal()`.

- [ ] **Step 4: Jalankan test untuk memverifikasi kelulusan (GREEN)**
Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest"`

---

### Task 2: Perbaiki Tombol Refresh & Cegah Kematian Singleton Scanner (Lifecycle Fix)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt:484-489`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt:1094-1116`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Produces: `fun triggerSmartRefresh(): Unit` (memicu `startScan()` jika cooldown = 0, atau memicu `refreshFromCache()` dan snackbar edukatif jika sedang masa cooldown).

- [ ] **Step 1: Tulis unit test untuk triggerSmartRefresh**
Uji bahwa saat scanner dalam cooldown, `triggerSmartRefresh()` memanggil `refreshFromCache()` dan memancarkan event snackbar tanpa macet.

- [ ] **Step 2: Hapus pemanggilan scanner.teardown() dan connector.teardown() dari onCleared()**
Hapus `scanner.teardown()` dan `connector.teardown()` dari `AroundCheckViewModel.onCleared()` agar singleton scanner tidak mati saat navigasi/recreate.

- [ ] **Step 3: Hubungkan tombol SCAN di AroundCheckScreen.kt ke smart refresh**
Ubah tombol SCAN agar selalu dapat diklik:
- Jika sedang cooldown: panggil `viewModel.triggerSmartRefresh()` (mengambil cache instan dan memberi tahu user).
- Jika siap: panggil pemindaian aktif `startScan()`.

- [ ] **Step 4: Verifikasi dengan unit test**
Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest"`

---

### Task 3: Harmonisasi Golden Time Rate Limiting di Seluruh Repositori

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt:59, 104-112, 137-142`
- Test: `app/src/test/java/com/wefi/analyzer/domain/repository/WifiScannerLoopTest.kt`

**Interfaces:**
- Consumes: `WifiScanThrottler`
- Produces: Pemindaian yang mematuhi batas 4 scan / 120 detik, interval minimal adaptif 20-30s.

- [ ] **Step 1: Sesuaikan MIN_SCAN_INTERVAL_MS pada WifiScannerRepositoryImpl.kt**
Ubah `MIN_SCAN_INTERVAL_MS` dari 6000L (6 detik) menjadi 20000L (20 detik Golden Time), dan perbarui dari cache OS jika scan dipanggil sebelum interval selesai.

- [ ] **Step 2: Jalankan test repository untuk memastikan tidak ada pemblokiran**
Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.repository.WifiScannerLoopTest"`

---

### Task 4: Perombakan UI/UX Around Check (Collapsible Control Panel & Expansive Scroll List)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`

**Interfaces:**
- Produces: `isControlPanelExpanded: StateFlow<Boolean>`, `toggleControlPanelExpanded()`, `Modifier.weight(1f)` pada LazyColumn.

- [ ] **Step 1: Tambahkan state isControlPanelExpanded di AroundCheckViewModel**
Sediakan `_isControlPanelExpanded = MutableStateFlow(true)` dan toggle-nya.

- [ ] **Step 2: Desain ulang kartu atas (BFS/DFS Panel) dengan mode Lipat / Perluas**
Pada `AroundCheckScreen.kt`:
1. Buat header panel kontrol memiliki tombol lipat (*expand/collapse chevron*).
2. Ketika dilipat (*collapsed*), kartu hanya memakan tinggi ~48 dp dengan ringkasan mode dan tombol aksi cepat.
3. Ketika diperluas (*expanded*), seluruh kontrol input dan CSV tampil rapi.
4. Otomatis dilipat saat traversal aktif dimulai agar layar fokus 100% ke router yang sedang diuji!

- [ ] **Step 3: Perbaiki layout constraint LazyColumn**
Ubah `LazyColumn` agar menggunakan `Modifier.weight(1f).fillMaxWidth()`. Ini menjamin `LazyColumn` mendapatkan 65-80% tinggi layar ponsel di semua ukuran perangkat!

- [ ] **Step 4: Validasi kompilasi dan preview Jetpack Compose**
Run: `.\gradlew.bat testDebugUnitTest`

---

### Task 5: Perbaikan Best Practices AndroidManifest (Feature & Location Flag)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:7-10`

- [ ] **Step 1: Hapus neverForLocation dari NEARBY_WIFI_DEVICES**
Hapus `android:usesPermissionFlags="neverForLocation"` agar Android 13/14 tidak menyembunyikan hasil scan Wi-Fi.

- [ ] **Step 2: Tambahkan deklarasi uses-feature untuk Wi-Fi hardware**
Tambahkan `<uses-feature android:name="android.hardware.wifi" android:required="true" />`.

- [ ] **Step 3: Bangun APK debug penuh untuk verifikasi**
Run: `.\gradlew.bat assembleDebug`
