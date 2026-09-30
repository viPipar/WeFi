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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    // State untuk Bar Input Password & Pengujian Sekuensial di Atas
    private val _topPasswordInput = MutableStateFlow("")
    val topPasswordInput: StateFlow<String> = _topPasswordInput.asStateFlow()

    private val _isTopPasswordVisible = MutableStateFlow(false)
    val isTopPasswordVisible: StateFlow<Boolean> = _isTopPasswordVisible.asStateFlow()

    private val _isSequentialTesting = MutableStateFlow(false)
    val isSequentialTesting: StateFlow<Boolean> = _isSequentialTesting.asStateFlow()

    private val _currentCandidateIndex = MutableStateFlow(-1)
    val currentCandidateIndex: StateFlow<Int> = _currentCandidateIndex.asStateFlow()

    private val _sequentialTestMessage = MutableStateFlow("")
    val sequentialTestMessage: StateFlow<String> = _sequentialTestMessage.asStateFlow()

    private var sequentialTestJob: Job? = null

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun sendSnackbar(message: String) {
        viewModelScope.launch(dispatcher) {
            _snackbarEvent.emit(message)
        }
    }

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
        sendSnackbar("Koneksi dibatalkan")
    }

    fun forgetNetwork(ssid: String) {
        connector.forgetNetwork(ssid)
        sendSnackbar("Jaringan $ssid telah dilupakan")
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

    fun setTopPasswordInput(input: String) {
        _topPasswordInput.value = input
    }

    fun toggleTopPasswordVisibility() {
        _isTopPasswordVisible.value = !_isTopPasswordVisible.value
    }

    fun startSequentialTest() {
        if (_isSequentialTesting.value) return

        val scanItems = when (val s = scanState.value) {
            is WifiScanState.Success -> s.items
            is WifiScanState.Throttled -> s.items
            else -> emptyList()
        }.filter { it.ssid.isNotBlank() }

        if (scanItems.isEmpty()) {
            _sequentialTestMessage.value = "Daftar Wi-Fi kosong. Silakan scan terlebih dahulu."
            scanner.startScan()
            sendSnackbar("Daftar Wi-Fi kosong. Memulai scan otomatis...")
            return
        }

        val password = _topPasswordInput.value
        if (password.isBlank() && scanItems.none { it.security == WifiSecurityType.OPEN }) {
            _sequentialTestMessage.value = "Password Wi-Fi masih kosong. Masukkan password target di atas."
            sendSnackbar("Masukkan password target terlebih dahulu.")
            return
        }

        _isSequentialTesting.value = true
        _currentCandidateIndex.value = 0
        _sequentialTestMessage.value = "Memulai pengujian Wi-Fi..."

        sequentialTestJob?.cancel()
        sequentialTestJob = viewModelScope.launch(dispatcher) {
            var index = 0
            while (isActive && index < scanItems.size) {
                _currentCandidateIndex.value = index
                val candidate = scanItems[index]

                // Periksa apakah candidate ini masih dalam masa cooldown throttler
                val waitSec = connector.remainingCooldownSeconds(candidate.ssid)
                if (waitSec > 0) {
                    for (sec in waitSec downTo 1) {
                        if (!isActive) break
                        _sequentialTestMessage.value = "Jeda aman router (${sec}s) sebelum ${candidate.ssid}..."
                        delay(1000L)
                    }
                }
                if (!isActive) break

                _sequentialTestMessage.value = "Menguji Wi-Fi [${index + 1}/${scanItems.size}]: ${candidate.ssid}..."
                connector.cancel()
                connector.connect(candidate.ssid, password, candidate.security)

                val resultState = connectState.first { state ->
                    state.targetSsid == candidate.ssid && (
                        state.status == WifiConnectStatus.Connected ||
                        state.status == WifiConnectStatus.Rejected ||
                        state.status == WifiConnectStatus.Failed ||
                        state.status == WifiConnectStatus.Timeout ||
                        state.status is WifiConnectStatus.Cooldown
                    )
                }

                if (resultState.status == WifiConnectStatus.Connected) {
                    _isSequentialTesting.value = false
                    _sequentialTestMessage.value = "Berhasil tersambung ke ${candidate.ssid}!"
                    sendSnackbar("Berhasil tersambung ke ${candidate.ssid}!")
                    return@launch
                } else if (resultState.status is WifiConnectStatus.Cooldown) {
                    val remaining = (resultState.status as WifiConnectStatus.Cooldown).remainingSeconds
                    for (sec in remaining downTo 1) {
                        if (!isActive) break
                        _sequentialTestMessage.value = "Cooldown router: menunggu ${sec}s..."
                        delay(1000L)
                    }
                    // Ulangi percobaan pada kandidat ini setelah cooldown
                    continue
                } else {
                    if (index + 1 < scanItems.size) {
                        val nextCandidate = scanItems[index + 1]
                        _sequentialTestMessage.value = "Gagal pada ${candidate.ssid}. Melanjutkan ke ${nextCandidate.ssid}..."
                        delay(1500L)
                        index++
                    } else {
                        _isSequentialTesting.value = false
                        _currentCandidateIndex.value = -1
                        _sequentialTestMessage.value = "Semua Wi-Fi selesai diuji. Tidak ada yang berhasil tersambung."
                        sendSnackbar("Semua Wi-Fi selesai diuji.")
                        return@launch
                    }
                }
            }
            _isSequentialTesting.value = false
        }
    }

    fun cancelSequentialTest() {
        sequentialTestJob?.cancel()
        sequentialTestJob = null
        connector.cancel()
        _isSequentialTesting.value = false
        _currentCandidateIndex.value = -1
        _sequentialTestMessage.value = "Pengujian dihentikan."
        sendSnackbar("Pengujian sekuensial dihentikan")
    }

    override fun onCleared() {
        super.onCleared()
        sequentialTestJob?.cancel()
        scanner.teardown()
        connector.teardown()
    }
}
