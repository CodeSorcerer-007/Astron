package com.astron.app.ui.screens.send

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import com.astron.app.core.util.AppFileManager
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.components.AppPickerSheet
import com.astron.app.ui.components.CameraQrScanner
import com.astron.app.ui.components.ProximityRadarSheet
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.StellarViolet
import com.astron.app.ui.theme.SurfaceHover
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.SurfaceSubtle
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onNavigateBack: () -> Unit,
    onTransferStarted: () -> Unit,
    onNavigateToWebDrop: () -> Unit,
    onNavigateToLightDrop: () -> Unit,
    initialUris: List<Uri> = emptyList(),
    viewModel: SendViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(initialUris) {
        viewModel.initializeWithUris(initialUris)
    }

    val selectedItems by viewModel.selectedItems.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()

    var showRadarSheet by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showDirectQrScanner by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Document/File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(uris)
        }
    }

    // Photo/Video Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(uris)
        }
    }

    Scaffold(
        containerColor = CanvasLight,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Select Files & Apps", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDirectQrScanner = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Receiver QR",
                            tint = CosmicCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasLight)
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                val totalBytes = selectedItems.sumOf { it.size }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceLight)
                        .border(1.dp, BorderLight, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedItems.size} items ready",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = NetworkUtils.formatBytes(totalBytes),
                                color = CosmicCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.startDiscovery()
                                showRadarSheet = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CosmicCyan,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.NearMe, contentDescription = "Send", modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Source Selection Trio: Photos, Files, Apps (APK)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photos & Videos Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CosmicCyan.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CosmicCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Photos", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Media", color = TextSecondary, fontSize = 11.sp)
                }

                // Any File / Documents Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                        .clickable {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Files", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Docs & Zip", color = TextSecondary, fontSize = 11.sp)
                }

                // Apps (APK) Extractor Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                        .clickable {
                            showAppPickerSheet = true
                        }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StellarViolet.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = StellarViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Apps", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Extract APK", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SurfaceSubtle)
                                .border(1.dp, BorderLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = "No files selected",
                                tint = TextSecondary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No items selected yet",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose photos, documents, or apps above to send",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected Items (${selectedItems.size})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Clear all",
                        color = CosmicCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { viewModel.clearItems() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedItems, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceLight)
                                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (item.mimeType.contains("package-archive"))
                                            StellarViolet.copy(alpha = 0.12f)
                                        else CosmicCyan.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.mimeType.contains("package-archive"))
                                        Icons.Default.Android
                                    else
                                        Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = item.mimeType,
                                    tint = if (item.mimeType.contains("package-archive")) StellarViolet else CosmicCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = NetworkUtils.formatBytes(item.size),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removeItem(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${item.name}",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // App Picker Sheet
    if (showAppPickerSheet) {
        AppPickerSheet(
            onDismiss = { showAppPickerSheet = false },
            onAppsSelected = { chosenApps ->
                showAppPickerSheet = false
                val transferItems = chosenApps.map { AppFileManager.appToTransferItem(it) }
                viewModel.addTransferItems(transferItems)
            }
        )
    }

    // Direct QR Scanner Modal
    if (showDirectQrScanner) {
        ModalBottomSheet(
            onDismissRequest = { showDirectQrScanner = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan Receiver's QR Code",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Point camera at the receiver's screen to connect instantly",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
                ) {
                    CameraQrScanner(
                        modifier = Modifier.fillMaxSize(),
                        onQrScanned = { qrText ->
                            val peer = parsePairingQr(qrText)
                            if (peer != null) {
                                showDirectQrScanner = false
                                viewModel.sendToPeer(peer) {
                                    onTransferStarted()
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Proximity Radar Sheet
    if (showRadarSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showRadarSheet = false
                viewModel.stopDiscovery()
            },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            ProximityRadarSheet(
                discoveredPeers = discoveredPeers,
                onSelectPeer = { peer ->
                    showRadarSheet = false
                    viewModel.stopDiscovery()
                    viewModel.sendToPeer(peer) {
                        onTransferStarted()
                    }
                },
                onWebDropSelected = {
                    showRadarSheet = false
                    onNavigateToWebDrop()
                },
                onLightDropSelected = {
                    showRadarSheet = false
                    onNavigateToLightDrop()
                }
            )
        }
    }
}

/**
 * Parses an Astron pairing QR string into a PeerDevice.
 */
private fun parsePairingQr(qrText: String): PeerDevice? {
    return try {
        val uri = Uri.parse(qrText)
        if (uri.scheme == "astron" && uri.host == "pair") {
            val rawName = uri.getQueryParameter("name") ?: "Astron Receiver"
            val name = try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
            val ip = uri.getQueryParameter("ip") ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 52737
            PeerDevice(
                id = "peer-qr-$ip-$port",
                name = name,
                hostAddress = ip,
                port = port,
                transportType = TransportType.LOCAL_WIFI,
                lastSeen = System.currentTimeMillis()
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
