package com.wefi.analyzer.ui.screens.aroundcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wefi.analyzer.domain.model.AroundCheckMode
import com.wefi.analyzer.domain.model.DfsParseResult
import com.wefi.analyzer.domain.model.VerifiedLabRouter
import com.wefi.analyzer.domain.model.WifiAuditLogEntry
import com.wefi.analyzer.domain.model.WifiConnectState
import com.wefi.analyzer.domain.model.WifiConnectStatus
import com.wefi.analyzer.domain.model.WifiScanItem
import com.wefi.analyzer.domain.model.WifiScanState
import com.wefi.analyzer.domain.model.WifiSecurityType
import com.wefi.analyzer.domain.repository.VerifiedWifiStore
import com.wefi.analyzer.domain.repository.WifiAuditLogger
import com.wefi.analyzer.domain.repository.WifiConnector
import com.wefi.analyzer.domain.repository.WifiScanner
import com.wefi.analyzer.domain.util.ConnectCheckResult
import com.wefi.analyzer.domain.util.DfsPasswordSanitizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel untuk tab Around Check.
 * Menangani strategi Golden Time pemindaian, countdown rate limiter, debounce password,
 * mode pencarian ganda (BFS dan DFS), perlindungan hardware router lab, serta vault router terverifikasi.
 */
