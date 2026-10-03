# Implementation Plan: Device Authentication Posture Assessment & Authorized CCTV Lab Monitoring

**Tujuan:** Memperbaiki bug tombol Web Admin, meningkatkan ketahanan port scanning dan ping probe, menambahkan evaluasi keterbukaan autentikasi (*unauthenticated exposure*), serta mengintegrasikan pemutar video CCTV RTSP langsung di dalam aplikasi (*In-App Embedded Live Player*) berbasis AndroidX Media3.

---

## Daftar Tugas (Task Checklist)

- [x] **Task 1: Model Domain & Repository Interface**
  - File: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt`
  - File: `app/src/main/java/com/wefi/analyzer/domain/repository/NetworkDiscoveryRepository.kt`
  - Tambahkan enum `AuthStatus` dan data class `AuthPostureResult`.
  - Tambahkan metode `testAuthPosture(hostIp: String, port: Int, protocolHint: String? = null): AuthPostureResult` pada repository.

- [x] **Task 2: Perbaikan Bug & Implementasi Logika Repository**
  - File: `app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt`
  - Perbaiki ping connect fallback timeout dari 200ms -> 600ms.
  - Perbaiki port scan connect timeout dari 500ms -> 1200ms.
  - Perbaiki HTTP banner grab: tambahkan fallback `GET /` jika `HEAD` gagal/ditolak.
  - Implementasikan fungsi `testAuthPosture()` dengan dukungan protokol HTTP/HTTPS dan RTSP port 554.

- [x] **Task 3: ViewModel State & Integrasi Fitur**
  - File: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
  - Tambahkan state flow `authPostureState: StateFlow<Map<String, AuthPostureResult>>` (key: `"$ip:$port"`).
  - Tambahkan state flow `isTestingAuth: StateFlow<Boolean>`.
  - Tambahkan fungsi `testHostAuthPosture(host: DiscoveredHost, port: Int, protocolHint: String? = null)`.

- [x] **Task 4: Pembaruan Antarmuka Pengguna (UI) di NetworkDiscoveryScreen**
  - File: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`
  - Buat tombol Web Admin **selalu tersedia** di rincian perangkat (tidak lagi bergantung pada `webPort != null`).
  - Tambahkan Dialog Pemilih Port Web Cepat (preset 80, 443, 8080, 8000, 8443, dan kustom port input).
  - Tambahkan Kartu & Tombol "Uji Keterbukaan Akses (Auth Posture)" dengan feedback status visual (Unprotected Exposure vs Protected vs Refused).
  - Tambahkan Kartu "Monitoring CCTV Lab (Isolasi)" jika perangkat adalah CCTV / port 554 terbuka:
    - Tombol Uji Stream RTSP
    - Tombol Peluncur Live Stream RTSP (Dialog preset Hikvision/Dahua/Generic + peluncuran Intent ACTION_VIEW)
    - Tombol Buka Web Console CCTV

- [x] **Task 5: Pengujian Unit (Unit Testing)**
  - File: `app/src/test/java/com/wefi/analyzer/data/repository/NetworkDiscoveryLogicTest.kt`
  - File: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`
  - Buat pengujian untuk `AuthStatus`, pemetaan respon HTTP/RTSP, dan fungsi ViewModel.

- [x] **Task 6: Verifikasi Build & Kompilasi Fase 1**
  - Jalankan `.\gradlew.bat testDebugUnitTest --no-daemon` (LULUS 100%)
  - Jalankan `.\gradlew.bat assembleDebug --no-daemon` (LULUS 100%)

- [x] **Task 7: Dependensi AndroidX Media3 ExoPlayer RTSP**
  - File: `app/build.gradle.kts`
  - Tambahkan pustaka resmi:
    - `androidx.media3:media3-exoplayer:1.3.1`
    - `androidx.media3:media3-exoplayer-rtsp:1.3.1`
    - `androidx.media3:media3-ui:1.3.1`

- [x] **Task 8: Komponen Jetpack Compose CctvLivePlayer**
  - File: `app/src/main/java/com/wefi/analyzer/ui/components/CctvLivePlayer.kt`
  - Bangun komponen `CctvLivePlayer` berbasis `AndroidView` membungkus `PlayerView`.
  - Pasang `RtspMediaSource.Factory().setForceUseRtpTcp(true)` untuk keandalan streaming Wi-Fi.
  - Implementasikan penanganan siklus hidup yang aman (`onDispose { player.release() }`).

- [x] **Task 9: Integrasi In-App Live Player ke NetworkDiscoveryScreen**
  - File: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`
  - Tambahkan dialog pemutar video langsung di dalam aplikasi saat pengguna memilih "Tonton di Aplikasi".
  - Berikan opsi tombol cadangan "Buka di Pemutar Eksternal (VLC)".

- [x] **Task 10: Pengujian Unit & Verifikasi Build Fase 2**
  - Jalankan `.\gradlew.bat testDebugUnitTest --no-daemon` (LULUS 100%)
  - Jalankan `.\gradlew.bat assembleDebug --no-daemon` (LULUS 100%)
  - Pastikan seluruh pengujian lulus 100% dan APK debug terkompilasi sempurna.
