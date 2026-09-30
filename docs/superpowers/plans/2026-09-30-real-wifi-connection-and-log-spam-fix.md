# Real Wi-Fi Connection & Log Spam Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate audit log spamming caused by device auto-reconnects, fix WPA2/WPA3 mixed mode classification, eliminate rogue background callbacks that dismiss OS connection dialogs prematurely, and ensure real hardware router authentication occurs via official Android `WifiNetworkSpecifier`.

**Architecture:** Android official network APIs (`ConnectivityManager.requestNetwork` with `WifiNetworkSpecifier`), dedicated interactive `NetworkCallback`, process network binding (`bindProcessToNetwork`), and clean lifecycle management without rogue global callbacks.

**Tech Stack:** Kotlin, Android ConnectivityManager & WifiManager APIs, Coroutines/StateFlow, JUnit4.

**Spec:** User bug report, real device test findings, and systematic debugging root-cause investigation.

## Global Constraints

- Strictly official Android APIs (`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`). Zero root, zero reflection, zero private API bypasses.
- Zero fake connections: The target router's hardware must perform the real 4-way WPA2/WPA3 handshake with the input password.
- No rogue callbacks listening to general device Wi-Fi state: Only callbacks dedicated to the active `WifiNetworkSpecifier` may alter connection state.
- 100% unit test pass rate with no regressions.

---

### Task 1: Fix WPA2/WPA3 Mixed (Transition) Mode Classification in `WifiScannerImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerImpl.kt:242-252`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/WifiScannerImplTest.kt`

**Interfaces:**
- Consumes: `WifiScannerImpl.parseSecurityType(capabilities: String?)`
- Produces: `WifiSecurityType.WPA2` for transition/mixed mode networks (`PSK` + `SAE`), reserving `WifiSecurityType.WPA3` strictly for pure SAE-only networks.

- [ ] **Step 1: Write failing unit test for WPA2/WPA3 transition mode**

In `app/src/test/java/com/wefi/analyzer/data/repository/WifiScannerImplTest.kt`:
```kotlin
    @Test
    fun parseSecurityType_mixedModeWpa2Wpa3_returnsWpa2ForSpecifierCompatibility() {
        val mixedCaps = "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"
        val result = WifiScannerImpl.parseSecurityType(mixedCaps)
        assertEquals(WifiSecurityType.WPA2, result)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.WifiScannerImplTest.parseSecurityType_mixedModeWpa2Wpa3_returnsWpa2ForSpecifierCompatibility --no-daemon`
Expected: FAIL (returns `WPA3` instead of `WPA2`).

- [ ] **Step 3: Update `parseSecurityType` in `WifiScannerImpl`**

In `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerImpl.kt`:
```kotlin
        fun parseSecurityType(capabilities: String?): WifiSecurityType {
            val caps = capabilities?.uppercase() ?: return WifiSecurityType.UNKNOWN
            return when {
                // Pure WPA3 Personal (SAE only, tanpa dukungan WPA2 PSK fallback)
                (caps.contains("SAE") || caps.contains("WPA3")) && !caps.contains("PSK") -> WifiSecurityType.WPA3
                // WPA2 Personal atau mode transisi (Mixed WPA2/WPA3 dengan PSK)
                caps.contains("PSK") || caps.contains("WPA2") || caps.contains("WPA") -> WifiSecurityType.WPA2
                caps.contains("WEP") -> WifiSecurityType.WEP
                !caps.contains("WPA") && !caps.contains("WEP") && !caps.contains("EAP") -> WifiSecurityType.OPEN
                else -> WifiSecurityType.UNKNOWN
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.WifiScannerImplTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/WifiScannerImplTest.kt
git commit -m "fix(scanner): classify wpa2/wpa3 mixed mode as wpa2 for specifier compatibility"
```

---

### Task 2: Eliminate Rogue `sharedNetworkCallback` and Bind Process Network in `WifiConnectorImpl`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt`

**Interfaces:**
- Consumes: `WifiConnector.connect(ssid, password, securityType)`
- Produces: Clean interactive connection lifecycle without interference from the phone's existing Wi-Fi connection, binding process socket traffic to the target network on success.

- [ ] **Step 1: Write unit tests for password length validation & clean state**

In `app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt`:
```kotlin
    @Test
    fun connect_whenWpa2PasswordTooShort_failsImmediatelyWithoutCrashing() = runTest(testDispatcher) {
        connector.connect("Lab-AP", "short", WifiSecurityType.WPA2)
        assertEquals(WifiConnectStatus.Failed, connector.connectState.value.status)
        assertTrue(connector.connectState.value.message.contains("8"))
    }
```

