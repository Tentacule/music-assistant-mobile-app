package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.tv.R
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.Text as TvText

/**
 * Where the TV app plays: this TV (first) or any other Music Assistant player. The choice drives
 * Now playing, the queue, the playback settings and every play request; the app comes back to this TV on
 * every launch.
 */
@Composable
fun TvPlayerPicker(
    players: List<PlayerData>,
    selectedPlayerId: String?,
    onPick: (PlayerData) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = remember(players) { players.sortedByDescending { it.isLocal } }
    val selectedFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvText(
                stringResource(R.string.tv_pick_player),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ordered.forEach { player ->
                val selected = player.playerId == selectedPlayerId
                TvFocusSurface(
                    onClick = {
                        onPick(player)
                        onDismiss()
                    },
                    selected = selected,
                    focusedScale = PICKER_FOCUSED_SCALE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier),
                ) {
                    PlayerRow(player, selected)
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    }
}

@Composable
private fun PlayerRow(player: PlayerData, selected: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvIcon(if (player.isLocal) Icons.Default.Tv else Icons.Default.Speaker, contentDescription = null)
        Column(Modifier.weight(1f)) {
            TvText(
                text = if (player.isLocal) {
                    stringResource(R.string.tv_this_tv, player.player.name)
                } else {
                    player.player.name
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            player.player.currentMedia?.title?.takeIf { it.isNotBlank() }?.let { title ->
                TvText(
                    text = listOfNotNull(title, player.player.currentMedia?.artist).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvLocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (player.player.isPlaying) TvIcon(Icons.Default.GraphicEq, contentDescription = null)
        if (selected) TvIcon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

private const val PICKER_FOCUSED_SCALE = 1.03f
private const val SECONDARY_ALPHA = 0.75f
