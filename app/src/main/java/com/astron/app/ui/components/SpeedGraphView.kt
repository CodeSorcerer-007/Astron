package com.astron.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astron.app.R
import com.astron.app.core.util.AppFileManager

/**
 * Real-time dynamic speed benchmark sparkline graph with gradient fill, peak, and average speed.
 */
@Composable
fun SpeedGraphView(
    currentSpeedBps: Long,
    modifier: Modifier = Modifier,
    maxSamples: Int = 30
) {
    val speedSamples = remember { mutableStateListOf<Long>() }

    // Sample speed periodically
    LaunchedEffect(currentSpeedBps) {
        speedSamples.add(currentSpeedBps)
        if (speedSamples.size > maxSamples) {
            speedSamples.removeAt(0)
        }
    }

    val peakSpeedBps = speedSamples.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val avgSpeedBps = if (speedSamples.isNotEmpty()) speedSamples.average().toLong() else 0L

    val infiniteTransition = rememberInfiniteTransition(label = "beaconHeadPulse")
    val beaconPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "beaconPulseScale"
    )
    val beaconPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "beaconPulseAlpha"
    )

    val lineColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gradientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val transparentColor = Color.Transparent

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Metrics Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.telemetry_live_speed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${AppFileManager.formatFileSize(currentSpeedBps)}/s",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stringResource(R.string.telemetry_peak)}: ${AppFileManager.formatFileSize(peakSpeedBps)}/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stringResource(R.string.telemetry_avg)}: ${AppFileManager.formatFileSize(avgSpeedBps)}/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Line & Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    if (speedSamples.size < 2) return@Canvas

                    val width = size.width
                    val height = size.height
                    val stepX = width / (maxSamples - 1).coerceAtLeast(1)

                    val points = mutableListOf<Offset>()
                    val startOffset = (maxSamples - speedSamples.size).coerceAtLeast(0)

                    speedSamples.forEachIndexed { index, sample ->
                        val x = (startOffset + index) * stepX
                        // normalize y to 0..height (inverted)
                        val normalized = (sample.toFloat() / peakSpeedBps.toFloat()).coerceIn(0f, 1f)
                        val y = height - (normalized * (height - 8.dp.toPx())) - 4.dp.toPx()
                        points.add(Offset(x, y))
                    }

                    // 0. Draw subtle session average benchmark dashed line
                    if (avgSpeedBps > 0 && peakSpeedBps > 0) {
                        val avgNorm = (avgSpeedBps.toFloat() / peakSpeedBps.toFloat()).coerceIn(0f, 1f)
                        val avgY = height - (avgNorm * (height - 8.dp.toPx())) - 4.dp.toPx()
                        drawLine(
                            color = secondaryColor.copy(alpha = 0.35f),
                            start = Offset(0f, avgY),
                            end = Offset(width, avgY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }

                    // 1. Build smooth cubic Bézier curve path
                    val curvePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cpX = (p0.x + p1.x) / 2f
                            cubicTo(cpX, p0.y, cpX, p1.y, p1.x, p1.y)
                        }
                    }

                    // 2. Draw gradient fill below cubic Bézier curve
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(points.last().x, height)
                        lineTo(points.first().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.35f),
                                secondaryColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // 3. Draw ambient glow behind line
                    drawPath(
                        path = curvePath,
                        color = lineColor.copy(alpha = 0.3f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 4. Draw crisp main curve line
                    drawPath(
                        path = curvePath,
                        color = lineColor,
                        style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 5. Draw glowing outer halo, animated pulsing beacon ring, and bright core at head dot
                    val head = points.last()

                    // Vertical dashed guideline from head to bottom
                    drawLine(
                        color = lineColor.copy(alpha = 0.22f),
                        start = head,
                        end = Offset(head.x, height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )

                    // Animated expanding radar pulse ring
                    drawCircle(
                        color = lineColor.copy(alpha = beaconPulseAlpha),
                        radius = 8.dp.toPx() * beaconPulseScale,
                        center = head,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    drawCircle(
                        color = lineColor.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = head
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f.dp.toPx(),
                        center = head
                    )
                }
            }
        }
    }
}
