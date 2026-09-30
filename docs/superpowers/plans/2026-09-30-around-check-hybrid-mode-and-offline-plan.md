# Around Check Mode Hybrid (DFS + BFS) & Jaminan 100% Offline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menambahkan Mode Hybrid (DFS + BFS) pada tab Around Check untuk penelusuran sekuensial multi-router otomatis dengan template praktikum 56 kata sandi, badge hasil visual per router (Found/NotFound/Testing), penyimpanan Vault otomatis, serta memastikan aplikasi 100% stabil dan toleran saat offline di jaringan lab tanpa koneksi WAN.

**Architecture:** 
- State Machine Traversal di `AroundCheckViewModel` yang mengorkestrasi penelusuran DFS (56 passphrase) pada setiap router secara berurutan (BFS level router).
- Model status reaktif `HybridRouterStatus` yang disimpan dalam `StateFlow<Map<String, HybridRouterStatus>>` per BSSID untuk mempertahankan badge kartu router selama sesi aplikasi.
- Handler offline `SpeedTestRepositoryImpl` yang mendeteksi ketiadaan gateway WAN dan secara elegan menghasilkan state `SpeedTestStage.OFFLINE_LAB_MODE` tanpa crash atau unhandled network exceptions.
- Desain antarmuka berstandar `clean-ui-procedural` dan Blynk.io: segmented tab switcher 3 opsi, kartu kontrol accordion, dan soft pill badges (`#DCFCE7` hijau untuk kata sandi cocok, `#F1F5F9` slate untuk tidak cocok, `BlynkBlueTint` untuk progress live).

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines (Flow / StateFlow), OkHttp, JUnit 4, AndroidX Test.

