package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface

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
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
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

private const val FOCUSED_SCALE = 1.05f
private const val ICON_FOCUSED_SCALE = 1.15f
