package io.music_assistant.client.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.provider.Settings
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.settings.DefaultClickOption
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.di.tvModule
import io.music_assistant.client.tv.services.MediaNotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class TvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        initKoin(androidModule(), tvModule(), verboseLogging = debuggable) {
            androidContext(this@TvApplication)
        }
        nameTheLocalPlayer()
        applyAudioDefaults()
        playTracksFromHereByDefault()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                MediaNotificationManager.CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /**
     * The shared default name of the local player is "My Phone". Until the user picks one, the TV
     * player is named after the device (Android TV settings > Device preferences > About) as
     * "SHIELD (Music Assistant)": the device name alone is also the name of its built-in Google
     * Cast receiver, which Music Assistant lists as a separate player. "Ma TV" without a device
     * name. Set before the player is turned on, so it registers under that name.
     */
    private fun nameTheLocalPlayer() {
        val settings: SettingsRepository = GlobalContext.get().get()
        val current = settings.sendspinPlayerSettings.value
        val deviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
        // Automatic names only: the shared default, or the bare device name of earlier versions.
        if (current.name != SettingsRepository.DEVICE_NAME && current.name != deviceName) return
        val name = deviceName?.let { getString(R.string.tv_player_default_name, it) }
            ?: getString(R.string.tv_default_player_name)
        settings.setSendspinPlayerSettings(current.copy(name = name))
    }

    /**
     * The TV plays from a LAN connection and only for itself (never in a group), so it trades a little
     * jitter cushion for shorter track changes, and spends its plentiful memory on a longer buffer.
     *
     * The startup buffer (`min_buffer_ms`) is set on every launch, since no TV screen sets it: the
     * server waits that long before the first chunk of every stream. At 1000 ms (the shared default)
     * every skip was silent for about 1 s; the reopened AudioTrack itself costs only ~40 ms.
     * The buffer length is the TV default once, only if the user kept the shared default; the TV
     * settings change it afterwards.
     */
    private fun applyAudioDefaults() {
        val koin = GlobalContext.get()
        val settings: SettingsRepository = koin.get()
        val prefs: TvPreferences = koin.get()
        settings.setSendspinMinBufferMs(TV_MIN_BUFFER_MS)
        if (prefs.audioDefaultsApplied) return
        val current = settings.sendspinPlayerSettings.value
        if (current.bufferSeconds == SettingsRepository.BUFFER_SECONDS_DEFAULT) {
            settings.setSendspinPlayerSettings(current.copy(bufferSeconds = TV_BUFFER_SECONDS))
        }
        prefs.audioDefaultsApplied = true
    }

    /**
     * A track picked in an album or a playlist plays that album or playlist from it on, instead of
     * the track alone (the shared default, "Play now"). Only where the user made no choice: the
     * phone sets it in its click-action settings, which the TV has no screen for.
     */
    private fun playTracksFromHereByDefault() {
        val settings: SettingsRepository = GlobalContext.get().get()
        val current = settings.defaultClickActions.value[ItemKind.TRACK].orEmpty()
        val missing = listOf(ClickContext.ALBUM, ClickContext.PLAYLIST).filterNot { it in current }
        if (missing.isEmpty()) return
        settings.setDefaultClickActions(
            ItemKind.TRACK,
            current + missing.associateWith { DefaultClickOption.PLAY_FROM_HERE },
        )
    }

    private companion object {
        const val TV_MIN_BUFFER_MS = 500
        const val TV_BUFFER_SECONDS = 20
    }
}
