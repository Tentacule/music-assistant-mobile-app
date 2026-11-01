package io.music_assistant.client.tv.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * The focus highlight of the TV: a tinted fill and a thick outline on the focused element, visible
 * from the couch. Provided as `LocalIndication`, so every `clickable` of the reused phone screens
 * gets it instead of the phone ripple, whose focus state is too faint on a TV.
 */
class TvFocusIndication(private val border: Color, private val fill: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusNode(interactionSource, border, fill)

    override fun equals(other: Any?): Boolean =
        other is TvFocusIndication && other.border == border && other.fill == fill

    override fun hashCode(): Int = 31 * border.hashCode() + fill.hashCode()
}

private class TvFocusNode(
    private val interactionSource: InteractionSource,
    private val border: Color,
    private val fill: Color,
) : Modifier.Node(), DrawModifierNode {
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<FocusInteraction.Focus>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                }
                val nowFocused = focuses.isNotEmpty()
                if (nowFocused != focused) {
                    focused = nowFocused
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            val radius = CornerRadius(CORNER_DP * density, CORNER_DP * density)
            drawRoundRect(color = border, cornerRadius = radius, style = Stroke(width = STROKE_DP * density))
        }
    }

    private companion object {
        const val CORNER_DP = 8f
        const val STROKE_DP = 4f
    }
}
