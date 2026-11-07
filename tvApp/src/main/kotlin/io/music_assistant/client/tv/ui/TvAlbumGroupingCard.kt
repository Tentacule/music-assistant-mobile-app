package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AlbumGrouping
import io.music_assistant.client.data.model.client.title
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.R
import org.koin.compose.koinInject
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText
import org.jetbrains.compose.resources.stringResource as composeStringResource

/**
 * The album rows of the artist page: grouped by type or not, and which types show in which order.
 * Applies at once. Other cannot be hidden, because the albums of a hidden type go to it.
 */
@Composable
fun AlbumGroupingCard() {
    val settings: SettingsRepository = koinInject()
    val grouping by settings.albumGrouping.collectAsStateWithLifecycle()

    SettingsCard(stringResource(R.string.tv_album_groups)) {
        Text(stringResource(R.string.tv_album_groups_hint), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(
                    if (grouping.enabled) R.string.tv_album_groups_enabled else R.string.tv_album_groups_disabled,
                ),
            )
            Switch(
                checked = grouping.enabled,
                onCheckedChange = { settings.setAlbumGrouping(grouping.copy(enabled = it)) },
            )
        }
        if (grouping.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouping.groups.forEach { pref ->
                    // Keyed by group, so that the focus travels with the row when it moves.
                    key(pref.group) {
                        TvOrderRow(
                            icon = null,
                            label = composeStringResource(pref.group.title()),
                            visible = pref.shown || !pref.group.canHide,
                            canHide = pref.group.canHide,
                            onToggle = { settings.setAlbumGrouping(grouping.toggled(pref.group)) },
                            onMoveUp = { settings.setAlbumGrouping(grouping.moved(pref.group, -1)) },
                            onMoveDown = { settings.setAlbumGrouping(grouping.moved(pref.group, 1)) },
                        )
                    }
                }
            }
            TvOutlinedButton(
                onClick = { settings.setAlbumGrouping(AlbumGrouping.Default) },
                modifier = Modifier.padding(top = 8.dp),
            ) { TvText(stringResource(R.string.tv_album_groups_reset)) }
        }
    }
}
