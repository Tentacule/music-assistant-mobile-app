package io.music_assistant.client.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** The track as vertical bars: the played part in white, the rest dimmed. Display only. */
@Composable
fun WaveformTimeline(bins: List<Float>, progress: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pitch = with(density) { BAR_PITCH.toPx() }
    val barWidth = with(density) { BAR_WIDTH.toPx() }
    val minHeight = with(density) { BAR_MIN_HEIGHT.toPx() }
    var barCount by remember { mutableIntStateOf(0) }
    val peaks = remember(bins, barCount) { waveformPeaks(bins, barCount) }
    Canvas(modifier.onSizeChanged { barCount = ceil(it.width / pitch).toInt() }) {
        val playedBars = progress * peaks.size
        peaks.forEachIndexed { index, peak ->
            val barHeight = max(minHeight, peak * size.height)
            val x = index * pitch + barWidth / 2
            drawLine(
                color = if (index < playedBars) Color.White else Color.White.copy(alpha = UNPLAYED_ALPHA),
                start = Offset(x, (size.height - barHeight) / 2),
                end = Offset(x, (size.height + barHeight) / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One peak for each bar. The highest bin of a bar and not their average, which would flatten the transients. */
internal fun waveformPeaks(bins: List<Float>, barCount: Int): List<Float> {
    if (bins.isEmpty() || barCount <= 0) return emptyList()
    return List(barCount) { bar ->
        val start = floor(bar.toDouble() * bins.size / barCount).toInt()
        val end = max(start + 1, floor((bar + 1).toDouble() * bins.size / barCount).toInt())
        (start until end).maxOf { bins[it] }
    }
}

private val BAR_PITCH = 3.dp
private val BAR_WIDTH = 2.dp
// Keeps a silent part visible as a thin line.
private val BAR_MIN_HEIGHT = 2.dp
private const val UNPLAYED_ALPHA = 0.3f
