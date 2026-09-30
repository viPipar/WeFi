# Speksifikasi Desain: Around Check Mode Hybrid (DFS + BFS) & Jaminan 100% Offline

## 1. Ringkasan Eksekutif
Fitur **Mode Hybrid** adalah strategi penelusuran nirkabel otomatis tingkat lanjut pada tab *Around Check* WeFi Analyzer yang menggabungkan keunggulan **DFS (Depth-First Search)** dan **BFS (Breadth-First Search)**:
- **DFS Level Router:** Setiap router lab yang terdeteksi diuji secara mendalam menggunakan koleksi kata sandi modul praktikum (56 passphrase).
- **BFS Level Jaringan:** Penelusuran dilakukan secara sekuensial bergantian dari Router 1, Router 2, hingga Router $N$ sampai seluruh router di area lab selesai diuji atau dihentikan secara manual oleh pengguna.
- **Badge Status Per-Router:** Setiap kartu router di daftar pemindaian menampilkan badge status visual:
  - 🟢 **Kata Sandi Ditemukan:** Menampilkan password yang cocok dan tombol salin.
  - ⚪ **Passphrase Tidak Ditemukan:** Menandakan 56 kata sandi telah selesai diuji dan tidak ada yang cocok.
  - 🔵 **Sedang Diuji:** Menampilkan indeks kata sandi yang sedang berjalan secara live.
- **Jaminan 100% Offline:** Seluruh fungsi pemindaian sinyal, analisis saluran, kalkulasi kualitas, pengujian asosiasi 802.11, dan penyimpanan audit log beroperasi sepenuhnya tanpa membutuhkan koneksi internet WAN. Speed Test dilengkapi penanganan offline yang aman (*graceful*).

---

## 2. Tujuan & Non-Tujuan (Goals & Non-Goals)

### Goals:
1. Menambahkan `AroundCheckMode.HYBRID` ke dalam model dan segmented switcher antarmuka.
2. Menyediakan tombol cepat pemuatan template praktikum (56 passphrase) pada panel Mode Hybrid.
3. Menjalankan penelusuran multi-router multi-passphrase otomatis dengan perlindungan hardware (jeda pacing aman 2–3s antar router dan quench delay).
4. Melewati (*skip*) router yang sudah tersimpan di Vault Terverifikasi untuk menghemat waktu pengujian lab.
5. Menyimpan status hasil pengujian pada `StateFlow<Map<String, HybridRouterStatus>>` di `AroundCheckViewModel` agar badge tetap tampil persisten selama sesi aplikasi.
6. Memastikan `SpeedTestRepositoryImpl` menangani kondisi offline secara elegan tanpa crash saat perangkat terputus dari internet luar.

### Non-Goals:
- Tidak mengubah logika internal BFS dan DFS manual yang sudah berjalan stabil.
- Tidak menyimpan password yang salah/gagal ke memori permanen disk (hanya password yang berhasil yang disimpan ke Vault).
- Tidak melakukan brute-force tanpa jeda hardware yang dapat merusak atau memicu DoS pada access point lab.

---

## 3. Arsitektur & Model Data

### 3.1 Model Status Hybrid (`HybridRouterStatus`)
Dibuat di `app/src/main/java/com/wefi/analyzer/domain/model/HybridRouterStatus.kt`:
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

### 3.2 Pembaruan Enum Mode (`AroundCheckMode.kt`)
```kotlin
package com.wefi.analyzer.domain.model

enum class AroundCheckMode {
    BFS,    // 1 Password -> Banyak Router
    DFS,    // Banyak Password -> 1 Router
    HYBRID  // Banyak Password -> Banyak Router (DFS + BFS)
}
```

---

## 4. Alur Kerja Logika Traversal (State Machine)

