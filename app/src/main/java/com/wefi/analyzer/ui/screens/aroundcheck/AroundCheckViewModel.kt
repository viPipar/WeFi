package com.wefi.analyzer.ui.screens.aroundcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.repository.WifiScanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel untuk tab Around Check.
 * Menangani alur pemindaian Wi-Fi resmi dan koneksi manual berbasis saran jaringan OS (WifiNetworkSuggestion).
 */
class AroundCheckViewModel(
    private val scanner: WifiScanner,
    private val connector: WifiConnector,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    val scanState: StateFlow<WifiScanState> = scanner.scanState
    val connectState: StateFlow<WifiConnectState> = connector.connectState

    private val _selectedItemForPasswordDialog = MutableStateFlow<WifiScanItem?>(null)
    val selectedItemForPasswordDialog: StateFlow<WifiScanItem?> = _selectedItemForPasswordDialog.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    init {
        // Pindai awal saat ViewModel pertama kali dibuat
        startScan()
    }

    fun startScan() {
        scanner.startScan()
    }

    fun isLocationEnabled(): Boolean {
        return scanner.isLocationEnabled()
    }

    fun openPasswordDialog(item: WifiScanItem) {
        if (item.security == WifiSecurityType.OPEN) {
            // Jaringan Open langsung dihubungkan tanpa dialog input password
            connector.connect(item.ssid, "", WifiSecurityType.OPEN)
            return
        }
        _selectedItemForPasswordDialog.value = item
        _passwordInput.value = ""
        _isPasswordVisible.value = false
    }

    fun dismissPasswordDialog() {
        _selectedItemForPasswordDialog.value = null
        _passwordInput.value = ""
        _isPasswordVisible.value = false
    }

    fun setPasswordInput(input: String) {
        _passwordInput.value = input
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun submitConnect() {
        val target = _selectedItemForPasswordDialog.value ?: return
        val password = _passwordInput.value
        // Hapus password dari state UI segera setelah dikirim ke sistem OS
        dismissPasswordDialog()

        viewModelScope.launch(dispatcher) {
            connector.connect(target.ssid, password, target.security)
        }
    }

    fun cancelConnect() {
        connector.cancel()
    }

    fun forgetNetwork(ssid: String) {
        connector.forgetNetwork(ssid)
    }

    fun isItemWaitingApproval(ssid: String): Boolean {
        val state = connectState.value
        return state.targetSsid == ssid && state.status == WifiConnectStatus.WaitingApproval
    }

    override fun onCleared() {
        super.onCleared()
        scanner.teardown()
        connector.teardown()
    }
}
