package io.music_assistant.client.tv.audio

import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle

/**
 * The TV's audio output: the platform sink, unchanged, with every written chunk also handed to
 * [tap] for the visualizations. Bound in place of the shared sink, so the player never knows.
 */
class TapAudioSink(private val delegate: AudioSink, private val tap: VisualizerTap) : AudioSink {
    override fun open(format: SinkFormat): SinkHandle {
        val handle = delegate.open(format)
        tap.onOpen(format, handle)
        return TapHandle(handle, tap)
    }
}

private class TapHandle(private val delegate: SinkHandle, private val tap: VisualizerTap) : SinkHandle by delegate {
    override fun write(pcm: ByteArray, offset: Int, length: Int): Int {
        val accepted = delegate.write(pcm, offset, length)
        if (accepted > 0) tap.onPcm(pcm, offset, accepted)
        return accepted
    }

    override fun close() {
        tap.onClose(delegate)
        delegate.close()
    }
}
