package com.astron.app.ui.screens.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astron.app.core.model.SessionState
import com.astron.app.core.util.HapticFeedbackManager
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.components.CosmicStarfield
import com.astron.app.ui.components.HolographicOrbitalSpeedometer
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.CrimsonError
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.RadiantAmber
import com.astron.app.ui.theme.SurfaceHover
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.SurfaceSubtle
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary

@Composable
fun TransferScreen(
    onDismiss: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEngine = remember(context) { HapticFeedbackManager(context) }
    val currentSession by viewModel.session.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()

    val progress = currentSession?.progress ?: 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val state = currentSession?.state ?: SessionState.IDLE
    val totalFiles = currentSession?.items?.size ?: currentSession?.manifest?.totalFiles ?: 0
    val completedFiles = currentSession?.completedFilesCount ?: currentSession?.verifiedFilesCount ?: 0
    val remainingFiles = (totalFiles - completedFiles).coerceAtLeast(0)

    val transferredBytes = currentSession?.transferredBytes ?: 0L
    val totalBytes = currentSession?.totalBytes ?: 0L
    val speed = currentSession?.speedBytesPerSec ?: 0L
    val eta = currentSession?.etaSeconds ?: 0L
    val activeFileName = currentSession?.currentItemName ?: ""
    val isCompleted = state == SessionState.COMPLETED
    val isFailed = state == SessionState.FAILED
    val isCancelled = state == SessionState.CANCELLED
    val isTerminal = isCompleted || isFailed || isCancelled

    BackHandler {
        if (isTerminal) {
            viewModel.dismissSession()
        }
        onDismiss()
    }

    var lastMilestoneCrossed by remember { androidx.compose.runtime.mutableStateOf(0) }
    LaunchedEffect(progress) {
        val currentMilestone = when {
            progress >= 0.75f -> 3
            progress >= 0.50f -> 2
            progress >= 0.25f -> 1
            else -> 0
        }
        if (currentMilestone > lastMilestoneCrossed && !isTerminal) {
            lastMilestoneCrossed = currentMilestone
            if (hapticsEnabled) {
                hapticEngine.milestone()
            }
        }
    }

    LaunchedEffect(isCompleted) {
        if (isCompleted && hapticsEnabled) {
            hapticEngine.success()
        }
    }

    LaunchedEffect(isFailed) {
        if (isFailed && hapticsEnabled) {
            hapticEngine.error()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CanvasLight)) {
        CosmicStarfield(starCount = 35)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Top status badge & cancel button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            isCompleted -> EmeraldVerified.copy(alpha = 0.12f)
                            isFailed -> CrimsonError.copy(alpha = 0.12f)
                            isCancelled -> RadiantAmber.copy(alpha = 0.12f)
                            else -> CosmicCyan.copy(alpha = 0.1f)
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            isCompleted -> EmeraldVerified
                            isFailed -> CrimsonError
                            isCancelled -> RadiantAmber
                            else -> CosmicCyan.copy(alpha = 0.3f)
                        },
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        isCompleted -> "COMPLETED"
                        isFailed -> "FAILED"
                        isCancelled -> "CANCELLED"
                        isPaused -> "PAUSED"
                        state == SessionState.TRANSFERRING -> "TRANSFERRING"
                        state == SessionState.VERIFYING -> "VERIFYING INTEGRITY"
                        else -> state.name
                    },
                    color = when {
                        isCompleted -> EmeraldVerified
                        isFailed -> CrimsonError
                        isCancelled -> RadiantAmber
                        else -> CosmicCyan
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            IconButton(
                onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isTerminal) {
                        viewModel.dismissSession()
                    } else {
                        viewModel.cancelTransfer()
                    }
                    onDismiss()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (isTerminal) "Close" else "Cancel Transfer",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Peer Device Avatar Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when {
                            isCompleted -> EmeraldVerified
                            isFailed -> CrimsonError
                            isCancelled -> RadiantAmber
                            else -> CosmicCyan
                        },
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentSession?.peer?.name ?: currentSession?.manifest?.senderDeviceName ?: "Nearby Device",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Holographic Planetary Orbital Speedometer Centerpiece
        HolographicOrbitalSpeedometer(
            progress = progress,
            state = state,
            transferredBytes = transferredBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speed,
            etaSeconds = eta,
            isPaused = isPaused,
            errorMessage = currentSession?.errorMessage
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Speed & ETA Telemetry
        AnimatedVisibility(visible = !isTerminal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPaused) "Transfer Paused" else NetworkUtils.formatSpeed(speed),
                    color = if (isPaused) TextSecondary else CosmicCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPaused) "Tap Resume to continue" else NetworkUtils.formatEta(eta),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Smooth Progress Bar
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = when {
                isCompleted -> EmeraldVerified
                isFailed -> CrimsonError
                isCancelled -> RadiantAmber
                else -> CosmicCyan
            },
            trackColor = SurfaceSubtle,
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Real-Time Dynamic Throughput Sparkline Graph
        AnimatedVisibility(visible = !isTerminal) {
            com.astron.app.ui.components.SpeedGraphView(
                currentSpeedBps = speed,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File Count Summary card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedFiles files completed",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$remainingFiles remaining",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activeFileName.isNotBlank() && !isTerminal) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = "Current active file",
                            tint = CosmicCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeFileName,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Controls
        if (isTerminal) {
            Button(
                onClick = {
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.dismissSession()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) CosmicCyan else SurfaceHover,
                    contentColor = if (isCompleted) Color.White else TextPrimary
                )
            ) {
                Text(
                    text = if (isCompleted) "Done" else "Dismiss",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // FUNCTIONAL PAUSE BUTTON
                OutlinedButton(
                    onClick = {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.togglePause()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPaused) "Resume" else "Pause", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.cancelTransfer()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceSubtle,
                        contentColor = TextSecondary
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
