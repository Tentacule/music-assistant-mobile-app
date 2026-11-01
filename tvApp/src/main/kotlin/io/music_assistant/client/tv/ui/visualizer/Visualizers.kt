package io.music_assistant.client.tv.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import io.music_assistant.client.tv.audio.AudioAnalyzer
import io.music_assistant.client.tv.audio.AudioFrame
import io.music_assistant.client.tv.audio.VisualizerTap
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The Now playing visualizations, in the order the remote cycles through them. */
enum class VisualizerStyle {
    NONE,

    /** The artwork beats with the bass. */
    PULSE,

    /** Spectrum bars along the bottom of the screen. */
    BARS,

    /** An oscilloscope line across the screen. */
    WAVE,

    /** A light halo behind the artwork, breathing with the music. */
    HALO,

    /** Spectrum rays all around the artwork. */
    RADIAL,
    ;

    fun next(): VisualizerStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * The music as heard now, analyzed once per drawn frame while [enabled]; null otherwise. Turns the
 * tap's copy on only for that time.
 */
@Composable
fun rememberAudioFrames(tap: VisualizerTap, enabled: Boolean): State<AudioFrame?> {
    val frame = remember { mutableStateOf<AudioFrame?>(null) }
    LaunchedEffect(enabled) {
        tap.active = enabled
        if (!enabled) {
            frame.value = null
            return@LaunchedEffect
        }
        val analyzer = AudioAnalyzer()
        while (isActive) {
            withFrameNanos { }
            frame.value = analyzer.analyze(tap)
        }
    }
    DisposableEffect(tap) { onDispose { tap.active = false } }
    return frame
}

/** The full-screen layer of the visualizations that are not tied to the artwork (bars, wave). */
@Composable
fun VisualizerBackdrop(style: VisualizerStyle, frame: AudioFrame?, accent: Color, modifier: Modifier = Modifier) {
    if (frame == null || style !in setOf(VisualizerStyle.BARS, VisualizerStyle.WAVE)) return
    Canvas(modifier) {
        when (style) {
            VisualizerStyle.BARS -> drawBars(frame, accent)
            VisualizerStyle.WAVE -> drawWave(frame, accent)
            else -> Unit
        }
    }
}

/**
 * The visualizations tied to the artwork, as a modifier on the artwork box: it scales with the
 * bass ([VisualizerStyle.PULSE]), or gets a halo or rays drawn around it, outside its bounds.
 */
fun Modifier.artworkVisualizer(style: VisualizerStyle, frame: AudioFrame?, accent: Color): Modifier {
    if (frame == null) return this
    return this.then(
        when (style) {
            VisualizerStyle.PULSE -> Modifier.graphicsLayer {
                val scale = 1f + frame.bass * PULSE_DEPTH
                scaleX = scale
                scaleY = scale
            }

            VisualizerStyle.HALO -> Modifier.drawBehind { drawHalo(frame, accent) }

            VisualizerStyle.RADIAL -> Modifier.drawBehind { drawRays(frame, accent) }

            else -> Modifier
        },
    )
}

private fun DrawScope.drawBars(frame: AudioFrame, accent: Color) {
    val count = frame.bands.size
    val gap = size.width * BAR_GAP_SHARE / count
    val barWidth = (size.width - gap * (count + 1)) / count
    val maxHeight = size.height * BARS_HEIGHT_SHARE
    val brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.9f), accent.copy(alpha = 0.85f), accent.copy(alpha = 0.2f)),
        startY = size.height - maxHeight,
        endY = size.height,
    )
    frame.bands.forEachIndexed { i, value ->
        val h = maxHeight * value
        drawRect(
            brush = brush,
            topLeft = Offset(gap + i * (barWidth + gap), size.height - h),
            size = Size(barWidth, h),
            alpha = BAR_ALPHA,
        )
    }
}

private fun DrawScope.drawWave(frame: AudioFrame, accent: Color) {
    val path = Path()
    val mid = size.height * WAVE_CENTER_SHARE
    val amplitude = size.height * WAVE_AMPLITUDE_SHARE
    frame.wave.forEachIndexed { i, v ->
        val x = size.width * i / (frame.wave.size - 1)
        val y = mid - v.coerceIn(-1f, 1f) * amplitude
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // A wide soft stroke under a thin bright one reads as a glowing line.
    drawPath(path, accent.copy(alpha = 0.35f), style = Stroke(width = GLOW_STROKE_DP * density, cap = StrokeCap.Round))
    drawPath(
        path,
        Color.White.copy(alpha = 0.9f),
        style = Stroke(width = LINE_STROKE_DP * density, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawHalo(frame: AudioFrame, accent: Color) {
    val radius = size.minDimension * (HALO_BASE + frame.level * HALO_GROWTH)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.55f + frame.bass * 0.4f), accent.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawRays(frame: AudioFrame, accent: Color) {
    val inner = min(size.width, size.height) / 2 + RAY_GAP_DP * density
    val maxLength = size.minDimension * RAY_LENGTH_SHARE
    // Mirrored, so the low frequencies sit at the top and the ring is symmetric.
    val bands = frame.bands + frame.bands.reversedArray()
    bands.forEachIndexed { i, value ->
        val angle = 2 * PI * i / bands.size - PI / 2
        val length = maxLength * (RAY_MIN + value)
        val start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner)
        val end = Offset(
            center.x + cos(angle).toFloat() * (inner + length),
            center.y + sin(angle).toFloat() * (inner + length),
        )
        drawLine(
            color = if (value > HOT_BAND) Color.White else accent,
            start = start,
            end = end,
            strokeWidth = RAY_STROKE_DP * density,
            cap = StrokeCap.Round,
        )
    }
}

private const val PULSE_DEPTH = 0.08f
private const val BAR_GAP_SHARE = 0.25f
private const val BARS_HEIGHT_SHARE = 0.45f
private const val BAR_ALPHA = 0.85f
private const val WAVE_CENTER_SHARE = 0.82f
private const val WAVE_AMPLITUDE_SHARE = 0.14f
private const val GLOW_STROKE_DP = 10f
private const val LINE_STROKE_DP = 2.5f
private const val HALO_BASE = 0.7f
private const val HALO_GROWTH = 0.35f
private const val RAY_GAP_DP = 10f
private const val RAY_LENGTH_SHARE = 0.18f
private const val RAY_MIN = 0.05f
private const val RAY_STROKE_DP = 4f
private const val HOT_BAND = 0.8f
