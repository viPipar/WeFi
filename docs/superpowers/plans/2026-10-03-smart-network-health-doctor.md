# Smart Network Health & Security Doctor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan fitur mutakhir **Smart Network Health & Security Doctor**, sebuah sistem diagnostik eksekutif satu sentuhan (*1-Click Network Audit*) yang mengorkestrasikan telemetri RF, interferensi kanal, routing gateway, keamanan Wi-Fi, dan kerentanan port ke dalam skor kesehatan terpadu 0–100%, kartu rincian 5 pilar, dan daftar rekomendasi perbaikan instan.

**Architecture:** Menerapkan Clean Architecture berbasis Kotlin Coroutines & StateFlow. Lapisan domain memiliki `EvaluateNetworkHealthUseCase` yang mengorkestrasikan data dari `CurrentConnectionRepository`, `WifiScannerRepository`, dan `NetworkDiscoveryRepository` secara paralel. Lapisan UI mengimplementasikan `NetworkDoctorBottomSheet` dengan Animated Circular Gauge, kartu 5 pilar, dan remediation checklist sesuai standar `clean-ui-procedural`.

**Tech Stack:** Kotlin 1.9+, Android SDK 34, Jetpack Compose, Material 3, Coroutines, StateFlow, JUnit 4, MockK.

**Spec:** `docs/superpowers/specs/2026-10-03-smart-network-health-doctor-design.md`

