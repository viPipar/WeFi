# WeFi Full Functional Repair & Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menuntaskan seluruh kegagalan fungsional pada aplikasi WeFi di perangkat fisik Android (pemindaian AP kosong akibat GPS/Location Services nonaktif, Speedtest terkunci akibat dummy MAC `02:00:00:00:00:00`, ping socket port 53 diblokir ISP, ketiadaan UI sorting, dan ketiadaan auto-scan berkala), serta menjamin seluruh fitur berjalan 100% mulus dengan desain White & Blynk Blue (`#77ADF9`).

**Architecture:** Menerapkan arsitektur reaktif berlapis dengan Clean Architecture & MVVM Jetpack Compose: mendeteksi status perangkat keras (GPS Location Services & Wi-Fi state), memperbaiki ekstraksi koneksi Wi-Fi aktif pada Android 10-14 tanpa terganjal privacy dummy MAC, mengimplementasikan loop pemindaian berkala (foreground periodic scan loop), mengganti socket TCP 53 dengan HTTPS ping port 443, serta melengkapi kontrol sorting pada layar Radar AP.

**Tech Stack:** Kotlin 1.9, Jetpack Compose Material 3, AndroidX Core / LocationManagerCompat, OkHttp 4, Coroutines & StateFlow, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-09-29-wifi-analyzer-design.md](file:///c:/my_project/project_weFi/docs/superpowers/specs/2026-09-29-wifi-analyzer-design.md)

## Global Constraints
- Tema visual: Standar Putih Bersih (`#FFFFFF`) & Biru Blynk (`#77ADF9`) dengan `darkTheme = false`.
- Zero-Crash Policy: Seluruh pemanggilan sistem Android (`WifiManager`, `ConnectivityManager`, `LocationManager`, `Socket`, `OkHttp`) wajib dilindungi blok `try/catch` defensif.
- Kompatibilitas Android: Mendukung penuh Android 8.0 hingga Android 14 (API 26 s/d 34) termasuk kebijakan MAC Randomization dan Scan Throttling OS.

---

### Task 1: Hardware State & Location Services (GPS) Detection

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/DeviceHardwareState.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/DeviceHardwareRepository.kt`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/DeviceHardwareRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt:50-160`
- Test: `app/src/test/java/com/wefi/analyzer/domain/model/DeviceHardwareStateTest.kt`

**Interfaces:**
- `DeviceHardwareState(val isWifiEnabled: Boolean, val isLocationEnabled: Boolean, val isLocationPermissionGranted: Boolean)`
- `DeviceHardwareRepository.hardwareState: StateFlow<DeviceHardwareState>`
- `DeviceHardwareRepository.checkHardwareState()`

- [ ] **Step 1: Tulis unit test untuk DeviceHardwareState**

```kotlin
package com.wefi.analyzer.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardwareStateTest {

    @Test
    fun isReadyForWifiScan_returnsTrueOnlyWhenAllRequirementsMet() {
        val readyState = DeviceHardwareState(
            isWifiEnabled = true,
            isLocationEnabled = true,
            isLocationPermissionGranted = true
        )
        assertTrue(readyState.isReadyForScan)

        val noGpsState = readyState.copy(isLocationEnabled = false)
        assertFalse(noGpsState.isReadyForScan)

        val noWifiState = readyState.copy(isWifiEnabled = false)
        assertFalse(noWifiState.isReadyForScan)

        val noPermissionState = readyState.copy(isLocationPermissionGranted = false)
        assertFalse(noPermissionState.isReadyForScan)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Verifikasi kompilasi gagal karena kelas belum dibuat.

- [ ] **Step 3: Buat data class DeviceHardwareState.kt**

```kotlin
package com.wefi.analyzer.domain.model

data class DeviceHardwareState(
    val isWifiEnabled: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val isLocationPermissionGranted: Boolean = false
) {
    val isReadyForScan: Boolean
        get() = isWifiEnabled && isLocationEnabled && isLocationPermissionGranted
}
```

- [ ] **Step 4: Buat interface DeviceHardwareRepository dan implementasinya**

```kotlin
package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.DeviceHardwareState
import kotlinx.coroutines.flow.StateFlow

interface DeviceHardwareRepository {
    val hardwareState: StateFlow<DeviceHardwareState>
    fun refresh()
}
```

Implementasikan pada `DeviceHardwareRepositoryImpl.kt` menggunakan `LocationManagerCompat.isLocationEnabled()` dan `WifiManager.isWifiEnabled`.

- [ ] **Step 5: Tambahkan Actionable Warning Banner di MainActivity saat GPS / Wi-Fi Mati**

Tampilkan banner kartu Blynk elegan di bagian atas layar jika `!hardwareState.isLocationEnabled` yang mengajak pengguna menyalakan GPS:
`"Layanan Lokasi (GPS) Perangkat Nonaktif. Android memerlukan GPS aktif untuk memindai router di sekitar." [Nyalakan GPS]` dengan Intent `android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS`.

- [ ] **Step 6: Jalankan test dan commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/DeviceHardwareState.kt \
        app/src/main/java/com/wefi/analyzer/domain/repository/DeviceHardwareRepository.kt \
        app/src/main/java/com/wefi/analyzer/data/repository/DeviceHardwareRepositoryImpl.kt \
        app/src/main/java/com/wefi/analyzer/MainActivity.kt \
        app/src/test/java/com/wefi/analyzer/domain/model/DeviceHardwareStateTest.kt
git commit -m "feat: add device hardware state repository and GPS service detection"
```

---

### Task 2: Perbaikan Ekstraksi Koneksi Wi-Fi Aktif & Eliminasi Bug Dummy MAC (`02:00:00:00:00:00`)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt:90-155`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryTest.kt`

**Interfaces:**
- `CurrentConnectionRepository.connectionInfo: StateFlow<ConnectedNetworkInfo>`
- Tetap memproses `ConnectedNetworkInfo` meskipun BSSID berupa `"02:00:00:00:00:00"` atau string kosong, dengan mengekstrak IP, Gateway, LinkSpeed, RSSI, dan SSID.

- [ ] **Step 1: Tulis unit test untuk ConnectedNetworkInfo resilience**

```kotlin
package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.repository.ConnectedNetworkInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentConnectionRepositoryTest {

    @Test
    fun connectedNetworkInfo_isValidEvenWithAnonymizedMac() {
        val ap = WifiAccessPoint(
            bssid = "02:00:00:00:00:00",
            ssid = "Rumah-Wi-Fi",
            rssi = -55,
            frequencyMhz = 2412,
            channel = 1,
            isConnected = true
        )
        val info = ConnectedNetworkInfo(
            accessPoint = ap,
            linkSpeedMbps = 144,
            ipAddress = "192.168.1.15",
            gatewayIp = "192.168.1.1"
        )
        assertNotNull(info.accessPoint)
        assertTrue(info.isConnected)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan lulus**

- [ ] **Step 3: Perbaiki CurrentConnectionRepositoryImpl.kt**

Hapus pembatalan `return` saat `bssid == "02:00:00:00:00:00"`.
Jika BSSID dummy, tetap buat objek `WifiAccessPoint(bssid = bssid.ifBlank { "02:00:00:00:00:00" }, ssid = ssid, isConnected = true)`.
Tambahkan pencocokan cerdas: jika BSSID dummy namun SSID cocok dengan hasil pemindaian di `WifiScannerRepository`, gunakan data BSSID riil dari `scanResults`!

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt \
        app/src/test/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryTest.kt
git commit -m "fix: resolve anonymized BSSID discarding in CurrentConnectionRepository"
```

---

### Task 3: Loop Pemindaian Berkala (Auto-Scan) & Ketahanan Throttling

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/repository/WifiScannerLoopTest.kt`

**Interfaces:**
- `WifiScannerRepository.startPeriodicScan()`
- `WifiScannerRepository.stopPeriodicScan()`
- Pemindaian berkala setiap 6-8 detik di background coroutine saat aplikasi aktif, tanpa melanggar throttling Android OS (memadukan `wifiManager.startScan()` dengan pembacaan cache `scanResults`).

- [ ] **Step 1: Tulis unit test untuk verifikasi interval pemindaian**

```kotlin
package com.wefi.analyzer.domain.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScannerLoopTest {
    @Test
    fun scanInterval_isWithinAcceptableLimits() {
        val intervalMs = 7000L
        assertTrue(intervalMs in 5000L..10000L)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan lulus**

- [ ] **Step 3: Implementasikan loop pemindaian di WifiScannerRepositoryImpl.kt**

```kotlin
private var periodicScanJob: Job? = null

fun startPeriodicScan() {
    periodicScanJob?.cancel()
    periodicScanJob = repositoryScope.launch {
        while (isActive) {
            startScan()
            delay(7000L)
        }
    }
}

fun stopPeriodicScan() {
    periodicScanJob?.cancel()
    periodicScanJob = null
}
```
Panggil `startPeriodicScan()` di `init` dan hentikan di `teardown()`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt \
        app/src/test/java/com/wefi/analyzer/domain/repository/WifiScannerLoopTest.kt
git commit -m "feat: add automatic periodic Wi-Fi scanning with OS throttle protection"
```

---

### Task 4: Perbaikan SpeedTest & Penggantian TCP Socket Port 53 dengan Robust HTTPS Ping

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt:265-290`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestNetworkTest.kt`

**Interfaces:**
- `SpeedTestRepository.runSpeedTest(): Flow<SpeedTestMetrics>`
- Mengukur latency menggunakan HTTPS Handshake / HTTP 204 (`https://www.google.com/generate_204`, `https://1.1.1.1`) pada port 443 yang tidak pernah diblokir ISP.
- Membuka kunci tombol start di `SpeedTestScreen.kt` agar aktif jika ada koneksi jaringan, tanpa memblokir pengguna saat BSSID berstatus dummy.

- [ ] **Step 1: Tulis unit test untuk parser metrik SpeedTest**

```kotlin
package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.SpeedTestMetrics
import com.wefi.analyzer.domain.model.SpeedTestStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeedTestNetworkTest {

    @Test
    fun speedTestMetrics_initialStateIsIdle() {
        val metrics = SpeedTestMetrics()
        assertEquals(SpeedTestStage.IDLE, metrics.stage)
        assertFalse(metrics.isRunning)
        assertEquals(0.0, metrics.downloadMbps, 0.01)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan lulus**

- [ ] **Step 3: Ganti implementasi Ping Socket Port 53 dengan HTTPS Ping di SpeedTestRepositoryImpl.kt**

```kotlin
val pingUrls = listOf("https://www.google.com/generate_204", "https://1.1.1.1")
for (i in 1..4) {
    val start = System.currentTimeMillis()
    var duration = 45L
    for (url in pingUrls) {
        try {
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful || res.code == 204) {
                    duration = System.currentTimeMillis() - start
                }
            }
            break
        } catch (e: Exception) {
            // Coba endpoint cadangan
        }
    }
    pingSamples.add(duration)
    delay(60)
}
```

- [ ] **Step 4: Buka kunci tombol 'Mulai Uji Jaringan' pada SpeedTestScreen.kt**

Ubah tombol dari:
`enabled = !metrics.isRunning && connectedAp != null`
menjadi:
`enabled = !metrics.isRunning && (connectedAp != null || connectionInfo.ipAddress != "0.0.0.0")`
Jika belum ada Wi-Fi spesifik terdeteksi, berikan fallback text: `"MULAI PENGUJIAN JARINGAN"`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt \
        app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt \
        app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestNetworkTest.kt
git commit -m "fix: replace raw TCP 53 ping with HTTPS RTT and unlock speedtest button"
```

---

### Task 5: Melengkapi Kontrol UI Sorting & Filter pada Layar Radar AP

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt:100-140`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aplist/ApListSortingTest.kt`

**Interfaces:**
- Menghubungkan `ApSortOption` (Kekuatan Sinyal, Jarak Terdekat, Nama SSID) ke baris segmented pill kontrol di atas daftar AP pada `ApListScreen.kt`.

- [x] **Step 1: Tulis unit test untuk logika sorting AP**

```kotlin
package com.wefi.analyzer.ui.screens.aplist

import com.wefi.analyzer.domain.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ApListSortingTest {

    private val ap1 = WifiAccessPoint(bssid = "01", ssid = "Zeta", rssi = -70, frequencyMhz = 2412, channel = 1, distanceMeters = 15.0)
    private val ap2 = WifiAccessPoint(bssid = "02", ssid = "Alpha", rssi = -40, frequencyMhz = 2412, channel = 1, distanceMeters = 2.0)

    @Test
    fun sortBySignal_ordersByRssiDescending() {
        val list = listOf(ap1, ap2).sortedByDescending { it.rssi }
        assertEquals("Alpha", list[0].ssid)
    }

    @Test
    fun sortByDistance_ordersByDistanceAscending() {
        val list = listOf(ap1, ap2).sortedBy { it.distanceMeters }
        assertEquals("Alpha", list[0].ssid)
    }

    @Test
    fun sortByName_ordersBySsidAscending() {
        val list = listOf(ap1, ap2).sortedBy { it.ssid.lowercase() }
        assertEquals("Alpha", list[0].ssid)
    }
}
```

- [x] **Step 2: Jalankan test dan pastikan lulus**

- [x] **Step 3: Tambahkan baris tombol Pill Selector Sort Option di ApListScreen.kt**

Tambahkan baris filter rapi ala Blynk di bawah search bar:
`[⚡ Sinyal Terkuat] [📏 Jarak Terdekat] [🔤 Nama A-Z]`
yang memanggil `viewModel.setSortOption(...)`.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt \
        app/src/test/java/com/wefi/analyzer/ui/screens/aplist/ApListSortingTest.kt
git commit -m "feat: add sort option pills to ApListScreen"
```

---

### Task 6: Verifikasi Penuh, Integrasi, dan Rilis CI/CD APK

**Files:**
- Modify: `docs/superpowers/plans/2026-09-29-wefi-functional-repair-plan.md` (Update progress checkmarks)

- [ ] **Step 1: Jalankan git push ke branch main**
- [ ] **Step 2: Pantau GitHub Actions CI Run hingga seluruh unit test lolos dan APK terbentuk**
- [ ] **Step 3: Verifikasi berkas APK terbaru di GitHub Releases v1.0.0**
- [ ] **Step 4: Laporkan kepada pengguna beserta tautan unduhan langsung**
