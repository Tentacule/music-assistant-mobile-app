package io.music_assistant.client.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The Android TV focus, for the TV's own controls: the focused element turns light with dark
 * content and grows a little, as in the system UI and the Google TV apps.
 */
@Composable
fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    enabled: Boolean = true,
    focusedScale: Float = FOCUSED_SCALE,
    // The unfocused background; transparent by default, the selected color when [selected].
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor ?: if (selected) colors.secondaryContainer else Color.Transparent,
            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
            focusedContainerColor = colors.inverseSurface,
            focusedContentColor = colors.inverseOnSurface,
            pressedContainerColor = colors.inverseSurface,
            pressedContentColor = colors.inverseOnSurface,
        ),
        content = content,
    )
}

/**
 * A round icon control of the TV. [tint] colors the icon while unfocused (a mode that is on, in
 * the accent color); focused, it follows the light focus like every other control.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        scale = IconButtonDefaults.scale(focusedScale = ICON_FOCUSED_SCALE),
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = tint ?: colors.onSurface,
            focusedContainerColor = colors.inverseSurface,
            focusedContentColor = colors.inverseOnSurface,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = LocalContentColor.current)
    }
}

/**
 * Keeps Left and Right inside a row of controls. Otherwise, at the end of a row, the focus jumps
 * diagonally to any control further that way, in another row, and the next OK changes that one.
 */
fun Modifier.tvHorizontalFocusRow(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) {
            cancelFocusChange()
        }
    }
}.focusGroup()

/** One choice of a settings row. The active choice carries a check, to read from across the room. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        // The light focus of every other control, also on the active choice.
        colors = FilterChipDefaults.colors(
            focusedContainerColor = colors.inverseSurface,
            focusedContentColor = colors.inverseOnSurface,
            pressedContainerColor = colors.inverseSurface,
            pressedContentColor = colors.inverseOnSurface,
            focusedSelectedContainerColor = colors.inverseSurface,
            focusedSelectedContentColor = colors.inverseOnSurface,
            pressedSelectedContainerColor = colors.inverseSurface,
            pressedSelectedContentColor = colors.inverseOnSurface,
        ),
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    ) { Text(label) }
}

private const val FOCUSED_SCALE = 1.05f
private const val ICON_FOCUSED_SCALE = 1.15f
