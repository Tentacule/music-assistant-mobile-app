package io.music_assistant.client.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

val MAX_DIALOG_HEIGHT = 400.dp
const val INACTIVE_ALPHA = 0.4f
private const val LUMINANCE_MIDPOINT = 0.5f

fun Modifier.alphaOn(enabled: Boolean) = alpha(if (enabled) 1f else INACTIVE_ALPHA)
fun Color.alphaOn(enabled: Boolean) = copy(alpha = if (enabled) 1f else INACTIVE_ALPHA)
fun Color.inactive() = alphaOn(false)

/** Black or white, whichever stays legible on top of this (opaque) color. */
fun Color.contentColorByLuminance(): Color =
    if (luminance() > LUMINANCE_MIDPOINT) Color.Black else Color.White

/**
 * The fill of a D-pad control without the focus: this color, opaque, and darkened if it is so light
 * that the white fill of the focused control would not stand out from it.
 */
fun Color.dpadRestingFill(): Color {
    var fill = copy(alpha = 1f)
    while (fill.luminance() > DPAD_MAX_RESTING_LUMINANCE) {
        fill = lerp(fill, Color.Black, DPAD_DARKEN_STEP)
    }
    return fill
}

/** Black or white, whichever has the higher contrast ratio on this (opaque) color. */
fun Color.bestContrastContent(): Color =
    if (luminance() > EQUAL_CONTRAST_LUMINANCE) Color.Black else Color.White

private const val DPAD_MAX_RESTING_LUMINANCE = 0.45f
private const val DPAD_DARKEN_STEP = 0.1f

// At this luminance, black and white have the same contrast ratio.
private const val EQUAL_CONTRAST_LUMINANCE = 0.179f

/**
 * Cancels the given [padding] on left/right/top so a child (e.g. a colored header) drawn inside a
 * padded parent — like a [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] item with
 * `contentPadding` — bleeds to the parent's real edges instead of stopping at the inset. Bottom is
 * intentionally left untouched. Pass the SAME padding the parent applied so the child re-aligns to
 * the true top-left.
 */
fun Modifier.fullBleed(padding: PaddingValues): Modifier = layout { measurable, constraints ->
    val left = padding.calculateLeftPadding(layoutDirection).roundToPx()
    val right = padding.calculateRightPadding(layoutDirection).roundToPx()
    val top = padding.calculateTopPadding().roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + left + right,
            maxWidth = constraints.maxWidth + left + right,
        ),
    )
    // Report the ORIGINAL (un-expanded) width — not the widened placeable's. Reporting the wider
    // size makes a LazyGrid full-span item overflow its slot on the trailing edge, which the grid
    // then clips (leaving an un-bled stripe on the right); the leading edge survives only because
    // the negative placement leaks into the left padding. Keeping the reported width at the slot
    // size lets the wider placeable draw past BOTH horizontal edges symmetrically.
    layout(
        (placeable.width - left - right).coerceAtLeast(0),
        (placeable.height - top).coerceAtLeast(0),
    ) {
        placeable.place(-left, -top)
    }
}

const val HUNDRED = 100
const val TEN = 10
const val ONE = 1
