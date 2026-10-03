package com.astron.app.ui.screens.lightdrop

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astron.app.ui.components.CameraQrScanner
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.StellarViolet
import com.astron.app.ui.theme.SurfaceHover
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.SurfaceSubtle
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LightDropScreen(
    onNavigateBack: () -> Unit,
    viewModel: LightDropViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        containerColor = CanvasLight,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Light Drop (Zero-Radio)", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasLight)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tab Selector
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceLight,
                contentColor = CosmicCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Transmit (Screen)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = "Transmit screen mode", modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Receive (Camera)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Receive camera mode", modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                TransmitOpticalView(viewModel)
            } else {
                if (!cameraPermissionState.status.isGranted) {
                    CameraPermissionGate(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
                } else {
                    ReceiveOpticalView(viewModel)
                }
            }
        }
    }
}

@Composable
fun CameraPermissionGate(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLight)
            .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CosmicCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Camera permission icon",
                tint = CosmicCyan,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera Access Required",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Light Drop uses your device's camera to scan and decode high-speed optical QR frame sequences without Wi-Fi, cellular, or Bluetooth.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = CosmicCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Grant Camera Access", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransmitOpticalView(viewModel: LightDropViewModel) {
    val frames by viewModel.frames.collectAsState()
    val currentFrameIndex by viewModel.currentFrameIndex.collectAsState()
    val currentBitmap by viewModel.currentFrameBitmap.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.prepareDemoData()
        viewModel.startTransmission()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTransmission()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(StellarViolet.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "ZERO-RADIO MODE • AIR-GAPPED",
                color = StellarViolet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Animated Frame QR Display
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.5.dp, BorderLight, RoundedCornerShape(20.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = "Optical Frame $currentFrameIndex",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Generating optical frames...", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Frame ${currentFrameIndex + 1} of ${frames.size.coerceAtLeast(1)}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Point receiver camera at this screen to transfer",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Frame rate slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Transmission Speed", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${fps.toInt()} FPS", color = CosmicCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = fps,
                onValueChange = { viewModel.setFps(it) },
                valueRange = 5f..25f,
                colors = SliderDefaults.colors(
                    thumbColor = CosmicCyan,
                    activeTrackColor = CosmicCyan,
                    inactiveTrackColor = SurfaceHover
                )
            )
        }
    }
}

@Composable
fun ReceiveOpticalView(viewModel: LightDropViewModel) {
    val receivedFrames by viewModel.receivedFrames.collectAsState()
    val totalExpected by viewModel.totalFramesExpected.collectAsState()
    val completedFile by viewModel.completedFile.collectAsState()

    val progress = if (totalExpected > 0) {
        (receivedFrames.size.toFloat() / totalExpected).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (completedFile != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceLight)
                    .border(1.5.dp, CosmicCyan, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CosmicCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Transfer complete",
                            tint = CosmicCyan,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Optical Transfer Complete",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = completedFile!!.name,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.resetReceiver() },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicCyan)
                    ) {
                        Text("Scan Another File", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            CameraQrScanner(
                onQrScanned = { rawPayload ->
                    viewModel.onFrameScanned(rawPayload)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                scanOverlayColor = CosmicCyan
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Decoded Frames Status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Optical Reassembly",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalExpected > 0) "${receivedFrames.size} / $totalExpected frames" else "Ready to scan",
                        color = CosmicCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CosmicCyan,
                    trackColor = SurfaceSubtle
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Screen-to-Camera optical stream automatically decodes frames and verifies integrity with SHA-256.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
