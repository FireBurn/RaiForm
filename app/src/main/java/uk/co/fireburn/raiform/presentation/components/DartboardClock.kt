package uk.co.fireburn.raiform.presentation.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun DartboardClock(
    takenHours: List<Int>, // 0-23
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error // Red for taken
    val availableColor = Color.Green.copy(alpha = 0.3f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square box
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // FIX: Explicitly convert IntSize to Float for Offset calculations
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()

                        val center = Offset(width / 2, height / 2)
                        val radius = min(width, height) / 2

                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)

                        // Ignore taps outside the clock or in the dead center
                        if (dist > radius || dist < radius * 0.1f) return@detectTapGestures

                        // Calculate Angle (-PI to PI)
                        // -PI/2 is 12 o'clock (Top). We need to adjust.
                        var angleRad = atan2(dy, dx) + (PI / 2)
                        if (angleRad < 0) angleRad += 2 * PI

                        // Convert angle to segment (0-11)
                        val segmentAngle = (2 * PI) / 12
                        val segmentIndex = (angleRad / segmentAngle).toInt() % 12

                        // Determine Ring (Inner = AM, Outer = PM)
                        val isOuterRing = dist > (radius * 0.6f)

                        // Map segment index to hour
                        val rawHour = if (segmentIndex == 0) 0 else segmentIndex

                        val finalHour = if (isOuterRing) {
                            // Outer Ring is PM
                            if (rawHour == 0) 12 else 12 + rawHour
                        } else {
                            // Inner Ring is AM
                            rawHour
                        }

                        // Check if blocked
                        if (!takenHours.contains(finalHour)) {
                            onHourSelected(finalHour)
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2
            val innerRadius = radius * 0.6f

            // --- DRAW SECTORS ---
            for (i in 0 until 12) {
                // Calculate start angle for this hour segment
                val startAngle = (i * 30f) - 90f - 15f

                // 1. Draw Outer Sector (PM)
                val pmHour = if (i == 0) 12 else i + 12
                val isPMTaken = takenHours.contains(pmHour)
                val isPMSelected = selectedHour == pmHour

                val pmColor = when {
                    isPMSelected -> primaryColor
                    isPMTaken -> errorColor.copy(alpha = 0.6f)
                    else -> availableColor
                }

                drawArc(
                    color = pmColor,
                    startAngle = startAngle,
                    sweepAngle = 30f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )

                // 2. Draw Inner Sector (AM)
                val amHour = i
                val isAMTaken = takenHours.contains(amHour)
                val isAMSelected = selectedHour == amHour

                val amColor = when {
                    isAMSelected -> primaryColor
                    isAMTaken -> errorColor.copy(alpha = 0.6f)
                    else -> availableColor.copy(alpha = 0.2f)
                }

                drawArc(
                    color = amColor,
                    startAngle = startAngle,
                    sweepAngle = 30f,
                    useCenter = true,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2)
                )
            }

            // --- DRAW GRID LINES ---
            drawCircle(
                color = surfaceColor,
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = surfaceColor,
                radius = innerRadius,
                style = Stroke(width = 4.dp.toPx())
            )

            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30 - 90 - 15).toDouble())
                val endX = center.x + radius * cos(angleRad).toFloat()
                val endY = center.y + radius * sin(angleRad).toFloat()

                drawLine(
                    color = surfaceColor,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // --- DRAW NUMBERS ---
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    textSize = 40.dp.toPx()
                    color = textColor.toArgb()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val innerPaint = Paint().apply {
                    textSize = 28.dp.toPx()
                    color = textColor.toArgb()
                    textAlign = Paint.Align.CENTER
                }

                for (i in 0 until 12) {
                    val angleRad = Math.toRadians((i * 30 - 90).toDouble())

                    // PM Numbers
                    val pmRadius = radius * 0.8f
                    val pmX = center.x + pmRadius * cos(angleRad).toFloat()
                    val pmY = center.y + pmRadius * sin(angleRad).toFloat() + (paint.textSize / 3)

                    val pmText = if (i == 0) "12" else (i + 12).toString()
                    canvas.nativeCanvas.drawText(pmText, pmX, pmY, paint)

                    // AM Numbers
                    val amRadius = innerRadius * 0.7f
                    val amX = center.x + amRadius * cos(angleRad).toFloat()
                    val amY =
                        center.y + amRadius * sin(angleRad).toFloat() + (innerPaint.textSize / 3)

                    val amText = i.toString()
                    canvas.nativeCanvas.drawText(amText, amX, amY, innerPaint)
                }
            }
        }
    }
}