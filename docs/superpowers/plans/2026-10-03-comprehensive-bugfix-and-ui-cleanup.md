# Comprehensive Bugfix and Clean UI/UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Menemukan dan menuntaskan seluruh bug (Critical, High, Medium, Low) di seluruh modul WeFi, mengoptimalkan arsitektur sesuai Android best practices, serta membersihkan UI/UX agar terlihat clean, aman, dan konsisten ala Blynk IoT & Slay the Spire 2 aesthetic.

**Architecture:** Menerapkan Clean Architecture berbasis Android Jetpack Compose, Kotlin Coroutines & StateFlow. Memperbaiki handling state jaringan sistem pada `CurrentConnectionRepositoryImpl`, mengeliminasi race conditions pada `NetworkDiscoveryViewModel`, menyelaraskan state kosong & offline di `ChannelRating`, merapikan tata letak metrik (2x2 grid) pada `ApListScreen`, mengamankan batas Canvas pada `ChannelGraphCanvas`, menyesuaikan safe-zone padding FAB pada `MorphingHelpFab`, dan membersihkan dead code `AuditLogBottomSheet`.

**Tech Stack:** Kotlin 1.9+, Android SDK 34 (Jetpack Compose, Material 3, Coroutines, StateFlow, OkHttp 4, Okio, JUnit 4).

**Spec:** Codebase Audit & Best Practice Solutions for WeFi Android Project.

## Global Constraints
- **Platform Floors:** Android Min SDK 24, Target SDK 34.
- **Language / Idioms:** 100% Kotlin, zero raw concurrency leaks, StateFlow untuk reactive UI.
- **UI/UX Standard:** Mematuhi `clean-ui-procedural` — warna aksen terkontrol (Blynk Blue `#77ADF9`, Ivory `#F8FAFC`, Surface `#FFFFFF`, Border `#E2E8F0`), tidak ada outline neon bertabrakan, safe-zones aman di atas navigasi, dan tipografi Plus Jakarta Sans kontras tinggi.
- **Bahasa Antarmuka:** Seluruh pesan antarmuka pengguna, label status, dan dialog troubleshooting menggunakan **Bahasa Indonesia** yang rapi dan profesional.
- **Verifikasi Sebelum Klaim:** Setiap task wajib lulus unit test (`.\gradlew.bat testDebugUnitTest`) dan verifikasi build APK (`.\gradlew.bat assembleDebug`).

---

### Task 1: Eliminasi Ghost Connected AP State & Lifecycle Leak di `CurrentConnectionRepositoryImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryTest.kt`

**Interfaces:**
- Consumes: Android `WifiManager`, `ConnectivityManager`, `NetworkCapabilities`, `WifiInfo`.
- Produces: `StateFlow<ConnectedNetworkInfo>` yang secara akurat bernilai `isConnected = false` ketika perangkat tidak tersambung ke AP nyata.

- [x] **Step 1: Tulis unit test untuk verifikasi kondisi disconnected tidak menghasilkan ghost AP**

Tambahkan pengujian di `CurrentConnectionRepositoryTest.kt`:

```kotlin
@Test
fun connectedNetworkInfo_whenDisconnected_hasNoConnectedAp() {
    val info = ConnectedNetworkInfo(
        accessPoint = null,
        linkSpeedMbps = 0,
        ipAddress = "0.0.0.0",
        gatewayIp = "0.0.0.0"
    )
    Assert.assertFalse(info.isConnected)
    Assert.assertNull(info.accessPoint)
}
```

- [x] **Step 2: Jalankan test untuk memverifikasi baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.data.repository.CurrentConnectionRepositoryTest"`
Expected: PASS

- [x] **Step 3: Perbaiki logika pemeriksaan koneksi pada `CurrentConnectionRepositoryImpl.kt`**

Di `CurrentConnectionRepositoryImpl.kt`:
1. Ubah pemeriksaan validitas koneksi dari:
   ```kotlin
   val hasValidConnection = isWifiTransport || (ip != "0.0.0.0") || (wifiInfo != null && wifiInfo.networkId != -1)
   if (!hasValidConnection && wifiInfo == null) {
       _connectionInfo.value = ConnectedNetworkInfo()
       return
   }
   ```
   Menjadi pemeriksaan ketat:
   ```kotlin
   val isDisconnectedDummy = wifiInfo == null || wifiInfo.networkId == -1 || wifiInfo.ssid == "<unknown ssid>"
   val hasValidConnection = isWifiTransport && (ip != "0.0.0.0" || !isDisconnectedDummy)

   if (!hasValidConnection || isDisconnectedDummy && !isWifiTransport) {
       _connectionInfo.value = ConnectedNetworkInfo()
       return
   }
   ```
