# Fix False Alarm & Wi-Fi Audit Engine Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate false positive / false alarm password matches during Around Check (DFS/Hybrid/BFS) by enforcing BSSID hardware binding, pre-flight active connection detection, unique attempt correlation tokens, cooldown delay handling, and BSSID-first Vault verification.

**Architecture:** 
1. Upgrade `WifiConnector` with explicit `bssid` parameter in `connect()` and `isCurrentlyConnectedTo()` inquiry.
2. Guard `WifiConnectorImpl` with unique `attemptId` tokens and `MacAddress` BSSID specifier constraints.
3. Fix `startHybridTraversal()` to properly await throttler cooldowns and pass target BSSIDs.
4. Correct `VerifiedWifiStoreImpl` to strictly match BSSIDs rather than collapsing all routers sharing an SSID.

**Tech Stack:** Kotlin 2.0, Android Jetpack Compose, Coroutines/StateFlow, Android `WifiNetworkSpecifier`, Android `ConnectivityManager`.

**Spec Reference:** Systematic Debugging Root Cause Investigation on 2026-09-30.

## Global Constraints
- Preserve Clean Architecture separation between data, domain, and UI layers.
- Strictly adhere to `clean-ui-procedural` and Blynk.io Blue design system (`#77ADF9`, `#FFFFFF`, `#F8FAFC`).
- Maintain 100% offline functionality (no external WAN or analytics dependencies).
- Android HAL Golden Time compliance (safe inter-association pacing delays).

---

### Task 1: Domain Interface & Model Updates

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiConnector.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImplTest.kt`

**Interfaces:**
- `WifiConnector`:
  - `fun connect(ssid: String, password: String, securityType: WifiSecurityType, bssid: String = "")`
  - `fun isCurrentlyConnectedTo(ssid: String, bssid: String = ""): Boolean`
- `VerifiedWifiStore`:
  - Prioritize `bssid` matching over `ssid`.

- [x] **Step 1: Write failing unit test for BSSID-first matching in VerifiedWifiStoreImplTest**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Update VerifiedWifiStoreImpl with BSSID-first matching logic**
- [x] **Step 4: Update WifiConnector interface with bssid and isCurrentlyConnectedTo**
- [x] **Step 5: Run tests and verify passing**
- [x] **Step 6: Commit changes**

---

### Task 2: WifiConnectorImpl Hardening (Token Correlation, BSSID Binding & Active Connection Guard)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiConnectorImpl.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/data/repository/WifiConnectorImplTest.kt`

**Interfaces:**
- Consumes: `WifiConnector`, `ConnectivityManager`, `WifiManager`, `WifiNetworkSpecifier`
- Produces: Hardened `connect()` with `MacAddress.fromString(bssid)` (when BSSID is valid), per-attempt `UUID` correlation token preventing stale IPC callbacks from triggering `Connected`, and `isCurrentlyConnectedTo()` implementation.

- [x] **Step 1: Write unit tests in WifiConnectorImplTest for stale callback rejection and BSSID specifier**
- [x] **Step 2: Run tests to verify failure**
- [x] **Step 3: Implement attemptId token check, MacAddress binding, and isCurrentlyConnectedTo in WifiConnectorImpl**
- [x] **Step 4: Run tests to verify passing**
- [x] **Step 5: Commit changes**

---

### Task 3: WifiConnectThrottler & Cooldown Handling in AroundCheckViewModel

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/domain/util/WifiConnectThrottler.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Modify: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- Consumes: `WifiConnector.isCurrentlyConnectedTo()`, `WifiConnectStatus.Cooldown`
- Produces:
  - Pre-flight connection warning/check before starting traversal.
  - Proper cooldown countdown wait in `startHybridTraversal()` instead of instant bypass.
  - Verification of genuine connection before accepting `candidatePassword`.

- [x] **Step 1: Write unit test in AroundCheckViewModelTest for Hybrid traversal cooldown handling and BSSID passing**
- [x] **Step 2: Run tests to verify failure**
- [x] **Step 3: Implement cooldown wait, BSSID passing, and pre-flight check in AroundCheckViewModel**
- [x] **Step 4: Run tests to verify passing**
- [x] **Step 5: Commit changes**

---

### Task 4: Full Verification, APK Assembly, and CI/CD Release

**Files:**
- Verify: Full test suite (`./gradlew testDebugUnitTest`)
- Verify: APK build (`./gradlew assembleDebug`)
- Remote: `git push origin main`

- [x] **Step 1: Run `./gradlew testDebugUnitTest` and ensure 100% green**
- [x] **Step 2: Run `./gradlew assembleDebug` and ensure successful APK assembly**
- [ ] **Step 3: Commit and push to GitHub remote**
- [ ] **Step 4: Verify CI/CD pipeline completion**
