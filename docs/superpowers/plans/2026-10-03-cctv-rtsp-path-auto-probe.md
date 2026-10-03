# CCTV RTSP Path Auto-Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Memungkinkan pendeteksian otomatis path aliran RTSP (stream discovery) pada CCTV merek sendiri/OEM/generic tanpa perlu tebak-menebak URL manual.

**Architecture:** Menggunakan Socket TCP ringan untuk mengirim probe RTSP `DESCRIBE` secara terkelola ke port 554, mengklasifikasikan respon (200 OK vs 401 Unauthorized), mengekspos state ke ViewModel, dan menampilkan chip path stream siap pakai di UI Jetpack Compose.

**Tech Stack:** Kotlin Coroutines (asinkron, safe timeout), AndroidX Media3 ExoPlayer RTSP, Jetpack Compose (`clean-ui-procedural`).

**Spec:** `docs/superpowers/specs/2026-10-03-cctv-rtsp-path-auto-probe-design.md`

## Global Constraints
- Target platform: Android API 26+ (Non-root, Wi-Fi lokal aman).
- Timeout terukur: masing-masing probe socket dibatasi maksimal 1200ms per path dengan konkurensi 3 untuk mencegah overload buffer kamera.
- Mematuhi standar `clean-ui-procedural` untuk semua elemen UI Jetpack Compose.

---

### Task 1: [x] Domain Model untuk Hasil Auto-Probe RTSP
**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/NetworkDiscoveryLogicTest.kt`

**Details:**
Tambahkan data class `RtspProbePath`:
```kotlin
data class RtspProbePath(
    val path: String,
    val fullUri: String,
    val isAccessible: Boolean,
    val requiresAuth: Boolean,
    val statusCode: Int,
    val statusMessage: String
)
```

---

### Task 2: [x] Repository Interface & Logika Auto-Probe Socket
**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/repository/NetworkDiscoveryRepository.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt`

**Details:**
1. Tambahkan metode pada interface:
   ```kotlin
   suspend fun probeRtspPaths(hostIp: String, port: Int = 554): List<RtspProbePath>
   ```
2. Implementasikan di `NetworkDiscoveryRepositoryImpl`:
   - Buat fungsi private `probeSingleRtspPath(hostIp: String, port: Int, path: String): RtspProbePath?`.
   - Buka `Socket` dengan timeout 1200ms.
   - Kirim `DESCRIBE rtsp://$hostIp:$port/$path RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: WeFi-Probe/1.0\r\nAccept: application/sdp\r\n\r\n`.
   - Baca baris status RTSP. Tangani `200` (terbuka), `401` (valid but protected), atau kode lainnya.
   - Eksekusi daftar `COMMON_RTSP_PATHS` secara terbagi (chunks of 3) agar hemat resource.

---

### Task 3: [x] Integrasi ViewModel
**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`

**Details:**
1. Tambahkan StateFlow:
   - `rtspProbeResults: StateFlow<Map<String, List<RtspProbePath>>>`
   - `isProbingRtsp: StateFlow<Boolean>`
2. Tambahkan fungsi:
   ```kotlin
   fun probeHostRtspPaths(host: DiscoveredHost, port: Int = 554)
   ```

---

### Task 4: [x] Pembaruan UI di NetworkDiscoveryScreen
**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`

**Details:**
1. Di dalam dialog `showCctvDialog`:
   - Tambahkan tombol "Deteksi Path Otomatis" lengkap dengan status loading saat sedang memindai.
   - Tampilkan chip hasil penemuan (misal: `[✓ live/ch0]`, `[🔒 stream1]`).
   - Saat chip diklik: otomatis set `cctvChannelPreset = "Kustom"`, set `cctvCustomPath = path`, dan perbarui preview URL secara instan.
2. Tambahkan aksi langsung "Putar Langsung" dari path yang terdeteksi.

---

### Task 5: [x] Unit Testing & Verifikasi Kompilasi
**Files:**
- Modify: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`
- Run: `.\gradlew.bat testDebugUnitTest --no-daemon` (LULUS 100%)
- Run: `.\gradlew.bat assembleDebug --no-daemon` (LULUS 100%)
