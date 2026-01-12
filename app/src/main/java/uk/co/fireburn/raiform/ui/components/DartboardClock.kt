package uk.co.fireburn.raiform.ui.components

import android.graphics.Paint
import android.graphics.Typeface
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

/**
 * A custom circular 24-hour clock picker ("The Dartboard").
 * Allows selecting an hour (0-23) by tapping on sectors.
 * Inner ring = AM (0-11), Outer ring = PM (12-23).
 */
@Composable
fun DartboardClock(
    takenHours: List<Int>, // 0-23
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val availableColor = Color.Green.copy(alpha = 0.3f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.surface
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

                        // Define Bullseye Radius (Dead zone for clicks)
                        val innerRadius = radius * 0.6f
                        val bullseyeRadius = innerRadius * 0.35f

                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)

                        // Ignore center dead zone (Bullseye) and outside touches
                        if (dist > radius || dist < bullseyeRadius) return@detectTapGestures

                        // 1. Calculate Angle in Degrees (0 at 12 o'clock, clockwise)
                        var angleDeg =
                            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (angleDeg < 0) angleDeg += 360f

                        // 2. Map to Segment Index (0-11)
                        // +15 degrees offset so the sector is centered on the number
                        val segmentIndex = ((angleDeg + 15f) % 360f / 30f).toInt()

                        // 3. Determine Ring (Inner=AM, Outer=PM)
                        val isOuterRing = dist > (radius * 0.6f)

                        // 4. Calculate Final Hour (0-23)
                        val rawHour = segmentIndex % 12

                        val finalHour = if (isOuterRing) {
                            // Outer Ring (PM): 12, 1, 2... 11 -> 12, 13, 14... 23
                            if (rawHour == 0) 12 else rawHour + 12
                        } else {
                            // Inner Ring (AM): 0, 1, 2... 11
                            rawHour
                        }

                        // Allow selection even if taken (to enable conflict resolution/overwriting)
                        onHourSelected(finalHour)
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2
            val innerRadius = radius * 0.6f
            val bullseyeRadius = innerRadius * 0.35f

            // --- DRAW SECTORS ---
            for (i in 0 until 12) {
                // Shift start angle so the sector centers on the clock position
                val startAngle = -90f - 15f + (i * 30f)

                // 1. Outer Sector (PM)
                val pmHour = if (i == 0) 12 else i + 12
                val isPMTaken = takenHours.contains(pmHour)
                val isPMSelected = selectedHour == pmHour

                // If selected, it takes priority color (Primary).
                // If taken but not selected, it shows Error (Red).
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

                // 2. Inner Sector (AM)
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
            // Outer Border
            drawCircle(color = surfaceColor, radius = radius, style = Stroke(width = 4.dp.toPx()))
            // Middle Divider
            drawCircle(
                color = surfaceColor,
                radius = innerRadius,
                style = Stroke(width = 4.dp.toPx())
            )

            // Radial Lines
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

            // --- DRAW BULLSEYE (AM Indicator) ---
            drawCircle(
                color = backgroundColor,
                radius = bullseyeRadius
            )
            drawCircle(
                color = surfaceColor,
                radius = bullseyeRadius,
                style = Stroke(width = 4.dp.toPx())
            )

            // --- DRAW TEXT ---
            drawIntoCanvas { canvas ->
                // Paint for PM Numbers (Outer)
                val pmPaint = Paint().apply {
                    textSize = 34.dp.toPx()
                    color = textColor.toArgb()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }

                // Paint for AM Numbers (Inner)
                val amPaint = Paint().apply {
                    textSize = 24.dp.toPx()
                    color = textColor.toArgb()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = false
                }

                // Paint for AM/PM Labels
                val labelPaint = Paint().apply {
                    textSize = 18.dp.toPx()
                    color = primaryColor.toArgb()
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }

                // Draw "AM" in Bullseye
                val amY = center.y - (labelPaint.descent() + labelPaint.ascent()) / 2
                canvas.nativeCanvas.drawText("AM", center.x, amY, labelPaint)

                for (i in 0 until 12) {
                    val angleRad = Math.toRadians((-90 + i * 30).toDouble())
                    val displayNum = if (i == 0) 12 else i

                    // --- Outer Ring (PM) ---
                    val pmRadius = radius * 0.75f
                    val pmX = center.x + pmRadius * cos(angleRad).toFloat()
                    val pmY =
                        center.y + pmRadius * sin(angleRad).toFloat() - (pmPaint.ascent() + pmPaint.descent()) / 2

                    canvas.nativeCanvas.drawText(displayNum.toString(), pmX, pmY, pmPaint)

                    // Special Label: "PM" above the 12
                    if (i == 0) {
                        val pmLabelRadius = radius * 0.92f
                        val pmLabelX = center.x + pmLabelRadius * cos(angleRad).toFloat()
                        val pmLabelY =
                            center.y + pmLabelRadius * sin(angleRad).toFloat() - (labelPaint.ascent() + labelPaint.descent()) / 2

                        canvas.nativeCanvas.drawText("PM", pmLabelX, pmLabelY, labelPaint)
                    }

                    // --- Inner Ring (AM Numbers) ---
                    val amRadius = innerRadius * 0.7f
                    val amX = center.x + amRadius * cos(angleRad).toFloat()
                    val amY =
                        center.y + amRadius * sin(angleRad).toFloat() - (amPaint.ascent() + amPaint.descent()) / 2

                    canvas.nativeCanvas.drawText(displayNum.toString(), amX, amY, amPaint)
                }
            }
        }
    }
}
