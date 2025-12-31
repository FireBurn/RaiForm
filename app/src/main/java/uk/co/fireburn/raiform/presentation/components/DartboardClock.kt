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
            .aspectRatio(1f)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val center = Offset(width / 2, height / 2)
                        val radius = min(width, height) / 2

                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)

                        // Ignore center dead zone and outside touches
                        if (dist > radius || dist < radius * 0.1f) return@detectTapGestures

                        // 1. Calculate Angle in Degrees (0 at 12 o'clock, clockwise)
                        // standard atan2 returns angle from X axis (3 o'clock).
                        // We rotate it 90 degrees to make 12 o'clock 0.
                        var angleDeg =
                            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (angleDeg < 0) angleDeg += 360f // Normalize to 0-360

                        // 2. Map to Segment Index (0-11)
                        // Each segment is 30 degrees.
                        // Segment 0 (12 o'clock) is centered at 0 deg, spanning 345 to 15.
                        // We shift by +15 so the division floors correctly.
                        // (350 + 15) % 360 = 5 / 30 = 0
                        // (10 + 15) % 360 = 25 / 30 = 0
                        // (20 + 15) % 360 = 35 / 30 = 1 (1 o'clock)
                        val segmentIndex = ((angleDeg + 15f) % 360f / 30f).toInt()

                        // 3. Determine Ring (Inner=AM, Outer=PM)
                        val isOuterRing = dist > (radius * 0.6f)

                        // 4. Calculate Final Hour (0-23)
                        val rawHour = segmentIndex % 12 // Ensure 0-11 range

                        val finalHour = if (isOuterRing) {
                            // Outer Ring (PM): 12, 1, 2... 11 -> 12, 13, 14... 23
                            if (rawHour == 0) 12 else rawHour + 12
                        } else {
                            // Inner Ring (AM): 0, 1, 2... 11
                            rawHour
                        }

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
                // Drawing starts from 3 o'clock (0 deg) in standard Compose Canvas.
                // 12 o'clock is -90 deg.
                // Center the wedge: start at -90 - 15 + (i*30)
                val startAngle = -90f - 15f + (i * 30f)

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
                val angleRad = Math.toRadians((-90 - 15 + i * 30).toDouble())
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
                    // Text is centered at the "middle" of the wedge
                    val angleRad = Math.toRadians((-90 + i * 30).toDouble())

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