package io.music_assistant.client.tv.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One analyzed moment of the music, every value in 0..1. */
class AudioFrame(
    /** Loudness per frequency band, low to high, on a log scale like the ear. */
    val bands: FloatArray,
    /** The waveform, for the oscilloscope. */
    val wave: FloatArray,
    val level: Float,
    val bass: Float,
)

/**
 * Turns the samples heard now into an [AudioFrame]: a Hann-windowed FFT folded into
 * [bandCount] logarithmic bands, with a fast rise and a slow fall so the motion reads as music
 * rather than noise. Kept between frames; call [analyze] once per drawn frame.
 */
class AudioAnalyzer(private val bandCount: Int = DEFAULT_BANDS) {
    private val samples = FloatArray(FFT_SIZE)
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2 * PI * it / (FFT_SIZE - 1)).toFloat() }
    private val bands = FloatArray(bandCount)
    private var level = 0f
    private var bass = 0f

    fun analyze(tap: VisualizerTap): AudioFrame {
        val fresh = tap.snapshot(samples)
        if (!fresh) samples.fill(0f)
        for (i in 0 until FFT_SIZE) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val rate = tap.sampleRate.toFloat()
        val binHz = rate / FFT_SIZE
        val minLog = ln(MIN_HZ)
        val maxLog = ln(MAX_HZ.coerceAtMost(rate / 2.0))
        for (b in 0 until bandCount) {
            val lo = kotlin.math.exp(minLog + (maxLog - minLog) * b / bandCount)
            val hi = kotlin.math.exp(minLog + (maxLog - minLog) * (b + 1) / bandCount)
            val from = (lo / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val to = (hi / binHz).toInt().coerceIn(from + 1, FFT_SIZE / 2)
            var peak = 0f
            for (k in from until to) peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
            val db = 20 * log10(peak / (FFT_SIZE / 4) + TINY)
            bands[b] = smooth(bands[b], ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).coerceIn(0f, 1f))
        }
        var sum = 0f
        for (s in samples) sum += s * s
        val rms = sqrt(sum / FFT_SIZE)
        level = smooth(level, (rms * LEVEL_GAIN).coerceIn(0f, 1f))
        val bassBands = (bandCount * BASS_SHARE).toInt().coerceAtLeast(1)
        bass = smooth(bass, (0 until bassBands).map { bands[it] }.average().toFloat().pow(BASS_CURVE))
        val wave = FloatArray(WAVE_POINTS) { samples[FFT_SIZE - WAVE_POINTS + it] }
        return AudioFrame(bands.copyOf(), wave, level, bass)
    }

    private fun smooth(previous: Float, target: Float) =
        if (target > previous) previous + (target - previous) * RISE else previous * FALL

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe
                    im[i + k + len / 2] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val FFT_SIZE = 2048
        const val DEFAULT_BANDS = 48
        const val WAVE_POINTS = 512
        const val MIN_HZ = 40.0
        const val MAX_HZ = 16_000.0
        const val FLOOR_DB = -60f
        const val CEIL_DB = -6f
        const val TINY = 1e-9f
        const val RISE = 0.6f
        const val FALL = 0.88f
        const val LEVEL_GAIN = 3f
        const val BASS_SHARE = 0.15f
        const val BASS_CURVE = 1.5f
    }
}
