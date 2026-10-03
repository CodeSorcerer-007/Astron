package com.astron.app.ui.screens.receive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.AstronApplication
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.util.NetworkUtils
import com.astron.app.core.util.QrCodeGenerator
import com.astron.app.ui.components.IncomingTransferDialog
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.SpaceBlack
import com.astron.app.ui.theme.SpaceBorder
import com.astron.app.ui.theme.SpaceDark
import com.astron.app.ui.theme.SpaceSurface
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import kotlinx.coroutines.CompletableDeferred

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onNavigateBack: () -> Unit,
    onTransferAccepted: () -> Unit
) {
    val transferEngine = AstronApplication.instance.transferEngine
    val prefsRepo = AstronApplication.instance.preferencesRepository
    val deviceName by prefsRepo.deviceNameFlow.collectAsState(initial = NetworkUtils.getDefaultDeviceName())
    val session by transferEngine.currentSession.collectAsState()

    var pendingManifest by remember { mutableStateOf<TransferManifest?>(null) }
    var approvalDeferred by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    val localIp = NetworkUtils.getLocalIpAddress()
    val qrPayload = remember(localIp, deviceName) {
        "astron://pair?name=${java.net.URLEncoder.encode(deviceName, "UTF-8")}&ip=${localIp ?: "127.0.0.1"}&port=52737"
    }

    val qrBitmap = remember(qrPayload) {
        QrCodeGenerator.generateQrBitmap(qrPayload, size = 320)
    }

    LaunchedEffect(Unit) {
        transferEngine.onIncomingTransferPrompt = { manifest ->
            val deferred = CompletableDeferred<Boolean>()
            pendingManifest = manifest
            approvalDeferred = deferred
            val result = deferred.await()
            pendingManifest = null
            approvalDeferred = null
            result
        }
        transferEngine.startReceiverMode()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (session?.state == SessionState.ADVERTISING || session?.state == SessionState.IDLE) {
                transferEngine.dismissSession()
            }
        }
    }

    // Auto-navigate to transfer screen when state turns into TRANSFERRING
    LaunchedEffect(session?.state) {
        if (session?.state == SessionState.TRANSFERRING) {
            onTransferAccepted()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        containerColor = SpaceBlack,
        topBar = {
            TopAppBar(
                title = { Text("Receive", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBlack)
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
            Spacer(modifier = Modifier.height(12.dp))

            // Presence Radar Pulse
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .background(CosmicCyan.copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(SpaceDark)
                        .border(1.5.dp, CosmicCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = CosmicCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "READY TO RECEIVE",
                color = CosmicCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your device is visible as \"$deviceName\"",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Anyone on the same Wi-Fi can send to you",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // QR Code Box for fast camera pairing
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(SpaceDark)
                    .border(1.dp, SpaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Receive QR Code",
                        modifier = Modifier.size(190.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scan with another phone to connect",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // IP & Network info
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpaceSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Wi-Fi: ${localIp ?: "Waiting for network"}",
                    color = CosmicCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    pendingManifest?.let { manifest ->
        IncomingTransferDialog(
            manifest = manifest,
            onAccept = { approvalDeferred?.complete(true) },
            onDecline = { approvalDeferred?.complete(false) }
        )
    }
}
