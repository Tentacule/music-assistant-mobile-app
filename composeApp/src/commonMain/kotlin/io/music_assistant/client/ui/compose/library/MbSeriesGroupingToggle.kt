package io.music_assistant.client.ui.compose.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import io.music_assistant.client.settings.MbSeriesGrouping
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_mb_series_grouping
import org.jetbrains.compose.resources.stringResource

/** Checked (tinted) while MB series are grouped. */
@Composable
fun MbSeriesGroupingToggle(grouping: MbSeriesGrouping, onToggle: () -> Unit) {
    IconToggleButton(
        checked = grouping == MbSeriesGrouping.GROUPED,
        onCheckedChange = { onToggle() },
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = stringResource(Res.string.cd_toggle_mb_series_grouping),
        )
    }
}
