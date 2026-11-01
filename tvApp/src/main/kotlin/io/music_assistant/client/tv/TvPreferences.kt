package io.music_assistant.client.tv

import android.content.Context
import io.music_assistant.client.tv.ui.visualizer.VisualizerStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The TV-only settings, kept apart from the shared [io.music_assistant.client.settings.SettingsRepository]. */
class TvPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tv_preferences", Context.MODE_PRIVATE)

    private val _nowPlayingDelay = MutableStateFlow(
        NowPlayingDelay.entries.firstOrNull { it.name == prefs.getString(KEY_NOW_PLAYING_DELAY, null) }
            ?: NowPlayingDelay.SECONDS_30,
    )

    /** How long the remote stays idle, during playback, before the Now playing mode opens by itself. */
    val nowPlayingDelay: StateFlow<NowPlayingDelay> = _nowPlayingDelay.asStateFlow()

    fun setNowPlayingDelay(delay: NowPlayingDelay) {
        prefs.edit().putString(KEY_NOW_PLAYING_DELAY, delay.name).apply()
        _nowPlayingDelay.value = delay
    }

    private val _visualizer = MutableStateFlow(
        VisualizerStyle.entries.firstOrNull { it.name == prefs.getString(KEY_VISUALIZER, null) }
            ?: VisualizerStyle.PULSE,
    )

    /** The Now playing visualization; saved at once whenever it changes. */
    val visualizer: StateFlow<VisualizerStyle> = _visualizer.asStateFlow()

    fun setVisualizer(style: VisualizerStyle) {
        prefs.edit().putString(KEY_VISUALIZER, style.name).apply()
        _visualizer.value = style
    }

    /**
     * The servers whose "unsupported installation" warning the user dismissed. The shared home
     * page forgets a dismissal with its ViewModel; on a TV, where the process restarts often, the
     * warning would come back on every launch.
     */
    fun isUnsupportedInstallDismissed(serverId: String): Boolean =
        serverId in prefs.getStringSet(KEY_UNSUPPORTED_DISMISSED, emptySet()).orEmpty()

    fun setUnsupportedInstallDismissed(serverId: String) {
        val ids = prefs.getStringSet(KEY_UNSUPPORTED_DISMISSED, emptySet()).orEmpty() + serverId
        prefs.edit().putStringSet(KEY_UNSUPPORTED_DISMISSED, ids).apply()
    }

    /** Whether the TV audio defaults were applied once; later, the user's choice stays. */
    var audioDefaultsApplied: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_DEFAULTS_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_DEFAULTS_APPLIED, value).apply()

    private companion object {
        const val KEY_AUDIO_DEFAULTS_APPLIED = "audio_defaults_applied"
        const val KEY_NOW_PLAYING_DELAY = "now_playing_delay"
        const val KEY_UNSUPPORTED_DISMISSED = "unsupported_install_dismissed"
        const val KEY_VISUALIZER = "visualizer"
    }
}

enum class NowPlayingDelay(val millis: Long?) {
    OFF(null),
    SECONDS_30(30_000),
    MINUTE_1(60_000),
    MINUTES_5(300_000),
}
