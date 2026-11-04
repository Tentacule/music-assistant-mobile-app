package io.music_assistant.client.tv.services

import android.media.AudioManager
import androidx.media.VolumeProviderCompat
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.TvVolumeController
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/**
 * Routes remote-control volume keys to the right target:
 * - local player  → Android system volume (STREAM_MUSIC), no native UI
 * - remote player → Music Assistant VolumeSet
 *
 * Passed to [SharedMediaSessionManager] so the MediaSession uses
 * [VolumeProviderCompat.VOLUME_CONTROL_RELATIVE] instead of the default
 * [setPlaybackToLocal], which would let Android TV absorb the keys before
 * the app ever sees them.
 */
class TvVolumeProvider(
    private val audioManager: AudioManager,
    private val dataSource: MainDataSource,
    private val volumeController: TvVolumeController,
) : VolumeProviderCompat(
    VOLUME_CONTROL_RELATIVE,
    MAX_VOLUME,
    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        * MAX_VOLUME / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
) {
    override fun onAdjustVolume(direction: Int) {
        if (direction == 0) return
        val selected = dataSource.selectedPlayer
        val local = dataSource.localPlayer.value
        if (selected == null || local == null || selected.playerId == local.playerId) {
            val adjust = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0)
            val sysMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val sysCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolume = sysCurrent * MAX_VOLUME / sysMax
            volumeController.show(sysCurrent.toFloat() / sysMax.toFloat())
        } else {
            val current = selected.player.currentVolume ?: return
            val newLevel = (current + direction * VOLUME_STEP).coerceIn(0f, 100f)
            dataSource.playerAction(selected, PlayerAction.VolumeSet(newLevel.toDouble()))
            currentVolume = newLevel.toInt()
            volumeController.show(newLevel / 100f)
        }
    }

    private companion object {
        const val MAX_VOLUME = 100
        const val VOLUME_STEP = 5f
    }
}
