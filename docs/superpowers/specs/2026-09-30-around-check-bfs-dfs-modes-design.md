# Around Check Dual-Mode (BFS & DFS) Architecture & Design Specification

**Date:** 2026-09-30  
**Status:** Validated & Ready for Planning  
**Target:** Android 10+ (API 29+) Official Wireless Connectivity Architecture  

---

## 1. Executive Summary & Goals

Aplikasi **Project WeFi** ditingkatkan pada modul **Around Check** untuk mendukung audit laboratorium nirkabel dunia nyata berbasis dua strategi pencarian traversal:

1. **Mode BFS (Breadth-First Search - 1 Password ke Banyak Router):**
   - Pengguna memasukkan **1 passphrase target**.
   - Sistem menguji password tersebut secara horizontal (lebar) ke seluruh router lab yang terdeteksi secara bergantian.
   - Saat ditemukan router yang cocok dan koneksi berhasil, traversal berhenti (*Goal Found*), dan router tersebut otomatis ditandai serta disimpan ke dalam **Verified Router Vault**.

2. **Mode DFS (Depth-First Search - Banyak Password ke 1 Router):**
   - Pengguna memasukkan **daftar banyak password** menggunakan format teks fleksibel (CSV, titik koma `;`, koma `,`, atau baris baru).
   - Pengguna memilih **1 router lab target** dari daftar Wi-Fi yang terdeteksi (melalui tap kartu atau menu dropdown).
   - Sistem menguji daftar password secara vertikal (mendalam) ke router terpilih satu per satu dengan jeda pengaman (*safe pacing*).
   - Saat salah satu password cocok dan koneksi berhasil, sistem langsung berhenti (*Goal Found*), mengumumkan keberhasilan, dan menyimpan kredensial ke **Verified Router Vault**.

---

## 2. Lab Hardware & Driver Safeguard Architecture

Untuk mencegah kerusakan subsistem ponsel dan pemblokiran perangkat oleh router laboratorium (*anti-bruteforce lockout*), sistem menerapkan proteksi bertingkat:

```
+-----------------------------------------------------------------------------------+
|                           LAB SAFETY ARCHITECTURE                                 |
+-----------------------------------------------------------------------------------+
|  1. Router Hardware Safeguard   -> Circuit Breaker & Safe Pacing (Anti-MAC Ban)   |
|  2. Android HAL Driver Safety   -> Cooldown Settle & Single-Callback Gate         |
|  3. CSV Sanitizer Engine        -> Filter <8 char, Deduplicate, Multi-Delimiter   |
|  4. Target AP Environmental     -> RSSI Threshold, Signal Drop & Security Check   |
|  5. Verified Vault Integrity    -> Atomic SharedPreferences + Anti-Corruption     |
+-----------------------------------------------------------------------------------+
```

### 2.1 Proteksi Router Lab (Hardware Level)
- **Safe Inter-Attempt Pacing (2–3 Detik):** Pada Mode DFS, pengujian password berturut-turut pada router yang sama diberikan jeda aman 2–3 detik disertai hitung mundur visual (*countdown*). Ini mencegah Access Point (AP) lab memicu mekanisme mitigasi intrusi (seperti *MAC Address Blacklisting* atau *Deauth Frame Flood*).
- **Circuit Breaker (10 Kegagalan Beruntun):** Jika terjadi 10 kegagalan password berturut-turut pada satu router, loop DFS otomatis dijeda (*pause*) dan menampilkan dialog: *"10 password gagal berturut-turut. Router lab mungkin memiliki proteksi pembatasan koneksi. Lanjutkan atau hentikan?"*
- **Penanganan AP Open & WEP:** Jaringan `OPEN` langsung dihubungkan tanpa loop password. Jaringan `WEP` ditolak di awal dengan pesan informatif karena tidak didukung oleh Android 10+.

### 2.2 Proteksi Subsistem & Driver Android (Software Level)
- **HAL Driver Quench (500ms Settle Delay):** Sebelum memulai request koneksi baru, callback aktif lama dilepaskan (`unregisterNetworkCallback`) dan diberikan jeda 500ms agar driver Wi-Fi (Qualcomm/MediaTek) kembali ke kondisi stabil (`IDLE`), mencegah *firmware deadlock*.
- **Single-Job Execution Gate:** Setiap sesi BFS/DFS dilindungi oleh pembatalan coroutine aktif terdahulu secara eksklusif. Tidak ada dua request koneksi yang dapat berjalan simultan.
- **Process Network Isolation:** Hanya trafik dari sesi yang berhasil yang diikat ke soket aplikasi (`bindProcessToNetwork`), dan selalu dibersihkan (`null`) saat terputus atau dibatalkan.

