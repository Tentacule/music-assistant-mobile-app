package io.music_assistant.client.tv.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Intercepts volume keys globally — before the TV firmware can route them to system audio —
 * when a remote (non-local) player is selected.
 *
 * The user must enable it once in Settings → Accessibility → Music Assistant TV.
 * If not enabled, volume keys fall through to the system (local TV volume changes normally).
 *
 * Dependencies are resolved in onServiceConnected() (not lazily at declaration) because
 * AccessibilityService can be started by Android before the app process is fully initialized,
 * and lazy Koin inject() can crash in that window.
 */
class TvVolumeAccessibilityService : AccessibilityService(), KoinComponent {
    private val tvVolumeProvider: TvVolumeProvider by inject()
    private val dataSource: MainDataSource by inject()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        Logger.withTag("TvVolumeA11y").i { "Service connected, filter key events flag set" }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Logger.withTag("TvVolumeA11y").d { "onKeyEvent keyCode=${event.keyCode} action=${event.action}" }
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        val selected = dataSource.selectedPlayer
        val local = dataSource.localPlayer.value
        val isRemote = selected != null && local != null && selected.playerId != local.playerId
        if (!isRemote) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            tvVolumeProvider.adjustVolume(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit
    override fun onInterrupt() = Unit
}
