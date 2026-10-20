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

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        for (star in stars) {
            val center = Offset(star.xPercent * width, star.yPercent * height)
            val dynamicAlpha = ((twinklePulse + star.phaseOffset) % 1f).coerceIn(0.2f, 0.9f)
            drawCircle(
                color = star.color.copy(alpha = star.color.alpha * dynamicAlpha),
                radius = star.radius,
                center = center
            )
        }
    }
}
