# Spec Design: Device Authentication Posture Assessment & Authorized CCTV Lab Monitoring

**Tanggal:** 2026-10-03  
**Penulis:** Antigravity Team  
**Status:** In Progress / Approved for Implementation  
**Konteks Operasional:** Pengujian keamanan defensif dalam lingkungan terisolasi milik pengguna sendiri (*fully authorized isolation lab*).

---

## 1. Analisis Masalah & Temuan Bug

### Bug 1: Tombol Web Admin Hilang / Tersembunyi
- **Lokasi:** `NetworkDiscoveryScreen.kt:896` & `1135`.
- **Root Cause:** 
  Tombol `Web Admin` hanya ditampilkan jika `host.openPorts.firstOrNull { it.port in listOf(80, 443, 8080, 8443) }?.port != null`.
  Pada saat pertama kali host ditemukan melalui Ping Sweep, `openPorts` masih berupa daftar kosong (`emptyList()`). 
  Jika pengguna belum menjalankan Deep Scan atau jika port web berada di port non-standar (contoh: 8000, 8081, 8888, 9000), tombol Web Admin sama sekali tidak pernah muncul di UI.

### Bug 2: Timeout Port Scan Terlalu Agresif (500 ms)
- **Lokasi:** `NetworkDiscoveryRepositoryImpl.kt:290`.
- **Root Cause:**
  `socket.connect(InetSocketAddress(hostIp, port), 500)`. 
  Ketika 64 coroutine port scan dijalankan serentak di Wi-Fi lab, router dan kamera CCTV berdaya komputasi rendah sering membutuhkan waktu 600–1000 ms untuk merespons TCP SYN/ACK. Akibatnya, port 80/443 menghasilkan `SocketTimeoutException` dan dicatat sebagai `PortStatus.TIMEOUT`, sehingga tidak masuk ke daftar `openPorts`.
- **Solusi:** Tingkatkan connect timeout ke `1200 ms` untuk port scan deep probe.

### Bug 3: Timeout Ping Probe Terlalu Rendah (200 ms)
- **Lokasi:** `NetworkDiscoveryRepositoryImpl.kt:237`.
- **Root Cause:**
  Fallback probe `socket.connect(..., 200)` terlalu pendek untuk perangkat IoT/CCTV yang sedang berada dalam mode sleep/low-power.
- **Solusi:** Tingkatkan timeout connect fallback ke `600 ms`.

### Bug 4: HTTP Banner Grab Menggunakan Metode `HEAD` yang Sering Ditolak
- **Lokasi:** `NetworkDiscoveryRepositoryImpl.kt:674`.
- **Root Cause:**
  Mengirimkan `HEAD / HTTP/1.1`. Banyak web server embedded router/CCTV (GoAhead, RomPager, BusyBox httpd) tidak mendukung `HEAD` dan merespons dengan `501 Not Implemented` atau me-reset koneksi TCP (`RST`).
- **Solusi:** Tambahkan fallback ke `GET / HTTP/1.1` dengan header `Range: bytes=0-1024` jika `HEAD` gagal.

### Bug 5: Ketiadaan Fitur Uji Keterbukaan Autentikasi & Monitoring CCTV
- **Kebutuhan:**
  Pengguna memerlukan verifikasi apakah perangkat lab (seperti Router dan CCTV) memiliki celah *unauthenticated exposure* (bisa diakses langsung tanpa kredensial) atau terlindungi oleh mekanisme autentikasi resmi (Basic/Digest/Form).
  Pengguna juga membutuhkan pemutar video tersemat langsung di aplikasi (*In-App Embedded Live Player*) serta opsi peluncur RTSP eksternal.

---

## 2. Arsitektur Domain & Model Data

### A. Model Data Baru (`NetworkDiscoveryModels.kt`)

```kotlin
enum class AuthStatus {
    UNPROTECTED_EXPOSURE, // 200 OK tanpa password - KRITIS
    PROTECTED_CREDENTIALS, // 401 Unauthorized / Form login - AMAN
    CONNECTION_REFUSED,    // Port tertutup / RST
    TIMEOUT,               // Tidak ada respon
    UNKNOWN                // Respon tidak dikenali
}

data class AuthPostureResult(
    val ip: String,
    val port: Int,
    val protocol: String, // "HTTP", "HTTPS", "RTSP"
    val status: AuthStatus,
    val httpStatusCode: Int? = null,
    val authHeader: String? = null, // WWW-Authenticate jika ada
    val serverBanner: String? = null,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

### B. Interface Repository (`NetworkDiscoveryRepository.kt`)
Menambahkan fungsi:
```kotlin
suspend fun testAuthPosture(hostIp: String, port: Int, protocolHint: String? = null): AuthPostureResult
```

### C. Implementasi Logika Uji Keterbukaan Autentikasi (`NetworkDiscoveryRepositoryImpl.kt`)
1. **HTTP/HTTPS Handshake (Port 80, 443, 8080, 8000, 8443, dll.):**
   - Melakukan probe HTTP `GET / HTTP/1.1` atau `HEAD / HTTP/1.1`.
   - Jika menerima kode status `401 Unauthorized`:
     - Status: `AuthStatus.PROTECTED_CREDENTIALS`.
     - Keterangan: *"Terlindungi: Memerlukan Autentikasi Resmi (HTTP 401: ${authHeader})"*
   - Jika menerima kode status `200 OK` atau `302/301` ke halaman dashboard:
     - Jika respon tidak memuat form login atau auth prompt, tandai `AuthStatus.UNPROTECTED_EXPOSURE`.
     - Keterangan: *"Peringatan: Portal Web Terbuka Bebas Tanpa Autentikasi (HTTP 200 OK)"*.
   - Jika koneksi ditolak atau timeout: tandai status yang sesuai.

2. **RTSP Handshake (Port 554):**
   - Mengirimkan string RTSP handshake:
     `OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: WeFi-LabAudit/1.0\r\n\r\n`
     diikuti oleh probe path stream:
     `DESCRIBE rtsp://$hostIp:$port/ RTSP/1.0\r\nCSeq: 2\r\nUser-Agent: WeFi-LabAudit/1.0\r\n\r\n`
   - Jika merespons `RTSP/1.0 401 Unauthorized`:
     - Status: `AuthStatus.PROTECTED_CREDENTIALS`.
     - Keterangan: *"Stream RTSP Terkunci: Memerlukan Kredensial Kamera"*.
   - Jika merespons `RTSP/1.0 200 OK`:
     - Status: `AuthStatus.UNPROTECTED_EXPOSURE`.
     - Keterangan: *"PERINGATAN: Stream RTSP Terbuka Bebas Tanpa Password (Public Video Exposure)"*.

