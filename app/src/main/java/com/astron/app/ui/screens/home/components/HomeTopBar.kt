package com.astron.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.core.util.LocalHapticEngine
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CrimsonError
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary

@Composable
fun HomeTopBar(
    deviceName: String,
    localIp: String?,
    onOpenDiagnostics: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val hapticEngine = LocalHapticEngine.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device Identity Pill (Opens Diagnostics)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(999.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenDiagnostics()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (localIp != null) EmeraldVerified else CrimsonError, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = deviceName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Icons: Built-in File Explorer, Transfer History, Settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                hapticEngine?.click() ?: haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenFileBrowser()
            }) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Built-in File Explorer",
                    tint = TextSecondary
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigateToHistory()
            }) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Transfer History",
                    tint = TextSecondary
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigateToSettings()
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }
    }
}

