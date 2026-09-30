# Real Wi-Fi Audit & UI/UX Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all remaining mock/simulated fallbacks, fix the `CHANGE_NETWORK_STATE` SecurityException, resolve the sequential testing cooldown deadlock, eliminate UI/UX layout overlaps (floating action button collision, card slicing under fixed header, raw technical error strings), and deliver a 100% verified real Android hardware implementation.

**Architecture:** Android official network APIs (`ConnectivityManager.requestNetwork` with `WifiNetworkSpecifier` and `WifiNetworkSuggestion`), reactive StateFlow state machines with dynamic inter-SSID pacing, and Modern Blynk Jetpack Compose design system.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, OkHttp3, Android ConnectivityManager & WifiManager APIs, JUnit4.

**Spec:** User bug report, real device screenshot analysis, and systematic debugging root-cause investigation.

## Global Constraints

- Strictly official Android APIs (no root, no reflection, no private API bypasses).
- Zero mock or fake latency/throughput values (offline/errors must report real 0.0 Mbps / error states).
- Modern Blynk UI design guidelines (clean cards, 100.dp bottom clearance for floating actions, no raw technical stacktraces in UI).
- 100% unit test pass rate with no regressions.

---

### Task 1: Declare `CHANGE_NETWORK_STATE` Permission in Manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:1-20`

**Interfaces:**
- Consumes: Android framework network permission specifications
- Produces: System grant for `ConnectivityManager.requestNetwork` with `TRANSPORT_WIFI`

- [ ] **Step 1: Check existing manifest permissions**

Verify current declared permissions in `app/src/main/AndroidManifest.xml`.

- [ ] **Step 2: Add `android.permission.CHANGE_NETWORK_STATE`**

In `app/src/main/AndroidManifest.xml`, declare:
```xml
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

- [ ] **Step 3: Verify build processes manifest cleanly**

Run: `.\gradlew processDebugMainManifest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix(manifest): declare CHANGE_NETWORK_STATE permission for requestNetwork"
```

---

### Task 2: Replace Mock SpeedTest Fallbacks with Real Error & Offline Handling

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestNetworkTest.kt`

**Interfaces:**
- Consumes: `SpeedTestRepository`
- Produces: `Flow<SpeedTestMetrics>` without fake dummy speeds (0.0 Mbps on failure)

- [ ] **Step 1: Write failing unit test for real failure handling**

In `app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestNetworkTest.kt`, add:
```kotlin
    @Test
    fun speedTestMetrics_onNetworkFailure_doesNotEmitFakeSpeeds() {
        val failedMetrics = SpeedTestMetrics(
            isRunning = false,
            stage = SpeedTestStage.FINISHED,
            downloadMbps = 0.0,
            uploadMbps = 0.0,
            pingMs = 0.0,
            jitterMs = 0.0
        )
        assertEquals(0.0, failedMetrics.downloadMbps, 0.001)
        assertEquals(0.0, failedMetrics.uploadMbps, 0.001)
        assertEquals(0.0, failedMetrics.pingMs, 0.001)
    }
```

- [ ] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.SpeedTestNetworkTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Remove dummy speed fallbacks in SpeedTestRepositoryImpl**

In `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`:
1. If `pingSamples` is empty, report `avgPing = 0.0` and `jitter = 0.0` (do not fake `28.0` ms or `3.5` ms).
2. If download fails, keep `downloadSpeedMbps = 0.0` (remove fake fallback `downloadSpeedMbps = 35.0`).
3. If upload fails, keep `uploadSpeedMbps = 0.0` (remove fake calculation `downloadSpeedMbps * 0.45`).

- [ ] **Step 4: Run unit tests to verify clean compilation**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.SpeedTestNetworkTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/SpeedTestNetworkTest.kt
git commit -m "fix(speedtest): eliminate simulated fallback speeds and report real zero on network failure"
```

---

### Task 3: Resolve Sequential Testing Deadlock & Cooldown Pacing in ViewModel

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Consumes: `WifiConnector.remainingCooldownSeconds(ssid)`, `WifiConnectStatus.Cooldown`
- Produces: Resilient `startSequentialTest()` loop that dynamically waits out cooldowns with live user progress messages and never hangs

- [ ] **Step 1: Write failing unit test for sequential test handling Cooldown**

In `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`, add test case:
```kotlin
    @Test
    fun startSequentialTest_whenCooldownActive_waitsAndRecoversWithoutDeadlock() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Lab-AP-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Lab-AP-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("labSecret")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        // Emulasikan kegagalan kandidat 1
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Lab-AP-1", status = WifiConnectStatus.Failed, message = "Ditolak"))
        testScheduler.advanceUntilIdle()

        // Harus melanjutkan ke AP 2 tanpa menggantung di Cooldown
        assertEquals(1, viewModel.currentCandidateIndex.value)
        assertEquals("Lab-AP-2", fakeConnector.lastConnectSsid)
    }
