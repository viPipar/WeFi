# weFi (Mobile Wi-Fi Analyzer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun aplikasi Android native (Kotlin + Jetpack Compose) "weFi" untuk analisis spektrum Wi-Fi sekitar dengan grafik parabola kanvas, estimasi jarak matematis (Log-Distance Path Loss), indikator garis vertikal router terkoneksi, parsing kapasitas radio pasif (PHY Rate), speedtest aktif, floating help assistant juicy, dan UI modern Blynk.io Style (Putih-Biru `#77ADF9`).

**Architecture:** Clean Architecture + MVVM + Kotlin Coroutines / StateFlow. Logika perambatan gelombang radio dan formula jaringan diisolasi murni di Domain Layer dengan Test-Driven Development (TDD). Tampilan dibangun dengan Jetpack Compose kustom (Custom Canvas Bezier) dan desain ramah mata (*anti-glare*).

**Tech Stack:** Kotlin 2.0, Android SDK 34 (Min SDK 26), Jetpack Compose BOM 2024.06.00+, OkHttp 4.12.0, Kotlin Coroutines, Navigation Compose, JUnit4 / MockK.

**Spec:** [docs/superpowers/specs/2026-09-29-wifi-analyzer-design.md](file:///c:/my_project/project_weFi/docs/superpowers/specs/2026-09-29-wifi-analyzer-design.md)

## Global Constraints

- **Platform Target:** Android Native murni (Kotlin + Jetpack Compose). Tidak menggunakan wrapper non-native.
- **UI Design System:** Blynk.io Style dengan palet Putih & Biru (`#77ADF9`), Soft Cloud Slate (`#F5F8FC`) & Soft Dark Slate (`#0F172A`). Mematuhi standar `clean-ui-procedural` (tanpa border pelangi murahan, tipografi kontras tinggi, border radius konsisten 16dp, zero AI-slop).
- **Scanning Mode:** Pure Live Hardware Scan via Android `android.net.wifi.WifiManager` (tanpa data simulasi/dummy generator).
- **Connected Indicator:** Garis vertikal putus-putus (*dashed line*) di tengah parabola AP yang sedang terkoneksi dengan badge `● TERHUBUNG (JARINGAN SAYA)`.
- **Help Button:** Juicy Morphing Floating Button dengan animasi *ease-in-ease-out* (`FastOutSlowInEasing`), membesar saat tab ganti / scroll, auto-docking ke pinggir layar saat idle 3 detik, membuka Contextual Help Drawer saat di-tap.
- **Open Source Gate:** Repositori publik GitHub harus dibuat dan dihubungkan sebelum eksekusi kompilasi kode utama.

---

### Task 1: GitHub Public Repository & Open Source Scaffolding

**Files:**
- Create: `LICENSE`
- Create: `README.md`
- Create: `.gitignore`

**Interfaces:**
- Produces: Git repository terhubung ke remote public GitHub (`viPipar/weFi`) dengan lisensi MIT dan dokumentasi resmi.

- [ ] **Step 1: Buat berkas lisensi MIT**

```text
MIT License

Copyright (c) 2026 viPipar (rafifilmanyy@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: Buat berkas .gitignore standar Android & Jetpack Compose**

```gitignore
*.iml
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.apk
*.aab
```

- [ ] **Step 3: Buat README.md standar internasional dengan dokumentasi Komunikasi Data**

Tulis `README.md` yang merinci latar belakang tugas Komdat, diagram OSI layer, rumus Log-Distance Path Loss, arsitektur Clean Architecture, screenshot mockup Blynk.io, dan panduan instalasi.

- [ ] **Step 4: Commit dan dorong ke GitHub Repository Publik**

Periksa ketersediaan GitHub CLI (`gh`) atau buat repositori publik `weFi` di akun `viPipar`, hubungkan remote origin, dan push branch `main`.

```bash
git add LICENSE .gitignore README.md
git commit -m "chore: initialize open-source repository with MIT license and README"
```

---

### Task 2: Android Project Structure & Gradle Configuration

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (Root)
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`

**Interfaces:**
- Produces: Konfigurasi build Gradle fungsional untuk Android 14/15, Compose BOM, OkHttp, Coroutines, dan JUnit.

- [ ] **Step 1: Buat `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "weFi"
include(":app")
```

- [ ] **Step 2: Buat root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
```

- [ ] **Step 3: Buat `app/build.gradle.kts` dengan dependensi lengkap**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wefi.analyzer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wefi.analyzer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 4: Commit build config**

```bash
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts app/proguard-rules.pro
git commit -m "build: setup Android Gradle project configuration with Jetpack Compose"
```

---

### Task 3: Domain Models & Log-Distance Path Loss Calculator (TDD)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/EnvironmentPreset.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/WifiAccessPoint.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateDistanceUseCase.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateDistanceUseCaseTest.kt`

**Interfaces:**
- Produces: `EnvironmentPreset`, `WifiAccessPoint`, `CalculateDistanceUseCase.execute(rssi: Int, frequencyMhz: Int, preset: EnvironmentPreset): Double`

- [ ] **Step 1: Tulis failing test untuk rumus estimasi jarak**

```kotlin
package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.EnvironmentPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateDistanceUseCaseTest {
    private val useCase = CalculateDistanceUseCase()

    @Test
    fun `when RSSI equals reference power at 1 meter then distance should be 1 meter`() {
        // 2.4 GHz reference power A0 is -40 dBm
        val distance = useCase.execute(rssi = -40, frequencyMhz = 2412, preset = EnvironmentPreset.INDOOR)
        assertEquals(1.0, distance, 0.05)
    }

    @Test
    fun `when RSSI drops by 10 times n then distance increases tenfold`() {
        // At n = 2.0 (Outdoor), every 20 dBm drop multiplies distance by 10
        val d1 = useCase.execute(rssi = -40, frequencyMhz = 2412, preset = EnvironmentPreset.OUTDOOR)
        val d2 = useCase.execute(rssi = -60, frequencyMhz = 2412, preset = EnvironmentPreset.OUTDOOR)
        assertEquals(1.0, d1, 0.05)
        assertEquals(10.0, d2, 0.5)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./gradlew testDebugUnitTest --tests CalculateDistanceUseCaseTest`
Expected: FAIL (Class not found)

- [ ] **Step 3: Implementasikan EnvironmentPreset, WifiAccessPoint, dan CalculateDistanceUseCase**

```kotlin
package com.wefi.analyzer.domain.model

enum class EnvironmentPreset(val label: String, val exponent: Double) {
    OUTDOOR("🌳 Outdoor", 2.0),
    INDOOR("🏢 Indoor", 2.8),
    CONCRETE("🧱 Beton", 3.5)
}

data class WifiAccessPoint(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val channelWidthMhz: Int,
    val distanceMeters: Double,
    val maxPhyRateMbps: Int,
    val security: String,
    val qualityScore: Int,
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
```

```kotlin
package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.EnvironmentPreset
import kotlin.math.pow

class CalculateDistanceUseCase {
    fun execute(rssi: Int, frequencyMhz: Int, preset: EnvironmentPreset = EnvironmentPreset.INDOOR): Double {
        val a0 = if (frequencyMhz >= 5000) -45.0 else -40.0
        val exponent = (a0 - rssi.toDouble()) / (10.0 * preset.exponent)
        val rawDistance = 10.0.pow(exponent)
        return kotlin.math.round(rawDistance * 10.0) / 10.0
    }
}
```

- [ ] **Step 4: Jalankan test dan pastikan PASS**

Run: `./gradlew testDebugUnitTest --tests CalculateDistanceUseCaseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/ app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateDistanceUseCase.kt app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateDistanceUseCaseTest.kt
git commit -m "feat(domain): add Log-Distance Path Loss calculator and unit tests"
```

---

### Task 4: Frequency-to-Channel & Theoretical PHY Rate Parser (TDD)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/util/ChannelFrequencyUtils.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/ParsePhyCapabilitiesUseCase.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/util/ChannelFrequencyUtilsTest.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/ParsePhyCapabilitiesUseCaseTest.kt`

**Interfaces:**
- Produces: `ChannelFrequencyUtils.toChannel(freqMhz: Int): Int`, `ParsePhyCapabilitiesUseCase.execute(standard: String, widthMhz: Int): Int`

- [ ] **Step 1: Tulis test untuk ChannelFrequencyUtils & ParsePhyCapabilitiesUseCase**

```kotlin
package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFrequencyUtilsTest {
    @Test
    fun `verify 2_4 GHz channel mapping`() {
        assertEquals(1, ChannelFrequencyUtils.toChannel(2412))
        assertEquals(6, ChannelFrequencyUtils.toChannel(2437))
        assertEquals(11, ChannelFrequencyUtils.toChannel(2462))
        assertEquals(14, ChannelFrequencyUtils.toChannel(2484))
    }

    @Test
    fun `verify 5 GHz channel mapping`() {
        assertEquals(36, ChannelFrequencyUtils.toChannel(5180))
        assertEquals(149, ChannelFrequencyUtils.toChannel(5745))
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./gradlew testDebugUnitTest --tests ChannelFrequencyUtilsTest`
Expected: FAIL

- [ ] **Step 3: Implementasikan ChannelFrequencyUtils dan ParsePhyCapabilitiesUseCase**

```kotlin
package com.wefi.analyzer.domain.util

object ChannelFrequencyUtils {
    fun toChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5945..7105 -> (frequencyMhz - 5940) / 5 + 1
            else -> 0
        }
    }
}
```

```kotlin
package com.wefi.analyzer.domain.usecase

class ParsePhyCapabilitiesUseCase {
    fun execute(standard: String, widthMhz: Int): Int {
        val std = standard.uppercase()
        return when {
            std.contains("BE") || std.contains("WIFI 7") -> if (widthMhz >= 160) 2400 else 1200
            std.contains("AX") || std.contains("WIFI 6") -> if (widthMhz >= 80) 1201 else 574
            std.contains("AC") || std.contains("WIFI 5") -> if (widthMhz >= 80) 867 else 433
            std.contains("N") || std.contains("WIFI 4") -> if (widthMhz >= 40) 300 else 144
            std.contains("G") || std.contains("A") -> 54
            else -> 11
        }
    }
}
```

- [ ] **Step 4: Jalankan test dan pastikan PASS**

Run: `./gradlew testDebugUnitTest --tests ChannelFrequencyUtilsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/util/ app/src/main/java/com/wefi/analyzer/domain/usecase/ParsePhyCapabilitiesUseCase.kt app/src/test/java/com/wefi/analyzer/domain/
git commit -m "feat(domain): add frequency-to-channel mapping and PHY rate parser with unit tests"
```

---

### Task 5: Channel Congestion Rating & WiFi Quality Score (TDD)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/ChannelRating.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCase.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateWifiQualityScoreUseCase.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCaseTest.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateWifiQualityScoreUseCaseTest.kt`

**Interfaces:**
- Produces: `CalculateChannelRatingUseCase.execute(apList: List<WifiAccessPoint>, bandGhz: Double): List<ChannelRating>`, `CalculateWifiQualityScoreUseCase.execute(rssi: Int, maxPhyRate: Int, channelPenalty: Double): Int`

- [ ] **Step 1: Tulis test untuk Channel Rating dan Quality Score**

```kotlin
package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.WifiAccessPoint
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateChannelRatingUseCaseTest {
    private val useCase = CalculateChannelRatingUseCase()

    @Test
    fun `channel with no APs should have maximum 10 stars rating`() {
        val ratings = useCase.execute(emptyList(), bandGhz = 2.4)
        val channel1 = ratings.first { it.channel == 1 }
        assertTrue(channel1.stars == 10)
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./gradlew testDebugUnitTest --tests CalculateChannelRatingUseCaseTest`
Expected: FAIL

- [ ] **Step 3: Implementasikan ChannelRating, CalculateChannelRatingUseCase, dan CalculateWifiQualityScoreUseCase**

```kotlin
package com.wefi.analyzer.domain.model

data class ChannelRating(
    val channel: Int,
    val frequencyMhz: Int,
    val stars: Int,
    val apCount: Int,
    val isRecommended: Boolean,
    val reason: String
)
```

```kotlin
package com.wefi.analyzer.domain.usecase

import com.wefi.analyzer.domain.model.ChannelRating
import com.wefi.analyzer.domain.model.WifiAccessPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CalculateChannelRatingUseCase {
    fun execute(apList: List<WifiAccessPoint>, bandGhz: Double): List<ChannelRating> {
        val channels = if (bandGhz == 2.4) (1..13).toList() else listOf(36, 40, 44, 48, 149, 153, 157, 161)
        val apsInBand = apList.filter { if (bandGhz == 2.4) it.frequencyMhz < 3000 else it.frequencyMhz >= 5000 }

        val ratings = channels.map { ch ->
            var penalty = 0.0
            var count = 0
            apsInBand.forEach { ap ->
                val chDiff = abs(ap.channel - ch)
                if (chDiff <= 4) {
                    val overlapFactor = (5.0 - chDiff) / 5.0
                    val signalWeight = (100.0 - abs(ap.rssi.toDouble())) / 10.0
                    penalty += signalWeight * overlapFactor
                    if (chDiff == 0) count++
                }
            }
            val stars = max(1, min(10, 10 - penalty.toInt()))
            ChannelRating(
                channel = ch,
                frequencyMhz = if (bandGhz == 2.4) 2407 + ch * 5 else 5000 + ch * 5,
                stars = stars,
                apCount = count,
                isRecommended = false,
                reason = if (stars >= 8) "Minim interferensi tetangga" else "Padat ($count AP pada kanal ini)"
            )
        }
        val bestScore = ratings.maxOfOrNull { it.stars } ?: 10
        return ratings.map { if (it.stars == bestScore) it.copy(isRecommended = true) else it }
    }
}
```

```kotlin
package com.wefi.analyzer.domain.usecase

import kotlin.math.max
import kotlin.math.min

class CalculateWifiQualityScoreUseCase {
    fun execute(rssi: Int, maxPhyRate: Int, channelPenalty: Double): Int {
        val signalScore = when {
            rssi >= -50 -> 100
            rssi <= -90 -> 10
            else -> ((rssi + 90) * 100) / 40
        }
        val phyScore = when {
            maxPhyRate >= 866 -> 100
            maxPhyRate >= 300 -> 75
            maxPhyRate >= 144 -> 50
            else -> 30
        }
        val cleanScore = max(10, (100 - channelPenalty * 10).toInt())
        val composite = (signalScore * 0.5) + (phyScore * 0.25) + (cleanScore * 0.25)
        return max(1, min(100, composite.toInt()))
    }
}
```

- [ ] **Step 4: Jalankan test dan pastikan PASS**

Run: `./gradlew testDebugUnitTest --tests CalculateChannelRatingUseCaseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/ChannelRating.kt app/src/main/java/com/wefi/analyzer/domain/usecase/ app/src/test/java/com/wefi/analyzer/domain/usecase/
git commit -m "feat(domain): add channel rating optimizer and wifi quality score use cases with tests"
```

---

### Task 6: Android Wi-Fi Hardware Scanner & Repository Implementation

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiScannerRepository.kt`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/CurrentConnectionRepository.kt`

**Interfaces:**
- Produces: `WifiScannerRepository.scanResults: StateFlow<List<WifiAccessPoint>>`, `WifiScannerRepository.startScan()`, `CurrentConnectionRepository.connectedWifi: StateFlow<WifiAccessPoint?>`

- [ ] **Step 1: Deklarasikan izin di `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_dialog_info"
        android:label="weFi"
        android:roundIcon="@android:drawable/ic_dialog_info"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 2: Buat Interface Repository di Domain**

```kotlin
package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.EnvironmentPreset
import com.wefi.analyzer.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.StateFlow

interface WifiScannerRepository {
    val scanResults: StateFlow<List<WifiAccessPoint>>
    val isScanning: StateFlow<Boolean>
    fun startScan()
    fun setEnvironmentPreset(preset: EnvironmentPreset)
}

interface CurrentConnectionRepository {
    val connectedWifi: StateFlow<WifiAccessPoint?>
    val linkSpeedMbps: StateFlow<Int>
}
```

- [ ] **Step 3: Implementasikan `WifiScannerRepositoryImpl` dengan BroadcastReceiver & mitigasi scan throttling**

Menggunakan `Context.getSystemService(Context.WIFI_SERVICE) as WifiManager`, mendaftarkan receiver untuk `SCAN_RESULTS_AVAILABLE_ACTION`, menghitung jarak menggunakan `CalculateDistanceUseCase`, dan memperbarui `StateFlow`.

- [ ] **Step 4: Implementasikan `CurrentConnectionRepositoryImpl` dengan ConnectivityManager & WifiInfo**

Membaca SSID dan BSSID dari koneksi aktif, mengambil link speed real-time via `WifiInfo.linkSpeed`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/wefi/analyzer/domain/repository/ app/src/main/java/com/wefi/analyzer/data/repository/
git commit -m "feat(data): implement hardware wifi scanner repository and active connection manager"
```

---

### Task 7: Active Speedtest Engine (Throughput & Latency)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/SpeedTestMetrics.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/SpeedTestRepository.kt`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/RunSpeedTestUseCase.kt`

**Interfaces:**
- Produces: `SpeedTestMetrics(pingMs, jitterMs, downloadMbps, uploadMbps, isRunning, progress)`, `RunSpeedTestUseCase.execute(): Flow<SpeedTestMetrics>`

- [ ] **Step 1: Buat model data `SpeedTestMetrics`**

```kotlin
package com.wefi.analyzer.domain.model

data class SpeedTestMetrics(
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val isRunning: Boolean = false,
    val stage: SpeedTestStage = SpeedTestStage.IDLE,
    val progress: Float = 0f
)

enum class SpeedTestStage {
    IDLE, PING, DOWNLOAD, UPLOAD, FINISHED, ERROR
}
```

- [ ] **Step 2: Implementasikan `SpeedTestRepositoryImpl` dengan streaming RAM Buffer OkHttp**

Menghitung ping & jitter via socket handshake ke `1.1.1.1:80` atau HTTP head request. Melakukan stream download data chunk 10MB dari public CDN tanpa menulis ke storage dan menghitung throughput Mbps secara real-time.

- [ ] **Step 3: Implementasikan `RunSpeedTestUseCase`**

Menghubungkan repository ke UI via Kotlin Flow.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/SpeedTestMetrics.kt app/src/main/java/com/wefi/analyzer/domain/repository/SpeedTestRepository.kt app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt app/src/main/java/com/wefi/analyzer/domain/usecase/RunSpeedTestUseCase.kt
git commit -m "feat(network): implement active speedtest engine for throughput and latency"
```

---

### Task 8: Blynk.io Design System & Reusable Widgets

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/theme/Color.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/theme/Type.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/BlynkCard.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/BlynkSegmentedControl.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/BlynkMetricTile.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/EnvironmentPresetSelector.kt`

**Interfaces:**
- Produces: Komponen desain Blynk.io Putih-Biru `#77ADF9`, anti-glare theme, kartu modular rounded 16dp.

- [ ] **Step 1: Buat token warna di `Color.kt`**

```kotlin
package com.wefi.analyzer.ui.theme

import androidx.compose.ui.graphics.Color

val BlynkBlue = Color(0xFF77ADF9)
val BlynkBlueTint = Color(0xFFEAF2FE)
val BlynkBackgroundLight = Color(0xFFF5F8FC)
val BlynkSurfaceLight = Color(0xFFFFFFFF)
val BlynkBorderLight = Color(0xFFE2E8F0)

val BlynkBackgroundDark = Color(0xFF0F172A)
val BlynkSurfaceDark = Color(0xFF1E293B)
val BlynkBorderDark = Color(0xFF334155)

val TextPrimaryLight = Color(0xFF1E293B)
val TextSecondaryLight = Color(0xFF64748B)

val QualityGreen = Color(0xFF10B981)
val QualityAmber = Color(0xFFF59E0B)
val QualityRed = Color(0xFFEF4444)

// Parabola spectrum curve palette
val CurveBlue = Color(0xFF77ADF9)
val CurveMint = Color(0xFF34D399)
val CurveAmber = Color(0xFFFBBF24)
val CurveLavender = Color(0xFFA78BFA)
val CurveCoral = Color(0xFFF87171)
```

- [ ] **Step 2: Buat `Theme.kt` dengan dukungan Light/Dark Eye-Comfort**

Mengonfigurasi `MaterialTheme` dengan palet warna di atas.

- [ ] **Step 3: Buat widget `BlynkCard`, `BlynkSegmentedControl`, dan `EnvironmentPresetSelector`**

Komponen modular dengan visual rapi, padding 16dp, border 1dp, dan tanpa ornamen berlebih (Zero AI-Slop).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/theme/ app/src/main/java/com/wefi/analyzer/ui/components/
git commit -m "feat(ui): implement Blynk.io design system tokens and reusable widgets"
```

---

### Task 9: Juicy Morphing Floating Help Assistant & Contextual Help Drawer

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/MorphingHelpFab.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/components/ContextualHelpDrawer.kt`

**Interfaces:**
- Produces: `MorphingHelpFab(currentTab, isScrolling, onHelpClick)`, `ContextualHelpDrawer(tab, onDismiss)`

- [ ] **Step 1: Implementasikan `MorphingHelpFab` dengan animasi juicy Ease-In-Ease-Out**

```kotlin
package com.wefi.analyzer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wefi.analyzer.ui.theme.BlynkBlue
import kotlinx.coroutines.delay

@Composable
fun MorphingHelpFab(
    tabId: Int,
    isUserActive: Boolean,
    onHelpClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Re-expand on tab change or scroll
    LaunchedEffect(tabId, isUserActive) {
        isExpanded = true
        delay(3000)
        isExpanded = false
    }

    val size by animateDpAsState(
        targetValue = if (isExpanded) 56.dp else 28.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "fab_size"
    )
    val offsetX by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "fab_offset_x"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 80.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier
                .offset(x = offsetX)
                .size(width = size, height = size)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .clickable {
                    if (!isExpanded) isExpanded = true else onHelpClick()
                },
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isExpanded) {
                    Icon(
                        imageVector = Icons.Rounded.QuestionMark,
                        contentDescription = "Help Guide",
                        tint = BlynkBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(BlynkBlue, CircleShape)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Implementasikan `ContextualHelpDrawer` (ModalBottomSheet)**

Menyajikan penjelasan kontekstual Komdat sesuai `tabId` (Grafik Kanal, List AP, Rating Kanal, Speedtest Aktif) dengan teks ramah dan tips troubleshooting.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/components/MorphingHelpFab.kt app/src/main/java/com/wefi/analyzer/ui/components/ContextualHelpDrawer.kt
git commit -m "feat(ui): implement juicy morphing help button and contextual drawer"
```

---

### Task 10: Custom Canvas Channel Graph (Parabola & Connected AP Plumb-Line)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphCanvas.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphViewModel.kt`

**Interfaces:**
- Produces: `ChannelGraphCanvas(apList, selectedBand, connectedBssid)`, `ChannelGraphScreen`

- [ ] **Step 1: Implementasikan `ChannelGraphCanvas` dengan Quadratic Bezier & Plumb-Line**

Menggambar grid sinyal $-20$ s/d $-100\text{ dBm}$, nomor kanal sumbu X, kurva parabola semi-transparan dengan label `SSID (~Xm)`.
Khusus untuk AP dengan `bssid == connectedBssid`:
- Gambar garis vertikal putus-putus (*dashed line*) warna `#77ADF9` dari puncak kanvas menembus puncak parabola hingga sumbu X.
- Gambar badge pill `● TERHUBUNG (JARINGAN SAYA)`.

- [ ] **Step 2: Implementasikan `ChannelGraphScreen` & `ChannelGraphViewModel`**

Menghubungkan flow scan results dan connection state ke UI, menyediakan switcher band `[ 2.4 GHz ] [ 5 GHz ] [ 6 GHz ]`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/graph/
git commit -m "feat(ui): implement custom canvas channel graph with connected plumb-line indicator"
```

---

### Task 11: AP List & Channel Rating Screens

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListViewModel.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingViewModel.kt`

**Interfaces:**
- Produces: `ApListScreen` (kartu list AP lengkap dengan jarak, PHY rate, skor kualitas, dan preset lingkungan), `ChannelRatingScreen` (bintang 1-10 kanal terbersih).

- [ ] **Step 1: Implementasikan `ApListScreen` & `ApListViewModel`**

Menampilkan ringkasan total AP, dropdown kalibrasi lingkungan (*Outdoor / Indoor / Beton*), serta kartu Blynk untuk setiap AP dengan badge sinyal, jarak, dan skor kualitas.

- [ ] **Step 2: Implementasikan `ChannelRatingScreen` & `ChannelRatingViewModel`**

Menampilkan daftar kanal berurutan dari bintang 10/10 hingga terendah, disertai teks rekomendasi perpindahan kanal.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ app/src/main/java/com/wefi/analyzer/ui/screens/rating/
git commit -m "feat(ui): implement AP list radar view and channel rating optimizer screens"
```

---

### Task 12: Active Speedtest Screen & Main App Shell Navigation

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestViewModel.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/navigation/BottomNavBar.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/navigation/Screen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`

**Interfaces:**
- Produces: `SpeedTestScreen`, `BottomNavBar` 4 tabs, `MainActivity` dengan integrasi `MorphingHelpFab` dan lifecycle permissions.

- [ ] **Step 1: Implementasikan `SpeedTestScreen` & `SpeedTestViewModel`**

Menampilkan widget dial speedometer Blynk, tombol uji kecepatan, hasil Ping, Jitter, Download, Upload, dan info hardware link speed Wi-Fi tersambung.

- [ ] **Step 2: Implementasikan `BottomNavBar` & `MainActivity`**

Menggabungkan 4 tab (Grafik Kanal, Radar AP, Rating Kanal, Speedtest Aktif) di bawah satu shell Compose, meletakkan `MorphingHelpFab` di atas konten, dan mengelola permintaan runtime permissions Android.

- [ ] **Step 3: Verifikasi build dan unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/ app/src/main/java/com/wefi/analyzer/ui/navigation/ app/src/main/java/com/wefi/analyzer/MainActivity.kt
git commit -m "feat(app): assemble main app shell with 4 tabs navigation and speedtest screen"
```