## Global Constraints
- Android Min SDK 24, Target SDK 34.
- 100% Kotlin coroutines idiomatic, thread-safe, timeout terjamin (maks 3500ms) tanpa kebocoran memori.
- Desain UI/UX mematuhi `clean-ui-procedural`: zero neon borders, tipografi kontras tinggi, palet Blynk IoT (#77ADF9, #F8FAFC, #FFFFFF), safe-zone padding.
- Bahasa UI: Bahasa Indonesia profesional, jelas, dan rapi.
- Verifikasi wajib sebelum klaim selesai: Unit tests pass (`testDebugUnitTest`) dan APK assemble pass (`assembleDebug`).

---

### Task 1: Buat Domain Model untuk Network Health & Scoring System

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkHealthModels.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/model/NetworkHealthModelsTest.kt`

**Interfaces:**
- Produces: `HealthScoreGrade`, `HealthPillarType`, `PillarStatus`, `HealthPillarScore`, `RemediationImpact`, `ActionableRemediation`, `NetworkHealthReport`.

- [ ] **Step 1: Tulis unit test untuk verifikasi model dan penentuan grade**

Di `NetworkHealthModelsTest.kt`:
```kotlin
package com.wefi.analyzer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkHealthModelsTest {

    @Test
    fun testHealthScoreGrade_fromScore_assignsCorrectGrade() {
        assertEquals(HealthScoreGrade.A_PLUS, HealthScoreGrade.fromScore(95))
        assertEquals(HealthScoreGrade.A, HealthScoreGrade.fromScore(85))
        assertEquals(HealthScoreGrade.B, HealthScoreGrade.fromScore(70))
        assertEquals(HealthScoreGrade.C, HealthScoreGrade.fromScore(55))
        assertEquals(HealthScoreGrade.CRITICAL, HealthScoreGrade.fromScore(30))
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan awal (TDD)**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.model.NetworkHealthModelsTest"`
Expected: FAIL (class not found).

- [ ] **Step 3: Implementasikan model data di `NetworkHealthModels.kt`**

```kotlin
package com.wefi.analyzer.domain.model

enum class HealthScoreGrade(val label: String, val minScore: Int) {
    A_PLUS("Sempurna (A+)", 90),
    A("Sangat Baik (A)", 80),
    B("Baik (B)", 65),
    C("Perlu Perhatian (C)", 50),
    CRITICAL("Kritis & Berisiko (D/F)", 0);

    companion object {
        fun fromScore(score: Int): HealthScoreGrade = when {
            score >= 90 -> A_PLUS
            score >= 80 -> A
            score >= 65 -> B
            score >= 50 -> C
            else -> CRITICAL
        }
    }
}

enum class HealthPillarType(val title: String) {
    PHYSICAL_RF("Sinyal & Kualitas Fisik RF"),
    CHANNEL_INTERFERENCE("Kepadatan & Interferensi Kanal"),
    GATEWAY_TRANSPORT("Konektivitas Gateway & DNS"),
    WIRELESS_SECURITY("Protokol Keamanan Wi-Fi"),
    SUBNET_EXPOSURE("Paparan Subnet & Port Lab")
}

enum class PillarStatus {
    OPTIMAL,
    FAIR,
    WARNING,
    DANGER
}

data class HealthPillarScore(
    val type: HealthPillarType,
    val score: Int,
    val weight: Double,
    val status: PillarStatus,
    val summary: String,
    val metrics: Map<String, String> = emptyMap()
)

enum class RemediationImpact {
    HIGH,
    MEDIUM,
    LOW
}

data class ActionableRemediation(
    val id: String,
    val title: String,
    val description: String,
    val impact: RemediationImpact,
    val targetTabRoute: String? = null
)

data class NetworkHealthReport(
    val timestamp: Long,
    val ssid: String,
    val bssid: String,
    val overallScore: Int,
    val grade: HealthScoreGrade,
    val pillars: List<HealthPillarScore>,
    val remediations: List<ActionableRemediation>,
    val isInternetReachable: Boolean
)
```

- [ ] **Step 4: Jalankan test untuk memverifikasi kelulusan**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.model.NetworkHealthModelsTest"`
Expected: PASS.

- [ ] **Step 5: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/NetworkHealthModels.kt app/src/test/java/com/wefi/analyzer/domain/model/NetworkHealthModelsTest.kt
git commit -m "feat: add domain models for network health doctor and scoring system"
```

---

### Task 2: Implementasikan `EvaluateNetworkHealthUseCase` & Unit Tests

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/usecase/EvaluateNetworkHealthUseCase.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/EvaluateNetworkHealthUseCaseTest.kt`

**Interfaces:**
- Consumes: `CurrentConnectionRepository`, `WifiScannerRepository`, `NetworkDiscoveryRepository`.
- Produces: `suspend fun execute(): NetworkHealthReport`.

- [ ] **Step 1: Tulis unit test untuk `EvaluateNetworkHealthUseCase`**

Di `EvaluateNetworkHealthUseCaseTest.kt`:
Menguji skenario:
1. Ketika koneksi optimal (RSSI kuat, kanal bersih, gateway cepat, WPA3, tanpa port berisiko) -> Skor >= 90 (Grade A+).
2. Ketika koneksi lemah dengan port berisiko terbuka (RSSI -85dBm, Telnet terbuka) -> Skor < 50 (Grade CRITICAL) dan remediations dihasilkan.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan awal**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.usecase.EvaluateNetworkHealthUseCaseTest"`
Expected: FAIL.

- [ ] **Step 3: Implementasikan logika evaluasi 5 pilar di `EvaluateNetworkHealthUseCase.kt`**

- [ ] **Step 4: Jalankan unit test untuk memastikan kelulusan**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.usecase.EvaluateNetworkHealthUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/usecase/EvaluateNetworkHealthUseCase.kt app/src/test/java/com/wefi/analyzer/domain/usecase/EvaluateNetworkHealthUseCaseTest.kt
git commit -m "feat: implement EvaluateNetworkHealthUseCase with 5-pillar scoring and remediation generation"
```

---

### Task 3: Implementasikan `NetworkDoctorViewModel` & State Management

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorViewModelTest.kt`

**Interfaces:**
- Produces: `uiState: StateFlow<NetworkDoctorUiState>`, `runDoctorAudit()`, `dismissDoctor()`.

- [ ] **Step 1: Tulis unit test untuk ViewModel**

Menguji pemanggilan `runDoctorAudit()` mengupdate `isAuditing` menjadi true lalu false dan menyimpan `report`.

- [ ] **Step 2: Jalankan unit test untuk verifikasi awal**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.doctor.NetworkDoctorViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implementasikan `NetworkDoctorViewModel.kt`**

- [ ] **Step 4: Jalankan unit test untuk memastikan kelulusan**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.doctor.NetworkDoctorViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorViewModelTest.kt
git commit -m "feat: add NetworkDoctorViewModel with reactive audit state management"
```

---

### Task 4: Implementasikan `NetworkDoctorBottomSheet` & Animated Health Gauge

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorBottomSheet.kt`

**Interfaces:**
- Consumes: `NetworkDoctorViewModel` (atau UI state & callback).
- Produces: Modal Bottom Sheet komprehensif dengan:
  - Header: SSID, BSSID, tombol tutup.
  - Animated Circular Arc Gauge: visualisasi skor kesehatan 0-100% dengan gradien halus dan grade badge.
  - Kartu 5 Pilar: menampilkan skor individual, status, dan breakdown metrik.
  - Kartu Rekomendasi Solusi (*Actionable Remediation*): tombol aksi langsung ke tab terkait (misal: "Buka Tab Rating", "Buka Audit Port").
  - Tombol Salin Ringkasan Laporan Dokter.

- [ ] **Step 1: Buat composable `NetworkDoctorBottomSheet`**
- [ ] **Step 2: Kompilasi dan verifikasi dengan unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/doctor/NetworkDoctorBottomSheet.kt
git commit -m "feat: implement NetworkDoctorBottomSheet with animated circular health gauge and 5-pillar cards"
```

---

### Task 5: Integrasikan Doctor Action Button di Header Bar & MainAppShell

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/components/HardwareStateBanner.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`

**Interfaces:**
- Menambahkan tombol aksi stetoskop/kesehatan 🩺 di header banner atau top action bar.
- Mengontrol `isDoctorSheetOpen: Boolean` di `MainAppShell`.

- [ ] **Step 1: Tambahkan tombol aksi Doctor di `HardwareStateBanner.kt`**
- [ ] **Step 2: Wire ViewModel dan Bottom Sheet di `MainActivity.kt`**
- [ ] **Step 3: Kompilasi dan verifikasi dengan unit test**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/components/HardwareStateBanner.kt app/src/main/java/com/wefi/analyzer/MainActivity.kt
git commit -m "feat: integrate Network Doctor quick action in main app shell and header banner"
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
