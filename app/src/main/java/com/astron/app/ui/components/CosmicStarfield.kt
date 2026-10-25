package com.astron.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.StellarViolet
import kotlin.random.Random

private data class Star(
    val xPercent: Float,
    val yPercent: Float,
    val radius: Float,
    val color: Color,
    val phaseOffset: Float
)

@Composable
fun CosmicStarfield(
    modifier: Modifier = Modifier,
    starCount: Int = 45
) {
    val stars = remember(starCount) {
        val random = Random(42) // Stable seed so stars don't jump on recomposition
        val starColors = listOf(
            CosmicCyan.copy(alpha = 0.65f),
            ElectricBlue.copy(alpha = 0.55f),
            StellarViolet.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.75f)
        )
        List(starCount) {
            Star(
                xPercent = random.nextFloat(),
                yPercent = random.nextFloat(),
                radius = random.nextFloat() * 1.6f + 0.8f,
                color = starColors[random.nextInt(starColors.size)],
                phaseOffset = random.nextFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starfieldTwinkle")
    val twinklePulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )

    // Cosmic nebula breathing pulsation
    val nebulaBreath by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebulaBreath"
    )

    // Periodic shooting star meteor progress
    val meteorProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteorProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Dual soft deep-space nebula auras with subtle respiratory pulsation
        val nebulaRadius1 = maxOf(width, height) * 0.65f * nebulaBreath
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    StellarViolet.copy(alpha = 0.09f),
                    ElectricBlue.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(width * 0.30f, height * 0.20f),
                radius = nebulaRadius1
            ),
            radius = nebulaRadius1,
            center = Offset(width * 0.30f, height * 0.20f)
        )

        val nebulaRadius2 = maxOf(width, height) * 0.55f * (2f - nebulaBreath)
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    CosmicCyan.copy(alpha = 0.06f),
                    StellarViolet.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(width * 0.80f, height * 0.75f),
                radius = nebulaRadius2
            ),
            radius = nebulaRadius2,
            center = Offset(width * 0.80f, height * 0.75f)
        )

        // 2. Stars and diffraction spikes
        for (star in stars) {
            val center = Offset(star.xPercent * width, star.yPercent * height)
            val dynamicAlpha = ((twinklePulse + star.phaseOffset) % 1f).coerceIn(0.2f, 0.95f)
            val finalColor = star.color.copy(alpha = star.color.alpha * dynamicAlpha)

            drawCircle(
                color = finalColor,
                radius = star.radius,
                center = center
            )

            // Diffraction flare for prominent stars
            if (star.radius > 2.0f && dynamicAlpha > 0.6f) {
                val flareLen = star.radius * 3.5f
                drawLine(
                    color = finalColor.copy(alpha = finalColor.alpha * 0.45f),
                    start = Offset(center.x - flareLen, center.y),
                    end = Offset(center.x + flareLen, center.y),
                    strokeWidth = 1f
                )
                drawLine(
                    color = finalColor.copy(alpha = finalColor.alpha * 0.45f),
                    start = Offset(center.x, center.y - flareLen),
                    end = Offset(center.x, center.y + flareLen),
                    strokeWidth = 1f
                )
            }
        }

        // 3. Cinematic Shooting Star / Meteor Streak
        val meteorWindowStart = 0.12f
        val meteorWindowEnd = 0.28f
        if (meteorProgress in meteorWindowStart..meteorWindowEnd) {
            val localNorm = (meteorProgress - meteorWindowStart) / (meteorWindowEnd - meteorWindowStart)
            val startX = width * 0.85f - (localNorm * width * 0.65f)
            val startY = height * 0.08f + (localNorm * height * 0.40f)
            val trailLength = 140f
            val endX = startX + trailLength * 0.85f
            val endY = startY - trailLength * 0.52f

            val meteorAlpha = when {
                localNorm < 0.2f -> localNorm / 0.2f
                localNorm > 0.8f -> (1f - localNorm) / 0.2f
                else -> 1f
            }.coerceIn(0f, 1f)

            drawLine(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f * meteorAlpha),
                        CosmicCyan.copy(alpha = 0.65f * meteorAlpha),
                        ElectricBlue.copy(alpha = 0.25f * meteorAlpha),
                        Color.Transparent
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Meteor head glow
            drawCircle(
                color = Color.White.copy(alpha = 0.95f * meteorAlpha),
                radius = 2.5f,
                center = Offset(startX, startY)
            )
            drawCircle(
                color = CosmicCyan.copy(alpha = 0.45f * meteorAlpha),
                radius = 6f,
                center = Offset(startX, startY)
            )
        }
    }
}
