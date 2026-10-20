package com.astron.app.ui.components

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val lineColor = MaterialTheme.colorScheme.primary
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
                        text = "LIVE SPEED",
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
                        text = "PEAK: ${AppFileManager.formatFileSize(peakSpeedBps)}/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "AVG: ${AppFileManager.formatFileSize(avgSpeedBps)}/s",
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

                    // 1. Draw gradient fill below path
                    val fillPath = Path().apply {
                        moveTo(points.first().x, height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientColor, transparentColor),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // 2. Draw stroke line
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }

                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 3. Draw pulsing head dot
                    val head = points.last()
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = head
                    )
                }
            }
        }
    }
}
