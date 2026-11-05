package io.music_assistant.client.tv.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import co.touchlab.kermit.Logger
import java.util.concurrent.Executor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Intercepts volume keys globally — before the TV firmware can route them to system audio —
 * when the user asked for it in the settings and a remote (non-local) player is selected.
 *
 * The user must enable it once in Settings → Accessibility → Music Assistant TV (see VolumeKeysCard).
 * If not enabled, volume keys fall through to the system (local TV volume changes normally).
 *
 * Dependencies are resolved in onServiceConnected() (not lazily at declaration) because
 * AccessibilityService can be started by Android before the app process is fully initialized,
 * and lazy Koin inject() can crash in that window.
 */
class TvVolumeAccessibilityService : AccessibilityService(), KoinComponent {
    private val tvVolumeProvider: TvVolumeProvider by inject()

    // Every key of the remote waits for onKeyEvent, which the framework runs on the "main" thread
    // of this context. On the real one, a screen that takes long to compose holds back the release
    // of a key, and the system takes the key as held and repeats it. So it gets its own thread.
    private val keyThread = HandlerThread("TvVolumeKeys").apply { start() }
    private val keyHandler = Handler(keyThread.looper)

    override fun getMainLooper(): Looper = keyThread.looper

    override fun getMainExecutor(): Executor = Executor { keyHandler.post(it) }

    override fun onDestroy() {
        super.onDestroy()
        keyThread.quitSafely()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        Logger.withTag("TvVolumeA11y").i { "Service connected, filter key events flag set" }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        Logger.withTag("TvVolumeA11y").d { "onKeyEvent keyCode=${event.keyCode} action=${event.action}" }
        if (!tvVolumeProvider.controlsRemotePlayer) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            tvVolumeProvider.adjustVolume(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit
    override fun onInterrupt() = Unit
}