---

## 3. Data Layer & Abstraksi Komponen

### 3.1 Domain Models
- **`AroundCheckMode`**:
  ```kotlin
  enum class AroundCheckMode {
      BFS, // 1 Password -> Multi Router
      DFS  // Multi Password -> 1 Router
  }
  ```
- **`VerifiedLabRouter`**:
  ```kotlin
  data class VerifiedLabRouter(
      val bssid: String,
      val ssid: String,
      val workingPassword: String,
      val discoveredTimestamp: Long,
      val securityType: WifiSecurityType
  )
  ```
- **`DfsParseResult`**:
  ```kotlin
  data class DfsParseResult(
      val validPasswords: List<String>,
      val skippedTooShortCount: Int,
      val duplicateCount: Int
  )
  ```

### 3.2 CSV Sanitizer Engine (`DfsPasswordSanitizer`)
- Memecah masukan menggunakan regex multi-pemisah: `[;,\\r\\n]+`.
- Melakukan `.trim()` pada setiap item.
- Menyaring hanya kata sandi yang memenuhi standar WPA2/WPA3: `length in 8..63`.
- Menghapus duplikasi dengan `.distinct()`.
- Menghasilkan statistik untuk feedback instan kepada pengguna di bawah TextField.

### 3.3 Verified Router Vault (`VerifiedWifiStore`)
- Menyimpan riwayat router yang berhasil di-*unlock* ke dalam `SharedPreferences` privat (`wefi_verified_vault`).
- Menggunakan JSON serialization (`JSONArray`/`JSONObject`) yang tahan korupsi data (*defensive fallback*).
- Menyediakan fungsi:
  - `saveVerifiedRouter(router: VerifiedLabRouter)`
  - `getAllVerifiedRouters(): List<VerifiedLabRouter>`
  - `isRouterVerified(bssid: String, ssid: String): Boolean`
  - `getVerifiedPassword(bssid: String, ssid: String): String?`
  - `removeVerifiedRouter(bssid: String)`
  - `clearAll()`

---

## 4. State Management di `AroundCheckViewModel`

ViewModel mengelola status traversal secara reaktif melalui `StateFlow`:

```kotlin
// Mode aktif saat ini
val selectedMode: StateFlow<AroundCheckMode>

// State Mode BFS
val bfsPasswordInput: StateFlow<String>
val isBfsPasswordVisible: StateFlow<Boolean>

// State Mode DFS
val dfsCsvInput: StateFlow<String>
val dfsTargetItem: StateFlow<WifiScanItem?>
val dfsParsedStats: StateFlow<DfsParseResult>

// Progress Traversal Terpadu
val isTraversalRunning: StateFlow<Boolean>
val traversalCurrentIndex: StateFlow<Int>
val traversalTotalCount: StateFlow<Int>
val traversalCurrentLabel: StateFlow<String>
val traversalMessage: StateFlow<String>
val goalFoundRouter: StateFlow<VerifiedLabRouter?>

// Vault State
val verifiedRouters: StateFlow<List<VerifiedLabRouter>>
```

### 4.1 Alur Logika Traversal

#### Alur BFS:
1. Pastikan `bfsPasswordInput` tidak kosong (jika ada AP terenkripsi) dan daftar scan tidak kosong.
2. Iterasi kandidat AP `[0 until scanItems.size]`.
3. Periksa status cooldown router dari throttler.
4. Hubungkan via `connector.connect(candidate.ssid, password, candidate.security)`.
5. Tunggu hasil (`Connected`, `Rejected`, `Failed`, `Timeout`).
6. Jika `Connected`:
   - Simpan router ke `VerifiedWifiStore`.
   - Set `goalFoundRouter` dan hentikan BFS (*Goal Found*).
   - Kirim notifikasi Snackbar sukses.
7. Jika gagal: tunggu 1.5 detik, lalu lanjut ke kandidat AP berikutnya.

