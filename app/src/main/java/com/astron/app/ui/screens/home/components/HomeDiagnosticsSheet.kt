package com.astron.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.CrimsonError
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDiagnosticsSheet(
    deviceName: String,
    localIp: String?,
    totalStorage: Long,
    freeStorage: Long,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLight,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Telemetry & Diagnostics",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (localIp != null) EmeraldVerified.copy(alpha = 0.12f) else CrimsonError.copy(alpha = 0.12f))
                        .border(1.dp, if (localIp != null) EmeraldVerified.copy(alpha = 0.3f) else CrimsonError.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (localIp != null) "Online" else "Offline",
                        color = if (localIp != null) EmeraldVerified else CrimsonError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            DiagnosticItem("Device Identity", "$deviceName (${android.os.Build.MODEL})")
            DiagnosticItem("Local IPv4 Address", localIp ?: "No active Wi-Fi interface")
            DiagnosticItem("LAN Socket Port", "TCP 52737 (Framed Binary Stream)")
            DiagnosticItem("Web Drop Server", if (localIp != null) "http://$localIp:8080" else "Offline")
            DiagnosticItem("P2P Hotspot Mode", "Local-Only SoftAP Ready")
            DiagnosticItem("Parallel Streams", "Multi-Socket Engine (up to 3x)")
            DiagnosticItem("Encryption Core", "AES-256-GCM • ECDH P-256 PFS")
            DiagnosticItem("Side-Channel Guard", "Constant-Time Hash & Token Verification")
            DiagnosticItem("STUN Traversal Engine", "RFC 5389 Active (Binding Discovery)")
            DiagnosticItem("Router Port Mapping", "UPnP / NAT-PMP Direct WAN Ready")
            DiagnosticItem("Multicast Group", "239.255.42.99:52738 (XOR FEC Active)")
            DiagnosticItem("BLE Beaconing", "Low-Power Proximity Discovery Active")
            DiagnosticItem("Background Service", "Foreground Service (dataSync + connectedDevice)")
            DiagnosticItem("Storage Remaining", "${NetworkUtils.formatBytes(freeStorage)} free of ${NetworkUtils.formatBytes(totalStorage)}")

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onDismiss()
                    onNavigateToSettings()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicCyan),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Manage App Settings", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

