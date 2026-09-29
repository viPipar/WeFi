# WeFi Stability & UI/UX Comprehensive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate 100% of application startup and runtime crashes on Android devices (Android 8.0 through Android 14/15 across Samsung OneUI, Xiaomi HyperOS, Google Pixel, etc.), introduce an un-crashable global diagnostic recovery architecture, and elevate the UI/UX with modern Blynk-style procedural aesthetics, glowing curves, and live network status indicators.

**Architecture:** 
1. Multi-layered defensive crash prevention: Custom `WeFiApplication` crash interceptor + `DiagnosticRecoveryScreen` in `MainActivity.onCreate()` fallback ensuring the OS never shows "Aplikasi ditutup karena memiliki bug".
2. Safe System Service Boundaries: Complete defensive wrapping for `WifiManager`, `ConnectivityManager`, and `BroadcastReceiver` with graceful offline/permission-pending fallbacks.
3. Native Resource & Theme Standard: Dedicated custom vector launcher icons (`res/drawable/ic_launcher.xml` and `ic_launcher_round.xml`) and AppCompat/Material3-compatible window themes.
4. Procedural Canvas Enhancements: Glowing parabolic curves with vertical gradient alpha fills, pulsing beacon for active connected AP, and strict geometric boundary validation.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (BOM 2024.06.00), Material 3, Coroutines Flow, OkHttp 4.12, AndroidX Core & Lifecycle 2.8.4, JUnit 4.

