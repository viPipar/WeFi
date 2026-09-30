package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
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
    fun isSsidAuthorized_validatesNonBlankSsid() {
        assertTrue(repository.isSsidAuthorized("ilmukomputeripb"))
        assertTrue(repository.isSsidAuthorized("Lab-IoT-01"))
        assertTrue(repository.isSsidAuthorized("RouterLab"))
        assertTrue(repository.isSsidAuthorized("TPLINK406"))
        assertTrue(repository.isSsidAuthorized("Halo"))
        assertTrue(repository.isSsidAuthorized("CustomRouter"))

        assertFalse(repository.isSsidAuthorized(""))
        assertFalse(repository.isSsidAuthorized("   "))
    }

    @Test
    fun blankSsid_returnsErrorStatus() = runTest {
        val target = LabAuditTarget(
            ssid = "",
            bssid = "11:22:33:44:55:66",
            isAuthorized = false
        )
        val results = repository.testRouterCredential(target, "dummyPass").toList()
        assertEquals(listOf(LabAuditStatus.ERROR), results)

        val logs = repository.auditLogs.value
        assertEquals(1, logs.size)
        assertEquals(LabAuditStatus.ERROR, logs[0].status)
        assertEquals("", logs[0].targetSsid)
    }

    @Test
    fun emptyCredential_returnsError() = runTest {
        val target = LabAuditTarget(
            ssid = "ilmukomputeripb",
            bssid = "00:AA:BB:CC:DD:EE",
            isAuthorized = true
        )
        val results = repository.testRouterCredential(target, "").toList()
        assertEquals(listOf(LabAuditStatus.ERROR), results)
    }

    @Test
    fun recordLog_and_clearLogs_managesLogHistory() {
        val target = LabAuditTarget(ssid = "RouterLab", bssid = "AA:BB:CC:DD:EE:FF", isAuthorized = true)
        repository.recordLog(
            LabAuditLogEntry(
                targetSsid = target.ssid,
                targetBssid = target.bssid,
                status = LabAuditStatus.MATCHED,
                notes = "Matched"
            )
        )
        assertEquals(1, repository.auditLogs.value.size)
        assertEquals("RouterLab", repository.auditLogs.value[0].targetSsid)

        repository.clearLogs()
        assertEquals(0, repository.auditLogs.value.size)
    }

    @Test
    fun recordLog_capsAt100Entries() {
        for (i in 1..110) {
            repository.recordLog(
                LabAuditLogEntry(
                    targetSsid = "Lab-$i",
                    targetBssid = "00:11:22:33:44:$i",
                    status = LabAuditStatus.MATCHED,
                    notes = "Log $i"
                )
            )
        }
        assertEquals(100, repository.auditLogs.value.size)
        // Entry terbaru ada di indeks 0
        assertEquals("Lab-110", repository.auditLogs.value[0].targetSsid)
    }

    @Test
    fun authorizedSsids_returnsEmptySet() {
        assertTrue(repository.authorizedSsids.isEmpty())
    }
}
