package com.wefi.analyzer.data.repository
 
import com.wefi.analyzer.domain.model.AssetCategory
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.PortStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
 
class NetworkDiscoveryLogicTest {
 
    @Test
    fun testAssetCategoryClassification_andCleartextDetection() {
        val repo = NetworkDiscoveryRepositoryImpl(context = null)

        val gatewayHost = DiscoveredHost(
            ip = "192.168.1.1",
            isGateway = true,
            openPorts = listOf(PortResult(80, PortStatus.OPEN, "HTTP", 2L))
        )
        assertEquals(AssetCategory.GATEWAY_ROUTER, repo.classifyAssetCategory(gatewayHost))
        assertTrue(repo.hasCleartextManagement(gatewayHost))

        val cctvHost = DiscoveredHost(
            ip = "192.168.1.50",
            openPorts = listOf(
                PortResult(554, PortStatus.OPEN, "RTSP", 5L),
                PortResult(3702, PortStatus.OPEN, "ONVIF", 4L)
            )
        )
        assertEquals(AssetCategory.SURVEILLANCE_CCTV, repo.classifyAssetCategory(cctvHost))
        assertFalse(repo.hasCleartextManagement(cctvHost))

        val nasHost = DiscoveredHost(
            ip = "192.168.1.100",
            openPorts = listOf(
                PortResult(445, PortStatus.OPEN, "SMB", 3L),
                PortResult(21, PortStatus.OPEN, "FTP", 4L)
            )
        )
        assertEquals(AssetCategory.STORAGE_NAS, repo.classifyAssetCategory(nasHost))
        assertTrue(repo.hasCleartextManagement(nasHost))
    }
}
