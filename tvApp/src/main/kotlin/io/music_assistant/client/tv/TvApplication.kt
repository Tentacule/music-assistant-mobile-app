package io.music_assistant.client.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.provider.Settings
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.initKoin
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
     * player takes the name of the device (Android TV settings > Device preferences > About), or
     * "Ma TV". Set before the player is turned on, so it registers under that name.
     */
    private fun nameTheLocalPlayer() {
        val settings: SettingsRepository = GlobalContext.get().get()
        val current = settings.sendspinPlayerSettings.value
        if (current.name != SettingsRepository.DEVICE_NAME) return
        val deviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.tv_default_player_name)
        settings.setSendspinPlayerSettings(current.copy(name = deviceName))
    }
}