### 4.1 Traversal Loop pada `AroundCheckViewModel`
```
[User tekan "Mulai Hybrid"]
          │
          ▼
Ambil daftar Wi-Fi hasil scan (Router 1 .. N)
Ambil daftar 56 passphrase valid
          │
          ▼
Loop Router [index = 0 until N]:
  ├── Apakah router sudah terverifikasi di Vault?
  │     ├── YA: Set status router = VerifiedFromVault, lanjut router berikutnya
  │     └── TIDAK: Lanjut uji DFS
  │
  ├── Set status router = Testing(0, 56)
  │
  ├── Loop Passphrase [passIndex = 0 until 56]:
  │     ├── Hubungkan ke router dengan passphrase[passIndex]
  │     ├── Tunggu status koneksi (Connected / Failed / Rejected / Cooldown)
  │     │
  │     ├── Status == Connected:
  │     │     ├── Simpan ke Vault (VerifiedLabRouter)
  │     │     ├── Set status router = Found(passphrase)
  │     │     ├── Tampilkan Goal Snackbar / Banner
  │     │     └── BREAK loop passphrase -> Lanjut Router berikutnya!
  │     │
  │     └── Status != Connected:
  │           └── Lanjut ke passphrase berikutnya (dengan pacing delay 2s)
  │
  ├── Jika seluruh 56 passphrase selesai dan tidak ada yang cocok:
  │     └── Set status router = NotFound(56)
  │
  └── Jeda aman router (2-3 detik) sebelum berpindah ke router berikutnya
```

### 4.2 Penghentian (Cancellation)
- Traversal dapat dibatalkan kapan saja oleh pengguna dengan menekan tombol **"Batal"** atau **"Hentikan"**.
- Ketika dibatalkan, router yang sedang diuji dikembalikan statusnya ke `Idle`, sementara router yang sudah selesai diuji tetap mempertahankan status `Found` atau `NotFound`.

---

## 5. Desain Tampilan Antarmuka (UI/UX)

Mematuhi pedoman **`clean-ui-procedural`** dan standar desain Blynk.io:
1. **Segmented Switcher 3 Opsi:**
   - `[ Mode BFS ]` `[ Mode DFS ]` `[ Mode Hybrid ]`
   - Menggunakan transisi seleksi halus (`RoundedCornerShape(9.dp)`).
2. **Panel Kontrol Mode Hybrid:**
   - Input CSV Passphrase (dengan tombol *"Muat Template Praktikum"*).
   - Indikator ringkasan: jumlah passphrase terdeteksi dan jumlah router target di sekitar.
   - Tombol Aksi Utama: *"Mulai Traversal Hybrid"*.
3. **Badge Kartu Router ([WifiScanItemCard]):**
   - **Found (Hijau):** Pill badge berlatar `#DCFCE7` dengan teks hijau tua `#15803D` memuat ikon centang, kata sandi, dan tombol satu-sentuhan salin.
   - **NotFound (Muted Slate):** Pill badge berlatar `#F1F5F9` dengan teks abu-abu `#64748B` bertuliskan *"56 Passphrase Tidak Cocok"*.
   - **Testing (Biru Animasi):** Pill badge berlatar `BlynkBlueTint` dengan progress ring kecil bertuliskan *"Menguji [K/56]..."*.

---

## 6. Jaminan 100% Operasional Offline

1. **Pemindaian & Analisis:**
   - Menggunakan API bawaan `WifiManager` Android yang berkomunikasi langsung dengan chip antena Wi-Fi ponsel. Tidak memerlukan server atau internet.
2. **Pengujian 4-Way Handshake:**
   - Menggunakan `WifiNetworkSpecifier` dan `ConnectivityManager` untuk bertukar frame EAPOL/WPA dengan Access Point fisik di lab.
3. **Penyimpanan Lokal:**
   - SharedPreferences & JSON terenkripsi di sandbox aplikasi lokal (`context.filesDir`).
4. **Resiliensi Speed Test (`SpeedTestRepositoryImpl.kt`):**
   - Ditambahkan pengecekan konektivitas sebelum inisiasi HTTP Cloudflare. Jika perangkat sedang terhubung ke router lab tanpa akses gateway internet WAN, sistem tidak melempar `UnknownHostException` yang merusak state, melainkan memancarkan `SpeedTestState.OfflineLabMode("Jaringan Lokal Lab - Tidak Ada Akses Internet Luar")`.

---

## 7. Rencana Pengujian (Testing Strategy)

1. **Unit Test di `AroundCheckViewModelTest.kt`:**
   - `startHybridTraversal_findsPasswordOnFirstRouter_andProceedsToSecondRouter()`
   - `startHybridTraversal_whenAllPasswordsFail_setsNotFoundBadge_andProceeds()`
   - `startHybridTraversal_skipsAlreadyVerifiedVaultRouters()`
   - `cancelTraversal_haltsHybridTraversalImmediately()`
2. **Unit Test di `SpeedTestRepositoryTest.kt`:**
   - `runSpeedTest_whenOffline_returnsGracefulOfflineStateWithoutCrashing()`
3. **Integrasi UI Test & APK Build:**
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
