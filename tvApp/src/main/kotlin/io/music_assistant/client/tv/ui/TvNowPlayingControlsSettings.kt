package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.tv.NpControlEntry
import io.music_assistant.client.tv.NpControlItem
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.defaultNpControls
import io.music_assistant.client.tv.isContextual
import io.music_assistant.client.tv.movedControl
import io.music_assistant.client.tv.toggledControl
import io.music_assistant.client.ui.compose.common.icons.CrossfadeIcon
import org.koin.compose.koinInject
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * Settings card for the Now playing controls: which buttons appear and in what order.
 * OK on a row shows or hides it; the arrows move it.
 */
@Composable
fun NowPlayingControlsCard() {
    val prefs: TvPreferences = koinInject()
    val controls by prefs.npControls.collectAsStateWithLifecycle()

    SettingsCard(stringResource(R.string.tv_np_controls)) {
        Text(
            stringResource(R.string.tv_np_controls_hint),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        controls.forEach { item ->
            key(item.entry) {
                NpControlRow(
                    item = item,
                    onToggle = { prefs.setNpControls(controls.toggledControl(item.entry)) },
                    onMoveUp = { prefs.setNpControls(controls.movedControl(item.entry, -1)) },
                    onMoveDown = { prefs.setNpControls(controls.movedControl(item.entry, 1)) },
                )
            }
        }
        TvOutlinedButton(
            onClick = { prefs.setNpControls(defaultNpControls) },
            modifier = Modifier.padding(top = 16.dp),
        ) { TvText(stringResource(R.string.tv_np_controls_reset)) }
    }
}

@Composable
private fun NpControlRow(
    item: NpControlItem,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.tvHorizontalFocusRow(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvFocusSurface(
            onClick = onToggle,
            focusedScale = ROW_FOCUSED_SCALE,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val contentAlpha = if (item.visible) 1f else HIDDEN_ALPHA
                TvIcon(
                    item.entry.settingsIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(contentAlpha),
                )
                TvText(
                    item.entry.settingsLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).alpha(contentAlpha),
                )
                if (item.entry.isContextual) {
                    TvText(
                        stringResource(R.string.tv_np_contextual_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                TvIcon(
                    if (item.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(
                        if (item.visible) R.string.tv_menu_shown else R.string.tv_menu_hidden,
                    ),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        TvIconButton(Icons.Default.ArrowUpward, stringResource(R.string.tv_menu_move_up), onMoveUp)
        TvIconButton(Icons.Default.ArrowDownward, stringResource(R.string.tv_menu_move_down), onMoveDown)
    }
}

@Composable
private fun NpControlEntry.settingsLabel(): String = when (this) {
    NpControlEntry.SHUFFLE -> stringResource(R.string.tv_shuffle)
    NpControlEntry.PREVIOUS -> stringResource(R.string.tv_previous)
    NpControlEntry.PLAY_PAUSE -> stringResource(R.string.tv_play_pause)
    NpControlEntry.NEXT -> stringResource(R.string.tv_next)
    NpControlEntry.REPEAT -> stringResource(R.string.tv_repeat)
    NpControlEntry.AUTOPLAY -> stringResource(R.string.tv_autoplay)
    NpControlEntry.CROSSFADE -> stringResource(R.string.tv_crossfade)
    NpControlEntry.QUEUE -> stringResource(R.string.tv_nav_queue)
    NpControlEntry.VISUALIZER -> stringResource(R.string.tv_visualizer)
    NpControlEntry.LYRICS -> stringResource(R.string.tv_lyrics)
    NpControlEntry.ARTIST -> stringResource(R.string.tv_go_to_artist)
    NpControlEntry.ALBUM -> stringResource(R.string.tv_go_to_album)
}

private fun NpControlEntry.settingsIcon(): ImageVector = when (this) {
    NpControlEntry.SHUFFLE -> Icons.Default.Shuffle
    NpControlEntry.PREVIOUS -> Icons.Default.SkipPrevious
    NpControlEntry.PLAY_PAUSE -> Icons.Default.PlayArrow
    NpControlEntry.NEXT -> Icons.Default.SkipNext
    NpControlEntry.REPEAT -> Icons.Default.Repeat
    NpControlEntry.AUTOPLAY -> Icons.Default.AllInclusive
    NpControlEntry.CROSSFADE -> CrossfadeIcon
    NpControlEntry.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NpControlEntry.VISUALIZER -> Icons.Default.GraphicEq
    NpControlEntry.LYRICS -> Icons.Outlined.Lyrics
    NpControlEntry.ARTIST -> Icons.Default.Person
    NpControlEntry.ALBUM -> Icons.Default.Album
}

private const val ROW_FOCUSED_SCALE = 1.02f
private const val HIDDEN_ALPHA = 0.45f
