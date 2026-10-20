package com.astron.app.ui.components

import android.net.Uri
import com.astron.app.core.util.PairingUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.SurfaceHover
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.SurfaceSubtle
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import java.net.URLDecoder

@Composable
fun ProximityRadarSheet(
    discoveredPeers: List<PeerDevice>,
    onSelectPeer: (PeerDevice) -> Unit,
    onWebDropSelected: () -> Unit,
    onLightDropSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isQrScanMode by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(SurfaceLight)
            .border(1.dp, BorderLight, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(24.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceHover)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Header with Radar / QR Scan Mode Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isQrScanMode) "Scan Receiver's QR Code" else "Who are you sending to?",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isQrScanMode) "Point camera at the QR code on receiver's screen" else "Zero-config high speed local peer discovery",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Mode toggle button (Radar <-> QR Camera)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isQrScanMode = false },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isQrScanMode) CosmicCyan else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar Mode",
                        tint = if (!isQrScanMode) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { isQrScanMode = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isQrScanMode) CosmicCyan else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Pairing QR",
                        tint = if (isQrScanMode) Color.White else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isQrScanMode) {
            // Live Camera QR Scanner Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
            ) {
                CameraQrScanner(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    onQrScanned = { qrText ->
                        val peer = PairingUtils.parsePairingQr(qrText)
                        if (peer != null) {
                            onSelectPeer(peer)
                        }
                    }
                )
            }
        } else {
            // Radar Peer List Mode
            if (discoveredPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceSubtle)
                        .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(pulse1)
                                    .background(CosmicCyan.copy(alpha = alpha1), CircleShape)
                            )
                            CosmicCompactSpinner(
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Searching for nearby devices on Wi-Fi...",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Or tap QR Scanner above to pair directly",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(discoveredPeers) { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceSubtle)
                                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                                .semantics { contentDescription = "Send files to ${peer.name}" }
                                .clickable { onSelectPeer(peer) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(CosmicCyan, ElectricBlue))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer.name,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Ready • ${peer.transportType.badge} • ${peer.hostAddress}",
                                    color = CosmicCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CosmicCyan)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Send",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Alternative modes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                    .clickable { onWebDropSelected() }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = CosmicCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Web Drop", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Browser share", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                    .clickable { onLightDropSelected() }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Light Drop", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Optical Air-Gap", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                    .clickable {
                        val groupPeer = PeerDevice(
                            id = "multicast-group",
                            name = "Wi-Fi Multicast Group",
                            hostAddress = "239.255.42.99",
                            port = 52738,
                            transportType = TransportType.MULTICAST_GROUP,
                            model = "1-to-Many FEC Broadcast"
                        )
                        onSelectPeer(groupPeer)
                    }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = com.astron.app.ui.theme.StellarViolet,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Multicast", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("1-to-Many Room", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
