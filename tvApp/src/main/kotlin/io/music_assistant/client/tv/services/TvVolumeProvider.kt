package io.music_assistant.client.tv.services

import android.media.AudioManager
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.TvVolumeController
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/**
 * Sends volume commands to the selected remote player and shows the overlay.
 * Local player volume is handled natively by the system (no overlay).
 * Called from dispatchKeyEvent on phone; on Android TV volume keys never reach the Activity,
 * so a separate mechanism (slider or AccessibilityService) is needed for remote players.
 */
class TvVolumeProvider(
    private val audioManager: AudioManager,
    private val dataSource: MainDataSource,
    private val volumeController: TvVolumeController,
) {
    fun adjustVolume(direction: Int) {
        if (direction == 0) return
        val selected = dataSource.selectedPlayer ?: return
        val local = dataSource.localPlayer.value ?: return
        if (selected.playerId == local.playerId) return
        val current = selected.player.currentVolume ?: return
        val newLevel = (current + direction * VOLUME_STEP).coerceIn(0f, 100f)
        dataSource.playerAction(selected, PlayerAction.VolumeSet(newLevel.toDouble()))
        volumeController.show(newLevel / 100f)
    }

    private companion object {
        const val VOLUME_STEP = 5f
    }
}
