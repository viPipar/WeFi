package com.wefi.analyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wefi.analyzer.data.repository.CurrentConnectionRepositoryImpl
import com.wefi.analyzer.data.repository.SpeedTestRepositoryImpl
import com.wefi.analyzer.data.repository.WifiScannerRepositoryImpl
import com.wefi.analyzer.domain.usecase.RunSpeedTestUseCase
import com.wefi.analyzer.ui.components.ContextualHelpDrawer
import com.wefi.analyzer.ui.components.MorphingHelpFab
import com.wefi.analyzer.ui.navigation.BottomNavBar
import com.wefi.analyzer.ui.navigation.Screen
import com.wefi.analyzer.ui.screens.aplist.ApListScreen
import com.wefi.analyzer.ui.screens.aplist.ApListViewModel
import com.wefi.analyzer.ui.screens.graph.ChannelGraphScreen
import com.wefi.analyzer.ui.screens.graph.ChannelGraphViewModel
import com.wefi.analyzer.ui.screens.rating.ChannelRatingScreen
import com.wefi.analyzer.ui.screens.rating.ChannelRatingViewModel
import com.wefi.analyzer.ui.screens.speedtest.SpeedTestScreen
import com.wefi.analyzer.ui.screens.speedtest.SpeedTestViewModel
import com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckScreen
import com.wefi.analyzer.ui.screens.aroundcheck.AroundCheckViewModel
import com.wefi.analyzer.data.repository.LabRouterAuditRepositoryImpl
import com.wefi.analyzer.ui.theme.WeFiTheme

import com.wefi.analyzer.data.repository.DeviceHardwareRepositoryImpl
import com.wefi.analyzer.domain.repository.DeviceHardwareRepository
import com.wefi.analyzer.ui.components.HardwareStateBanner
import com.wefi.analyzer.ui.screens.diagnostic.DiagnosticRecoveryScreen

class MainActivity : ComponentActivity() {

    private var scannerRepository: WifiScannerRepositoryImpl? = null
    private var connectionRepository: CurrentConnectionRepositoryImpl? = null
    private var hardwareRepository: DeviceHardwareRepositoryImpl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Inisialisasi Data Repositories
            val scannerRepo = WifiScannerRepositoryImpl(applicationContext)
            val connectionRepo = CurrentConnectionRepositoryImpl(applicationContext)
            val hardwareRepo = DeviceHardwareRepositoryImpl(applicationContext)
            val speedTestRepository = SpeedTestRepositoryImpl()
            val runSpeedTestUseCase = RunSpeedTestUseCase(speedTestRepository)
            val auditRepo = LabRouterAuditRepositoryImpl()

            scannerRepository = scannerRepo
            connectionRepository = connectionRepo
            hardwareRepository = hardwareRepo

            // Inisialisasi ViewModels
            val channelGraphViewModel = ChannelGraphViewModel(scannerRepo, connectionRepo)
            val apListViewModel = ApListViewModel(scannerRepo)
            val channelRatingViewModel = ChannelRatingViewModel(scannerRepo)
            val speedTestViewModel = SpeedTestViewModel(runSpeedTestUseCase, connectionRepo)
            val aroundCheckViewModel = AroundCheckViewModel(scannerRepo, auditRepo)

            setContent {
                WeFiTheme {
                    MainAppShell(
                        channelGraphViewModel = channelGraphViewModel,
                        apListViewModel = apListViewModel,
                        channelRatingViewModel = channelRatingViewModel,
                        speedTestViewModel = speedTestViewModel,
                        aroundCheckViewModel = aroundCheckViewModel,
                        hardwareRepository = hardwareRepo,
                        onTriggerInitialScan = { scannerRepo.startScan() }
                    )
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Inisialisasi startup gagal, mengalihkan ke mode recovery", t)
            setContent {
                WeFiTheme {
                    DiagnosticRecoveryScreen(
                        error = t,
                        onRetry = { recreate() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            hardwareRepository?.refresh()
            connectionRepository?.refreshConnectionInfo()
            scannerRepository?.startScan()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            scannerRepository?.teardown()
            connectionRepository?.teardown()
            hardwareRepository?.teardown()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Gagal membersihkan repository", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
    channelGraphViewModel: ChannelGraphViewModel,
    apListViewModel: ApListViewModel,
    channelRatingViewModel: ChannelRatingViewModel,
    speedTestViewModel: SpeedTestViewModel,
    aroundCheckViewModel: AroundCheckViewModel,
    hardwareRepository: DeviceHardwareRepository,
    onTriggerInitialScan: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ChannelGraph) }
    var isHelpDrawerOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val hardwareState by hardwareRepository.hardwareState.collectAsState()

    // Android Runtime Permissions Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hardwareRepository.refresh()
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            onTriggerInitialScan()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val notGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            onTriggerInitialScan()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    val activeScreen = currentScreen ?: Screen.ChannelGraph

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentRoute = activeScreen.route,
                onNavigate = { screen ->
                    currentScreen = screen
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HardwareStateBanner(
                hardwareState = hardwareState,
                onRequestPermissions = {
                    val reqList = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        reqList.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    permissionLauncher.launch(reqList.toTypedArray())
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Screen Content Routing
                when (activeScreen) {
                    Screen.ChannelGraph -> ChannelGraphScreen(viewModel = channelGraphViewModel)
                    Screen.ApList -> ApListScreen(viewModel = apListViewModel)
                    Screen.ChannelRating -> ChannelRatingScreen(viewModel = channelRatingViewModel)
                    Screen.SpeedTest -> SpeedTestScreen(viewModel = speedTestViewModel)
                    Screen.AroundCheck -> AroundCheckScreen(viewModel = aroundCheckViewModel)
                }

                // Juicy Morphing Floating Help Button (Melayang di atas konten)
                MorphingHelpFab(
                    tabId = activeScreen.tabId,
                    onHelpClick = {
                        isHelpDrawerOpen = true
                    }
                )

                // Contextual Help & Troubleshooting Drawer
                if (isHelpDrawerOpen) {
                    ContextualHelpDrawer(
                        tabId = activeScreen.tabId,
                        onDismiss = { isHelpDrawerOpen = false },
                        sheetState = sheetState
                    )
                }
            }
        }
    }
}
