package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

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

/**
 * Under [LocalDpadNavigation], a focused tile grows a little over its neighbors with a soft light
 * glow around it, the Android TV card focus. Goes before the tile's clip, so the zoom is not cut.
 * A no-op otherwise.
 */
fun Modifier.dpadFocusZoom(shape: Shape): Modifier = composed {
    if (!LocalDpadNavigation.current) return@composed this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TILE_FOCUSED_SCALE else 1f, label = "tileScale")
    val glow by animateFloatAsState(if (focused) 1f else 0f, label = "tileGlow")
    this
        .onFocusChanged { focused = it.hasFocus }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (glow <= 0f) return@drawBehind
            // A soft light halo OUTSIDE the tile: rings fading out, so a transparent tile shows
            // nothing of it inside (an elevation shadow would).
            val step = GLOW_STEP_DP * density
            for (ring in 1..GLOW_RINGS) {
                val grow = ring * step
                val outline = shape.createOutline(
                    Size(size.width + 2 * grow, size.height + 2 * grow),
                    layoutDirection,
                    this,
                )
                translate(-grow, -grow) {
                    drawOutline(
                        outline = outline,
                        color = Color.White.copy(alpha = GLOW_ALPHA * glow * (GLOW_RINGS - ring + 1) / GLOW_RINGS),
                        style = Stroke(width = step),
                    )
                }
            }
        }
}

private const val FOCUSED_SCALE = 1.06f
private const val TILE_FOCUSED_SCALE = 1.05f
private const val GLOW_RINGS = 6
private const val GLOW_STEP_DP = 2f
private const val GLOW_ALPHA = 0.22f
private const val OUTLINE_DP = 3f
