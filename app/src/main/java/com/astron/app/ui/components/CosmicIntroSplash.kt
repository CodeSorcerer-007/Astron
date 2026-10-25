package com.astron.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.R
import com.astron.app.core.util.LocalHapticEngine
import com.astron.app.ui.theme.CanvasLight
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.astron.app.ui.theme.EmeraldVerified
import com.astron.app.ui.theme.LocalAstronColors
import com.astron.app.ui.theme.StellarViolet
import com.astron.app.ui.theme.TextPrimary
import com.astron.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * CosmicIntroSplash - A celestial holographic intro transition displayed when Astron launches.
 * Features an expanding starlight aura, rotating constellation rings, breathing logo pulse,
 * and a smooth fluid scale-fade handoff into the main application.
 */
@Composable
fun CosmicIntroSplash(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAstronColors.current.isDark
    val hapticEngine = LocalHapticEngine.current
    var isExiting by remember { mutableStateOf(false) }

    // Initial entrance animatables
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    // Infinite ambient animations
    val infiniteTransition = rememberInfiniteTransition(label = "splashOrbital")
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )

    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverseRotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )

    // Exit transition values
    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        finishedListener = {
            if (isExiting) {
                onSplashFinished()
            }
        },
        label = "exitAlpha"
    )

    val exitScale by animateFloatAsState(
        targetValue = if (isExiting) 1.08f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "exitScale"
    )

    fun triggerExit() {
        if (!isExiting) {
            hapticEngine?.click()
            isExiting = true
        }
    }

    LaunchedEffect(Unit) {
        hapticEngine?.heavyClick()
        // Phase 1: Logo springs in with celestial flare
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing)
        )
        // Phase 2: Brand and subtitle fade in
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = LinearEasing)
        )
        // Auto dismiss after 1.35 seconds for ultra-fluid startup
        delay(1350)
        triggerExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasLight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { triggerExit() }
            .alpha(exitAlpha)
            .scale(exitScale),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic live cosmic starfield tailored to current theme
        CosmicStarfield(
            starCount = if (isDark) 70 else 55
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Celestial Emblem Stack
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .scale(logoScale.value * breathingScale)
                    .alpha(logoAlpha.value)
            ) {
                // Expanding Starlight Aura
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CosmicCyan.copy(alpha = if (isDark) 0.35f * auraPulse else 0.20f * auraPulse),
                                    StellarViolet.copy(alpha = if (isDark) 0.20f * auraPulse else 0.12f * auraPulse),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Outer Rotating Sweep Constellation Ring
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .rotate(orbitRotation)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    CosmicCyan,
                                    Color.Transparent,
                                    StellarViolet,
                                    Color.Transparent,
                                    ElectricBlue
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Inner Counter-Rotating Prismatic Ring
                Box(
                    modifier = Modifier
                        .size(122.dp)
                        .rotate(reverseRotation)
                        .border(
                            width = 1.2.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    ElectricBlue.copy(alpha = 0.8f),
                                    Color.Transparent,
                                    CosmicCyan.copy(alpha = 0.7f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Central High-Res Astron Brand Mark
                Image(
                    painter = painterResource(id = R.drawable.astron_logo),
                    contentDescription = "Astron Logo",
                    modifier = Modifier.size(92.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(contentAlpha.value)
            ) {
                Text(
                    text = "A S T R O N",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subtitle Badge with Live Mesh Indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isDark) Color(0xFF141F36) else Color(0xFFEFF6FF))
                        .border(
                            1.dp,
                            if (isDark) CosmicCyan.copy(alpha = 0.4f) else CosmicCyan.copy(alpha = 0.25f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(EmeraldVerified, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HYPER-SPEED DECENTRALIZED MESH",
                        color = CosmicCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "Tap anywhere to begin",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
