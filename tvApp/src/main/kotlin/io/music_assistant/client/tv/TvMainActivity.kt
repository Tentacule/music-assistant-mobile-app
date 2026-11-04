package io.music_assistant.client.tv

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.asLiveData
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.services.MainMediaPlaybackService
import io.music_assistant.client.tv.ui.TvApp
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.android.inject

class TvMainActivity : ComponentActivity() {
    private val dataSource: MainDataSource by inject()
    private val userActivity: TvUserActivity by inject()
    private val prefs: TvPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Playback lives in this foreground service and the shared local player, not in the UI,
        // so switching screens (app <-> Now playing) never interrupts the music.
        dataSource.isAnythingPlaying.asLiveData().observe(this) { playing ->
            if (playing) startPlaybackService()
        }

        combine(dataSource.isAnythingPlaying, prefs.keepScreenOn) { playing, keepOn ->
            playing && keepOn
        }.asLiveData().observe(this) { keepScreen ->
            if (keepScreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
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
        // A keyboard's Escape (the emulator, a TV keyboard) is Back here. Left to Compose, the first
        // Escape only clears the focus, which strands the remote, and only the next one goes back.
        // A synthesized BACK key event would not reach the back callbacks (Android 13+ routes the
        // real one through OnBackInvokedDispatcher), so the release presses Back directly.
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) userActivity.onKey(event.keyCode)
        if (event.keyCode in SELECT_KEYS && !replayingSelect) return onSelectKey(event)
        return super.dispatchKeyEvent(event)
    }

    // The OK key, read here rather than by Compose. A long press opens the item menu as soon as the
    // timeout passes, while OK is still held; the menu takes the focus, and the release then
    // presses its first entry (playing the item). So the press is held back until the release:
    // a short one is replayed as a click, a long one as a long click whose release never reaches
    // the menu.
    private var selectDownAt = NO_PRESS
    private var replayingSelect = false

    private fun onSelectKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) selectDownAt = event.eventTime

            KeyEvent.ACTION_UP -> {
                if (selectDownAt == NO_PRESS) return true
                val held = event.eventTime - selectDownAt
                selectDownAt = NO_PRESS
                replaySelect(event, longPress = held >= ViewConfiguration.getLongPressTimeout())
            }
        }
        return true
    }

    private fun replaySelect(up: KeyEvent, longPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        val down =
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, up.keyCode, 0, up.metaState, up.deviceId, up.scanCode, 0, up.source)
        val release = KeyEvent.changeAction(down, KeyEvent.ACTION_UP)
        replayingSelect = true
        super.dispatchKeyEvent(down)
        replayingSelect = false
        if (!longPress) {
            replayingSelect = true
            super.dispatchKeyEvent(release)
            replayingSelect = false
            return
        }
        // Compose fires the long click once the timeout passes with the key down. The release
        // then goes straight to this window's views, not to the menu window that has the focus,
        // so it only resets the item, whose long click already ran.
        window.decorView.postDelayed(
            { window.decorView.dispatchKeyEvent(release) },
            ViewConfiguration.getLongPressTimeout() + LONG_PRESS_MARGIN_MS,
        )
    }

    // The remote's media keys drive the selected player (this TV by default) from any screen.
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
        val target = dataSource.selectedPlayer ?: dataSource.localPlayer.value
        if (action != null && target != null && event?.repeatCount == 0) {
            dataSource.playerAction(target, action)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        val SELECT_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        const val NO_PRESS = -1L
        const val LONG_PRESS_MARGIN_MS = 150L
        const val SEEK_SECONDS = 10L
    }
}
