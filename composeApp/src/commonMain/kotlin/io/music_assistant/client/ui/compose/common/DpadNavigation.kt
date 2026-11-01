package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/**
 * True when the app is driven by a D-pad (Android TV remote) rather than touch. The TV app
 * provides it; the phone keeps the default, so the hooks below change nothing there.
 */
val LocalDpadNavigation = staticCompositionLocalOf { false }

/**
 * Under [LocalDpadNavigation], a focused control gets a white outline and grows a little: the
 * Material state layer alone is too faint on a colored button seen from a couch. A no-op otherwise.
 */
fun Modifier.dpadFocusOutline(shape: Shape = CircleShape): Modifier = composed {
    if (!LocalDpadNavigation.current) return@composed this
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            val scale = if (focused) FOCUSED_SCALE else 1f
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = Color.White,
                    style = Stroke(width = OUTLINE_DP * density),
                )
            }
        }
}

private const val FOCUSED_SCALE = 1.06f
private const val OUTLINE_DP = 3f
