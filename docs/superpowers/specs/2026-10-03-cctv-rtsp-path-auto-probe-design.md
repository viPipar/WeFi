# Spesifikasi Desain: Auto-Probe RTSP Stream Paths untuk CCTV Generic / White-Label / OEM

## 1. Latar Belakang & Masalah
Di pasar kamera keamanan, sebagian besar CCTV "merek lokal / sendiri" (OEM / White-label seperti Xiongmai, HiSilicon, Anyka, V380, ESCAM, Besder) tidak mempublikasikan dokumentasi RTSP URL mereka secara transparan kepada pengguna.
Akibatnya:
- Pengguna harus menebak apakah path stream kameranya adalah `/live/ch0`, `/stream1`, `/11`, `/h264Preview_01_main`, atau `/onvif1`.
- Pengujian di aplikasi mobile memakan waktu dan rentan *trial-and-error* yang melelahkan.

## 2. Solusi Teknis (Arsitektur Auto-Probe)
Aplikasi WeFi menambahkan fitur **Smart RTSP Path Probe**:
1. Menyiapkan daftar 12 *most-common generic RTSP stream paths* di industri CCTV OEM.
2. Mengirimkan permintaan TCP `DESCRIBE` standar RTSP (RFC 2326) secara asinkron (concurrency terkelola) ke port 554 target.
3. Menganalisis respon status code kamera:
   - `RTSP/1.0 200 OK`: Path ditemukan dan aliran video **terbuka bebas tanpa kata sandi**!
   - `RTSP/1.0 401 Unauthorized`: Path **valid** pada kamera, tetapi kamera memerlukan otentikasi Digest/Basic.
   - `RTSP/1.0 404 Not Found` / `400 Bad Request`: Path tidak didukung oleh kamera.
4. Menampilkan hasil deteksi otomatis ke UI dengan status visual (Bebas / Terkunci Kredensial).
5. Pengguna cukup mengetuk path yang ditemukan untuk otomatis memuat dan memutar video di dalam aplikasi.

## 3. Daftar Path Target Utama
```kotlin
val COMMON_RTSP_PATHS = listOf(
    "live/ch0",                           // Xiongmai (XM), Generic OEM Main
    "live/ch1",                           // Xiongmai (XM) Sub stream
    "stream1",                            // Generic Chinese IP Cam Main
    "stream2",                            // Generic Chinese IP Cam Sub
    "11",                                 // HiSilicon SoC Mainstream
    "12",                                 // HiSilicon SoC Substream
    "h264Preview_01_main",                // High-res OEM IP Cam
    "onvif1",                             // ONVIF Profile S Stream 1
    "live.sdp",                           // Linux Busybox IP Camera
    "media.sdp",                          // Generic RTSP Server
    "Streaming/Channels/101",             // Hikvision / Ezviz OEM
    "cam/realmonitor?channel=1&subtype=0" // Dahua / Imou OEM
)
```

## 4. UI / UX Design (`clean-ui-procedural`)
- Tombol aksi: "Deteksi Otomatis (Auto-Scan Path)" dengan ikon radar/search.
- Indikator pemindaian: Progress indikator halus dengan estimasi progres.
- Chip hasil:
  - Hijau/Amber untuk path yang merespon `200 OK` (Bebas) atau `401` (Valid, perlu sandi).
  - Ketukan pada chip langsung mengisi `cctvCustomPath` dan memilih preset `Kustom`, sehingga video live langsung dapat diputar dengan sekali klik.
