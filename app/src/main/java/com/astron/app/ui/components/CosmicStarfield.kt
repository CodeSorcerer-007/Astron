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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.LocalAstronColors
import com.astron.app.ui.theme.StellarViolet
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val xPercent: Float,
    val yPercent: Float,
    val radius: Float,
    val darkColor: Color,
    val lightColor: Color,
    val phaseOffset: Float,
    val hasFlare: Boolean,
    val driftSpeed: Float
)

private data class ConstellationLink(
    val starIndexA: Int,
    val starIndexB: Int
)

/**
 * CosmicStarfield - An adaptive, live celestial animation engine tailored separately
 * for both Obsidian Dark and Swiss Precision Light/White themes.
 *
 * In Dark Theme: Deep multi-spectral breathing nebulae, diamond-and-cyan twinkling stars,
 * diffraction spikes, kinetic shooting stars/meteors, and glowing constellation threads.
 *
 * In Light Theme: Iridescent chromatic prism auras, high-visibility crisp sapphire/violet
 * celestial stars, sparkling diffraction flares, luminous prism meteors, and delicate
 * constellation network lines that pop cleanly without washing out on white surfaces.
 */
@Composable
fun CosmicStarfield(
    modifier: Modifier = Modifier,
    starCount: Int = 60,
    showMeteors: Boolean = true
) {
    val isDark = LocalAstronColors.current.isDark

    // Generate stable star cluster with deterministic seed
    val (stars, constellationLinks) = remember(starCount) {
        val random = Random(42) // Stable seed to eliminate flicker on recomposition
        val darkStarColors = listOf(
            Color.White.copy(alpha = 0.90f),
            CosmicCyan.copy(alpha = 0.85f),
            ElectricBlue.copy(alpha = 0.75f),
            StellarViolet.copy(alpha = 0.70f),
            Color(0xFFBAE6FD).copy(alpha = 0.85f), // Ice blue diamond
            Color(0xFFDDD6FE).copy(alpha = 0.80f)  // Amethyst mist
        )

        // Specially tuned high-contrast, vivid celestial sapphire & violet colors for Light Theme
        val lightStarColors = listOf(
            Color(0xFF1D4ED8).copy(alpha = 0.75f), // Deep Royal Cobalt
            Color(0xFF6D28D9).copy(alpha = 0.70f), // Celestial Violet
            Color(0xFF0284C7).copy(alpha = 0.80f), // Vivid Electric Cyan
            Color(0xFF4338CA).copy(alpha = 0.72f), // Midnight Indigo
            Color(0xFF0369A1).copy(alpha = 0.75f), // Aegean Azure
            Color(0xFF7C3AED).copy(alpha = 0.78f)  // Luminous Purple
        )

        val starList = List(starCount) { idx ->
            val isProminent = (idx % 7 == 0)
            Star(
                xPercent = random.nextFloat(),
                yPercent = random.nextFloat(),
                radius = if (isProminent) random.nextFloat() * 1.5f + 1.8f else random.nextFloat() * 1.2f + 0.9f,
                darkColor = darkStarColors[random.nextInt(darkStarColors.size)],
                lightColor = lightStarColors[random.nextInt(lightStarColors.size)],
                phaseOffset = random.nextFloat(),
                hasFlare = isProminent,
                driftSpeed = random.nextFloat() * 0.4f + 0.2f
            )
        }

        // Calculate proximity-based constellation links (mesh network)
        val links = mutableListOf<ConstellationLink>()
        for (i in 0 until starCount) {
            for (j in i + 1 until starCount) {
                val dx = starList[i].xPercent - starList[j].xPercent
                val dy = starList[i].yPercent - starList[j].yPercent
                val distSq = dx * dx + dy * dy
                if (distSq < 0.018f && links.size < 22) {
                    links.add(ConstellationLink(i, j))
                }
            }
        }

        Pair(starList, links)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cosmicStarfield")

    // Star twinkling harmonic oscillation
    val twinkleCycle by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleCycle"
    )

    // Cosmic nebula breathing pulsation
    val nebulaBreath by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebulaBreath"
    )

    // Primary Meteor streak progress (fires periodically)
    val meteorProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteorProgress1"
    )

    // Secondary subtle micro-meteor streak on alternating angle
    val meteorProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meteorProgress2"
    )

    // Ambient floating stardust drift
    val dustDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dustDrift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. DUAL THEME NEBULA & CHROMATIC PRISM AURAS
        if (isDark) {
            // Dark Mode: Deep Cosmic Nebulae
            val radius1 = maxOf(width, height) * 0.72f * nebulaBreath
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StellarViolet.copy(alpha = 0.16f),
                        ElectricBlue.copy(alpha = 0.08f),
                        CosmicCyan.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.25f, height * 0.18f),
                    radius = radius1
                ),
                radius = radius1,
                center = Offset(width * 0.25f, height * 0.18f)
            )

            val radius2 = maxOf(width, height) * 0.65f * (2f - nebulaBreath)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CosmicCyan.copy(alpha = 0.12f),
                        StellarViolet.copy(alpha = 0.06f),
                        ElectricBlue.copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.78f, height * 0.82f),
                    radius = radius2
                ),
                radius = radius2,
                center = Offset(width * 0.78f, height * 0.82f)
            )
        } else {
            // Light / White Mode: Iridescent Chromatic Prism Auras (clearly visible on white)
            val lightRadius1 = maxOf(width, height) * 0.68f * nebulaBreath
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.13f),
                        StellarViolet.copy(alpha = 0.09f),
                        CosmicCyan.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.20f, height * 0.15f),
                    radius = lightRadius1
                ),
                radius = lightRadius1,
                center = Offset(width * 0.20f, height * 0.15f)
            )

            val lightRadius2 = maxOf(width, height) * 0.60f * (2f - nebulaBreath)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StellarViolet.copy(alpha = 0.11f),
                        CosmicCyan.copy(alpha = 0.08f),
                        ElectricBlue.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.82f, height * 0.75f),
                    radius = lightRadius2
                ),
                radius = lightRadius2,
                center = Offset(width * 0.82f, height * 0.75f)
            )
        }

        // 2. CONSTELLATION NETWORK MESH THREADS
        val linkColor = if (isDark) {
            CosmicCyan.copy(alpha = 0.18f * twinkleCycle)
        } else {
            Color(0xFF2563EB).copy(alpha = 0.22f * twinkleCycle)
        }
        for (link in constellationLinks) {
            val starA = stars[link.starIndexA]
            val starB = stars[link.starIndexB]
            val posA = Offset(starA.xPercent * width, starA.yPercent * height)
            val posB = Offset(starB.xPercent * width, starB.yPercent * height)

            drawLine(
                color = linkColor,
                start = posA,
                end = posB,
                strokeWidth = if (isDark) 0.8f else 1.0f
            )
        }

        // 3. STARS, DIFFRACTION SPIKES & SPARKLE CORES
        for (star in stars) {
            // Subtle floating drift
            val radians = Math.toRadians((dustDrift * star.driftSpeed).toDouble())
            val driftOffsetX = sin(radians).toFloat() * 4.5f
            val driftOffsetY = (sin(radians * 0.8).toFloat() * 3.5f)

            val center = Offset(
                x = star.xPercent * width + driftOffsetX,
                y = star.yPercent * height + driftOffsetY
            )

            val dynamicAlpha = ((twinkleCycle + star.phaseOffset) % 1f).coerceIn(0.22f, 1f)
            val baseColor = if (isDark) star.darkColor else star.lightColor
            val finalColor = baseColor.copy(alpha = (baseColor.alpha * dynamicAlpha).coerceIn(0.15f, 1f))

            // Outer soft glow halo for larger stars
            if (star.radius > 1.6f) {
                drawCircle(
                    color = finalColor.copy(alpha = finalColor.alpha * if (isDark) 0.35f else 0.45f),
                    radius = star.radius * 2.2f,
                    center = center
                )
            }

            // Core Star Dot
            drawCircle(
                color = finalColor,
                radius = star.radius,
                center = center
            )

            // Dynamic 4-Point Diffraction Flare for prominent stars
            if (star.hasFlare && dynamicAlpha > 0.55f) {
                val flareLen = star.radius * (if (isDark) 3.8f else 3.4f)
                val flareColor = finalColor.copy(alpha = finalColor.alpha * (if (isDark) 0.55f else 0.65f))
                val strokeW = if (isDark) 1.0f else 1.2f

                // Horizontal Spike
                drawLine(
                    color = flareColor,
                    start = Offset(center.x - flareLen, center.y),
                    end = Offset(center.x + flareLen, center.y),
                    strokeWidth = strokeW
                )
                // Vertical Spike
                drawLine(
                    color = flareColor,
                    start = Offset(center.x, center.y - flareLen),
                    end = Offset(center.x, center.y + flareLen),
                    strokeWidth = strokeW
                )
            }
        }

        // 4. KINETIC SHOOTING STARS / METEORS (DARK & LIGHT PALETTES)
        if (showMeteors) {
            // Primary Meteor 1
            val windowStart1 = 0.10f
            val windowEnd1 = 0.26f
            if (meteorProgress1 in windowStart1..windowEnd1) {
                val localNorm = (meteorProgress1 - windowStart1) / (windowEnd1 - windowStart1)
                val startX = width * 0.90f - (localNorm * width * 0.70f)
                val startY = height * 0.06f + (localNorm * height * 0.42f)
                val trailLength = 160f
                val endX = startX + trailLength * 0.86f
                val endY = startY - trailLength * 0.50f

                val meteorAlpha = when {
                    localNorm < 0.15f -> localNorm / 0.15f
                    localNorm > 0.85f -> (1f - localNorm) / 0.15f
                    else -> 1f
                }.coerceIn(0f, 1f)

                val trailColors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.95f * meteorAlpha),
                        CosmicCyan.copy(alpha = 0.75f * meteorAlpha),
                        ElectricBlue.copy(alpha = 0.35f * meteorAlpha),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFF1D4ED8).copy(alpha = 0.90f * meteorAlpha),
                        Color(0xFF7C3AED).copy(alpha = 0.70f * meteorAlpha),
                        ElectricBlue.copy(alpha = 0.30f * meteorAlpha),
                        Color.Transparent
                    )
                }

                drawLine(
                    brush = Brush.linearGradient(
                        colors = trailColors,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isDark) 2.6f else 2.8f,
                    cap = StrokeCap.Round
                )

                // Meteor head glow
                val headColor = if (isDark) Color.White else Color(0xFF1E40AF)
                drawCircle(
                    color = headColor.copy(alpha = 0.95f * meteorAlpha),
                    radius = 2.8f,
                    center = Offset(startX, startY)
                )
                drawCircle(
                    color = CosmicCyan.copy(alpha = (if (isDark) 0.50f else 0.60f) * meteorAlpha),
                    radius = 7f,
                    center = Offset(startX, startY)
                )
            }

            // Secondary Micro-Meteor 2
            val windowStart2 = 0.60f
            val windowEnd2 = 0.74f
            if (meteorProgress2 in windowStart2..windowEnd2) {
                val localNorm = (meteorProgress2 - windowStart2) / (windowEnd2 - windowStart2)
                val startX = width * 0.40f - (localNorm * width * 0.50f)
                val startY = height * 0.50f + (localNorm * height * 0.32f)
                val trailLength = 110f
                val endX = startX + trailLength * 0.82f
                val endY = startY - trailLength * 0.48f

                val meteorAlpha = when {
                    localNorm < 0.2f -> localNorm / 0.2f
                    localNorm > 0.8f -> (1f - localNorm) / 0.2f
                    else -> 1f
                }.coerceIn(0f, 1f)

                val microTrailColors = if (isDark) {
                    listOf(
                        Color(0xFFBAE6FD).copy(alpha = 0.85f * meteorAlpha),
                        StellarViolet.copy(alpha = 0.55f * meteorAlpha),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color(0xFF6D28D9).copy(alpha = 0.80f * meteorAlpha),
                        Color(0xFF0284C7).copy(alpha = 0.50f * meteorAlpha),
                        Color.Transparent
                    )
                }

                drawLine(
                    brush = Brush.linearGradient(
                        colors = microTrailColors,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
