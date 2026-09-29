package com.wefi.analyzer.ui.screens.aroundcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.WifiAuditLogger
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.repository.WifiScanner
import com.wefi.analyzer.domain.util.ConnectCheckResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel untuk tab Around Check.
 * Menangani strategi Golden Time pemindaian, countdown rate limiter, debounce password,
 * dan penyajian audit log koneksi Wi-Fi lab.
 */
class AroundCheckViewModel(
    private val scanner: WifiScanner,
    private val connector: WifiConnector,
    private val auditLogger: WifiAuditLogger? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    val scanState: StateFlow<WifiScanState> = scanner.scanState

    val connectState: StateFlow<WifiConnectState> = connector.connectState
    val lastScanTimestamp: StateFlow<Long> = scanner.lastScanTimestamp
    val remainingScanCooldownSeconds: StateFlow<Int> = scanner.remainingScanCooldownSeconds

    val auditLogs: StateFlow<List<WifiAuditLogEntry>> = auditLogger?.auditLogs
        ?: MutableStateFlow(emptyList())

    private val _showAuditBottomSheet = MutableStateFlow(false)
    val showAuditBottomSheet: StateFlow<Boolean> = _showAuditBottomSheet.asStateFlow()

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

    fun startScan(): Boolean {
        return scanner.startScan()
    }

    fun refreshFromCache() {
        scanner.refreshFromCache()
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

    fun setShowAuditBottomSheet(show: Boolean) {
        _showAuditBottomSheet.value = show
    }

    fun clearAuditLogs() {
        auditLogger?.clear()
    }

    fun canConnectToSsid(ssid: String): Boolean {
        return connector.canConnect(ssid) is ConnectCheckResult.Allowed
    }

    fun getRemainingCooldownForSsid(ssid: String): Int {
        return connector.remainingCooldownSeconds(ssid)
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
