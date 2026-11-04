package com.softwareidiot.soundsentinel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent
import kotlin.math.max

@Composable
fun LiveScrollingWaveformView(
    waveformHistory: List<Int>,
    effectiveThreshold: Int,
    isRecording: Boolean,
    isArmed: Boolean,
    modifier: Modifier = Modifier
) {
    // Pre-allocate brushes and path effects outside Canvas onDraw for zero-allocation 120Hz rendering
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }
    val activeTriggeredBrush = remember {
        Brush.verticalGradient(colors = listOf(CoralPrimary, GoldAccent, CoralPrimary))
    }
    val recordingAmbientBrush = remember {
        Brush.verticalGradient(colors = listOf(GoldAccent.copy(alpha = 0.8f), VioletAccent.copy(alpha = 0.85f), GoldAccent.copy(alpha = 0.8f)))
    }
    val monitoringTriggeredBrush = remember {
        Brush.verticalGradient(colors = listOf(GoldAccent, CyanAccent, GoldAccent))
    }
    val monitoringQuietBrush = remember {
        Brush.verticalGradient(colors = listOf(CyanAccent.copy(alpha = 0.8f), VioletAccent.copy(alpha = 0.75f), CyanAccent.copy(alpha = 0.8f)))
    }
    val standbyBrush = remember {
        Brush.verticalGradient(colors = listOf(TextMuted.copy(alpha = 0.28f), TextMuted.copy(alpha = 0.28f)))
    }

    Box(modifier = modifier.testTag("live_scrolling_waveform")) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // Center baseline axis
            drawLine(
                color = TextMuted.copy(alpha = 0.22f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Symmetrical Threshold Guide Lines (Top & Bottom)
            val threshRatio = (effectiveThreshold.coerceIn(5, 95) / 100f)
            val topThreshY = midY - (midY * threshRatio * 0.9f)
            val botThreshY = midY + (midY * threshRatio * 0.9f)
            val threshColor = if (isRecording) CoralPrimary.copy(alpha = 0.85f) else GoldAccent.copy(alpha = 0.75f)

            drawLine(
                color = threshColor,
                start = Offset(0f, topThreshY),
                end = Offset(width, topThreshY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = threshColor,
                start = Offset(0f, botThreshY),
                end = Offset(width, botThreshY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )

            val count = max(1, waveformHistory.size)
            val slotWidth = width / count.toFloat()
            val barWidth = (slotWidth * 0.66f).coerceAtLeast(2.5f)
            val minHalfHeightPx = 3.dp.toPx()
            val cornerRadiusPx = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            for (i in waveformHistory.indices) {
                val level = if (isArmed) waveformHistory[i].coerceIn(4, 100) else 4
                val ratio = level / 100f
                val barHalfHeight = max(minHalfHeightPx, (midY * 0.92f) * ratio)
                val x = i * slotWidth + (slotWidth - barWidth) / 2f

                val barBrush = when {
                    !isArmed -> standbyBrush
                    isRecording && level >= effectiveThreshold -> activeTriggeredBrush
                    isRecording -> recordingAmbientBrush
                    level >= effectiveThreshold -> monitoringTriggeredBrush
                    else -> monitoringQuietBrush
                }

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(x, midY - barHalfHeight),
                    size = Size(barWidth, barHalfHeight * 2f),
                    cornerRadius = cornerRadiusPx
                )
            }
        }
    }
}
