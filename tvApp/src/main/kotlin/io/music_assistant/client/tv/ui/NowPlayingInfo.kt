package io.music_assistant.client.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import kotlinx.coroutines.flow.flowOf

/** What the player bar and the Now playing mode show of the local player. */
data class NowPlayingInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val imageUrl: String?,
    val durationSec: Double?,
    val isPlaying: Boolean,
    val upNext: String?,
    val shuffle: Boolean,
    val repeatMode: RepeatMode,
    // A dynamic queue (a smart mix the server manages) is always mixed; the server refuses
    // shuffle and repeat changes on it, and mixes added tracks into it.
    val isDynamic: Boolean,
)

fun PlayerData.nowPlayingInfo(): NowPlayingInfo? {
    val media = player.currentMedia
    val current = queueInfo?.currentItem?.track
    val title = media?.title?.takeIf { it.isNotBlank() } ?: current?.displayName ?: return null
    val nextIndex = queueInfo?.currentIndex?.plus(1)
    return NowPlayingInfo(
        title = title,
        artist = media?.artist?.takeIf { it.isNotBlank() },
        album = media?.album?.takeIf { it.isNotBlank() },
        imageUrl = media?.imageUrl,
        durationSec = media?.duration?.takeIf { it > 0 },
        isPlaying = player.isPlaying,
        upNext = nextIndex?.let { queueItems?.getOrNull(it) }?.track?.displayName,
        shuffle = queueInfo?.shuffleEnabled == true,
        repeatMode = queueInfo?.repeatMode ?: RepeatMode.OFF,
        isDynamic = queueInfo?.isDynamicPlaylist == true,
    )
}

/** The live position, in seconds, of the local player's queue (interpolated between server updates). */
@Composable
fun rememberPositionSec(homeScreenViewModel: HomeScreenViewModel, playerData: PlayerData?): State<Double> {
    val queueId = playerData?.queueOrPlayerId
    val flow = remember(queueId) {
        queueId?.let(homeScreenViewModel::observePosition) ?: flowOf(0.0)
    }
    return flow.collectAsState(initial = playerData?.queueInfo?.elapsedTime ?: 0.0)
}

fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
