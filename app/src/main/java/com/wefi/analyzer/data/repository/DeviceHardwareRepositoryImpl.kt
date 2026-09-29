package com.wefi.analyzer.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.wefi.analyzer.domain.model.DeviceHardwareState
import com.wefi.analyzer.domain.repository.DeviceHardwareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceHardwareRepositoryImpl(
    private val context: Context
) : DeviceHardwareRepository {

    private val wifiManager = try {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (e: Exception) {
        Log.w(TAG, "Gagal mendapatkan WifiManager", e)
        null
    }

    private val locationManager = try {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    } catch (e: Exception) {
        Log.w(TAG, "Gagal mendapatkan LocationManager", e)
        null
    }

    private val _hardwareState = MutableStateFlow(readCurrentState())
    override val hardwareState: StateFlow<DeviceHardwareState> = _hardwareState.asStateFlow()

    private var isReceiverRegistered = false

    private val hardwareChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                hardwareChangeReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendaftarkan receiver hardware", e)
        }
        refresh()
    }

    override fun refresh() {
        _hardwareState.value = readCurrentState()
    }

    override fun teardown() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(hardwareChangeReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Gagal melepaskan receiver hardware", e)
            }
        }
    }

    private fun readCurrentState(): DeviceHardwareState {
        val isWifi = try {
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }

        val isLocation = try {
            locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false
        } catch (e: Exception) {
            false
        }

        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return DeviceHardwareState(
            isWifiEnabled = isWifi,
            isLocationEnabled = isLocation,
            isLocationPermissionGranted = finePermission && nearbyPermission
        )
    }

    companion object {
        private const val TAG = "DeviceHardwareRepo"
    }
}
