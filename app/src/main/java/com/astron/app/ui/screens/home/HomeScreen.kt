package com.astron.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.permissions.PermissionManager
import com.astron.app.core.util.AppFileManager
import com.astron.app.ui.components.AppPickerSheet
import com.astron.app.ui.components.AstronLogoHeader
import com.astron.app.ui.components.CosmicStarfield
import com.astron.app.ui.screens.home.components.HomeDiagnosticsSheet
import com.astron.app.ui.screens.home.components.HomePermissionSheet
import com.astron.app.ui.screens.home.components.HomeTopBar
import com.astron.app.ui.screens.home.components.HomeWifiWarningBanner
import com.astron.app.ui.screens.home.components.InterruptedSessionCard
import com.astron.app.ui.screens.home.components.LatestFileCard
import com.astron.app.ui.screens.home.components.PrimaryActionCards
import com.astron.app.ui.screens.home.components.QuickActionsSuite
import com.astron.app.ui.screens.home.components.RecentTransfersCard
import com.astron.app.ui.screens.home.components.StorageTelemetryCard
import com.astron.app.ui.theme.CanvasLight
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToSend: (List<com.astron.app.core.model.TransferItem>) -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToWebDrop: () -> Unit,
    onNavigateToLightDrop: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFileBrowser: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val deviceName by viewModel.deviceName.collectAsState()
    val historyList by viewModel.recentTransfers.collectAsState()
    val localIp by viewModel.localIpAddress.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val latestFile by viewModel.latestReceivedFile.collectAsState()
    val interruptedSessions by viewModel.interruptedSessions.collectAsState()

    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionManager.getRequiredPermissions()
    )

    LaunchedEffect(Unit) {
        viewModel.refreshState()
    }

    val totalStorage = storageInfo.first
    val freeStorage = storageInfo.second

    Box(modifier = Modifier.fillMaxSize().background(CanvasLight)) {
        CosmicStarfield(starCount = 40)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR: Device Identity Pill & Quick System Actions
            HomeTopBar(
                deviceName = deviceName,
                localIp = localIp,
                onOpenDiagnostics = { showDiagnosticsSheet = true },
                onOpenFileBrowser = {
                    if (onNavigateToFileBrowser != null) {
                        onNavigateToFileBrowser()
                    } else {
                        AppFileManager.openDownloadsFolder(context)
                    }
                },
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings
            )

            // Wi-Fi Disconnected Warning Banner if no local IP
            if (localIp == null) {
                Spacer(modifier = Modifier.height(10.dp))
                HomeWifiWarningBanner(onRefresh = { viewModel.refreshState() })
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Astron Logo Hero
            AstronLogoHeader(compact = true)

            // Interrupted Session Recovery Card
            if (interruptedSessions.isNotEmpty()) {
                val session = interruptedSessions.first()
                Spacer(modifier = Modifier.height(10.dp))
                InterruptedSessionCard(
                    session = session,
                    onResume = { direction ->
                        if (direction == TransferDirection.SEND) {
                            onNavigateToSend(emptyList())
                        } else {
                            onNavigateToReceive()
                        }
                    },
                    onDismiss = { sessionId -> viewModel.dismissInterruptedSession(sessionId) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Storage & Network Telemetry Card
            StorageTelemetryCard(
                totalStorage = totalStorage,
                freeStorage = freeStorage,
                localIp = localIp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Actions: Send, Receive, File Explorer
            PrimaryActionCards(
                onSendClicked = {
                    if (!permissionsState.allPermissionsGranted) {
                        showPermissionSheet = true
                    } else {
                        onNavigateToSend(emptyList())
                    }
                },
                onReceiveClicked = {
                    if (!permissionsState.allPermissionsGranted) {
                        showPermissionSheet = true
                    } else {
                        onNavigateToReceive()
                    }
                },
                onFileBrowserClicked = onNavigateToFileBrowser
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Suite: Installed Apps, Web Drop, Light Drop
            QuickActionsSuite(
                onAppsClicked = { showAppPickerSheet = true },
                onWebDropClicked = onNavigateToWebDrop,
                onLightDropClicked = onNavigateToLightDrop
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Latest Received File Quick Actions Card
            val file = latestFile?.takeIf { it.exists() }
            if (file != null) {
                LatestFileCard(file = file, context = context)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recent Transfers Feed
            RecentTransfersCard(
                historyList = historyList,
                onNavigateToHistory = onNavigateToHistory
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // App Picker Sheet from Home
        if (showAppPickerSheet) {
            AppPickerSheet(
                onDismiss = { showAppPickerSheet = false },
                onAppsSelected = { chosenApps ->
                    showAppPickerSheet = false
                    val transferItems = chosenApps.map { AppFileManager.appToTransferItem(it) }
                    onNavigateToSend(transferItems)
                }
            )
        }

        // Permission Dialog Bottom Sheet
        if (showPermissionSheet) {
            HomePermissionSheet(
                permissionsState = permissionsState,
                context = context,
                onDismiss = { showPermissionSheet = false }
            )
        }

        // Diagnostics & Network Telemetry Bottom Sheet
        if (showDiagnosticsSheet) {
            HomeDiagnosticsSheet(
                deviceName = deviceName,
                localIp = localIp,
                totalStorage = totalStorage,
                freeStorage = freeStorage,
                onDismiss = { showDiagnosticsSheet = false },
                onNavigateToSettings = {
                    showDiagnosticsSheet = false
                    onNavigateToSettings()
                }
            )
        }
    }
}
