package io.music_assistant.client.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/** One entry of the TV menu. [group] separates the menu sections with a divider. */
data class TvRailItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val group: Int,
    // Shown instead of the icon when set: the artwork of what plays, for the Now playing entry.
    val artworkUrl: String? = null,
    val onClick: () -> Unit,
)

val TvRailCollapsedWidth: Dp = 80.dp
private val TvRailExpandedWidth = 280.dp

/**
 * The menu of the phone app, collapsible for the remote: icons only while the focus is in the
 * content, expanded with the labels as soon as the focus enters it (D-pad Left at the edge of the
 * content). It is drawn over the content, which keeps the collapsed width as its start padding, so
 * expanding the menu never moves the content.
 */
@Composable
fun TvNavigationRail(
    items: List<TvRailItem>,
    selectedKey: String?,
    modifier: Modifier = Modifier,
    // Asked when the focus enters the menu: false when the user did not move it there, and the
    // menu then stays collapsed while the caller sends the focus back to the content.
    acceptsFocus: () -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) TvRailExpandedWidth else TvRailCollapsedWidth, label = "railWidth")
    val background = MaterialTheme.colorScheme.surfaceContainer
        .copy(alpha = if (expanded) 1f else COLLAPSED_ALPHA)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(background)
            .onFocusChanged {
                hasFocus = it.hasFocus
                expanded = it.hasFocus && (expanded || acceptsFocus())
            }
            // A page with nothing to focus leaves the focus here, refused and collapsed: the first
            // key press shows the labels of the entries that the remote is now moving through.
            .onPreviewKeyEvent { event ->
                if (hasFocus && !expanded && event.type == KeyEventType.KeyDown) expanded = true
                false
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0 && items[index - 1].group != item.group) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            RailEntry(item = item, selected = item.key == selectedKey, expanded = expanded)
        }
    }
}

@Composable
private fun RailEntry(item: TvRailItem, selected: Boolean, expanded: Boolean) {
    TvFocusSurface(
        onClick = item.onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.artworkUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = item.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                )
            } ?: TvIcon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Row {
                    Spacer(Modifier.width(16.dp))
                    TvText(
                        text = item.label,
                        style = TvMaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val COLLAPSED_ALPHA = 0.85f