2. Pastikan `networkCallback` hanya disimpan setelah berhasil dipanggil di `registerNetworkCallback`.

- [x] **Step 4: Jalankan unit test untuk memastikan perbaikan sukses**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.data.repository.CurrentConnectionRepositoryTest"`
Expected: PASS

- [x] **Step 5: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryTest.kt
git commit -m "fix: eliminate ghost connected AP state and harden network callback registration"
```

---

### Task 2: Perbaiki Thread Safety Race Condition di `NetworkDiscoveryViewModel`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt`

**Interfaces:**
- Consumes: `NetworkDiscoveryRepository.discoverSsdp()`, `NetworkDiscoveryRepository.discoverMdns()`.
- Produces: Thread-safe `discoveredServices` collection yang aman diakses bersamaan oleh SSDP dan mDNS coroutines.

- [x] **Step 1: Periksa unit test `NetworkDiscoveryViewModelTest.kt` untuk konkurensi**

Jalankan test yang sudah ada:
Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.discovery.NetworkDiscoveryViewModelTest"`
Expected: PASS

- [x] **Step 2: Implementasikan koleksi thread-safe pada `NetworkDiscoveryViewModel.kt`**

Ganti `mutableListOf<ServiceInfo>()` yang non-thread-safe pada baris 73 dengan struktur thread-safe:
```kotlin
import java.util.concurrent.ConcurrentLinkedQueue

// Di dalam startDiscovery():
val discoveredServices = ConcurrentLinkedQueue<ServiceInfo>()
```
Dan iterasi menggunakan `discoveredServices.forEach { srv -> ... }`.

- [x] **Step 3: Tulis unit test baru yang memvalidasi penggabungan servis paralel mDNS dan SSDP**

Tambahkan test di `NetworkDiscoveryViewModelTest.kt` untuk menguji penggabungan simultan tanpa race condition.

- [x] **Step 4: Jalankan seluruh test Network Discovery**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.discovery.NetworkDiscoveryViewModelTest"`
Expected: PASS

- [x] **Step 5: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/discovery/NetworkDiscoveryViewModelTest.kt
git commit -m "fix: resolve thread safety race condition in network discovery service collection"
```

---

### Task 3: Refaktor Logika & UI Rating Kanal (`CalculateChannelRatingUseCase`, `ChannelRatingViewModel`, `ChannelRatingScreen`)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCase.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingViewModel.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingScreen.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCaseTest.kt`

**Interfaces:**
- Consumes: `WifiScannerRepository.scanResults`, `WifiScannerRepository.isWifiEnabled`.
- Produces: Rating kanal realistis (hanya merekomendasikan non-overlapping channels 1, 6, 11 ketika spektrum kosong) dan antarmuka rating yang memiliki penanganan Wi-Fi mati serta status kosong.

- [x] **Step 1: Tulis test kasus spektrum kosong di `CalculateChannelRatingUseCaseTest.kt`**

```kotlin
@Test
fun execute_whenScanListIsEmpty_recommendsOnlyStandardNonOverlappingChannelsIn24GHz() {
    val useCase = CalculateChannelRatingUseCase()
    val ratings = useCase.execute(emptyList(), 2.4)
    val recommended = ratings.filter { it.isRecommended }.map { it.channel }
    
    // Harus hanya merekomendasikan Ch 1, 6, 11 bukan semua 13 kanal
    assertEquals(listOf(1, 6, 11), recommended)
}
```

- [x] **Step 2: Jalankan test untuk melihat kegagalan**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.usecase.CalculateChannelRatingUseCaseTest.execute_whenScanListIsEmpty_recommendsOnlyStandardNonOverlappingChannelsIn24GHz"`
Expected: FAIL (karena kode saat ini menandai semua 13 kanal dengan `isRecommended = true`).

- [x] **Step 3: Implementasikan perbaikan di `CalculateChannelRatingUseCase.kt`**

Jika `apsInBand.isEmpty()`:
- Pada 2.4 GHz, set `isRecommended = true` hanya untuk kanal standard non-overlapping (1, 6, 11) dengan `reason = "Spektrum kosong, kanal 1/6/11 ideal bebas interferensi"`.
- Pada 5 GHz, set `isRecommended = true` untuk kanal pertama dalam blok UNII-1 (36, 40, 44, 48).
Jika `apsInBand.isNotEmpty()`:
- Rekomendasikan kanal dengan bintang tertinggi yang masuk kategori non-overlapping atau kanal paling bersih dengan interferensi minimum.

