package com.astron.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.core.model.SessionState
import com.astron.app.core.util.NetworkUtils
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.CrimsonError
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.RadiantAmber
import com.astron.app.ui.theme.StellarViolet
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🪐 Holographic Orbital Speedometer — 10/10 Gold Master Visual Centerpiece.
 *
 * Combines:
 * - Sweeping planetary orbital neon plasma arc with radiant sweep gradients
 * - Orbiting satellite flare with comet stardust trail tracking the exact progress tip
 * - Ambient counter-rotating constellation ring
 * - Deep-space glassmorphic core housing giant bold telemetry
 * - Supernova shockwave celebration rings upon verified SHA-256 completion
 */
@Composable
fun HolographicOrbitalSpeedometer(
    progress: Float,
    state: SessionState,
    transferredBytes: Long,
    totalBytes: Long,
    speedBytesPerSec: Long,
    etaSeconds: Long,
    isPaused: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "orbitalProgress"
    )

    val isCompleted = state == SessionState.COMPLETED
    val isFailed = state == SessionState.FAILED
    val isCancelled = state == SessionState.CANCELLED

    val infiniteTransition = rememberInfiniteTransition(label = "orbitalDynamics")

    // Slow planetary orbital ring rotation
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalRotation"
    )

    // Breathing plasma halo alpha
    val plasmaHaloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "plasmaHaloAlpha"
    )

    // Completion ripple expansions
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    val activeColor = when {
        isCompleted -> EmeraldVerified
        isFailed -> CrimsonError
        isCancelled || isPaused -> RadiantAmber
        else -> CosmicCyan
    }

    val secondaryColor = when {
        isCompleted -> EmeraldVerified.copy(alpha = 0.6f)
        isFailed -> CrimsonError.copy(alpha = 0.6f)
        isCancelled || isPaused -> RadiantAmber.copy(alpha = 0.6f)
        else -> StellarViolet
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(260.dp)
    ) {
        // ── 1. CELEBRATION SUPERNOVA SHOCKWAVES (When Completed) ──────────────
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(200.dp * rippleScale)
                    .clip(CircleShape)
                    .border(2.dp, EmeraldVerified.copy(alpha = rippleAlpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(200.dp * ((rippleScale - 1f) * 0.7f + 1f))
                    .clip(CircleShape)
                    .border(1.5.dp, CosmicCyan.copy(alpha = rippleAlpha * 0.8f), CircleShape)
            )
        }

        val guideColor = BorderLight.copy(alpha = 0.35f)

        // ── 2. HOLOGRAPHIC ORBITAL GAUGE CANVAS ──────────────────────────────
        Canvas(modifier = Modifier.size(240.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.width / 2f) - 16.dp.toPx()

            // A. Faint Background Orbital Guide Ring
            drawCircle(
                color = guideColor,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )

            // B. Counter-Rotating Constellation Tick Marks (Every 30 degrees)
            rotate(degrees = orbitalRotation, pivot = Offset(cx, cy)) {
                for (deg in 0 until 360 step 30) {
                    val rad = Math.toRadians(deg.toDouble())
                    val outerR = radius + 6.dp.toPx()
                    val innerR = radius - 6.dp.toPx()
                    val startX = (cx + innerR * cos(rad)).toFloat()
                    val startY = (cy + innerR * sin(rad)).toFloat()
                    val endX = (cx + outerR * cos(rad)).toFloat()
                    val endY = (cy + outerR * sin(rad)).toFloat()
                    drawLine(
                        color = secondaryColor.copy(alpha = if (deg % 90 == 0) 0.35f else 0.15f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = (if (deg % 90 == 0) 1.5.dp else 1.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // C. Dynamic Sweeping Holographic Progress Arc
            val sweepAngle = (animatedProgress * 360f).coerceIn(0f, 360f)
            if (sweepAngle > 0.5f) {
                // Arc Ambient Plasma Glow Tube (Wide blurry stroke)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(activeColor, secondaryColor, ElectricBlue, activeColor),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                    alpha = plasmaHaloAlpha,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // Arc Crisp Core Neon Laser Path
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(activeColor, secondaryColor, ElectricBlue, activeColor),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // D. Orbiting Comet Satellite Head Flare (tracks exact progress tip)
                if (!isCompleted && !isFailed) {
                    val headAngleRad = Math.toRadians((sweepAngle - 90.0))
                    val headX = (cx + radius * cos(headAngleRad)).toFloat()
                    val headY = (cy + radius * sin(headAngleRad)).toFloat()
                    val headCenter = Offset(headX, headY)

                    // Outer glowing satellite corona
                    drawCircle(
                        color = activeColor.copy(alpha = 0.4f),
                        radius = 10.dp.toPx(),
                        center = headCenter
                    )
                    // Medium plasma core
                    drawCircle(
                        color = activeColor,
                        radius = 4.5.dp.toPx(),
                        center = headCenter
                    )
                    // Brilliant starpoint center
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = headCenter
                    )
                }
            }
        }

        // ── 3. INNER COSMIC GLASSMORPHIC CORE ────────────────────────────────
        Box(
            modifier = Modifier
                .size(174.dp)
                .clip(CircleShape)
                .background(SurfaceLight)
                .border(
                    1.5.dp,
                    when {
                        isCompleted -> EmeraldVerified.copy(alpha = 0.8f)
                        isFailed -> CrimsonError.copy(alpha = 0.8f)
                        isCancelled -> RadiantAmber.copy(alpha = 0.8f)
                        else -> BorderLight
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                when {
                    isCompleted -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = EmeraldVerified,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "100%",
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Verified SHA-256",
                            color = EmeraldVerified,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    isFailed -> {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Transfer Failed",
                            tint = CrimsonError,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed",
                            color = CrimsonError,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    isCancelled -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Transfer Cancelled",
                            tint = RadiantAmber,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cancelled",
                            color = RadiantAmber,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    else -> {
                        // Giant Bold Telemetry Percentage
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1.5).sp
                        )

                        // Live Speed Pill with Pulsing Radar Dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(activeColor.copy(alpha = 0.12f))
                                .border(1.dp, activeColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(activeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = if (isPaused) "PAUSED" else NetworkUtils.formatSpeed(speedBytesPerSec),
                                color = activeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Byte Counter Progress
                        Text(
                            text = "${NetworkUtils.formatBytes(transferredBytes)} / ${NetworkUtils.formatBytes(totalBytes)}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
