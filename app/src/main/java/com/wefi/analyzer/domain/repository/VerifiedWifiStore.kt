package com.wefi.analyzer.domain.repository

import com.wefi.analyzer.domain.model.VerifiedLabRouter
import kotlinx.coroutines.flow.StateFlow

/**
 * Vault penyimpanan permanen untuk router lab yang telah berhasil di-unlock/diverifikasi.
 * Menyimpan data BSSID, SSID, kata sandi yang valid, timestamp, dan jenis keamanan.
 */
interface VerifiedWifiStore {
    val verifiedRouters: StateFlow<List<VerifiedLabRouter>>

    fun saveVerifiedRouter(router: VerifiedLabRouter)
    fun isRouterVerified(bssid: String, ssid: String): Boolean
    fun getVerifiedPassword(bssid: String, ssid: String): String?
    fun removeVerifiedRouter(bssid: String)
    fun clearAll()
}