class AroundCheckViewModel(
    private val scanner: WifiScanner,
    private val connector: WifiConnector,
    private val auditLogger: WifiAuditLogger? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val verifiedStore: VerifiedWifiStore? = null
) : ViewModel() {

    val scanState: StateFlow<WifiScanState> = scanner.scanState
    val connectState: StateFlow<WifiConnectState> = connector.connectState
    val lastScanTimestamp: StateFlow<Long> = scanner.lastScanTimestamp
    val remainingScanCooldownSeconds: StateFlow<Int> = scanner.remainingScanCooldownSeconds

    val auditLogs: StateFlow<List<WifiAuditLogEntry>> = auditLogger?.auditLogs
        ?: MutableStateFlow(emptyList())

    val verifiedRouters: StateFlow<List<VerifiedLabRouter>> = verifiedStore?.verifiedRouters
        ?: MutableStateFlow(emptyList())

    private val _showAuditBottomSheet = MutableStateFlow(false)
    val showAuditBottomSheet: StateFlow<Boolean> = _showAuditBottomSheet.asStateFlow()

    // Mode Seleksi Traversal (BFS / DFS)
    private val _selectedMode = MutableStateFlow(AroundCheckMode.BFS)
    val selectedMode: StateFlow<AroundCheckMode> = _selectedMode.asStateFlow()

    // Dialog Sambung Manual Satu Router
    private val _selectedItemForPasswordDialog = MutableStateFlow<WifiScanItem?>(null)
    val selectedItemForPasswordDialog: StateFlow<WifiScanItem?> = _selectedItemForPasswordDialog.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    // State Mode BFS (1 Password ke Banyak Router)
    private val _topPasswordInput = MutableStateFlow("")
    val topPasswordInput: StateFlow<String> = _topPasswordInput.asStateFlow()

    private val _isTopPasswordVisible = MutableStateFlow(false)
    val isTopPasswordVisible: StateFlow<Boolean> = _isTopPasswordVisible.asStateFlow()

    // State Mode DFS (Banyak Password ke 1 Router)
    private val _dfsCsvInput = MutableStateFlow("")
    val dfsCsvInput: StateFlow<String> = _dfsCsvInput.asStateFlow()

    private val _isDfsCsvVisible = MutableStateFlow(true)
    val isDfsCsvVisible: StateFlow<Boolean> = _isDfsCsvVisible.asStateFlow()

    private val _dfsTargetItem = MutableStateFlow<WifiScanItem?>(null)
    val dfsTargetItem: StateFlow<WifiScanItem?> = _dfsTargetItem.asStateFlow()

    private val _dfsParsedStats = MutableStateFlow(DfsParseResult(emptyList(), 0, 0))
    val dfsParsedStats: StateFlow<DfsParseResult> = _dfsParsedStats.asStateFlow()

    // Status Traversal Aktif (Terpadu untuk BFS & DFS)
    private val _isSequentialTesting = MutableStateFlow(false)
    val isSequentialTesting: StateFlow<Boolean> = _isSequentialTesting.asStateFlow()

    private val _currentCandidateIndex = MutableStateFlow(-1)
    val currentCandidateIndex: StateFlow<Int> = _currentCandidateIndex.asStateFlow()

    private val _traversalTotalCount = MutableStateFlow(0)
    val traversalTotalCount: StateFlow<Int> = _traversalTotalCount.asStateFlow()

    private val _sequentialTestMessage = MutableStateFlow("")
    val sequentialTestMessage: StateFlow<String> = _sequentialTestMessage.asStateFlow()

    // Hasil Goal Ditemukan & Peringatan Circuit Breaker
    private val _goalFoundRouter = MutableStateFlow<VerifiedLabRouter?>(null)
    val goalFoundRouter: StateFlow<VerifiedLabRouter?> = _goalFoundRouter.asStateFlow()

    private val _showCircuitBreakerDialog = MutableStateFlow(false)
    val showCircuitBreakerDialog: StateFlow<Boolean> = _showCircuitBreakerDialog.asStateFlow()

    private var circuitBreakerDeferred: CompletableDeferred<Boolean>? = null
    private var circuitBreakerAcknowledged = false

    private var traversalJob: Job? = null

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun sendSnackbar(message: String) {
        viewModelScope.launch(dispatcher) {
            _snackbarEvent.emit(message)
        }
    }

    init {
        startScan()
    }

    fun setMode(mode: AroundCheckMode) {
        if (_isSequentialTesting.value) return
        _selectedMode.value = mode
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

    fun isRouterVerified(bssid: String, ssid: String): Boolean {
        return verifiedStore?.isRouterVerified(bssid, ssid) ?: false
    }

    fun getVerifiedPassword(bssid: String, ssid: String): String? {
        return verifiedStore?.getVerifiedPassword(bssid, ssid)
    }

    fun removeVerifiedRouter(bssid: String) {
        verifiedStore?.removeVerifiedRouter(bssid)
        sendSnackbar("Router dihapus dari daftar terverifikasi")
    }

    fun dismissGoalFound() {
        _goalFoundRouter.value = null
    }

    fun acknowledgeCircuitBreaker(continueTraversal: Boolean) {
        _showCircuitBreakerDialog.value = false
        circuitBreakerAcknowledged = true
        circuitBreakerDeferred?.complete(continueTraversal)
        circuitBreakerDeferred = null
        if (!continueTraversal) {
            cancelTraversal()
        }
    }

    // --- Mode BFS & DFS Setters ---

    fun setTopPasswordInput(input: String) {
        _topPasswordInput.value = input
    }

    fun toggleTopPasswordVisibility() {
        _isTopPasswordVisible.value = !_isTopPasswordVisible.value
    }

    fun setDfsCsvInput(input: String) {
        _dfsCsvInput.value = input
        _dfsParsedStats.value = DfsPasswordSanitizer.parse(input)
    }

    fun applyDfsPracticumTemplate() {
        setDfsCsvInput(DFS_PRACTICUM_TEMPLATE)
        sendSnackbar("Template praktikum dimuat (56 kata sandi)")
    }

    fun toggleDfsCsvVisibility() {
        _isDfsCsvVisible.value = !_isDfsCsvVisible.value
    }

    fun selectDfsTargetItem(item: WifiScanItem?) {
        if (_isSequentialTesting.value) return
        _dfsTargetItem.value = item
    }

    // --- Traversal BFS: 1 Password ke Banyak Router ---

    fun startBfsTraversal() {
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
        _traversalTotalCount.value = scanItems.size
        _sequentialTestMessage.value = "Memulai pencarian BFS..."

        traversalJob?.cancel()
        traversalJob = viewModelScope.launch(dispatcher) {
            var index = 0
            while (isActive && index < scanItems.size) {
                _currentCandidateIndex.value = index
                val candidate = scanItems[index]

                val waitSec = connector.remainingCooldownSeconds(candidate.ssid)
                if (waitSec > 0) {
                    for (sec in waitSec downTo 1) {
                        if (!isActive) break
                        _sequentialTestMessage.value = "Jeda aman router (${sec}s) sebelum ${candidate.ssid}..."
                        delay(1000L)
                    }
                }
                if (!isActive) break

                _sequentialTestMessage.value = "BFS [${index + 1}/${scanItems.size}]: Menguji ${candidate.ssid}..."
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
                    val verified = VerifiedLabRouter(
                        bssid = candidate.bssid,
                        ssid = candidate.ssid,
                        workingPassword = password,
                        discoveredTimestamp = System.currentTimeMillis(),
                        securityType = candidate.security
                    )
                    verifiedStore?.saveVerifiedRouter(verified)
                    _goalFoundRouter.value = verified

                    _isSequentialTesting.value = false
                    _sequentialTestMessage.value = "Goal Ditemukan! Berhasil tersambung ke ${candidate.ssid}!"
                    sendSnackbar("Goal Ditemukan: ${candidate.ssid}!")
                    return@launch
                } else if (resultState.status is WifiConnectStatus.Cooldown) {
                    val remaining = (resultState.status as WifiConnectStatus.Cooldown).remainingSeconds
                    for (sec in remaining downTo 1) {
                        if (!isActive) break
                        _sequentialTestMessage.value = "Cooldown router: menunggu ${sec}s..."
                        delay(1000L)
                    }
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

    // Alias untuk backward compatibility dengan kode lama
    fun startSequentialTest() {
        startBfsTraversal()
    }

    // --- Traversal DFS: Banyak Password ke 1 Router Lab ---

    fun startDfsTraversal() {
        if (_isSequentialTesting.value) return

        val target = _dfsTargetItem.value
        if (target == null) {
            _sequentialTestMessage.value = "Pilih router lab target terlebih dahulu."
            sendSnackbar("Pilih salah satu router lab target pada daftar di bawah.")
            return
        }

        if (target.security == WifiSecurityType.OPEN) {
            _sequentialTestMessage.value = "Target adalah jaringan terbuka tanpa enkripsi."
            connector.connect(target.ssid, "", WifiSecurityType.OPEN)
            return
        }

        val parseResult = _dfsParsedStats.value
        val validPasswords = parseResult.validPasswords
        if (validPasswords.isEmpty()) {
            _sequentialTestMessage.value = "Daftar password valid kosong. Masukkan minimal 1 password (min 8 karakter)."
            sendSnackbar("Daftar password CSV belum memiliki kata sandi yang valid.")
            return
        }

        _isSequentialTesting.value = true
        _currentCandidateIndex.value = 0
        _traversalTotalCount.value = validPasswords.size
        _sequentialTestMessage.value = "Memulai pengujian DFS pada ${target.ssid}..."

        circuitBreakerAcknowledged = false
        circuitBreakerDeferred?.complete(false)
        circuitBreakerDeferred = null

        traversalJob?.cancel()
        traversalJob = viewModelScope.launch(dispatcher) {
            var consecutiveFailures = 0
            for (index in validPasswords.indices) {
                if (!isActive) break
                _currentCandidateIndex.value = index
                val candidatePassword = validPasswords[index]

                // Proteksi Pacing Hardware: Jeda aman 2-3s antar percobaan pada router yang sama
                if (index > 0) {
                    for (sec in 2 downTo 1) {
                        if (!isActive) break
                        _sequentialTestMessage.value = "Jeda aman router (${sec}s) sebelum password [${index + 1}/${validPasswords.size}]..."
                        delay(1000L)
                    }
                }
                if (!isActive) break

                // Quench HAL Driver delay
                connector.cancel()
                delay(500L)

                _sequentialTestMessage.value = "DFS [${index + 1}/${validPasswords.size}]: Menguji '${maskPassword(candidatePassword)}' pada ${target.ssid}..."
                connector.connect(target.ssid, candidatePassword, target.security)

                val resultState = connectState.first { state ->
                    state.targetSsid == target.ssid && (
                        state.status == WifiConnectStatus.Connected ||
                        state.status == WifiConnectStatus.Rejected ||
                        state.status == WifiConnectStatus.Failed ||
                        state.status == WifiConnectStatus.Timeout ||
                        state.status is WifiConnectStatus.Cooldown
                    )
                }

                if (resultState.status == WifiConnectStatus.Connected) {
                    val verified = VerifiedLabRouter(
                        bssid = target.bssid,
                        ssid = target.ssid,
                        workingPassword = candidatePassword,
                        discoveredTimestamp = System.currentTimeMillis(),
                        securityType = target.security
                    )
                    verifiedStore?.saveVerifiedRouter(verified)
                    _goalFoundRouter.value = verified

                    _isSequentialTesting.value = false
                    _sequentialTestMessage.value = "Goal DFS Ditemukan! Password cocok: $candidatePassword"
                    sendSnackbar("Goal DFS Ditemukan untuk ${target.ssid}!")
                    return@launch
                } else {
                    consecutiveFailures++
                    // Circuit Breaker jika terjadi 10 kegagalan beruntun dan belum pernah disetujui sebelumnya
                    if (consecutiveFailures >= 10 && !circuitBreakerAcknowledged && index + 1 < validPasswords.size) {
                        _showCircuitBreakerDialog.value = true
                        val deferred = CompletableDeferred<Boolean>()
                        circuitBreakerDeferred = deferred
                        val shouldContinue = deferred.await()
                        if (!shouldContinue || !isActive) {
                            break
                        }
                    }
                }
            }

            _isSequentialTesting.value = false
            _currentCandidateIndex.value = -1
            _sequentialTestMessage.value = "Seluruh ${validPasswords.size} password DFS telah diuji. Tidak ada yang cocok."
            sendSnackbar("Pengujian DFS selesai. Tidak ada password yang cocok.")
        }
    }

    private fun maskPassword(password: String): String {
        return if (password.length <= 4) "****" else password.take(2) + "***" + password.takeLast(2)
    }

    fun cancelTraversal() {
        circuitBreakerDeferred?.complete(false)
        circuitBreakerDeferred = null
        traversalJob?.cancel()
        traversalJob = null
        connector.cancel()
        _isSequentialTesting.value = false
        _currentCandidateIndex.value = -1
        _sequentialTestMessage.value = "Pengujian dihentikan."
        sendSnackbar("Pengujian dihentikan")
    }

    fun cancelSequentialTest() {
        cancelTraversal()
    }

    override fun onCleared() {
        super.onCleared()
        traversalJob?.cancel()
        scanner.teardown()
        connector.teardown()
    }

    companion object {
        const val DFS_PRACTICUM_TEMPLATE =
            "12345678;123456789;1234567890;12345678910;Password1;Aa123456;Pass@123;admin123;admin123456;qwerty123;P@ssw0rd;Admin@123;Abcd@1234;iloveyou;bismillah;theworldinyourhand;Telkomdso123;Kapler123;guru123456;Aboy1234;Tanjung99;gallant123;1hateyou;ZZZzzz111;asd123456;00000000;11111111;22222222;33333333;44444444;55555555;66666666;77777777;88888888;99999999;87654321;23456789;98765432;01234567;10987654;11223344;22334455;12121212;21212121;12341234;qwertyui;qwertyuiop;asdfghjk;asdfghjkl;zxcvbnm12;1qaz2wsx;qazwsxed;qweasdzxc;1qazxsw2;poiuytrew;ilmukomputeripb"
    }
}
