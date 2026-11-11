package io.music_assistant.client.tv

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
import io.music_assistant.client.tv.services.TvVolumeProvider
import io.music_assistant.client.tv.services.isAnyQueueItemPlaying
import io.music_assistant.client.tv.services.isAnythingPlaying
import io.music_assistant.client.tv.ui.TvApp
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.android.inject

class TvMainActivity : ComponentActivity() {
    private val dataSource: MainDataSource by inject()
    private val userActivity: TvUserActivity by inject()
    private val prefs: TvPreferences by inject()
    private val tvVolumeProvider: TvVolumeProvider by inject()

    // Fallback for devices (e.g. some Xiaomi TV boxes) where the firmware intercepts volume keys
    // before the accessibility service. Listens to system volume changes and forwards them to the
    // remote player. Registered only while the Activity is started, and only when the pref is on.
    private var volumeBroadcastReceiver: BroadcastReceiver? = null
    private var lastVolumeFallbackMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Playback lives in this foreground service and the shared local player, not in the UI,
        // so switching screens (app <-> Now playing) never interrupts the music.
        dataSource.isAnyQueueItemPlaying.asLiveData().observe(this) { playing ->
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

        prefs.volumeBroadcastFallback.asLiveData().observe(this) { enabled ->
            if (enabled) registerVolumeFallback() else unregisterVolumeFallback()
        }

        setContent { TvApp() }
    }

    override fun onStop() {
        super.onStop()
        unregisterVolumeFallback()
    }

    override fun onStart() {
        super.onStart()
        if (prefs.volumeBroadcastFallback.value) registerVolumeFallback()
    }

    private fun registerVolumeFallback() {
        if (volumeBroadcastReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_VOLUME_CHANGED) return
                // The firmware on some TV boxes fires several cascading broadcasts per key press.
                // A cooldown ensures only the first one triggers a command.
                val now = SystemClock.elapsedRealtime()
                if (now - lastVolumeFallbackMs < VOLUME_FALLBACK_COOLDOWN_MS) return
                if (!tvVolumeProvider.controlsRemotePlayer) return
                val streamType = intent.getIntExtra(EXTRA_STREAM_TYPE, -1)
                if (streamType != AudioManager.STREAM_MUSIC) return
                val newVal = intent.getIntExtra(EXTRA_STREAM_VALUE, -1)
                val prevVal = intent.getIntExtra(EXTRA_PREV_STREAM_VALUE, -1)
                if (newVal < 0 || prevVal < 0 || newVal == prevVal) return
                lastVolumeFallbackMs = now
                tvVolumeProvider.openOverlay()
            }
        }
        registerReceiver(receiver, IntentFilter(ACTION_VOLUME_CHANGED))
        volumeBroadcastReceiver = receiver
    }

    private fun unregisterVolumeFallback() {
        volumeBroadcastReceiver?.let { unregisterReceiver(it) }
        volumeBroadcastReceiver = null
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
        // When the volume overlay is open and a remote player is selected, the left/right D-pad
        // keys adjust the remote player's volume instead of navigating the UI. This pairs with the
        // volume broadcast fallback: on devices (e.g. Xiaomi TV boxes) where volume keys cannot be
        // intercepted, a volume key press opens the overlay via a system broadcast, and the user
        // then uses the D-pad to adjust precisely.
        if (
            tvVolumeProvider.isVolumeOverlayVisible &&
            tvVolumeProvider.controlsRemotePlayer &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                tvVolumeProvider.adjustVolume(if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            }
            return true
        }
        // On phone, dispatchKeyEvent receives volume keys; on Android TV they never arrive here.
        // Intercept only for a remote player: send VolumeSet to the server and consume the event
        // so the native volume bar does not appear. For the local player, let the system handle.
        if (
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            tvVolumeProvider.controlsRemotePlayer
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                tvVolumeProvider.adjustVolume(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
            }
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
        // AudioManager hidden API strings for the volume broadcast fallback.
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREV_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        const val VOLUME_FALLBACK_COOLDOWN_MS = 400L
    }
}
