# Implementation Plan: Device Authentication Posture Assessment & Authorized CCTV Lab Monitoring

**Tujuan:** Memperbaiki bug tombol Web Admin, meningkatkan ketahanan port scanning dan ping probe, serta menambahkan kapabilitas evaluasi keterbukaan autentikasi (*unauthenticated exposure*) dan monitoring CCTV lab terisolasi.

---

## Daftar Tugas (Task Checklist)

- [ ] **Task 1: Model Domain & Repository Interface**
  - File: `app/src/main/java/com/wefi/analyzer/domain/model/NetworkDiscoveryModels.kt`
  - File: `app/src/main/java/com/wefi/analyzer/domain/repository/NetworkDiscoveryRepository.kt`
  - Tambahkan enum `AuthStatus` dan data class `AuthPostureResult`.
  - Tambahkan metode `testAuthPosture(hostIp: String, port: Int, protocolHint: String? = null): AuthPostureResult` pada repository.

- [ ] **Task 2: Perbaikan Bug & Implementasi Logika Repository**
  - File: `app/src/main/java/com/wefi/analyzer/data/repository/NetworkDiscoveryRepositoryImpl.kt`
  - Perbaiki ping connect fallback timeout dari 200ms -> 600ms.
  - Perbaiki port scan connect timeout dari 500ms -> 1200ms.
  - Perbaiki HTTP banner grab: tambahkan fallback `GET /` jika `HEAD` gagal/ditolak.
  - Implementasikan fungsi `testAuthPosture()` dengan dukungan protokol HTTP/HTTPS dan RTSP port 554.

- [ ] **Task 3: ViewModel State & Integrasi Fitur**
  - File: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
  - Tambahkan state flow `authPostureState: StateFlow<Map<String, AuthPostureResult>>` (key: `"$ip:$port"`).
  - Tambahkan state flow `isTestingAuth: StateFlow<Boolean>`.
  - Tambahkan fungsi `testHostAuthPosture(host: DiscoveredHost, port: Int, protocolHint: String? = null)`.

- [ ] **Task 4: Pembaruan Antarmuka Pengguna (UI) di NetworkDiscoveryScreen**
  - File: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryScreen.kt`
  - Buat tombol Web Admin **selalu tersedia** di rincian perangkat (tidak lagi bergantung pada `webPort != null`).
  - Tambahkan Dialog Pemilih Port Web Cepat (preset 80, 443, 8080, 8000, 8443, dan kustom port input).
  - Tambahkan Kartu & Tombol "Uji Keterbukaan Akses (Auth Posture)" dengan feedback status visual (Unprotected Exposure vs Protected vs Refused).
  - Tambahkan Kartu "Monitoring CCTV Lab (Isolasi)" jika perangkat adalah CCTV / port 554 terbuka:
    - Tombol Uji Stream RTSP
    - Tombol Peluncur Live Stream RTSP (Dialog preset Hikvision/Dahua/Generic + peluncuran Intent ACTION_VIEW)
    - Tombol Buka Web Console CCTV

- [ ] **Task 5: Pengujian Unit (Unit Testing)**
  - File: `app/src/test/java/com/wefi/analyzer/NetworkDiscoveryAuthPostureTest.kt`
  - File: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`
  - Buat pengujian untuk `AuthStatus`, pemetaan respon HTTP/RTSP, dan fungsi ViewModel.

- [ ] **Task 6: Verifikasi Build & Kompilasi**
  - Jalankan `.\gradlew.bat testDebugUnitTest --no-daemon`
  - Jalankan `.\gradlew.bat assembleDebug --no-daemon`
  - Pastikan seluruh pengujian lulus 100% dan APK debug terkompilasi tanpa error.
