# Around Check Dual-Mode (BFS & DFS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Breadth-First Search (BFS) and Depth-First Search (DFS) modes in Around Check with lab hardware safeguards, CSV multi-delimiter sanitizer, and a persistent Verified Router Vault.

**Architecture:** Strategy-based traversal in `AroundCheckViewModel`, multi-delimiter sanitizer in `DfsPasswordSanitizer`, persistent vault via `VerifiedWifiStoreImpl`, and a Modern Blynk UI with top segmented tab controls and celebratory goal completion cards.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlinx Coroutines/StateFlow, Android SharedPreferences, JUnit4.

**Spec:** [docs/superpowers/specs/2026-09-30-around-check-bfs-dfs-modes-design.md](file:///c:/my_project/project_weFi/docs/superpowers/specs/2026-09-30-around-check-bfs-dfs-modes-design.md)

## Global Constraints

- Strictly official Android APIs (`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`). Zero root, zero reflection, zero private API bypasses.
- Lab Hardware Safeguards: 2-3s inter-attempt pacing for DFS, 10-attempt circuit breaker, 500ms HAL settle delay.
- Clean UI: Comply with Modern Blynk and `clean-ui-procedural` (solid colors, clear layout hierarchy, 100.dp bottom clearance).
- 100% unit test pass rate with no regressions.

---

### Task 1: Create Domain Models & `DfsPasswordSanitizer` with TDD

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/AroundCheckMode.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/VerifiedLabRouter.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/model/DfsParseResult.kt`
- Create: `app/src/main/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizer.kt`
- Test: `app/src/test/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizerTest.kt`

**Interfaces:**
- `AroundCheckMode { BFS, DFS }`
- `VerifiedLabRouter(bssid, ssid, workingPassword, discoveredTimestamp, securityType)`
- `DfsParseResult(validPasswords, skippedTooShortCount, duplicateCount)`
- `DfsPasswordSanitizer.parse(rawInput: String): DfsParseResult`

- [ ] **Step 1: Create domain model classes**

Create `AroundCheckMode.kt`, `VerifiedLabRouter.kt`, and `DfsParseResult.kt`.

- [ ] **Step 2: Write failing unit test for `DfsPasswordSanitizer`**

In `app/src/test/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizerTest.kt`:
```kotlin
package com.wefi.analyzer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DfsPasswordSanitizerTest {

    @Test
    fun parse_mixedDelimiters_extractsAndSanitizesCorrectly() {
        val input = "pass12345; pass67890, short \n pass12345; validPassword99\r\n"
        val result = DfsPasswordSanitizer.parse(input)

        assertEquals(listOf("pass12345", "pass67890", "validPassword99"), result.validPasswords)
        assertEquals(1, result.skippedTooShortCount)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun parse_emptyInput_returnsZeroValid() {
        val result = DfsPasswordSanitizer.parse("   ;; \n  ")
        assertTrue(result.validPasswords.isEmpty())
        assertEquals(0, result.skippedTooShortCount)
        assertEquals(0, result.duplicateCount)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.domain.util.DfsPasswordSanitizerTest --no-daemon`
Expected: FAIL (class not found).

- [ ] **Step 4: Implement `DfsPasswordSanitizer`**

In `app/src/main/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizer.kt`:
```kotlin
package com.wefi.analyzer.domain.util

import com.wefi.analyzer.domain.model.DfsParseResult

object DfsPasswordSanitizer {
    fun parse(rawInput: String?): DfsParseResult {
        if (rawInput.isNullOrBlank()) {
            return DfsParseResult(emptyList(), 0, 0)
        }

        val tokens = rawInput.split(Regex("[;,\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var skippedTooShort = 0
        var duplicates = 0
        val seen = mutableSetOf<String>()
        val validList = mutableListOf<String>()

        for (token in tokens) {
            if (token.length !in 8..63) {
                skippedTooShort++
            } else if (!seen.add(token)) {
                duplicates++
            } else {
                validList.add(token)
            }
        }

        return DfsParseResult(
            validPasswords = validList,
            skippedTooShortCount = skippedTooShort,
            duplicateCount = duplicates
        )
    }
}
```

- [ ] **Step 5: Run unit tests to verify pass**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.domain.util.DfsPasswordSanitizerTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/model/AroundCheckMode.kt \
        app/src/main/java/com/wefi/analyzer/domain/model/VerifiedLabRouter.kt \
        app/src/main/java/com/wefi/analyzer/domain/model/DfsParseResult.kt \
        app/src/main/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizer.kt \
        app/src/test/java/com/wefi/analyzer/domain/util/DfsPasswordSanitizerTest.kt
git commit -m "feat(domain): add bfs dfs models and dfs password sanitizer engine"
```

---

### Task 2: Implement Verified Router Vault (`VerifiedWifiStore`)

**Files:**
- Create: `app/src/main/java/com/wefi/analyzer/domain/repository/VerifiedWifiStore.kt`
- Create: `app/src/main/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImpl.kt`
- Test: `app/src/test/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImplTest.kt`

**Interfaces:**
```kotlin
interface VerifiedWifiStore {
    val verifiedRouters: StateFlow<List<VerifiedLabRouter>>
    fun saveVerifiedRouter(router: VerifiedLabRouter)
    fun isRouterVerified(bssid: String, ssid: String): Boolean
    fun getVerifiedPassword(bssid: String, ssid: String): String?
    fun removeVerifiedRouter(bssid: String)
    fun clearAll()
}
```

- [ ] **Step 1: Define `VerifiedWifiStore` interface**

Create `app/src/main/java/com/wefi/analyzer/domain/repository/VerifiedWifiStore.kt`.

- [ ] **Step 2: Write failing unit test for `VerifiedWifiStoreImpl`**

In `app/src/test/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImplTest.kt`:
Test save, retrieve, anti-duplication, removal, and corrupted JSON resilience.

- [ ] **Step 3: Implement `VerifiedWifiStoreImpl`**

In `app/src/main/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImpl.kt`:
Store JSON array in private SharedPreferences with in-memory caching in `MutableStateFlow`.

- [ ] **Step 4: Run unit tests**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.data.repository.VerifiedWifiStoreImplTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/domain/repository/VerifiedWifiStore.kt \
        app/src/main/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImpl.kt \
        app/src/test/java/com/wefi/analyzer/data/repository/VerifiedWifiStoreImplTest.kt
git commit -m "feat(vault): implement persistent verified wifi store with json serialization"
```

---

### Task 3: Upgrade `AroundCheckViewModel` for BFS & DFS Modes

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt`
- Test: `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`

**Interfaces:**
- `setMode(mode: AroundCheckMode)`
- `setBfsPasswordInput(input: String)`
- `setDfsCsvInput(input: String)`
- `selectDfsTargetItem(item: WifiScanItem?)`
- `startBfsTraversal()`
- `startDfsTraversal()`
- `cancelTraversal()`
- `dismissGoalFound()`
- `acknowledgeCircuitBreaker(continueTraversal: Boolean)`

- [ ] **Step 1: Write unit tests for BFS & DFS in ViewModel**

In `app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt`:
- Add test for `startDfsTraversal_iteratesPasswordsOnTargetRouter()`
- Add test for `startDfsTraversal_whenPasswordMatches_stopsAndSavesToVault()`
- Add test for `startDfsTraversal_circuitBreakerAfter10ConsecutiveFailures()`
- Add test for `startBfsTraversal_whenRouterMatches_savesToVault()`

- [ ] **Step 2: Update `AroundCheckViewModel`**

Implement state flows, sanitizer integration, BFS traversal, DFS traversal with safe pacing (2-3s delay) and HAL quench (500ms delay), and vault persistence.

- [ ] **Step 3: Run unit tests**

Run: `.\gradlew testDebugUnitTest --tests com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModelTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModel.kt \
        app/src/test/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckViewModelTest.kt
git commit -m "feat(aroundcheck): implement bfs and dfs traversal engine with lab safeguards"
```

---

### Task 4: Upgrade `AroundCheckScreen` UI/UX with Segmented Tab, DFS Panel, & Goal Card

**Files:**
- Modify: `app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt`
- Modify: `app/src/main/java/com/wefi/analyzer/MainActivity.kt`

**Interfaces:**
- Consumes: `AroundCheckViewModel` StateFlows
- Produces: Modern Blynk UI with top segmented tab, dynamic BFS/DFS panels, live traversal progress bar, celebration card, and verified badges on Wi-Fi cards.

- [ ] **Step 1: Wire `VerifiedWifiStoreImpl` into `MainActivity.kt`**

Pass `verifiedStore = VerifiedWifiStoreImpl(applicationContext)` to `AroundCheckViewModel`.

- [ ] **Step 2: Update `AroundCheckScreen.kt`**

1. Top Segmented Control (`Mode BFS` vs `Mode DFS`).
2. Mode BFS Panel: Single password + Search button.
3. Mode DFS Panel: Target Chip/Dropdown + Multi-line CSV TextField + validation chip + Search button.
4. Active Traversal Progress Banner: `[X/Total]` counter, progress bar, cancel button.
5. Celebration Card ("Goal Ditemukan!"): Displays unlocked SSID, working password, and "Salin Password" action.
6. Wi-Fi Cards: Highlight border for DFS target; emerald green "TERVERIFIKASI" badge for verified routers.
7. Circuit Breaker Dialog: Prompts user when 10 consecutive failures occur.

- [ ] **Step 3: Verify Kotlin compilation**

Run: `.\gradlew compileDebugKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wefi/analyzer/ui/screens/aroundcheck/AroundCheckScreen.kt \
        app/src/main/java/com/wefi/analyzer/MainActivity.kt
git commit -m "feat(ui): add bfs dfs segmented tabs, dfs panel, verified badge, and goal celebration card"
```

---

### Task 5: Full Test Suite Verification & Debug APK Assembly

**Files:**
- Test: All tests in `app/src/test`

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew testDebugUnitTest --no-daemon`
Expected: 100% pass (28+ tests passing).

- [ ] **Step 2: Assemble Debug APK**

Run: `.\gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`, `app-debug.apk` built.

- [ ] **Step 3: Push changes to main**

```bash
git push origin main
```