```

- [ ] **Step 2: Update ViewModel sequential test loop**

In `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`:
1. Before calling `connector.connect(candidate.ssid, ...)`, check:
   ```kotlin
   val cooldownSec = connector.remainingCooldownSeconds(candidate.ssid)
   if (cooldownSec > 0) {
       for (sec in cooldownSec downTo 1) {
           _sequentialTestMessage.value = "Menunggu jeda aman router (${sec}s)..."
           delay(1000L)
       }
   }
   ```
2. In `connectState.first { ... }`, also accept `WifiConnectStatus.Cooldown` or handle it gracefully:
   ```kotlin
   val resultState = connectState.first { state ->
       state.targetSsid == candidate.ssid && (
           state.status == WifiConnectStatus.Connected ||
           state.status == WifiConnectStatus.Rejected ||
           state.status == WifiConnectStatus.Failed ||
           state.status == WifiConnectStatus.Timeout ||
           state.status is WifiConnectStatus.Cooldown
       )
   }
   ```
   If `resultState.status is WifiConnectStatus.Cooldown`, wait the cooldown and retry or advance smoothly without hanging.

- [ ] **Step 3: Run unit tests**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt
git commit -m "fix(aroundcheck): eliminate infinite deadlock in sequential test during throttler cooldown"
```

---

### Task 4: Format Human-Readable Connection Errors in `WifiConnectorImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`

**Interfaces:**
- Consumes: Exceptions from `ConnectivityManager` & `WifiManager`
- Produces: Clean Indonesian user-friendly error messages in `WifiConnectState.message`

- [ ] **Step 1: Implement user-friendly exception mapping in WifiConnectorImpl**

In `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`:
Replace raw `${e.message}` concatenation with:
```kotlin
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai proses koneksi nirkabel", e)
            val friendlyReason = when (e) {
                is SecurityException -> "Izin sistem koneksi jaringan (CHANGE_NETWORK_STATE) belum diberikan."
                is IllegalArgumentException -> "Parameter SSID atau keamanan router tidak valid."
                is IllegalStateException -> "Layanan konektivitas sistem sedang sibuk. Silakan coba sesaat lagi."
                else -> "Gagal berkomunikasi dengan layanan jaringan sistem."
            }
            recordFailure(
                ssid = ssid,
                reason = friendlyReason,
                auditResult = WifiAuditResult.FAILED
            )
        }
```

- [ ] **Step 2: Run unit tests to verify WifiConnectorImplTest**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.WifiConnectorImplTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt
git commit -m "fix(connector): map raw system exceptions to user-friendly indonesian messages"
```

---

### Task 5: Fix UI/UX Overlaps, FAB Collision, Card Slicing, and Button Symmetry

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`

**Interfaces:**
- Consumes: `AroundCheckViewModel` state
- Produces: Modern Blynk Compose UI with zero overlaps, 100.dp clearance for docked FAB, clean header boundary, and balanced card actions

- [ ] **Step 1: Remove nested Scaffold and fix bottom contentPadding**

In `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`:
1. Remove redundant inner `Scaffold(...)`. Use a root `Box` or `Column` with `SnackbarHost(modifier = Modifier.align(Alignment.BottomCenter))`.
2. Update `LazyColumn` contentPadding:
   ```kotlin
   contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
   ```
   This guarantees cards can be scrolled completely clear of `MorphingHelpFab` docked at `bottom = 86.dp`.

- [ ] **Step 2: Isolate cooldown button display to the active/tested network**

In `AroundCheckScreen.kt`:
Instead of turning every card into `Tunggu (Xs)` when the global inter-SSID throttler is cooling down:
```kotlin
val isActivelyCoolingDown = isTarget && cooldownSec > 0
```
Only show `Tunggu (Xs)` on the card if it was the target of the recent attempt. All other cards keep their standard `Connect` button.

- [ ] **Step 3: Balance bottom card action row**

In `WifiScanItemCard`:
Use `Arrangement.SpaceBetween`:
- Left side: Display frequency badge / channel info or status note.
- Right side: Group `IconButton(onForgetClick)` and `Button(onConnectClick)` neatly side-by-side with 8.dp spacing.

- [ ] **Step 4: Provide clean container boundary under top header**

Wrap top search and header in a neat surface container with subtle divider so scrolling cards clip cleanly without looking sliced horizontally.

- [ ] **Step 5: Verify build passes**

Run: `.\gradlew compileDebugKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt
git commit -m "style(aroundcheck): eliminate fab collision, header slicing, and awkward button alignment"
```

---

### Task 6: Full Verification & Debug APK Assembly

**Files:**
- Test: All test suites in `app/src/test`

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew testDebugUnitTest --no-daemon`
Expected: 100% pass (22+ unit tests passing)

- [ ] **Step 2: Assemble Debug APK**

Run: `.\gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`, `app-debug.apk` generated in `app/build/outputs/apk/debug/`

- [ ] **Step 3: Final verification commit & branch status check**

```bash
git status
```
Ensure working tree is clean.
