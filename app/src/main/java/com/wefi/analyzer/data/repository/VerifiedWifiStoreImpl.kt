package com.wefi.analyzer.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.wefi.analyzer.domain.model.VerifiedLabRouter
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.VerifiedWifiStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class VerifiedWifiStoreImpl(
    context: Context? = null,
    customPrefs: SharedPreferences? = null
) : VerifiedWifiStore {

    private val prefs: SharedPreferences? = customPrefs ?: try {
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

    private val _verifiedRouters = MutableStateFlow<List<VerifiedLabRouter>>(loadFromPrefs())
    override val verifiedRouters: StateFlow<List<VerifiedLabRouter>> = _verifiedRouters.asStateFlow()

    @Synchronized
    override fun saveVerifiedRouter(router: VerifiedLabRouter) {
        val current = _verifiedRouters.value.toMutableList()
        val index = current.indexOfFirst {
            (it.bssid.isNotBlank() && it.bssid.equals(router.bssid, ignoreCase = true)) || it.ssid == router.ssid
        }
        if (index >= 0) {
            current[index] = router
        } else {
            current.add(0, router)
        }
        _verifiedRouters.value = current
        persistToPrefs(current)
    }

    override fun isRouterVerified(bssid: String, ssid: String): Boolean {
        return _verifiedRouters.value.any {
            (it.bssid.isNotBlank() && it.bssid.equals(bssid, ignoreCase = true)) || (it.ssid.isNotBlank() && it.ssid == ssid)
        }
    }

    override fun getVerifiedPassword(bssid: String, ssid: String): String? {
        return _verifiedRouters.value.firstOrNull {
            (it.bssid.isNotBlank() && it.bssid.equals(bssid, ignoreCase = true)) || (it.ssid.isNotBlank() && it.ssid == ssid)
        }?.workingPassword
    }

    @Synchronized
    override fun removeVerifiedRouter(bssid: String) {
        val current = _verifiedRouters.value.filterNot { it.bssid.equals(bssid, ignoreCase = true) }
        _verifiedRouters.value = current
        persistToPrefs(current)
    }

    @Synchronized
    override fun clearAll() {
        _verifiedRouters.value = emptyList()
        prefs?.edit()?.remove(KEY_ROUTERS_JSON)?.apply()
    }

    private fun persistToPrefs(routers: List<VerifiedLabRouter>) {
        val p = prefs ?: return
        try {
            val jsonArray = JSONArray()
            for (r in routers) {
                val obj = JSONObject()
                obj.put("bssid", r.bssid)
                obj.put("ssid", r.ssid)
                obj.put("password", r.workingPassword)
                obj.put("timestamp", r.discoveredTimestamp)
                obj.put("security", r.securityType.name)
                jsonArray.put(obj)
            }
            p.edit().putString(KEY_ROUTERS_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyimpan verified routers ke SharedPreferences", e)
        }
    }

    private fun loadFromPrefs(): List<VerifiedLabRouter> {
        val p = prefs ?: return emptyList()
        val rawJson = p.getString(KEY_ROUTERS_JSON, null) ?: return emptyList()
        val list = mutableListOf<VerifiedLabRouter>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val securityName = obj.optString("security", "WPA2")
                val securityType = try {
                    WifiSecurityType.valueOf(securityName)
                } catch (e: Exception) {
                    WifiSecurityType.WPA2
                }
                list.add(
                    VerifiedLabRouter(
                        bssid = obj.optString("bssid", ""),
                        ssid = obj.optString("ssid", ""),
                        workingPassword = obj.optString("password", ""),
                        discoveredTimestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        securityType = securityType
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca verified routers dari SharedPreferences", e)
        }
        return list
    }

    companion object {
        private const val TAG = "VerifiedWifiStore"
        const val PREFS_NAME = "wefi_verified_vault"
        const val KEY_ROUTERS_JSON = "key_verified_routers_json"
    }
}
