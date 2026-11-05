package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlin.math.roundToInt
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * The speakers that play along with [leaderId], as one list of rows to tick with OK: a ticked row
 * plays in sync with the leader. Volumes change by steps with − and +, to the right of a row; the
 * group volume moves every member at once.
 *
 * This TV is left out: its player is tuned to play only for itself (see TvApplication), and the
 * speakers of a group would wait for it. [players] is the live list, so the rows follow the server.
 */
@Composable
fun TvGroupDialog(
    leaderId: String,
    players: List<PlayerData>,
    canLeaveGroup: Boolean,
    onAction: (playerId: String, action: PlayerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val leader = players.firstOrNull { it.playerId == leaderId } ?: run {
        // The leader went away (unavailable, or removed on the server): nothing left to show.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val localId = players.firstOrNull { it.isLocal }?.playerId
    // Members first, each part in name order, so the rows keep their place while the user ticks.
    val members = leader.childrenBinds
        .filter { it.id != localId }
        .sortedWith(compareByDescending<PlayerData.ChildBind> { it.isBound }.thenBy { it.name.lowercase() })
    val firstFocus = remember { FocusRequester() }

    // The platform width is too narrow for a name next to its volume steps.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(DIALOG_WIDTH)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvText(
                stringResource(R.string.tv_group_title, leader.player.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TvText(
                stringResource(R.string.tv_group_hint, leader.player.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (leader.player.isGrouped) {
                VolumeLine(
                    label = stringResource(R.string.tv_group_volume),
                    volume = leader.player.groupVolume,
                    onDown = { onAction(leader.playerId, PlayerAction.GroupVolumeDown) },
                    onUp = { onAction(leader.playerId, PlayerAction.GroupVolumeUp) },
                )
            }
            // The leader is always in its own group: no box to untick, its own volume only.
            VolumeLine(
                label = stringResource(R.string.tv_group_leader, leader.player.name),
                volume = leader.player.volumeLevel.takeIf { leader.player.canSetVolume },
                onDown = { onAction(leader.playerId, PlayerAction.VolumeDown) },
                onUp = { onAction(leader.playerId, PlayerAction.VolumeUp) },
            )

            if (members.isEmpty()) {
                TvText(
                    stringResource(R.string.tv_group_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            members.forEachIndexed { index, member ->
                MemberRow(
                    member = member,
                    // The first focus goes to a speaker, not to a volume step that OK would apply.
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onToggle = {
                        val ids = listOf(member.id)
                        onAction(
                            member.parentId,
                            PlayerAction.GroupManage(
                                toAdd = ids.takeIf { !member.isBound },
                                toRemove = ids.takeIf { member.isBound },
                            ),
                        )
                    },
                    onDown = { onAction(member.id, PlayerAction.VolumeDown) },
                    onUp = { onAction(member.id, PlayerAction.VolumeUp) },
                )
            }

            // The server hands the queue to a remaining member; the TV then follows that player.
            if (canLeaveGroup && leader.player.canLeaveOwnGroup) {
                TvOutlinedButton(
                    onClick = {
                        onAction(leader.playerId, PlayerAction.LeaveGroup)
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TvText(stringResource(R.string.tv_group_leave, leader.player.name))
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun MemberRow(
    member: PlayerData.ChildBind,
    onToggle: () -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tvHorizontalFocusRow(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvFocusSurface(
            onClick = onToggle,
            // A member that the server set up as permanent cannot be taken out here.
            enabled = member.isManageable,
            selected = member.isBound,
            focusedScale = ROW_FOCUSED_SCALE,
            modifier = Modifier.weight(1f).then(modifier),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val icon = when {
                    !member.isManageable -> Icons.Default.Lock
                    member.isBound -> Icons.Default.CheckBox
                    else -> Icons.Default.CheckBoxOutlineBlank
                }
                TvIcon(icon, contentDescription = null)
                TvText(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Only a member that plays has a volume worth changing from here.
        val volume = member.volume?.takeIf { member.isBound && member.volumeSliderAccessible }
        if (volume != null) {
            VolumeSteps(volume = volume, onDown = onDown, onUp = onUp)
        } else {
            // Keeps the names of all rows at the same width.
            androidx.compose.foundation.layout.Spacer(Modifier.width(VOLUME_STEPS_WIDTH))
        }
    }
}

@Composable
private fun VolumeLine(
    label: String,
    volume: Float?,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tvHorizontalFocusRow().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvText(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        volume?.let { VolumeSteps(it, onDown, onUp) }
    }
}

@Composable
private fun VolumeSteps(
    volume: Float,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        modifier = Modifier.width(VOLUME_STEPS_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvOutlinedButton(onClick = onDown) {
            TvIcon(Icons.Default.Remove, contentDescription = stringResource(R.string.tv_group_volume_down))
        }
        TvText(
            "${volume.roundToInt()} %",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TvOutlinedButton(onClick = onUp) {
            TvIcon(Icons.Default.Add, contentDescription = stringResource(R.string.tv_group_volume_up))
        }
    }
}

private val DIALOG_WIDTH = 760.dp
private val VOLUME_STEPS_WIDTH = 220.dp
private const val ROW_FOCUSED_SCALE = 1.02f

/** Whether [player] can lead a group from this TV: a speaker with at least one possible member. */
fun canGroupFrom(player: PlayerData?, players: List<PlayerData>): Boolean {
    if (player == null || player.isLocal || player.player.isGroup) return false
    val localId = players.firstOrNull { it.isLocal }?.playerId
    return player.childrenBinds.any { it.id != localId }
}
