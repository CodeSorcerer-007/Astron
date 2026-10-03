package com.astron.app.ui.screens.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.AstronApplication
import com.astron.app.core.model.SessionState
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.SpaceBlack
import com.astron.app.ui.theme.SpaceBorder
import com.astron.app.ui.theme.SpaceDark
import com.astron.app.ui.theme.SpaceSurface
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary

@Composable
fun TransferScreen(
    onDismiss: () -> Unit
) {
    val transferEngine = AstronApplication.instance.transferEngine
    val session by transferEngine.currentSession.collectAsState()

    var isPaused by remember { mutableStateOf(false) }

    val currentSession = session
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(24.dp),
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
                    .background(SpaceSurface)
                    .border(1.dp, SpaceBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSession?.transport?.badge ?: "⚡ Local Wi-Fi",
                    color = CosmicCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isCompleted) {
                IconButton(
                    onClick = {
                        transferEngine.cancelTransfer()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Transfer",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        if (isCompleted) {
            // COMPLETED STATE
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(EmeraldVerified.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = EmeraldVerified,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TRANSFER COMPLETE",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${NetworkUtils.formatBytes(totalBytes)} • $totalFiles files",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(EmeraldVerified.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldVerified.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Verified ✓",
                    color = EmeraldVerified,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    transferEngine.dismissSession()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CosmicCyan,
                    contentColor = SpaceBlack
                )
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // ACTIVE TRANSFER IN PROGRESS STATE

            // Big Percentage
            val percent = (animatedProgress * 100).toInt()
            Text(
                text = "$percent%",
                color = TextPrimary,
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Bytes Transferred / Total
            Text(
                text = "${NetworkUtils.formatBytes(transferredBytes)} / ${NetworkUtils.formatBytes(totalBytes)}",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Current Transfer Speed (Smooth animated)
            Text(
                text = NetworkUtils.formatSpeed(speed),
                color = CosmicCyan,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Stabilized ETA Countdown
            Text(
                text = NetworkUtils.formatEta(eta),
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Smooth Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CosmicCyan,
                trackColor = SpaceSurface,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(28.dp))

            // File Count Summary card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SpaceDark)
                    .border(1.dp, SpaceBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$completedFiles files completed",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$remainingFiles remaining",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    if (activeFileName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = CosmicCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeFileName,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Pause / Cancel Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isPaused = !isPaused
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(SpaceBorder, SpaceBorder)))
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPaused) "Resume" else "Pause", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        transferEngine.cancelTransfer()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpaceSurface,
                        contentColor = TextSecondary
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