- [ ] **Step 2: Run test to verify it fails or needs implementation**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.WifiConnectorImplTest.connect_whenWpa2PasswordTooShort_failsImmediatelyWithoutCrashing --no-daemon`
Expected: Verification of failure behavior.

- [ ] **Step 3: Refactor `WifiConnectorImpl`**

In `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`:
1. Remove `sharedNetworkCallback`, `isSharedCallbackRegistered`, and `registerSharedNetworkCallback()`.
2. Remove `WifiNetworkSuggestion` companion logic (`activeSuggestion`, `buildNetworkSuggestion()`, `removeCurrentSuggestion()`).
3. Add `unbindProcessNetwork()` helper:
   ```kotlin
   private fun unbindProcessNetwork() {
       try {
           connectivityManager?.bindProcessToNetwork(null)
       } catch (e: Exception) {
           Log.w(TAG, "Gagal melepaskan ikatan proses jaringan", e)
       }
   }
   ```
4. Validate password length before building specifier:
   - For `WPA2` and `WPA3`: enforce `password.length in 8..63`.
   - For `WEP`: fail immediately with clear deprecation notice ("Protokol keamanan WEP sudah usang dan tidak didukung oleh Android 10+").
5. In `interactiveCallback.onAvailable(network)`:
   - Call `connectivityManager?.bindProcessToNetwork(network)`.
   - Cancel timers and update state to `Connected`.
   - Log `CONNECTED` in `auditLogger` with "User menyetujui koneksi OS dan berhasil terhubung ke router".
6. In `interactiveCallback.onUnavailable()`:
   - Call `unbindProcessNetwork()`.
   - Call `recordFailure(ssid, "Ditolak oleh user atau autentikasi router gagal", WifiAuditResult.REJECTED)`.
7. In `interactiveCallback.onLost(network)`:
   - Call `unbindProcessNetwork()`.
   - Call `recordFailure(ssid, "Koneksi terputus dari jaringan", WifiAuditResult.FAILED)`.
8. In `recordFailure`, `cancel`, `forgetNetwork`, and `teardown`:
   - Call `unbindProcessNetwork()`.

- [ ] **Step 4: Run unit tests**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.WifiConnectorImplTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt
git commit -m "fix(connector): remove rogue shared callback, bind process network, and prevent premature dialog cancellation"
```

---

### Task 3: Strengthen Sequential Test State Handling in `AroundCheckViewModel`

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Consumes: `AroundCheckViewModel.startSequentialTest()`
- Produces: Resilient candidate iteration that handles real user response timing, allows full 30s for OS dialog approval, and handles rejection or wrong password without thrashing.

- [ ] **Step 1: Write unit test for candidate failure advancing smoothly**

In `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`:
```kotlin
    @Test
    fun startSequentialTest_whenCandidateRejected_movesToNextCandidateAfterDelay() = runTest(testDispatcher) {
        val candidate1 = WifiScanItem("Target-1", "00:11:22:33:44:01", -50, WifiSecurityType.WPA2, 2412, 1)
        val candidate2 = WifiScanItem("Target-2", "00:11:22:33:44:02", -60, WifiSecurityType.WPA2, 2412, 1)
        fakeScanner.setScanItems(listOf(candidate1, candidate2))

        viewModel.setTopPasswordInput("12345678")
        viewModel.startSequentialTest()
        testScheduler.advanceUntilIdle()

        // Kandidat 1 ditolak user
        fakeConnector.emitConnectState(WifiConnectState(targetSsid = "Target-1", status = WifiConnectStatus.Rejected, message = "Ditolak"))
        testScheduler.advanceTimeBy(1600L)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.currentCandidateIndex.value)
        assertEquals("Target-2", fakeConnector.lastConnectSsid)
    }
```

- [ ] **Step 2: Run test to verify behavior**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Update `AroundCheckViewModel` sequential test loop**

Ensure `startSequentialTest()` clears previous connection state before calling `connector.connect(candidate.ssid, password, candidate.security)`, so stale state from prior single connections cannot prematurely complete `connectState.first { ... }`.

- [ ] **Step 4: Run unit tests**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt
git commit -m "fix(aroundcheck): prevent stale state race conditions during sequential router testing"
```

---

### Task 4: Full Test Suite Verification & Debug APK Assembly

**Files:**
- Test: All unit test suites in `app/src/test`

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew testDebugUnitTest --no-daemon`
Expected: 100% pass (24+ tests passing).

- [ ] **Step 2: Build Debug APK**

Run: `.\gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`, `app-debug.apk` built.

- [ ] **Step 3: Push changes to main**

```bash
git push origin main
```
