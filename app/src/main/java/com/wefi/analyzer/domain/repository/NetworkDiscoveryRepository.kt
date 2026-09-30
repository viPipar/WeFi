package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.BannerInfo
import com.wefi.analyzer.domain.model.CveMatch
import com.wefi.analyzer.domain.model.DiscoveredHost
import com.wefi.analyzer.domain.model.DiscoveryReport
import com.wefi.analyzer.domain.model.PortResult
import com.wefi.analyzer.domain.model.ServiceInfo
import com.wefi.analyzer.domain.model.SubnetInfo
import kotlinx.coroutines.flow.Flow

interface NetworkDiscoveryRepository {
    fun getSubnetInfo(): SubnetInfo?
    
    fun pingSweep(subnet: SubnetInfo): Flow<DiscoveredHost>
    
    fun scanPorts(hostIp: String, ports: List<Int>): Flow<PortResult>
    
    fun discoverMdns(timeoutMs: Long = 4000L): Flow<ServiceInfo>
    
    fun discoverSsdp(timeoutMs: Long = 3000L): Flow<ServiceInfo>
    
    fun lookupVendor(macAddress: String): String
    
    suspend fun grabBanner(hostIp: String, port: Int): BannerInfo?
    
    fun matchCve(vendor: String, model: String, firmware: String): List<CveMatch>
    
    fun identifyDeviceType(openPorts: List<Int>, banner: BannerInfo?, services: List<ServiceInfo>): String
    
    fun generateReport(hosts: List<DiscoveredHost>, durationMs: Long, subnet: String): DiscoveryReport
    
    fun exportReportJson(report: DiscoveryReport): String
    
    fun exportReportText(report: DiscoveryReport): String
    
    fun teardown()
}
