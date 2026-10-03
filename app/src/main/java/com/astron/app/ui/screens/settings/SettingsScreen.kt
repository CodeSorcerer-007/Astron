package com.astron.app.ui.screens.settings

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astron.app.BuildConfig
import com.astron.app.core.model.TransferPriority
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.SurfaceHover
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.SurfaceSubtle
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val transferPriority by viewModel.transferPriority.collectAsState()
    val autoAccept by viewModel.autoAcceptTrusted.collectAsState()
    val haptics by viewModel.hapticsEnabled.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()

    var showNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf(deviceName) }

    var showPriorityDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val localIp = NetworkUtils.getLocalIpAddress()

    Scaffold(
        containerColor = CanvasLight,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // SECTION 1: DEVICE IDENTITY
            Text(
                text = "Device Identity",
                color = CosmicCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .clickable {
                        editNameText = deviceName
                        showNameDialog = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Device Name", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(deviceName, color = CosmicCyan, fontSize = 13.sp)
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: TRANSFER PREFERENCES
            Text(
                text = "Transfer Strategy",
                color = CosmicCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Priority mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .clickable { showPriorityDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Transfer Priority", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(transferPriority.title, color = CosmicCyan, fontSize = 13.sp)
                }
                Icon(Icons.Default.Speed, contentDescription = "Transfer Priority", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-accept toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Accept Transfers", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Skip confirmation prompt for incoming transfers", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = autoAccept,
                    onCheckedChange = { viewModel.setAutoAcceptTrusted(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CosmicCyan,
                        uncheckedTrackColor = SurfaceHover
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: SYSTEM & FEEDBACK
            Text(
                text = "Interaction",
                color = CosmicCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Dark Mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dark Theme", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("OLED high-contrast dark aesthetic", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CosmicCyan,
                        uncheckedTrackColor = SurfaceHover
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Haptics toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tactile vibrations on connect & completion", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = haptics,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CosmicCyan,
                        uncheckedTrackColor = SurfaceHover
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 4: ADVANCED DIAGNOSTICS
            Text(
                text = "Advanced",
                color = CosmicCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .clickable { showDiagnosticsDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Connection Diagnostics", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Inspect network interfaces, IP addresses & socket state", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Default.BugReport, contentDescription = "Connection Diagnostics", tint = CosmicCyan, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Version info footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Astron v${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Local-First • Encrypted • Zero Cloud Storage", color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit Name Dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Device Name", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CosmicCyan,
                        unfocusedBorderColor = BorderLight
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameText.isNotBlank()) {
                            viewModel.updateDeviceName(editNameText)
                        }
                        showNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicCyan, contentColor = Color.White)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceLight
        )
    }

    // Priority Selection Dialog
    if (showPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text("Transfer Priority Mode", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferPriority.entries.forEach { priority ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (priority == transferPriority) CosmicCyan.copy(alpha = 0.1f) else SurfaceSubtle)
                                .clickable {
                                    viewModel.updateTransferPriority(priority)
                                    showPriorityDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(priority.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(priority.description, color = TextSecondary, fontSize = 11.sp)
                            }
                            if (priority == transferPriority) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = CosmicCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPriorityDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = SurfaceLight
        )
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Connection Diagnostics", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IPv4 Address: ${localIp ?: "None (Offline)"}", color = CosmicCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("LAN Port: 52737 (TCP)", color = TextSecondary, fontSize = 13.sp)
                    Text("Web Drop Port: 8080 (HTTP)", color = TextSecondary, fontSize = 13.sp)
                    Text("Chunk Buffer Size: 128 KB", color = TextSecondary, fontSize = 13.sp)
                    Text("Encryption: AES-256-GCM (128-bit Tag)", color = TextSecondary, fontSize = 13.sp)
                    Text("Integrity: SHA-256 Checksum", color = TextSecondary, fontSize = 13.sp)
                    Text("Optical Transport: Light Drop v1.0 (CameraX)", color = TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDiagnosticsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicCyan, contentColor = Color.White)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            containerColor = SurfaceLight
        )
    }
}
