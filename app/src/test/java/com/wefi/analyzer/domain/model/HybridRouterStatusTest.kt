package com.wefi.analyzer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRouterStatusTest {

    @Test
    fun hybridRouterStatus_sealedTypes_instantiateCorrectly() {
        val idle: HybridRouterStatus = HybridRouterStatus.Idle
        val testing: HybridRouterStatus = HybridRouterStatus.Testing(currentPasswordIndex = 5, totalPasswords = 56)
        val found: HybridRouterStatus = HybridRouterStatus.Found(workingPassword = "ilmukomputeripb")
        val notFound: HybridRouterStatus = HybridRouterStatus.NotFound(testedCount = 56)
        val vault: HybridRouterStatus = HybridRouterStatus.VerifiedFromVault(workingPassword = "admin")
        val osConnected: HybridRouterStatus = HybridRouterStatus.AlreadyConnectedViaOS()

        assertTrue(idle is HybridRouterStatus.Idle)
        assertEquals(5, (testing as HybridRouterStatus.Testing).currentPasswordIndex)
        assertEquals(56, testing.totalPasswords)
        assertEquals("ilmukomputeripb", (found as HybridRouterStatus.Found).workingPassword)
        assertEquals(56, (notFound as HybridRouterStatus.NotFound).testedCount)
        assertEquals("admin", (vault as HybridRouterStatus.VerifiedFromVault).workingPassword)
        assertTrue(osConnected is HybridRouterStatus.AlreadyConnectedViaOS)
        assertTrue((osConnected as HybridRouterStatus.AlreadyConnectedViaOS).message.contains("Pengaturan OS"))
    }

    @Test
    fun aroundCheckMode_containsHybrid() {
        val modes = AroundCheckMode.values().map { it.name }
        assertTrue(modes.contains("HYBRID"))
        assertTrue(modes.contains("BFS"))
        assertTrue(modes.contains("DFS"))
    }

    @Test
    fun speedTestStage_containsOfflineLabMode() {
        val stages = SpeedTestStage.values().map { it.name }
        assertTrue(stages.contains("OFFLINE_LAB_MODE"))
    }
}
