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
}
