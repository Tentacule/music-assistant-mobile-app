package io.music_assistant.client.tv.services

import android.os.SystemClock
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.TvVolumeController
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/**
 * Sends volume commands to the selected remote player and shows the overlay, when the user asked for
 * it ([TvPreferences.remoteVolumeKeys]). Otherwise the system changes the volume of this device.
 * On Android TV the volume keys never reach the Activity: they come from [TvVolumeAccessibilityService].
 * On devices where the firmware intercepts volume keys before the accessibility service (e.g. some
 * Xiaomi boxes), [TvMainActivity] registers a VOLUME_CHANGED_ACTION receiver as a fallback when
 * [TvPreferences.volumeBroadcastFallback] is on.
 */
class TvVolumeProvider(
    private val dataSource: MainDataSource,
    private val volumeController: TvVolumeController,
    private val prefs: TvPreferences,
) {
    /** Whether a volume key is for this class; if not, it stays with the system. */
    val controlsRemotePlayer: Boolean
        get() {
            if (!prefs.remoteVolumeKeys.value) return false
            val selected = dataSource.selectedPlayer ?: return false
            val local = dataSource.localPlayer.value
            // If no local player is set up, any selected player is remote by definition.
            return local == null || selected.playerId != local.playerId
        }

    /** True while the volume overlay is showing (used to route D-pad left/right to volume). */
    val isVolumeOverlayVisible: Boolean get() = volumeController.isVisible

    private class SentLevel(val playerId: String, val level: Float, val at: Long)

    // The server reports a new volume only after the command, so a quick second press would start
    // from the old value and the step would be lost: for a moment, it starts from the level sent.
    private var lastSent: SentLevel? = null

    /**
     * Opens the volume overlay at the current player level without sending a command.
     * Used on devices where volume keys cannot be intercepted: the broadcast opens the overlay,
     * and D-pad left/right then calls [adjustVolume] to actually change the remote volume.
     */
    fun openOverlay() {
        val selected = dataSource.selectedPlayer ?: return
        val now = SystemClock.uptimeMillis()
        val level = lastSent
            ?.takeIf { it.playerId == selected.playerId && now - it.at < SENT_LEVEL_VALID_MS }
            ?.level
            ?: selected.player.currentVolume
            ?: 50f
        volumeController.show(level / 100f, playerName = selected.player.name)
    }

    fun adjustVolume(direction: Int) {
        if (direction == 0 || !controlsRemotePlayer) return
        val selected = dataSource.selectedPlayer ?: return
        val now = SystemClock.uptimeMillis()
        val current = lastSent
            ?.takeIf { it.playerId == selected.playerId && now - it.at < SENT_LEVEL_VALID_MS }
            ?.level
            ?: selected.player.currentVolume
            ?: return
        val newLevel = (current + direction * VOLUME_STEP).coerceIn(0f, 100f)
        lastSent = SentLevel(selected.playerId, newLevel, now)
        dataSource.playerAction(selected, PlayerAction.VolumeSet(newLevel.toDouble()))
        volumeController.show(newLevel / 100f, playerName = selected.player.name)
    }

    private companion object {
        const val VOLUME_STEP = 1f
        const val SENT_LEVEL_VALID_MS = 1_500L
    }
}
