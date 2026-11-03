package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonDefaults.LeadingButton
import androidx.compose.material3.SplitButtonDefaults.TrailingButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.itemKind
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.PlayMediaSupport
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.ui.bestContrastContent
import io.music_assistant.client.ui.compose.common.DpadControlSpacing
import io.music_assistant.client.ui.compose.common.DpadFocusedContent
import io.music_assistant.client.ui.compose.common.DpadFocusedFill
import io.music_assistant.client.ui.compose.common.LocalDpadNavigation
import io.music_assistant.client.ui.compose.common.NoDpadStateLayer
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.items.DefaultClickActionsDialog
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.LocalClickActionConfig
import io.music_assistant.client.ui.compose.common.items.icon
import io.music_assistant.client.ui.compose.common.items.resolvePlayButtonActions
import io.music_assistant.client.ui.compose.common.items.title
import io.music_assistant.client.ui.compose.common.items.toOverflowOption
import io.music_assistant.client.ui.compose.common.items.toPlayMode
import io.music_assistant.client.ui.contentColorByLuminance
import io.music_assistant.client.ui.dpadRestingFill
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_play_options
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ItemPlayButton(
    item: AppMediaItem,
    onPlayClick: (PlayMode) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (!item.isPlayable) return

    // Art-derived control tint as the button fill; black/white content per its luminance.
    val dpad = LocalDpadNavigation.current
    val fill = if (dpad) tint.dpadRestingFill() else tint
    val onFill = if (dpad) fill.bestContrastContent() else fill.contentColorByLuminance()
    var leadingFocused by remember { mutableStateOf(false) }
    var trailingFocused by remember { mutableStateOf(false) }
    val colorsFor: @Composable (Boolean) -> ButtonColors = { focused ->
        ButtonDefaults.buttonColors(
            containerColor = if (focused) DpadFocusedFill else fill,
            contentColor = if (focused) DpadFocusedContent else onFill,
        )
    }

    // The detail header is wrapped in ProvideClickActions(DETAIL), so the config resolves
    // this item's DETAIL default. effectiveActionFor is non-null here (item is playable).
    val clickActionConfig = LocalClickActionConfig.current
    val effective = clickActionConfig.effectiveActionFor(item)
        ?: ItemAction.Play(QueueOption.REPLACE)
    val kind = item.itemKind()

    var showCustomizeDialog by remember { mutableStateOf(false) }

    val runPlayAction: (ItemAction) -> Unit = { action ->
        action.toPlayMode()?.let(onPlayClick)
    }

    // D-pad (Android TV): entering the button lands on the play action, and not on the options
    // chevron next to it, except moving left from the controls after it: the chevron is the next
    // control then. The page opens with the focus on the play action (once per item).
    val playFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable(item.itemId) { mutableStateOf(false) }
    if (dpad) {
        LaunchedEffect(item.itemId) {
            if (!initialFocusDone) {
                initialFocusDone = runCatching { playFocus.requestFocus() }.getOrDefault(false)
            }
        }
    }

    SplitButtonLayout(
        modifier = if (dpad) {
            modifier
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection != FocusDirection.Left) playFocus.requestFocus()
                    }
                }
                .focusGroup()
        } else {
            modifier
        },
        spacing = if (dpad) DpadControlSpacing else SplitButtonDefaults.Spacing,
        leadingButton = {
            val label = stringResource(effective.title())
            val leadingModifier = Modifier
                .semantics { contentDescription = label }
                .focusRequester(playFocus)
                .onFocusChanged { leadingFocused = dpad && it.hasFocus }
            val leadingContent: @Composable RowScope.() -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = effective.icon(clickActionConfig.context), contentDescription = null)
                    Text(modifier = Modifier.padding(start = 8.dp), text = label)
                }
            }
            NoDpadStateLayer {
                if (dpad) {
                    LeadingButton(
                        modifier = leadingModifier,
                        onClick = { runPlayAction(effective) },
                        colors = colorsFor(leadingFocused),
                        shapes = DpadPillShapes,
                        content = leadingContent,
                    )
                } else {
                    LeadingButton(
                        modifier = leadingModifier,
                        onClick = { runPlayAction(effective) },
                        colors = colorsFor(false),
                        content = leadingContent,
                    )
                }
            }
        },
        trailingButton = {
            PlayOverflow(
                item = item,
                default = effective,
                // Customize opens the per-kind table — meaningless for kindless items (e.g. Genre).
                onCustomize = if (kind != null) ({ showCustomizeDialog = true }) else null,
                onPlayAction = runPlayAction,
                context = clickActionConfig.context,
                playMediaSupport = clickActionConfig.playMediaSupport,
            ) { onClick ->
                val trailingContent: @Composable RowScope.() -> Unit = {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(Res.string.cd_play_options),
                    )
                }
                NoDpadStateLayer {
                    if (dpad) {
                        TrailingButton(
                            onClick = onClick,
                            colors = colorsFor(trailingFocused),
                            modifier = Modifier.onFocusChanged { trailingFocused = it.hasFocus },
                            shapes = DpadPillShapes,
                            content = trailingContent,
                        )
                    } else {
                        TrailingButton(onClick = onClick, colors = colorsFor(false), content = trailingContent)
                    }
                }
            }
        },
    )

    if (showCustomizeDialog && kind != null) {
        DefaultClickActionsDialog(
            itemKind = kind,
            onDismiss = { showCustomizeDialog = false },
        )
    }
}

// Under a D-pad the two halves stand apart, so each one is a whole pill.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DpadPillShapes = SplitButtonShapes(
    shape = CircleShape,
    pressedShape = CircleShape,
    checkedShape = CircleShape,
)

@Composable
private fun PlayOverflow(
    item: AppMediaItem,
    default: ItemAction,
    onCustomize: (() -> Unit)?,
    onPlayAction: (ItemAction) -> Unit,
    context: ClickContext?,
    playMediaSupport: PlayMediaSupport,
    button: @Composable (() -> Unit) -> Unit,
) {
    val actions = resolvePlayButtonActions(item, default, playMediaSupport, onCustomize != null)
    val options = actions.map { action ->
        action.toOverflowOption(context) {
            if (it == ItemAction.Customize) onCustomize?.invoke() else onPlayAction(it)
        }
    }
    OverflowMenuButton(
        options = options,
        buttonContent = button,
    )
}
