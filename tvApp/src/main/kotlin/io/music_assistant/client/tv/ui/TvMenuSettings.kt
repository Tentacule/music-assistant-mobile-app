package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvMenuEntry
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.defaultTvMenu
import io.music_assistant.client.tv.moved
import io.music_assistant.client.tv.toggled
import io.music_assistant.client.ui.compose.library.icon
import io.music_assistant.client.ui.compose.library.stringResource
import org.koin.compose.koinInject
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText
import org.jetbrains.compose.resources.stringResource as composeStringResource

/** Whether this server offers the entry; AI Radio depends on a server plugin. */
fun TvMenuEntry.isOffered(aiRadioAvailable: Boolean): Boolean = this != TvMenuEntry.AI_RADIO || aiRadioAvailable

fun TvMenuEntry.icon(): ImageVector = when (this) {
    TvMenuEntry.HOME -> Icons.Default.Home
    TvMenuEntry.NOW_PLAYING -> Icons.Default.MusicNote
    TvMenuEntry.SEARCH -> Icons.Default.Search
    TvMenuEntry.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    TvMenuEntry.PLAYER -> Icons.Default.Speaker
    else -> category?.icon() ?: Icons.Default.MusicNote
}

@Composable
fun TvMenuEntry.label(): String = when (this) {
    TvMenuEntry.HOME -> stringResource(R.string.tv_nav_home)
    TvMenuEntry.NOW_PLAYING -> stringResource(R.string.tv_nav_now_playing)
    TvMenuEntry.SEARCH -> stringResource(R.string.tv_nav_search)
    TvMenuEntry.QUEUE -> stringResource(R.string.tv_nav_queue)
    TvMenuEntry.PLAYER -> stringResource(R.string.tv_nav_player)
    else -> category?.let { composeStringResource(it.stringResource()) }.orEmpty()
}

/**
 * The menu tab: which entries the menu shows and their order. OK on an entry shows or hides it and
 * the arrows on its row move it, so all of it works from the remote. Applies at once.
 */
@Composable
fun TvMenuSettingsTab() {
    val prefs: TvPreferences = koinInject()
    val dataSource: MainDataSource = koinInject()
    val menu by prefs.menu.collectAsStateWithLifecycle()
    val aiRadioAvailable by dataSource.aiRadioAvailable.collectAsStateWithLifecycle()
    val isListed: (TvMenuEntry) -> Boolean = { it.isOffered(aiRadioAvailable) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The Material text: it takes the color of the surface, and the TV text needs a TV surface.
        Text(
            stringResource(R.string.tv_menu_hint),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        menu.filter { isListed(it.entry) }.forEach { item ->
            // Keyed by entry, so that the focus travels with the row when it moves.
            key(item.entry) {
                TvOrderRow(
                    icon = item.entry.icon(),
                    label = item.entry.label(),
                    visible = item.visible,
                    onToggle = { prefs.setMenu(menu.toggled(item.entry)) },
                    onMoveUp = { prefs.setMenu(menu.moved(item.entry, -1, isListed)) },
                    onMoveDown = { prefs.setMenu(menu.moved(item.entry, 1, isListed)) },
                )
            }
        }
        TvOutlinedButton(
            onClick = { prefs.setMenu(defaultTvMenu) },
            modifier = Modifier.padding(top = 16.dp),
        ) { TvText(stringResource(R.string.tv_menu_reset)) }
    }
}

/**
 * A row of an ordered list that the remote edits: OK on the row shows or hides it, unless
 * [canHide] is false, and the arrows beside it move it.
 */
@Composable
fun TvOrderRow(
    icon: ImageVector?,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canHide: Boolean = true,
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
                val contentAlpha = if (visible) 1f else HIDDEN_ALPHA
                icon?.let {
                    TvIcon(it, contentDescription = null, modifier = Modifier.size(24.dp).alpha(contentAlpha))
                }
                TvText(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).alpha(contentAlpha),
                )
                if (canHide) {
                    TvIcon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(
                            if (visible) R.string.tv_menu_shown else R.string.tv_menu_hidden,
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        TvIconButton(Icons.Default.ArrowUpward, stringResource(R.string.tv_menu_move_up), onMoveUp)
        TvIconButton(Icons.Default.ArrowDownward, stringResource(R.string.tv_menu_move_down), onMoveDown)
    }
}

private const val ROW_FOCUSED_SCALE = 1.02f
private const val HIDDEN_ALPHA = 0.45f
