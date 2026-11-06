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

    private val _nowPlayingStyle = MutableStateFlow(
        NowPlayingStyle.entries.firstOrNull { it.name == prefs.getString(KEY_NOW_PLAYING_STYLE, null) }
            ?: NowPlayingStyle.CLASSIC,
    )

    /** The layout of the Now playing mode. */
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = _nowPlayingStyle.asStateFlow()

    fun setNowPlayingStyle(style: NowPlayingStyle) {
        prefs.edit().putString(KEY_NOW_PLAYING_STYLE, style.name).apply()
        _nowPlayingStyle.value = style
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

    private val _menu = MutableStateFlow(parseTvMenu(prefs.getString(KEY_MENU, null)))

    /** The entries of the menu, in their order, with the ones that the user hid. */
    val menu: StateFlow<List<TvMenuItem>> = _menu.asStateFlow()

    fun setMenu(menu: List<TvMenuItem>) {
        prefs.edit().putString(KEY_MENU, menu.encodeTvMenu()).apply()
        _menu.value = menu
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

    private val _defaultPlayerId = MutableStateFlow<String?>(prefs.getString(KEY_DEFAULT_PLAYER_ID, null))

    /** The player to select at startup when the last used player is unavailable. Null = last used. */
    val defaultPlayerId: StateFlow<String?> = _defaultPlayerId.asStateFlow()

    fun setDefaultPlayerId(id: String?) {
        if (id == null) prefs.edit().remove(KEY_DEFAULT_PLAYER_ID).apply()
        else prefs.edit().putString(KEY_DEFAULT_PLAYER_ID, id).apply()
        _defaultPlayerId.value = id
    }

    private val _lyricsOffsetMs = MutableStateFlow(
        prefs.getInt(KEY_LYRICS_OFFSET_MS, 0).coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS),
    )

    /** How many milliseconds to shift the lyrics display: negative = earlier, positive = later. */
    val lyricsOffsetMs: StateFlow<Int> = _lyricsOffsetMs.asStateFlow()

    fun setLyricsOffsetMs(ms: Int) {
        val clamped = ms.coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS)
        prefs.edit().putInt(KEY_LYRICS_OFFSET_MS, clamped).apply()
        _lyricsOffsetMs.value = clamped
    }

    private val _exitOnBackBehavior = MutableStateFlow(
        ExitOnBackBehavior.entries.firstOrNull { it.name == prefs.getString(KEY_EXIT_ON_BACK, null) }
            ?: ExitOnBackBehavior.ASK_IF_LOCAL,
    )

    /** What to do when the user presses Back on the app's root screen. */
    val exitOnBackBehavior: StateFlow<ExitOnBackBehavior> = _exitOnBackBehavior.asStateFlow()

    fun setExitOnBackBehavior(behavior: ExitOnBackBehavior) {
        prefs.edit().putString(KEY_EXIT_ON_BACK, behavior.name).apply()
        _exitOnBackBehavior.value = behavior
    }

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))

    /** Keep the screen on during playback; enabled by default. */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(on: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
        _keepScreenOn.value = on
    }

    private val _remoteVolumeKeys = MutableStateFlow(prefs.getBoolean(KEY_REMOTE_VOLUME_KEYS, false))

    /** Whether the volume keys control a selected remote player; off by default, they stay on this device. */
    val remoteVolumeKeys: StateFlow<Boolean> = _remoteVolumeKeys.asStateFlow()

    fun setRemoteVolumeKeys(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMOTE_VOLUME_KEYS, on).apply()
        _remoteVolumeKeys.value = on
    }

    private val _lyricsMode = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_MODE, false))

    /** Whether Now playing shows the lyrics (when the track has some); kept across tracks. */
    val lyricsMode: StateFlow<Boolean> = _lyricsMode.asStateFlow()

    fun setLyricsMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_LYRICS_MODE, on).apply()
        _lyricsMode.value = on
    }

    /** Whether the TV audio defaults were applied once; later, the user's choice stays. */
    var audioDefaultsApplied: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_DEFAULTS_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_DEFAULTS_APPLIED, value).apply()

    private companion object {
        const val KEY_EXIT_ON_BACK = "exit_on_back"
        const val KEY_AUDIO_DEFAULTS_APPLIED = "audio_defaults_applied"
        const val KEY_LYRICS_MODE = "lyrics_mode"
        const val KEY_DEFAULT_PLAYER_ID = "default_player_id"
        const val KEY_LYRICS_OFFSET_MS = "lyrics_offset_ms"
        const val LYRICS_OFFSET_MIN_MS = -1000
        const val LYRICS_OFFSET_MAX_MS = 1000
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_REMOTE_VOLUME_KEYS = "remote_volume_keys"
        const val KEY_MENU = "menu"
        const val KEY_NOW_PLAYING_DELAY = "now_playing_delay"
        const val KEY_NOW_PLAYING_STYLE = "now_playing_style"
        const val KEY_UNSUPPORTED_DISMISSED = "unsupported_install_dismissed"
        const val KEY_VISUALIZER = "visualizer"
    }
}

enum class ExitOnBackBehavior { NEVER_ASK, ASK_IF_LOCAL, ALWAYS_ASK }

enum class NowPlayingStyle { CLASSIC, BANNER }

enum class NowPlayingDelay(val millis: Long?) {
    OFF(null),
    SECONDS_30(30_000),
    MINUTE_1(60_000),
    MINUTES_5(300_000),
}
