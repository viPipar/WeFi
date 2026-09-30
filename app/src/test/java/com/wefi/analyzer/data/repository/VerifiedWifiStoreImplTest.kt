package com.wefi.analyzer.data.repository

import android.content.SharedPreferences
import com.wefi.analyzer.domain.model.VerifiedLabRouter
import com.wefi.analyzer.domain.model.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerifiedWifiStoreImplTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var store: VerifiedWifiStoreImpl

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        store = VerifiedWifiStoreImpl(context = null, customPrefs = fakePrefs)
    }

    @Test
    fun saveVerifiedRouter_savesAndEmitsCorrectly() {
        val router = VerifiedLabRouter(
            bssid = "00:11:22:33:44:55",
            ssid = "Lab-AP-1",
            workingPassword = "password123",
            discoveredTimestamp = 1000L,
            securityType = WifiSecurityType.WPA2
        )

        store.saveVerifiedRouter(router)

        assertTrue(store.isRouterVerified("00:11:22:33:44:55", "Lab-AP-1"))
        assertEquals("password123", store.getVerifiedPassword("00:11:22:33:44:55", "Lab-AP-1"))
        assertEquals(1, store.verifiedRouters.value.size)
        assertEquals("Lab-AP-1", store.verifiedRouters.value[0].ssid)
    }

    @Test
    fun saveVerifiedRouter_duplicateBssid_updatesExistingEntry() {
        val router1 = VerifiedLabRouter("00:11:22:33:44:55", "Lab-AP-1", "oldPass", 1000L)
        val router2 = VerifiedLabRouter("00:11:22:33:44:55", "Lab-AP-1", "newPass", 2000L)

        store.saveVerifiedRouter(router1)
        store.saveVerifiedRouter(router2)

        assertEquals(1, store.verifiedRouters.value.size)
        assertEquals("newPass", store.getVerifiedPassword("00:11:22:33:44:55", "Lab-AP-1"))
    }

    @Test
    fun removeVerifiedRouter_removesTargetBssid() {
        val router = VerifiedLabRouter("00:11:22:33:44:55", "Lab-AP-1", "pass", 1000L)
        store.saveVerifiedRouter(router)
        assertTrue(store.isRouterVerified("00:11:22:33:44:55", "Lab-AP-1"))

        store.removeVerifiedRouter("00:11:22:33:44:55")
        assertFalse(store.isRouterVerified("00:11:22:33:44:55", "Lab-AP-1"))
        assertNull(store.getVerifiedPassword("00:11:22:33:44:55", "Lab-AP-1"))
        assertTrue(store.verifiedRouters.value.isEmpty())
    }

    @Test
    fun clearAll_emptiesStore() {
        store.saveVerifiedRouter(VerifiedLabRouter("01", "SSID1", "p1"))
        store.saveVerifiedRouter(VerifiedLabRouter("02", "SSID2", "p2"))
        assertEquals(2, store.verifiedRouters.value.size)

        store.clearAll()
        assertTrue(store.verifiedRouters.value.isEmpty())
    }

    @Test
    fun corruptedJsonInPrefs_recoversGracefullyToEmptyList() {
        fakePrefs.putString("key_verified_routers_json", "{ broken json content [")
        val resilientStore = VerifiedWifiStoreImpl(context = null, customPrefs = fakePrefs)

        assertTrue(resilientStore.verifiedRouters.value.isEmpty())
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) {
        data[key] = value
    }

    override fun getString(key: String?, defValue: String?): String? {
        return (data[key] as? String) ?: defValue
    }

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun getAll(): MutableMap<String, *> = data
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clearCalled = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearCalled = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearCalled) data.clear()
            temp.forEach { (k, v) ->
                if (v == null) data.remove(k) else data[k] = v
            }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }
}