#### Alur DFS:
1. Pastikan `dfsTargetItem` telah dipilih dan `validPasswords.isNotEmpty()`.
2. Iterasi daftar password `[0 until validPasswords.size]`.
3. Terapkan jeda aman 2–3 detik hitung mundur antar password.
4. Bersihkan callback lama (`connector.cancel()`) dan tunggu 500ms quench delay.
5. Hubungkan via `connector.connect(target.ssid, currentPassword, target.security)`.
6. Jika `Connected`:
   - Simpan router beserta password ke `VerifiedWifiStore`.
   - Set `goalFoundRouter` dan hentikan DFS (*Goal Found*).
   - Tampilkan banner kemenangan dengan tombol salin.
7. Jika gagal:
   - Tambah counter gagal berturut-turut.
   - Jika mencapai 10 kegagalan: picu Circuit Breaker.
   - Lanjutkan ke password berikutnya hingga selesai.

---

## 5. UI/UX Design System (Modern Blynk & clean-ui-procedural Compliance)

### 5.1 Struktur Layout Layar
1. **Top Segmented Tab Switcher:**
   - Pill selector elegan dengan warna tema `BlynkBlue` dan transisi halus.
   - Tab 1: **"Mode BFS (Jelajah)"**
   - Tab 2: **"Mode DFS (Uji Target)"**
2. **Form Input Dinamis:**
   - **Mode BFS Panel:** Single passphrase input dengan toggle mata intip, tombol aksi "Mulai BFS", dan badge status scan.
   - **Mode DFS Panel:**
     - Target Router Selector: Chip pemilih router yang dapat ditap langsung dari list atau melalui dropdown.
     - Multi-line CSV Input: TextField fleksibel dengan placeholder format contoh (`lab123;labkomputer;ipb123`).
     - Feedback Chip: Menampilkan teks info dinamis (misal: *"3 password siap diuji, 1 dilewati (<8 char)"*).
     - Tombol "Mulai DFS (X Password)".
3. **Traversal Progress Banner (Live Animation):**
   - Menampilkan persentase progress bar horizontal.
   - Counter `[X/Total]` dengan label router/password yang sedang aktif diuji.
   - Tombol bahaya merah "Hentikan Pengujian".
4. **Celebration Card ("Goal Ditemukan!"):**
   - Background hijau lembut (`#DCFCE7`) dengan border hijau solid.
   - Menampilkan nama SSID yang berhasil ditembus dan password yang cocok.
   - Tombol utama **"Salin Password"** (otomatis menyalin ke clipboard Android).
5. **Daftar Kartu Wi-Fi (Scan Items):**
   - Kartu yang sudah terdaftar di `VerifiedWifiStore` memiliki badge hijau solid **"TERVERIFIKASI"**.
   - Pada kartu terverifikasi, terdapat tombol langsung untuk menyalin password yang tersimpan.
   - Pada Mode DFS, kartu yang sedang menjadi target terpilih diberikan border highlight `BlynkBlue` (2.dp).

---

## 6. Testing & Quality Assurance Plan

1. **`DfsPasswordSanitizerTest`**:
   - Memverifikasi parsing delimiter titik koma, koma, baris baru, dan campuran.
   - Memverifikasi filter password < 8 karakter dan > 63 karakter.
   - Memverifikasi penghapusan duplikasi dan spasi ekstra.
2. **`VerifiedWifiStoreTest`**:
   - Memverifikasi penyimpanan, pembacaan, dan penghapusan entri router.
   - Memverifikasi pencegahan duplikasi data.
   - Memverifikasi ketahanan terhadap JSON rusak (*corrupted recovery*).
3. **`AroundCheckViewModelTest`**:
   - Memverifikasi alur traversal BFS berhenti saat kandidat berhasil tersambung.
   - Memverifikasi alur traversal DFS mencoba password berikutnya saat password pertama ditolak.
   - Memverifikasi aktivasi Circuit Breaker setelah kegagalan beruntun.
   - Memverifikasi pembatalan bersih (*clean cancellation*) saat tombol batal ditekan.
4. **Kompilasi & Build:**
   - 100% lulus seluruh rangkaian *unit test* Gradle.
   - `assembleDebug` berhasil membangun `app-debug.apk`.