- [x] **Step 4: Sambungkan `isWifiEnabled` ke `ChannelRatingViewModel.kt` dan update `ChannelRatingScreen.kt`**

1. Di `ChannelRatingViewModel.kt`:
   ```kotlin
   val isWifiEnabled: StateFlow<Boolean> = scannerRepository.isWifiEnabled
   ```
2. Di `ChannelRatingScreen.kt`:
   Tambahkan blok UI ketika `!isWifiEnabled` yang menampilkan `BlynkCard` dengan tombol "Buka Pengaturan Wi-Fi" dan icon `WifiOff` (menyelaraskan dengan `ApListScreen` & `ChannelGraphScreen`).
   Tambahkan penanganan jika `ratings.isEmpty()`.

- [x] **Step 5: Jalankan unit test dan compile**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.domain.usecase.CalculateChannelRatingUseCaseTest"`
Expected: ALL PASS

- [x] **Step 6: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCase.kt app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingViewModel.kt app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingScreen.kt app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateChannelRatingUseCaseTest.kt
git commit -m "feat: improve channel rating logic on empty spectrum and add wifi-disabled state handling"
```

---

### Task 4: Amankan Batas Canvas & Optimalkan Render di `ChannelGraphCanvas`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphCanvas.kt`

**Interfaces:**
- Consumes: `List<WifiAccessPoint>`, `selectedBandGhz`, `connectedBssid`.
- Produces: Kurva parabola dan indikator plumb-line yang aman dari clipping di sisi tepi layar HP.

- [x] **Step 1: Amankan perhitungan posisi horizontal badge & label puncak**

Di `ChannelGraphCanvas.kt`:
1. Clamp posisi horizontal badge `drawConnectedPlumbLine`:
   ```kotlin
   val badgeWidth = 120.dp.toPx()
   val badgeHeight = 22.dp.toPx()
   val minBadgeLeft = paddingLeft + 4.dp.toPx()
   val maxBadgeLeft = width - paddingRight - badgeWidth - 4.dp.toPx()
   val rawBadgeLeft = centerX - (badgeWidth / 2f)
   val badgeLeft = rawBadgeLeft.coerceIn(minBadgeLeft, maxBadgeLeft)
   val badgeTop = topY - badgeHeight - 6.dp.toPx()
   ```
2. Pusatkan titik indikator vektor dan teks relatif terhadap `badgeLeft + (badgeWidth / 2f)` agar tidak tergeser saat di-clamp.
3. Clamp posisi label SSID teks AP (`labelX`) agar teks pada kanal 1 atau kanal 13 tidak terpotong tepi layar.
4. Reuse objek `Paint` menggunakan `remember` di luar draw block untuk menghindari alokasi GC berulang saat live scanning.

- [x] **Step 2: Jalankan build dan unit tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 3: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphCanvas.kt
git commit -m "fix: clamp badge and label bounds in ChannelGraphCanvas to prevent edge clipping"
```

---

### Task 5: Refaktor Grid Metrik Telemetri 2x2 di `ApListScreen`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt`

**Interfaces:**
- Consumes: `WifiAccessPoint` (RSSI, formattedDistance, channel, band, maxPhyRateMbps).
- Produces: Kartu AP dengan susunan 2 baris x 2 kolom (`2x2 Grid`) yang lega, bebas teks terpotong, dan rapi pada semua lebar layar Android.

- [x] **Step 1: Ubah susunan 1 baris 4 kolom menjadi 2 baris 2 kolom**

Di `ApListScreen.kt` dalam `ApItemCard`:
Ganti baris 376-399 dari:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    BlynkMetricTile(label = "Kekuatan Sinyal", ...)
    BlynkMetricTile(label = "Estimasi Jarak", ...)
    BlynkMetricTile(label = "Kanal & Band", ...)
    BlynkMetricTile(label = "Max PHY Rate", ...)
}
```
Menjadi susunan 2x2:
```kotlin
Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlynkMetricTile(
            label = "Kekuatan Sinyal",
            value = "${ap.rssi}",
            unit = "dBm",
            modifier = Modifier.weight(1f)
        )
        BlynkMetricTile(
            label = "Estimasi Jarak",
            value = ap.formattedDistance,
            valueColor = BlynkBlue,
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlynkMetricTile(
            label = "Kanal & Band",
            value = "Ch ${ap.channel}",
            unit = if (ap.is24GHz) "2.4G" else if (ap.is5GHz) "5G" else "6G",
            modifier = Modifier.weight(1f)
        )
        BlynkMetricTile(
            label = "Max PHY Rate",
            value = "${ap.maxPhyRateMbps}",
            unit = "Mbps",
            modifier = Modifier.weight(1f)
        )
    }
}
```

- [x] **Step 2: Jalankan build dan tes sorting**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.aplist.ApListSortingTest"`
Expected: PASS

