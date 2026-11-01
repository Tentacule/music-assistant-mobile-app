package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.Text as TvText

/**
 * The bar at the bottom of the app: what plays on the TV, its controls, and the way into the Now
 * playing mode (OK on the title opens it).
 */
@Composable
fun TvPlayerBar(
    playerData: PlayerData?,
    homeScreenViewModel: HomeScreenViewModel,
    onAction: (PlayerAction) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    // Focused by the menu's "Player controls" entry, so the bar is one press away from any page.
    playPauseFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val info = playerData?.nowPlayingInfo()
    val position by rememberPositionSec(homeScreenViewModel, playerData)

    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val duration = info?.durationSec
        if (duration != null) {
            LinearProgressIndicator(
                progress = { (position / duration).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        }
        // Inside the TV safe area: 48 dp from the screen edge, 24 dp from the bottom.
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvFocusSurface(
                onClick = onOpenNowPlaying,
                enabled = info != null,
                focusedScale = BAR_FOCUSED_SCALE,
                modifier = Modifier.weight(1f),
            ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    TvIcon(Icons.Default.MusicNote, contentDescription = null)
                    info?.imageUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvText(
                            text = info?.title ?: stringResource(R.string.tv_nothing_playing),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (info?.isDynamic == true) SmartMixBadge()
                    }
                    listOfNotNull(info?.artist, info?.album).takeIf { it.isNotEmpty() }?.let {
                        TvText(
                            text = it.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TvLocalContentColor.current.copy(alpha = SECONDARY_TEXT_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            }
            if (info != null) {
                // Shuffle and repeat, lit while on: with shuffle, the server mixes an added track
                // into the rest of the queue instead of appending it. A smart mix shows as shuffled.
                TvIconButton(
                    Icons.Default.Shuffle,
                    stringResource(R.string.tv_shuffle),
                    onClick = { onAction(PlayerAction.ToggleShuffle(current = info.shuffle)) },
                    tint = modeTint(info.shuffle || info.isDynamic),
                )
                TvIconButton(
                    if (info.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    stringResource(R.string.tv_repeat),
                    onClick = { onAction(PlayerAction.ToggleRepeatMode(current = info.repeatMode)) },
                    tint = modeTint(info.repeatMode != RepeatMode.OFF),
                )
                TvIconButton(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.tv_nav_queue), onOpenQueue)
                TvIconButton(
                    Icons.Default.SkipPrevious,
                    stringResource(R.string.tv_previous),
                    onClick = { onAction(PlayerAction.Previous) },
                )
                TvIconButton(
                    if (info.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (info.isPlaying) R.string.tv_pause else R.string.tv_play),
                    onClick = { onAction(PlayerAction.TogglePlayPause) },
                    modifier = Modifier.focusRequester(playPauseFocus),
                )
                TvIconButton(
                    Icons.Default.SkipNext,
                    stringResource(R.string.tv_next),
                    onClick = { onAction(PlayerAction.Next) },
                )
            }
        }
    }
}

@Composable
private fun modeTint(on: Boolean) =
    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OFF_ALPHA)

private const val OFF_ALPHA = 0.6f
private const val SECONDARY_TEXT_ALPHA = 0.75f
private const val BAR_FOCUSED_SCALE = 1.02f
