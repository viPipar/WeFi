package com.wefi.analyzer.ui.screens.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.wefi.analyzer.domain.model.WifiAccessPoint
import com.wefi.analyzer.domain.util.ChannelFrequencyUtils
import com.wefi.analyzer.ui.theme.BlynkBlue
import com.wefi.analyzer.ui.theme.SpectrumCurveColors

@Composable
fun ChannelGraphCanvas(
    apList: List<WifiAccessPoint>,
    selectedBandGhz: Double,
    connectedBssid: String?,
    modifier: Modifier = Modifier
) {
    val channels = ChannelFrequencyUtils.getChannelsForBand(selectedBandGhz)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val paddingLeft = 52.dp.toPx()
        val paddingRight = 24.dp.toPx()
        val paddingTop = 40.dp.toPx()
        val paddingBottom = 48.dp.toPx()

        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        if (graphWidth <= 0f || graphHeight <= 0f || channels.isEmpty()) {
            return@Canvas
        }

        // 1. Draw dBm Grid Lines (-20 to -100 dBm)
        val dbmSteps = listOf(-20, -30, -40, -50, -60, -70, -80, -90, -100)
        val textPaint = Paint().apply {
            color = textSecondaryColor
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        dbmSteps.forEach { dbm ->
            val y = paddingTop + ((-20 - dbm).toFloat() / 80f) * graphHeight
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$dbm",
                paddingLeft - 8.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // 2. Draw Channel X Axis Grid & Labels
        val channelCount = channels.size
        val channelStepX = graphWidth / (channelCount + 1).toFloat()

        val channelXMap = mutableMapOf<Int, Float>()
        channels.forEachIndexed { index, ch ->
            val x = paddingLeft + (index + 1) * channelStepX
            channelXMap[ch] = x

            // Vertical subtle grid
            drawLine(
                color = gridColor.copy(alpha = 0.15f),
                start = Offset(x, paddingTop),
                end = Offset(x, height - paddingBottom),
                strokeWidth = 1.dp.toPx()
            )

            // Label Channel number
            drawContext.canvas.nativeCanvas.drawText(
                "$ch",
                x,
                height - paddingBottom + 18.dp.toPx(),
                Paint().apply {
                    color = textSecondaryColor
                    textSize = 11.dp.toPx()
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }

        // 3. Draw Parabola Curves for each Access Point
        val filteredAps = apList.filter {
            if (selectedBandGhz == 2.4) it.is24GHz else if (selectedBandGhz == 5.0) it.is5GHz else it.is6GHz
        }

        filteredAps.forEachIndexed { index, ap ->
            val color = SpectrumCurveColors[index % SpectrumCurveColors.size]
            val isConnected = ap.bssid.equals(connectedBssid, ignoreCase = true) || ap.isConnected

            val centerX = channelXMap[ap.channel] ?: run {
                // Approximate position if channel outside basic set
                val firstCh = channels.firstOrNull() ?: 1
                val lastCh = channels.lastOrNull() ?: 13
                val fraction = (ap.channel - firstCh).toFloat() / (lastCh - firstCh).coerceAtLeast(1)
                paddingLeft + fraction * graphWidth
            }

            // Curve width in pixels based on channelWidthMhz
            val spanChannels = if (selectedBandGhz == 2.4) {
                if (ap.channelWidthMhz >= 40) 8f else 4f
            } else {
                if (ap.channelWidthMhz >= 80) 8f else 4f
            }
            val halfWidthPx = (spanChannels / 2f) * channelStepX

            // RSSI to Y: -20 dBm is top (paddingTop), -100 dBm is bottom (height - paddingBottom)
            val clampedRssi = ap.rssi.coerceIn(-100, -20)
            val peakY = paddingTop + ((-20 - clampedRssi).toFloat() / 80f) * graphHeight
            val baseY = height - paddingBottom

            val leftX = (centerX - halfWidthPx).coerceAtLeast(paddingLeft)
            val rightX = (centerX + halfWidthPx).coerceAtMost(width - paddingRight)

            // Construct smooth quadratic Bezier parabola
            val path = Path().apply {
                moveTo(leftX, baseY)
                quadraticBezierTo(centerX, peakY, rightX, baseY)
                close()
            }

            // Draw filled semi-transparent parabola
            drawPath(
                path = path,
                color = color.copy(alpha = if (isConnected) 0.35f else 0.18f),
                style = Fill
            )

            // Draw stroke outline
            drawPath(
                path = path,
                color = if (isConnected) BlynkBlue else color,
                style = Stroke(width = if (isConnected) 3.dp.toPx() else 2.dp.toPx())
            )

            // 4. Draw Connected AP Center Plumb-Line (Garis Vertikal Penanda)
            if (isConnected) {
                drawConnectedPlumbLine(
                    centerX = centerX,
                    peakY = peakY,
                    topY = paddingTop,
                    bottomY = baseY,
                    color = BlynkBlue,
                    ap = ap,
                    textPaint = textPaint
                )
            }

            // 5. Draw Label on Peak (SSID ~Xm)
            val labelText = "${ap.displaySsid} (${ap.formattedDistance})"
            val labelY = (peakY - 6.dp.toPx()).coerceAtLeast(paddingTop + 14.dp.toPx())

            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                centerX,
                labelY,
                Paint().apply {
                    this.color = if (isConnected) BlynkBlue.toArgb() else textPrimaryColor
                    textSize = 12.dp.toPx()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = isConnected
                    isAntiAlias = true
                }
            )
        }
    }
}

private fun DrawScope.drawConnectedPlumbLine(
    centerX: Float,
    peakY: Float,
    topY: Float,
    bottomY: Float,
    color: Color,
    ap: WifiAccessPoint,
    textPaint: Paint
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    // Vertical dashed plumb-line from top of canvas through the peak down to X axis
    drawLine(
        color = color,
        start = Offset(centerX, topY),
        end = Offset(centerX, bottomY),
        strokeWidth = 2.dp.toPx(),
        pathEffect = dashEffect
    )

    // Floating Badge Pill at top of the plumb line
    drawContext.canvas.nativeCanvas.drawText(
        "● TERHUBUNG (JARINGAN SAYA)",
        centerX,
        topY - 10.dp.toPx(),
        Paint().apply {
            this.color = color.toArgb()
            textSize = 10.dp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    )
}
