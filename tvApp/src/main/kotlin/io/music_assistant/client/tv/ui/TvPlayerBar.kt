package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel

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
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = info != null, onClick = onOpenNowPlaying)
                    .padding(8.dp),
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
                    Icon(Icons.Default.MusicNote, contentDescription = null)
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
                    Text(
                        text = info?.title ?: stringResource(R.string.tv_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    listOfNotNull(info?.artist, info?.album).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = it.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (info != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.tv_open_now_playing),
                    )
                }
            }
            if (info != null) {
                IconButton(onClick = { onAction(PlayerAction.Previous) }) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.tv_previous))
                }
                IconButton(
                    onClick = { onAction(PlayerAction.TogglePlayPause) },
                    modifier = Modifier.focusRequester(playPauseFocus),
                ) {
                    Icon(
                        if (info.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (info.isPlaying) R.string.tv_pause else R.string.tv_play),
                    )
                }
                IconButton(onClick = { onAction(PlayerAction.Next) }) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.tv_next))
                }
            }
        }
    }
}
