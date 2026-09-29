package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LabRouterAuditRepositoryImplTest {

    private lateinit var repository: LabRouterAuditRepositoryImpl

    @Before
    fun setUp() {
        repository = LabRouterAuditRepositoryImpl()
    }

    @Test
    fun isSsidAuthorized_validatesWhitelistAndPatterns() {
        assertTrue(repository.isSsidAuthorized("ilmukomputeripb"))
        assertTrue(repository.isSsidAuthorized("Lab-IoT-01"))
        assertTrue(repository.isSsidAuthorized("RouterLab"))
        assertTrue(repository.isSsidAuthorized("TPLINK406"))
        assertTrue(repository.isSsidAuthorized("Halo"))
        assertTrue(repository.isSsidAuthorized("Lab-Security-05"))
        assertTrue(repository.isSsidAuthorized("IPB-AccessPoint"))

        assertFalse(repository.isSsidAuthorized("Public-Free-Wifi"))
        assertFalse(repository.isSsidAuthorized("Cafe-Net"))
    }

    @Test
    fun unauthorizedSsid_immediatelyReturnsUnauthorizedStatus() = runTest {
        val target = LabAuditTarget(
            ssid = "Public-Free-Wifi",
            bssid = "11:22:33:44:55:66",
            isAuthorized = false
        )
        val results = repository.testRouterCredential(target, "dummyPass").toList()
        assertEquals(listOf(LabAuditStatus.UNAUTHORIZED), results)

        val logs = repository.auditLogs.value
        assertEquals(1, logs.size)
        assertEquals(LabAuditStatus.UNAUTHORIZED, logs[0].status)
        assertEquals("Public-Free-Wifi", logs[0].targetSsid)
    }

    @Test
    fun authorizedSsid_matchesCredentialCorrectly() = runTest {
        val target = LabAuditTarget(
            ssid = "ilmukomputeripb",
            bssid = "00:AA:BB:CC:DD:EE",
            isAuthorized = true
        )
        val results = repository.testRouterCredential(target, "ilmukomputeripb").toList()
        assertTrue(results.contains(LabAuditStatus.TESTING))
        assertTrue(results.contains(LabAuditStatus.MATCHED))

        val logs = repository.auditLogs.value
        assertTrue(logs.any { it.targetSsid == "ilmukomputeripb" && it.status == LabAuditStatus.MATCHED })
    }

    @Test
    fun authorizedSsid_failsWhenCredentialIncorrect() = runTest {
        val target = LabAuditTarget(
            ssid = "ilmukomputeripb",
            bssid = "00:AA:BB:CC:DD:EE",
            isAuthorized = true
        )
        val results = repository.testRouterCredential(target, "wrongPassword999").toList()
        assertTrue(results.contains(LabAuditStatus.TESTING))
        assertTrue(results.contains(LabAuditStatus.FAILED))
    }

    @Test
    fun emptyCredential_returnsError() = runTest {
        val target = LabAuditTarget(
            ssid = "ilmukomputeripb",
            bssid = "00:AA:BB:CC:DD:EE",
            isAuthorized = true
        )
        val results = repository.testRouterCredential(target, "").toList()
        assertTrue(results.contains(LabAuditStatus.ERROR))
    }

    @Test
    fun clearLogs_emptiesLogHistory() {
        val target = LabAuditTarget(ssid = "Public-Wifi", bssid = "AA:BB:CC:DD:EE:FF", isAuthorized = false)
        repository.recordLog(
            com.wefi.analyzer.domain.model.LabAuditLogEntry(
                targetSsid = target.ssid,
                targetBssid = target.bssid,
                status = LabAuditStatus.UNAUTHORIZED,
                notes = "Unauthorized"
            )
        )
        assertEquals(1, repository.auditLogs.value.size)
        repository.clearLogs()
        assertEquals(0, repository.auditLogs.value.size)
    }

    @Test
    fun addAuthorizedSsid_dynamicallyExpandsScope() {
        assertFalse(repository.isSsidAuthorized("Custom-Lab-Router"))
        repository.addAuthorizedSsid("Custom-Lab-Router")
        assertTrue(repository.isSsidAuthorized("Custom-Lab-Router"))
    }
}