---

## 3. Desain Antarmuka Pengguna & In-App Player (`NetworkDiscoveryScreen.kt` & `CctvLivePlayer.kt`)

Sesuai dengan pedoman **clean-ui-procedural**:
1. **Tombol Web Admin Permanen & Port Selector Dialog:**
   - Tombol "Web Admin" selalu terlihat pada BottomSheet rincian perangkat.
   - Jika `openPorts` memiliki port web (80/443/8080/8000), default ke port tersebut.
   - Jika belum ada atau pengguna ingin port lain, mengetuk tombol Web Admin menampilkan pop-up cepat berisi chip preset: `80 (HTTP)`, `443 (HTTPS)`, `8080 (Alt-HTTP)`, `8000 (Hik/Dahua Web)`, serta kolom input manual `Port Kustom`.
   - Tombol aksi "Buka Browser" meluncurkan `Intent(Intent.ACTION_VIEW, Uri.parse(url))` dengan aman.

2. **Kartu Uji Keterbukaan Akses (Auth Posture Card):**
   - Menampilkan tombol "Uji Keterbukaan Akses (Auth Posture)".
   - Ketika ditekan, menampilkan status visual dengan warna kontras tajam:
     - Merah: ⚠️ **TERBUKA BEBAS (UNAUTHENTICATED EXPOSURE)**
     - Hijau: 🔒 **TERLINDUNGI PASSWORD (401 UNAUTHORIZED)**
     - Abu-abu/Biru: ℹ️ **PORT TERTUTUP / TIMEOUT**

3. **Kartu Monitoring CCTV Lab (Untuk Kamera CCTV/NVR atau Port 554):**
   - Jika `probableDeviceType == "Kamera CCTV / NVR"` atau `openPorts` memuat port 554/3702/37777:
     - Tampilkan kartu beraksen rapi "Akses & Monitoring CCTV Lab".
     - **Tombol Uji Stream RTSP:** Mengetes unauthenticated exposure stream port 554.
     - **Tombol Live Stream RTSP:** Membuka dialog pemilih format RTSP (Generic, Hikvision, Dahua, ONVIF) dengan opsi:
       - **Mode Tonton In-App (Langsung di Aplikasi):** Membuka dialog player tertanam (`CctvLivePlayer`) berbasis AndroidX Media3 ExoPlayer RTSP.
       - **Mode Buka di Pemutar Eksternal:** Meluncurkan Intent ke VLC/MX Player sebagai opsi cadangan.
     - **Tombol Web Console CCTV:** Membuka web interface kamera.

4. **In-App Embedded RTSP Video Player (`CctvLivePlayer.kt` via AndroidX Media3):**
   - Menggunakan pustaka resmi Google `androidx.media3:media3-exoplayer:1.3.1`, `media3-exoplayer-rtsp:1.3.1`, dan `media3-ui:1.3.1`.
   - Mengintegrasikan `PlayerView` ke dalam Jetpack Compose melalui `AndroidView`.
   - Konfigurasi `RtspMediaSource.Factory().setForceUseRtpTcp(true)` untuk menjamin stabilitas streaming video tanpa kehilangan paket di jaringan Wi-Fi lokal.
   - Penanganan siklus hidup (*Lifecycle-Aware*): `player.release()` otomatis dipanggil saat dialog ditutup (`onDispose`) untuk mencegah memory leaks.

---

## 4. Rencana Verifikasi & Kualitas

1. Unit Test di `NetworkDiscoveryLogicTest` untuk menguji:
   - Parsing dan klasifikasi `AuthStatus.UNPROTECTED_EXPOSURE` saat HTTP 200 OK tanpa auth.
   - Parsing dan klasifikasi `AuthStatus.PROTECTED_CREDENTIALS` saat HTTP 401 Unauthorized.
   - Handshake RTSP 200 vs 401.
2. Unit Test di `NetworkDiscoveryViewModelTest` untuk menguji:
   - State `authPostureState` terupdate dengan benar saat `testHostAuthPosture` dipanggil.
3. Menjalankan build lengkap:
   - `.\gradlew.bat testDebugUnitTest --no-daemon`
   - `.\gradlew.bat assembleDebug --no-daemon`
