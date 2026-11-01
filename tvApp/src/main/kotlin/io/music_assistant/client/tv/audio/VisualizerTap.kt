package io.music_assistant.client.tv.audio

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle

/**
 * A copy of the audio the TV plays, for the Now playing visualizations.
 *
 * [TapAudioSink] hands it every PCM chunk on its way to the device; while [active] (a visualization
 * is on screen) it keeps the last [RING_FRAMES] frames as mono floats. [snapshot] returns the frames
 * being HEARD now, not the ones just written: the sink's play position says how far behind the
 * written stream the speakers are.
 */
class VisualizerTap(private val clock: MonotonicClock) {
    /** Off unless a visualization shows: the copy costs nothing otherwise. */
    @Volatile
    var active: Boolean = false

    private val lock = Any()
    private val ring = FloatArray(RING_FRAMES)
    private var writtenFrames = 0L
    private var format: SinkFormat? = null
    private var handle: SinkHandle? = null

    val sampleRate: Int get() = format?.sampleRate ?: DEFAULT_RATE

    fun onOpen(format: SinkFormat, handle: SinkHandle) = synchronized(lock) {
        this.format = format
        this.handle = handle
        writtenFrames = 0L
        ring.fill(0f)
    }

    fun onClose(handle: SinkHandle) = synchronized(lock) {
        if (this.handle === handle) {
            this.handle = null
            ring.fill(0f)
        }
    }

    fun onPcm(pcm: ByteArray, offset: Int, length: Int) {
        val fmt = format ?: return
        if (!fmt.isPcm) return
        val frameBytes = fmt.bytesPerFrame.takeIf { it > 0 } ?: return
        val frames = length / frameBytes
        synchronized(lock) {
            if (active) {
                val sampleBytes = fmt.bitDepth / BITS_PER_BYTE
                for (f in 0 until frames) {
                    var sum = 0f
                    val base = offset + f * frameBytes
                    for (c in 0 until fmt.channels) sum += sample(pcm, base + c * sampleBytes, fmt.bitDepth)
                    ring[((writtenFrames + f) % RING_FRAMES).toInt()] = sum / fmt.channels
                }
            }
            writtenFrames += frames
        }
    }

    /**
     * Fills [out] with the mono samples that end at the frame heard now. False when there is
     * nothing to show (no stream, or the copy was off when those frames went by).
     */
    fun snapshot(out: FloatArray): Boolean {
        val sinkHandle: SinkHandle
        val written: Long
        val rate: Int
        synchronized(lock) {
            sinkHandle = handle ?: return false
            written = writtenFrames
            rate = sampleRate
        }
        val heard = sinkHandle.position()?.let { pos ->
            // Extrapolated from the last position report, at the stream's rate.
            pos.framesPlayed + (clock.nowMicros() - pos.atMicros).coerceAtLeast(0) * rate / MICROS_PER_SECOND
        } ?: (written - rate * FALLBACK_LATENCY_MS / MILLIS_PER_SECOND)
        val end = heard.coerceIn(written - RING_FRAMES + out.size, written)
        if (end < out.size) return false
        synchronized(lock) {
            for (i in out.indices) out[i] = ring[((end - out.size + i) % RING_FRAMES).toInt()]
        }
        return true
    }

    private fun sample(pcm: ByteArray, at: Int, bitDepth: Int): Float = when (bitDepth) {
        BITS_16 -> ((pcm[at].toInt() and BYTE) or (pcm[at + 1].toInt() shl BITS_PER_BYTE)).toShort() / SCALE_16
        BITS_24 -> {
            val v = (pcm[at].toInt() and BYTE) or ((pcm[at + 1].toInt() and BYTE) shl BITS_PER_BYTE) or (pcm[at + 2].toInt() shl BITS_16)
            v / SCALE_24
        }
        else -> {
            val v = (pcm[at].toInt() and BYTE) or ((pcm[at + 1].toInt() and BYTE) shl BITS_PER_BYTE) or
                ((pcm[at + 2].toInt() and BYTE) shl BITS_16) or (pcm[at + 3].toInt() shl BITS_24)
            v / SCALE_32
        }
    }

    private companion object {
        const val RING_FRAMES = 1 shl 17 // ~2.7 s at 48 kHz
        const val DEFAULT_RATE = 48_000
        const val FALLBACK_LATENCY_MS = 200L
        const val MICROS_PER_SECOND = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val BITS_PER_BYTE = 8
        const val BITS_16 = 16
        const val BITS_24 = 24
        const val BYTE = 0xFF
        const val SCALE_16 = 32_768f
        const val SCALE_24 = 8_388_608f
        const val SCALE_32 = 2_147_483_648f
    }
}
