package com.example.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBusiness
import com.example.ui.theme.ColorCharity
import com.example.ui.theme.ColorMedical
import com.example.ui.theme.ColorOther
import com.example.ui.theme.ColorPersonal

data class DonutSlice(
    val value: Double,
    val color: Color,
    val label: String
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    totalLabel: String = "TOTAL",
    totalMiles: Double,
    unit: String = "mi",
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    strokeWidth: Dp = 14.dp
) {
    val total = slices.sumOf { it.value }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 600))
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("donut_chart_container"),
        contentAlignment = Alignment.Center
    ) {
        val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = this.size.width - strokePx
            val arcOffset = strokePx / 2f

            if (total <= 0.0) {
                drawCircle(
                    color = emptyColor,
                    style = Stroke(width = strokePx)
                )
            } else {
                var startAngle = -90f
                for (slice in slices) {
                    if (slice.value <= 0.0) continue
                    val sweepAngle = ((slice.value / total) * 360f * progress.value).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(arcOffset, arcOffset),
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        // Center content inside donut hole
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%.1f".format(totalMiles),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