**Spec:** [Speksifikasi Desain: Around Check Mode Hybrid (DFS + BFS) & Jaminan 100% Offline](file:///c:/my_project/project_weFi/docs/superpowers/specs/2026-09-30-around-check-hybrid-mode-and-offline-design.md)

## Global Constraints

- **Pedoman UI:** Wajib mematuhi `clean-ui-procedural` dan Blynk token style (`BlynkBlue` #77ADF9, `BlynkBlueTint` #EBF2FE, soft green #DCFCE7 / #15803D, soft slate #F1F5F9 / #64748B, rounded corners 9.dp - 16.dp, zero neon borders).
- **Proteksi Hardware:** Jeda pacing 2-3 detik antar passphrase pada router yang sama, dan quench delay `connector.cancel()` 500ms sebelum percobaan baru untuk melindungi HAL driver Wi-Fi dan router lab.
- **Toleransi 100% Offline:** Seluruh fungsi pemindaian Wi-Fi, kalkulasi skor sinyal, asosiasi WPA2/WPA3, dan audit logging beroperasi lokal tanpa panggilan server luar. Speed Test menangani ketiadaan internet WAN secara elegan tanpa exception.
- **TDD & Kerapian Kode:** Setiap perubahan logika wajib diawali dengan unit test yang gagal (Red), implementasi minimal (Green), lalu refactor dan commit terpisah.

---

### Task 1: Domain Models (HybridRouterStatus, AroundCheckMode & SpeedTestStage)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/HybridRouterStatus.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/AroundCheckMode.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/SpeedTestMetrics.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/model/HybridRouterStatusTest.kt`

**Interfaces:**
- Consumes: Tidak ada.
- Produces:
  ```kotlin
  package com.wefi.analyzer.domain.model

  sealed interface HybridRouterStatus {
      object Idle : HybridRouterStatus
      data class Testing(val currentPasswordIndex: Int, val totalPasswords: Int) : HybridRouterStatus
      data class Found(val workingPassword: String) : HybridRouterStatus
      data class NotFound(val testedCount: Int) : HybridRouterStatus
      data class VerifiedFromVault(val workingPassword: String) : HybridRouterStatus
  }

  enum class AroundCheckMode {
      BFS,
      DFS,
      HYBRID
  }

  enum class SpeedTestStage {
      IDLE,
      PING,
      DOWNLOAD,
      UPLOAD,
      FINISHED,
      ERROR,
      OFFLINE_LAB_MODE
  }
  ```

- [ ] **Step 1: Tulis unit test yang gagal (Red)**

Buat file `app/src/test/java/com/wefi/analyzer/domain/model/HybridRouterStatusTest.kt`:
```kotlin
package com.wefi.analyzer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRouterStatusTest {

    @Test
    fun hybridRouterStatus_sealedTypes_instantiateCorrectly() {
        val idle: HybridRouterStatus = HybridRouterStatus.Idle
        val testing: HybridRouterStatus = HybridRouterStatus.Testing(currentPasswordIndex = 5, totalPasswords = 56)
        val found: HybridRouterStatus = HybridRouterStatus.Found(workingPassword = "ilmukomputeripb")
        val notFound: HybridRouterStatus = HybridRouterStatus.NotFound(testedCount = 56)
        val vault: HybridRouterStatus = HybridRouterStatus.VerifiedFromVault(workingPassword = "admin")

        assertTrue(idle is HybridRouterStatus.Idle)
        assertEquals(5, (testing as HybridRouterStatus.Testing).currentPasswordIndex)
        assertEquals(56, testing.totalPasswords)
        assertEquals("ilmukomputeripb", (found as HybridRouterStatus.Found).workingPassword)
        assertEquals(56, (notFound as HybridRouterStatus.NotFound).testedCount)
        assertEquals("admin", (vault as HybridRouterStatus.VerifiedFromVault).workingPassword)
    }

    @Test
    fun aroundCheckMode_containsHybrid() {
        val modes = AroundCheckMode.values().map { it.name }
        assertTrue(modes.contains("HYBRID"))
        assertTrue(modes.contains("BFS"))
        assertTrue(modes.contains("DFS"))
    }

    @Test
    fun speedTestStage_containsOfflineLabMode() {
        val stages = SpeedTestStage.values().map { it.name }
        assertTrue(stages.contains("OFFLINE_LAB_MODE"))
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan kompilasi/eksekusi (Red)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.domain.model.HybridRouterStatusTest`
Expected: FAIL (unresolved reference `HybridRouterStatus`, `HYBRID`, `OFFLINE_LAB_MODE`).

- [ ] **Step 3: Implementasi model minimal (Green)**

1. Buat `app/src/main/java/com/wefi/analyzer/domain/model/HybridRouterStatus.kt`:
```kotlin
package com.wefi.analyzer.domain.model

sealed interface HybridRouterStatus {
    object Idle : HybridRouterStatus
    data class Testing(val currentPasswordIndex: Int, val totalPasswords: Int) : HybridRouterStatus
    data class Found(val workingPassword: String) : HybridRouterStatus
    data class NotFound(val testedCount: Int) : HybridRouterStatus
    data class VerifiedFromVault(val workingPassword: String) : HybridRouterStatus
}
```

2. Ubah `app/src/main/java/com/wefi/analyzer/domain/model/AroundCheckMode.kt`:
```kotlin
package com.wefi.analyzer.domain.model

enum class AroundCheckMode {
    BFS,    // 1 Password -> Multi Router
    DFS,    // Multi Password -> 1 Router
    HYBRID  // Multi Password -> Multi Router (DFS + BFS)
}
```

3. Perbarui `app/src/main/java/com/wefi/analyzer/domain/model/SpeedTestMetrics.kt`:
Tambahkan `OFFLINE_LAB_MODE` pada enum `SpeedTestStage`.

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan (Pass)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.domain.model.HybridRouterStatusTest`
Expected: BUILD SUCCESSFUL (3 tests passed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/HybridRouterStatus.kt app/src/main/java/com/wefi/analyzer/domain/model/AroundCheckMode.kt app/src/main/java/com/wefi/analyzer/domain/model/SpeedTestMetrics.kt app/src/test/java/com/wefi/analyzer/domain/model/HybridRouterStatusTest.kt
git commit -m "feat(domain): add HybridRouterStatus, AroundCheckMode.HYBRID and SpeedTestStage.OFFLINE_LAB_MODE"
```

---

### Task 2: SpeedTest Offline Lab Mode Support (100% Offline Resilience)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SpeedTestRepository`, `SpeedTestMetrics`, `SpeedTestStage`.
- Produces: `runSpeedTest(): Flow<SpeedTestMetrics>` memancarkan `SpeedTestStage.OFFLINE_LAB_MODE` jika semua endpoint HTTPS RTT tidak dapat dihubungi (kondisi lab Wi-Fi terisolasi tanpa WAN).

- [ ] **Step 1: Tulis unit test yang gagal (Red)**

Buat file `app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImplTest.kt`:
```kotlin
package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.SpeedTestStage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.IOException
import java.net.UnknownHostException

class SpeedTestRepositoryImplTest {

    @Test
    fun runSpeedTest_whenOfflineWithUnknownHost_emitsOfflineLabModeGracefully() = runBlocking {
        val mockClient = mock(OkHttpClient::class.java)
        val mockCall = mock(Call::class.java)
        `when`(mockClient.newCall(org.mockito.ArgumentMatchers.any(Request::class.java))).thenReturn(mockCall)
        `when`(mockCall.execute()).thenThrow(UnknownHostException("No address associated with hostname"))

        val repo = SpeedTestRepositoryImpl(mockClient)
        val metricsList = repo.runSpeedTest().toList()

        val lastMetric = metricsList.last()
        assertEquals(SpeedTestStage.OFFLINE_LAB_MODE, lastMetric.stage)
        assertFalse(lastMetric.isRunning)
        assertEquals(1.0f, lastMetric.progress)
        assertTrue(lastMetric.errorMessage?.contains("Offline") == true || lastMetric.errorMessage?.contains("Lab") == true)
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (Red)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.SpeedTestRepositoryImplTest`
Expected: FAIL (sebelumnya `runSpeedTest` tetap melanjutkan ke download/upload atau emit FINISHED dengan ping 35.0).

- [ ] **Step 3: Implementasi deteksi offline lab mode di SpeedTestRepositoryImpl (Green)**

Modifikasi `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`:
- Lacak jumlah endpoint ping yang berhasil dikontak (`successfulPings`).
- Jika setelah mencoba seluruh endpoint tidak ada 1 pun yang berhasil terhubung karena `UnknownHostException` / network failure (`successfulPings == 0`):
```kotlin
if (successfulPings == 0) {
    emit(
        currentMetrics.copy(
            isRunning = false,
            stage = SpeedTestStage.OFFLINE_LAB_MODE,
            progress = 1.0f,
            errorMessage = "Jaringan Lokal Lab - Tidak Ada Akses Internet Luar (100% Offline)"
        )
    )
    return@flow
}
```

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan (Pass)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.SpeedTestRepositoryImplTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImplTest.kt
git commit -m "feat(network): handle offline lab mode in SpeedTestRepositoryImpl gracefully"
```

---

### Task 3: AroundCheckViewModel Hybrid Mode Engine with TDD

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `HybridRouterStatus`
  - `AroundCheckMode.HYBRID`
  - `VerifiedWifiStore`
  - `WifiScanner`, `WifiConnector`
- Produces:
  - `hybridRouterStatuses: StateFlow<Map<String, HybridRouterStatus>>`
  - `hybridCsvInput: StateFlow<String>`
  - `isHybridCsvVisible: StateFlow<Boolean>`
  - `fun setHybridCsvInput(input: String)`
  - `fun applyHybridPracticumTemplate()`
  - `fun toggleHybridCsvVisibility()`
  - `fun startHybridTraversal()`
  - `fun cancelTraversal()` (mereset router yang sedang `Testing` ke `Idle`, menjaga router `Found`/`NotFound`)

- [ ] **Step 1: Tulis 4 unit test yang gagal untuk Hybrid Mode (Red)**

Tambahkan test berikut ke `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`:

1. `startHybridTraversal_findsPasswordOnFirstRouter_andProceedsToSecondRouter()`:
   - Setup 2 router: `Router-A` (bssid: `11:22:33:44:55:01`) dan `Router-B` (bssid: `11:22:33:44:55:02`).
   - Masukkan 3 passphrase: `wrongPass1`, `secretMatch`, `extraPass3`.
   - Jalankan `startHybridTraversal()`.
   - Router-A: percobaan 1 gagal (`wrongPass1`), percobaan 2 berhasil (`secretMatch`).
   - Verifikasi: Router-A status berubah jadi `HybridRouterStatus.Found("secretMatch")`, disimpan ke Vault, loop passphrase Router-A berhenti (tidak menguji `extraPass3`), dan traversal berlanjut ke Router-B.

2. `startHybridTraversal_whenAllPasswordsFail_setsNotFoundBadge_andProceedsToNextRouter()`:
   - Setup 2 router: `Router-A` dan `Router-B`.
   - Router-A gagal untuk semua passphrase.
   - Verifikasi: Router-A berstatus `HybridRouterStatus.NotFound(testedCount = 3)`, traversal berlanjut ke Router-B.

3. `startHybridTraversal_skipsAlreadyVerifiedVaultRouters()`:
   - Setup Router-A sudah ada di `fakeVerifiedStore` dengan password `vaultPassword123`.
   - Jalankan `startHybridTraversal()`.
   - Verifikasi: Router-A langsung diberi status `HybridRouterStatus.VerifiedFromVault("vaultPassword123")` tanpa memanggil `fakeConnector.connect` sama sekali untuk Router-A, dan langsung menguji Router-B.

4. `cancelTraversal_haltsHybridTraversalImmediately_andResetsTestingRouterToIdle()`:
   - Traversal sedang berjalan di Router-A pada password ke-2.
   - Panggil `cancelTraversal()`.
   - Verifikasi: `isSequentialTesting` false, status Router-A kembali ke `Idle`, konektor dibatalkan.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan (Red)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest.startHybridTraversal*`
Expected: FAIL (method belum ada atau logic belum diimplementasikan).

- [ ] **Step 3: Implementasi logika Traversal Hybrid pada AroundCheckViewModel (Green)**

Modifikasi `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`:
1. Tambahkan state reaktif:
```kotlin
private val _hybridRouterStatuses = MutableStateFlow<Map<String, HybridRouterStatus>>(emptyMap())
val hybridRouterStatuses: StateFlow<Map<String, HybridRouterStatus>> = _hybridRouterStatuses.asStateFlow()

private val _hybridCsvInput = MutableStateFlow(DFS_PRACTICUM_TEMPLATE)
val hybridCsvInput: StateFlow<String> = _hybridCsvInput.asStateFlow()

private val _isHybridCsvVisible = MutableStateFlow(false)
val isHybridCsvVisible: StateFlow<Boolean> = _isHybridCsvVisible.asStateFlow()
```
2. Tambahkan fungsi helper kontrol UI Hybrid:
```kotlin
fun setHybridCsvInput(input: String) {
    _hybridCsvInput.value = input
}

fun applyHybridPracticumTemplate() {
    _hybridCsvInput.value = DFS_PRACTICUM_TEMPLATE
    sendSnackbar("Template praktikum dimuat (56 kata sandi)")
}

fun toggleHybridCsvVisibility() {
    _isHybridCsvVisible.value = !_isHybridCsvVisible.value
}
```
3. Implementasikan `startHybridTraversal()`:
   - Validasi: periksa apakah traversal sedang aktif, daftar router kosong, atau passphrase kosong.
   - Loop sekuensial router dengan proteksi:
     - Jika router sudah terverifikasi di vault (`verifiedStore?.isRouterVerified(...) == true`):
       Set status `_hybridRouterStatuses` untuk BSSID tersebut ke `HybridRouterStatus.VerifiedFromVault(verifiedPassword)`. Lanjut router berikutnya.
     - Jika router berkeamanan `OPEN`:
       Set status ke `HybridRouterStatus.Found("")`. Lanjut router berikutnya.
     - Jika tidak:
       Iterasi passphrase:
       - Update status router ke `HybridRouterStatus.Testing(passIndex + 1, validPasswords.size)`.
       - Pacing delay 2 detik jika `passIndex > 0`.
       - Quench cancel 500ms.
       - Panggil `connector.connect(candidate.ssid, password, candidate.security)`.
       - Tunggu result state via `connectState.first { ... }`.
       - Jika `Connected`:
         Simpan ke vault, update status ke `HybridRouterStatus.Found(password)`, emit goal snackbar/banner, dan `break` loop passphrase (langsung lanjut ke router berikutnya!).
       - Jika semua passphrase selesai diuji tanpa hasil:
         Update status ke `HybridRouterStatus.NotFound(validPasswords.size)`.
     - Delay antar router 2 detik sebelum router selanjutnya.
4. Perbarui `cancelTraversal()`:
   - Jika ada router dengan status `HybridRouterStatus.Testing`, kembalikan menjadi `HybridRouterStatus.Idle`.
   - Router yang sudah `Found`, `NotFound`, atau `VerifiedFromVault` tetap dipertahankan.

- [ ] **Step 4: Jalankan unit test untuk memverifikasi kelulusan (Pass)**

Run: `./gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest`
Expected: ALL TESTS PASSING (28+ tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt
git commit -m "feat(aroundcheck): implement hybrid traversal engine with reactive router status badges"
```

---

### Task 4: UI Updates (Mode Switcher, Hybrid Control Panel, Badges & SpeedTest UI)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt`

**Interfaces:**
- Consumes:
  - `AroundCheckMode.HYBRID`
  - `HybridRouterStatus`
  - `hybridRouterStatuses`, `hybridCsvInput`, `isHybridCsvVisible`
  - `SpeedTestStage.OFFLINE_LAB_MODE`
- Produces:
  - 3-tab segmented switcher (`Mode BFS`, `Mode DFS`, `Mode Hybrid`).
  - Panel kontrol Mode Hybrid dengan tombol "Muat Template Praktikum", ringkasan counter passphrase dan router target, serta tombol eksekusi "Mulai Traversal Hybrid" / "Hentikan".
  - Kartu router `WifiScanItemCard` yang menampilkan badge hasil:
    - 🟢 Found: `#DCFCE7` soft green, kata sandi, tombol salin clipboard.
    - ⚪ NotFound: `#F1F5F9` soft slate, *"56 Passphrase Tidak Cocok"*.
    - 🔵 Testing: `BlynkBlueTint`, mini progress ring *"Menguji [K/56]..."*.
  - `SpeedTestScreen` menampilkan chip status "MODE LAB OFFLINE" saat pengujian offline tanpa melempar crash.

- [ ] **Step 1: Perbarui Segmented Switcher & Tambahkan Panel Kontrol Mode Hybrid**

Di `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`:
1. Tambahkan tab ke-3 "Mode Hybrid" pada segmented control:
   - Ikon `Icons.Rounded.Hub` atau `Icons.Rounded.AccountTree` / `Icons.Rounded.AltRoute`.
   - Warna aktif `BlynkBlue`, inaktif transparan.
2. Tambahkan blok `if (selectedMode == AroundCheckMode.HYBRID)`:
   - Header ringkasan: jumlah passphrase valid dan router lab di sekitar.
   - Tombol cepat "Muat Template Praktikum (56 Kata Sandi)" dengan ikon `Icons.Rounded.PlaylistAddCheck`.
   - Collapsible input field CSV Passphrase (dengan toggle sembunyikan/tampilkan).
   - Tombol Aksi Utama: "Mulai Traversal Hybrid" (atau "Hentikan Traversal" saat sedang berjalan).

- [ ] **Step 2: Perbarui WifiScanItemCard untuk menampilkan Badge Hybrid**

Tambahkan parameter `hybridStatus: HybridRouterStatus? = null` pada `WifiScanItemCard`.
Di dalam kartu router, render badge status:
```kotlin
when (hybridStatus) {
    is HybridRouterStatus.Found, is HybridRouterStatus.VerifiedFromVault -> {
        val pwd = if (hybridStatus is HybridRouterStatus.Found) hybridStatus.workingPassword else (hybridStatus as HybridRouterStatus.VerifiedFromVault).workingPassword
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFDCFCE7),
            border = BorderStroke(1.dp, Color(0xFF86EFAC))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Password: $pwd", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Salin",
                    tint = Color(0xFF15803D),
                    modifier = Modifier.size(13.dp).clickable { onCopyVerifiedPassword?.invoke(pwd) }
                )
            }
        }
    }
    is HybridRouterStatus.NotFound -> {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${hybridStatus.testedCount} Passphrase Tidak Cocok", fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }
    }
    is HybridRouterStatus.Testing -> {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BlynkBlueTint,
            border = BorderStroke(1.dp, BlynkBlue.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = BlynkBlue)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Menguji [${hybridStatus.currentPasswordIndex}/${hybridStatus.totalPasswords}]...", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BlynkBlueDark)
            }
        }
    }
    else -> {}
}
```

- [ ] **Step 3: Update SpeedTestScreen untuk Stage OFFLINE_LAB_MODE**

Di `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt`:
Tambahkan penanganan stage `SpeedTestStage.OFFLINE_LAB_MODE` pada pill status dan teks keterangan:
- Teks pill: `"MODE LAB OFFLINE"`
- Teks banner / error jika `metrics.stage == SpeedTestStage.OFFLINE_LAB_MODE`: menampilkan pesan informatif ramah: *"Jaringan lab beroperasi secara lokal tanpa koneksi WAN. Seluruh fitur pemindaian dan Around Check berfungsi 100% offline."*

- [ ] **Step 4: Jalankan verifikasi build lokal**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt
git commit -m "feat(ui): add Mode Hybrid switcher, control panel, router badges, and offline lab mode display"
```

---

### Task 5: Full Verification, APK Build & CI/CD Pipeline Push

**Files:**
- All files across the project.

**Interfaces:**
- Seluruh unit test suite dan Gradle compilation.

- [ ] **Step 1: Jalankan seluruh Unit Test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL (100% tests passing, zero failures).

- [ ] **Step 2: Bangun APK Android Debug**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, menghasilkan APK siap pakai.

- [ ] **Step 3: Push commit ke GitHub origin main**

Run: `git push origin main`
Expected: Push sukses ke remote repository.

- [ ] **Step 4: Pantau CI/CD GitHub Actions hingga selesai**

Gunakan browser subagent atau gh tool untuk memantau status run GitHub Actions workflow `Android CI` hingga status **SUCCESS**.
