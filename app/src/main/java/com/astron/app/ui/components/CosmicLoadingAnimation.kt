package com.astron.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.ui.theme.BorderLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.StellarViolet
import com.astron.app.ui.theme.SurfaceLight
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-precision, state-of-the-art Cosmic Orbital Loading Animation.
 * Features rotating planetary rings, glowing atmospheric corona, and orbiting quantum satellites.
 */
@Composable
fun CosmicOrbitLoader(
    modifier: Modifier = Modifier,
    size: Dp = 90.dp,
    message: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_loader")

    // Planetary rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Counter orbital ring rotation
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )

    // Core pulsing scale and glow
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Core breathing alpha
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.minDimension / 2f

                // 1. Outer Atmospheric Neon Halo / Corona
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CosmicCyan.copy(alpha = 0.35f * pulseAlpha),
                            StellarViolet.copy(alpha = 0.15f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.95f
                    ),
                    radius = radius * 0.95f,
                    center = center
                )

                // 2. Primary Planetary Ring (tilted at 25 degrees)
                rotate(degrees = 25f, pivot = center) {
                    rotate(degrees = rotationAngle, pivot = center) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                listOf(
                                    CosmicCyan,
                                    ElectricBlue,
                                    StellarViolet,
                                    CosmicCyan.copy(alpha = 0.1f),
                                    CosmicCyan
                                ),
                                center = center
                            ),
                            topLeft = Offset(center.x - radius * 0.85f, center.y - radius * 0.38f),
                            size = Size(radius * 1.7f, radius * 0.76f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Orbiting Quantum Particle on Outer Ring
                        val rad = Math.toRadians(rotationAngle.toDouble())
                        val px = center.x + (radius * 0.85f) * cos(rad).toFloat()
                        val py = center.y + (radius * 0.38f) * sin(rad).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // 3. Counter-tilted Secondary Orbital Ring (tilted at -45 degrees)
                rotate(degrees = -45f, pivot = center) {
                    rotate(degrees = counterRotation, pivot = center) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                listOf(
                                    StellarViolet,
                                    CosmicCyan,
                                    StellarViolet.copy(alpha = 0.15f),
                                    StellarViolet
                                ),
                                center = center
                            ),
                            topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.28f),
                            size = Size(radius * 1.44f, radius * 0.56f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Orbiting Satellite on Secondary Ring
                        val rad2 = Math.toRadians(counterRotation.toDouble())
                        val sx = center.x + (radius * 0.72f) * cos(rad2).toFloat()
                        val sy = center.y + (radius * 0.28f) * sin(rad2).toFloat()
                        drawCircle(
                            color = CosmicCyan,
                            radius = 2.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }

                // 4. Central Celestial Planet Core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            CosmicCyan,
                            StellarViolet
                        ),
                        center = Offset(center.x - radius * 0.08f, center.y - radius * 0.08f),
                        radius = radius * 0.28f * pulseScale
                    ),
                    radius = radius * 0.26f * pulseScale,
                    center = center
                )
            }
        }

        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Compact inline cosmic loader for headers, lists, and buttons.
 */
@Composable
fun CosmicCompactSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "compact_spinner")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f

        rotate(degrees = angle, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        CosmicCyan.copy(alpha = 0.1f),
                        CosmicCyan,
                        ElectricBlue,
                        StellarViolet,
                        Color.White
                    ),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Fullscreen or modal loading overlay with rich cosmic card styling.
 */
@Composable
fun CosmicLoadingOverlay(
    message: String = "Connecting celestial peers..."
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceLight)
                .border(1.dp, BorderLight, RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            CosmicOrbitLoader(
                size = 110.dp,
                message = message
            )
        }
    }
}