- [x] **Step 3: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt
git commit -m "fix: refactor AP card metrics into spacious 2x2 grid to prevent label squishing"
```

---

### Task 6: Perbaiki Penempatan FAB & Safe-Zones di `MorphingHelpFab` dan `BottomNavBar`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/components/MorphingHelpFab.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/navigation/BottomNavBar.kt`

**Interfaces:**
- Consumes: `tabId`, `onHelpClick`.
- Produces: Floating help button dengan padding bawah natural (`16.dp` di atas Bottom Navigation Bar) yang tidak menghalangi konten layar.

- [x] **Step 1: Sesuaikan padding bawah FAB**

Di `MorphingHelpFab.kt`:
Ganti padding baris 74:
```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .padding(end = 16.dp, bottom = 16.dp),
    contentAlignment = Alignment.BottomEnd
)
```

- [x] **Step 2: Bersihkan redundansi di `BottomNavBar.kt`**

Hapus pemanggilan redundant `.filterNotNull()` pada `Screen.items.forEach`.

- [x] **Step 3: Jalankan verifikasi tes navigasi**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.navigation.ScreenTest"`
Expected: PASS

- [x] **Step 4: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/components/MorphingHelpFab.kt app/src/main/java/com/wefi/analyzer/ui/navigation/BottomNavBar.kt
git commit -m "fix: adjust MorphingHelpFab bottom clearance and clean up BottomNavBar"
```

---

### Task 7: Konsolidasi & Bersihkan Dead Code `AuditLogBottomSheet`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AuditLogBottomSheet.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Remove / Refactor: Dead `LabAuditLogEntry` model references jika ada yang terduplikasi.

**Interfaces:**
- Consumes: `List<WifiAuditLogEntry>`.
- Produces: Modal bottom sheet tunggal yang bersih dan reusable untuk menampilkan riwayat audit Around Check.

- [x] **Step 1: Selaraskan `AuditLogBottomSheet.kt` agar menerima `WifiAuditLogEntry`**

Ubah model yang digunakan di `AuditLogBottomSheet.kt` dari `LabAuditLogEntry` menjadi `WifiAuditLogEntry` resmi:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogBottomSheet(
    logs: List<WifiAuditLogEntry>,
    onDismissRequest: () -> Unit,
    onClearLogs: () -> Unit
)
```
Dan tampilkan chip status `WifiAuditResult` (CONNECTED = hijau, REJECTED/FAILED = merah, TIMEOUT = kuning).

- [x] **Step 2: Gunakan `AuditLogBottomSheet` di `AroundCheckScreen.kt`**

Ganti pemanggilan private `AuditLogSheetContent` di `AroundCheckScreen.kt` dengan `AuditLogBottomSheet` yang sudah diselaraskan, lalu hapus duplikasi fungsi private di bagian bawah `AroundCheckScreen.kt`.

- [x] **Step 3: Jalankan seluruh test suite AroundCheck**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest"`
Expected: PASS

- [x] **Step 4: Commit perubahan**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AuditLogBottomSheet.kt app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt
git commit -m "refactor: consolidate AuditLogBottomSheet with WifiAuditLogEntry and eliminate duplicated code"
```

---

### Task 8: Verifikasi Komprehensif Seluruh Modul & Final Build Check

**Files:**
- Semua file proyek.

- [x] **Step 1: Jalankan seluruh unit test suite proyek**

Run: `.\gradlew.bat testDebugUnitTest --no-daemon`
Expected: Seluruh unit test (134+ tests) PASS 100% tanpa error.

- [x] **Step 2: Compile APK Debug untuk memastikan tidak ada lint / manifest / compose build error**

Run: `.\gradlew.bat assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Lakukan audit git status untuk memastikan workspace bersih**

Run: `git status`
Expected: Working tree clean

- [x] **Step 4: Commit dan siapkan laporan akhir penyelesaian tujuan (/goal)**
