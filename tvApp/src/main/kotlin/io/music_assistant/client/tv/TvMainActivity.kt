package io.music_assistant.client.tv

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.asLiveData
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.services.MainMediaPlaybackService
import io.music_assistant.client.tv.ui.TvApp
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import org.koin.android.ext.android.inject

class TvMainActivity : ComponentActivity() {
    private val dataSource: MainDataSource by inject()
    private val userActivity: TvUserActivity by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Playback lives in this foreground service and the shared local player, not in the UI,
        // so switching screens (app <-> Now playing) never interrupts the music.
        dataSource.isAnythingPlaying.asLiveData().observe(this) { playing ->
            if (playing) startPlaybackService()
        }
        setContent { TvApp() }
    }

    private fun startPlaybackService() {
        val serviceIntent = Intent(this, MainMediaPlaybackService::class.java)
        serviceIntent.action = "ACTION_PLAY"
        try {
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Logger.withTag("TvMainActivity")
                    .w("Cannot start foreground service from background, will retry when foregrounded")
            } else {
                throw e
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) userActivity.onKey(event.keyCode)
        return super.dispatchKeyEvent(event)
    }

    // The remote's media keys drive the local player from any screen.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> PlayerAction.TogglePlayPause
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerAction.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerAction.Pause
            KeyEvent.KEYCODE_MEDIA_STOP -> PlayerAction.Stop
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerAction.Next
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerAction.Previous
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerAction.SeekBy(SEEK_SECONDS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerAction.SeekBy(-SEEK_SECONDS)
            else -> null
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event?.repeatCount == 0) userActivity.toggleNowPlaying()
            return true
        }
        val local = dataSource.localPlayer.value
        if (action != null && local != null && event?.repeatCount == 0) {
            dataSource.playerAction(local, action)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val SEEK_SECONDS = 10L
    }
}
