@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.common.dpadFocusOutline
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.discover_row_hidden
import musicassistantclient.composeapp.generated.resources.discover_row_move_down
import musicassistantclient.composeapp.generated.resources.discover_row_move_up
import musicassistantclient.composeapp.generated.resources.discover_row_shown
import org.jetbrains.compose.resources.stringResource

/**
 * One home row in edit mode under a D-pad. The phone covers the row's tiles with a layer that
 * stops touch only, so a remote would still land on the tiles, and its drag handle has no key
 * equivalent. Here the row is one plate, without tiles: OK shows or hides it, and two buttons
 * move it.
 *
 * The move buttons stay enabled at the ends of the list: a disabled button loses the focus. A
 * hidden row has none, because only the shown rows have an order.
 */
@Composable
internal fun HomeRowDpadEditor(
    title: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onFocused: () -> Unit,
) {
    val plateShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
            // Right at the end of the row stays there, as in a row of tiles.
            .focusProperties {
                onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
            }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(plateShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val contentAlpha = if (enabled) 1f else HIDDEN_ALPHA
            Text(
                text = title,
                modifier = Modifier.weight(1f).alpha(contentAlpha),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = if (enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = stringResource(
                    if (enabled) Res.string.discover_row_shown else Res.string.discover_row_hidden,
                ),
                modifier = Modifier.size(24.dp).alpha(contentAlpha),
            )
        }
        MoveButton(Icons.Default.ArrowUpward, stringResource(Res.string.discover_row_move_up), enabled, onMoveUp)
        MoveButton(Icons.Default.ArrowDownward, stringResource(Res.string.discover_row_move_down), enabled, onMoveDown)
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.dpadFocusOutline().alpha(if (enabled) 1f else HIDDEN_ALPHA),
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

private const val HIDDEN_ALPHA = 0.45f