**Spec:** [WiFi Analyzer System Design](file:///c:/my_project/project_weFi/docs/superpowers/specs/2026-09-29-wifi-analyzer-design.md)

## Global Constraints

- Android API Compatibility: `minSdk = 26`, `targetSdk = 34` (Strict Android 14 BroadcastReceiver export rules).
- Design Aesthetics: Conform to `clean-ui-procedural` and Blynk.io design guidelines (Soft Cloud `#F5F8FC`, Azure Blue `#77ADF9`, high-contrast typography, anti-glare, 16dp rounded cards, zero garish borders).
- Zero-Crash Mandate: Under NO circumstances may an unhandled exception escape `MainActivity.onCreate()` or background threads. All system queries must return valid fallback domain objects (`ConnectedNetworkInfo()`, `emptyList()`).

---

## Tasks

### Task 1: Bulletproof Android Manifest & Custom Vector Launcher Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: Standard Android vector drawables and adaptive icon tags.
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` referenced in `AndroidManifest.xml`.

- [ ] **Step 1: Create `ic_launcher_background.xml` and `ic_launcher_foreground.xml`**
  Generate a clean vector graphic featuring a modern Wi-Fi spectrum parabola with azure blue gradient `#77ADF9` on `#0F172A` deep navy slate.

- [ ] **Step 2: Create adaptive icon definitions in `mipmap-anydpi-v26`**
  Provide adaptive launcher XML for modern Android 8.0 to 14 home screens to eliminate OEM launcher `Resources$NotFoundException`.

- [ ] **Step 3: Update `AndroidManifest.xml`**
  Reference `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, add orientation lock (`portrait`) to avoid mid-layout config crashes, and configure window soft input mode.

- [ ] **Step 4: Commit Task 1**
  `git commit -m "fix(manifest): add native adaptive launcher icons and robust theme definitions"`

---

### Task 2: Global Diagnostic Recovery System & Safe `onCreate` Guard

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/diagnostic/DiagnosticRecoveryScreen.kt`
- Create: `app/src/main/java/com/wefi/analyzer/ui/screens/diagnostic/DiagnosticCrashActivity.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/WeFiApplication.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `Throwable` exceptions captured at runtime.
- Produces: `DiagnosticRecoveryScreen` composable and `DiagnosticCrashActivity` for uncaught background threads.

- [ ] **Step 1: Write Unit Test for exception formatting helper**
  Ensure stack trace strings and root cause messages are cleanly sanitized for user viewing.

- [ ] **Step 2: Create `DiagnosticRecoveryScreen.kt`**
  Blynk-styled friendly UI showing:
  - Icon: Warning/Info shield in Azure Blue.
  - Title: "WeFi Sedang Menyesuaikan Perangkat Anda".
  - Clean error card with collapsible technical stack trace.
  - "Salin Detail Error" button (copies stack trace to clipboard).
  - "Muat Ulang / Coba Lagi" button (`onRetry: () -> Unit`).

- [ ] **Step 3: Create `DiagnosticCrashActivity.kt`**
  Secondary process activity that opens if an unhandled thread exception occurs, preventing Android's fatal crash dialog and displaying the diagnostic UI.

- [ ] **Step 4: Update `WeFiApplication.kt` and `MainActivity.kt`**
  Wrap `MainActivity.onCreate()` in top-level try/catch. If any repository fails, render `DiagnosticRecoveryScreen` inside Compose rather than crashing out to the OS.

- [ ] **Step 5: Commit Task 2**
  `git commit -m "feat(diagnostics): add un-crashable recovery screen and application crash handler"`

---

### Task 3: Resilient Repository Layer with Lifecycle Teardown & OEM Wi-Fi Fallbacks

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/CurrentConnectionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/WifiScannerRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/repository/WifiScannerRepository.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/repository/CurrentConnectionRepository.kt`

**Interfaces:**
- Consumes: Android `WifiManager`, `ConnectivityManager`.
- Produces: Teardown methods `cleanup()` or lifecycle awareness, throttle-protected `startScan()`.

- [ ] **Step 1: Add teardown methods to Repository interfaces**
  Define `fun teardown()` on `WifiScannerRepository` and `CurrentConnectionRepository` to unregister network callbacks and broadcast receivers when activity destroys.

- [ ] **Step 2: Implement throttle protection in `WifiScannerRepositoryImpl`**
  Add a minimum 10-second debounce between calls to `wifiManager.startScan()` (Android throttles to 4 scans per 2 minutes in foreground). If throttled, silently parse cached scan results without errors.

- [ ] **Step 3: Add multi-version `WifiInfo` extraction in `CurrentConnectionRepositoryImpl`**
  On Android 12+ (API 31+), extract `WifiInfo` from `NetworkCapabilities.transportInfo` within `onCapabilitiesChanged()`, falling back to `wifiManager.connectionInfo` inside a try/catch.

- [ ] **Step 4: Write repository unit tests**
  Verify state flows emit valid empty models when system services throw `SecurityException` or return null.

- [ ] **Step 5: Commit Task 3**
  `git commit -m "fix(repository): add lifecycle teardown, scan throttle protection, and transportInfo parsing"`

---

### Task 4: Canvas Layout & Parabola Geometry Safety

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphCanvas.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/domain/util/ChannelFrequencyUtils.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/util/ChannelFrequencyUtilsTest.kt`

**Interfaces:**
- Consumes: `List<WifiAccessPoint>`, `selectedBandGhz`, `connectedBssid`.
- Produces: Robust, artifact-free quadratic Bezier curves with vertical gradient shading and plumb line.

- [ ] **Step 1: Write test for invalid and edge-case frequencies in `ChannelFrequencyUtilsTest`**
  Verify frequency `0`, negative frequencies, and frequencies outside standard bands don't crash and are filtered.

- [ ] **Step 2: Add geometry validation in `ChannelGraphCanvas.kt`**
  - Check `if (leftX >= rightX || peakY >= baseY) return@forEachIndexed`.
  - Filter out APs with channel <= 0 or frequency <= 0.
  - Implement vertical gradient fill from `color.copy(alpha = 0.40f)` at the peak down to `Color.Transparent` at the baseline.

- [ ] **Step 3: Add glowing pulsing beacon on connected AP plumb line**
  Draw a concentric glowing ring at `(centerX, peakY)` for the connected AP.

- [ ] **Step 4: Run tests and verify**
  Run `./gradlew testDebugUnitTest`.

- [ ] **Step 5: Commit Task 4**
  `git commit -m "fix(canvas): harden Bezier geometry, filter invalid channels, and add gradient shading"`

---

### Task 5: Speedtest Resilience & Multi-CDN Fallback

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/data/repository/SpeedTestRepositoryImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/usecase/CalculateDistanceUseCaseTest.kt`

**Interfaces:**
- Consumes: OkHttp streaming download/upload calls.
- Produces: `Flow<SpeedTestMetrics>` with zero unhandled network exceptions.

- [ ] **Step 1: Add multi-endpoint fallback in `SpeedTestRepositoryImpl`**
  Support primary CDN (`https://speed.cloudflare.com/__down?bytes=10000000`) with backup HTTP/2 CDN endpoints and socket timeout guards (5 seconds).

- [ ] **Step 2: Add comprehensive catch blocks in Ping, Download, and Upload stages**
  If internet drops during test, smoothly transition `stage` to `FINISHED` and mark `isRunning = false` with last valid measured throughput instead of failing silently.

- [ ] **Step 3: Commit Task 5**
  `git commit -m "fix(speedtest): add multi-CDN fallback, connection timeout guards, and graceful error recovery"`

---

### Task 6: Visual & UI/UX Enhancement (Blynk-Style Procedural)

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/components/BlynkCard.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/graph/ChannelGraphScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aplist/ApListScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/rating/ChannelRatingScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/speedtest/SpeedTestScreen.kt`

**Interfaces:**
- Consumes: Theme colors, domain models, ViewModels.
- Produces: Ultra-clean, premium procedural UI complying with `clean-ui-procedural`.

- [ ] **Step 1: Fix `BlynkCard.kt` internal layout constraint**
  Add `fillMaxWidth()` and optional `contentModifier` to internal column so weighted cards scale properly on all screen densities.

- [ ] **Step 2: Add Wi-Fi Disabled / Permission Pending Banner**
  In `ChannelGraphScreen` and `ApListScreen`, if Wi-Fi is toggled off or 0 APs are found, show an informative card with an "Aktifkan Wi-Fi & Pindai Ulang" button.

- [ ] **Step 3: Elevate Connected AP Badge Pill**
  In `ChannelGraphCanvas`, refine the top badge pill with `● TERHUBUNG: [SSID]` with a high-contrast soft blue background pill for maximum legibility.

- [ ] **Step 4: Commit Task 6**
  `git commit -m "feat(ui): elevate procedural Blynk aesthetics, add empty state cards, and fix layout constraints"`

---

### Task 7: Full Verification & Automated GitHub Release Pipeline

**Files:**
- Modify: `.github/workflows/build-apk.yml`

**Interfaces:**
- Consumes: Git commits on `main`.
- Produces: Automated build, unit test execution, and GitHub Release with direct APK download.

- [ ] **Step 1: Run all unit tests locally or verify test suite**
- [ ] **Step 2: Push changes to GitHub `main`**
- [ ] **Step 3: Monitor GitHub Actions run to completion (`completed` / `success`)**
- [ ] **Step 4: Verify generated `app-debug.apk` in GitHub Releases**
- [ ] **Step 5: Provide download link and instructions to user**

---
