package io.music_assistant.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DpadControlColorsTest {
    private val colors = listOf(
        Color.Black, Color.White, Color.Gray, Color.Red, Color.Green, Color.Blue,
        Color.Yellow, Color.Cyan, Color.Magenta,
        Color(0xFFB0AE8C), // the beige of a real album
        Color(0xFFE8E08A), // the pale yellow of a real album
        Color(0xFF1592C8), // the TV primary
        Color(0xFF808000), Color(0xFF3A1F5C), Color(0xFFE8E8E8),
    )

    private fun contrast(a: Color, b: Color): Float {
        val (la, lb) = a.luminance() to b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test
    fun `the content is legible on any resting fill`() {
        colors.forEach { color ->
            val fill = color.dpadRestingFill()
            assertTrue(contrast(fill, fill.bestContrastContent()) >= 4.5f, "Content too faint on $color")
        }
    }

    @Test
    fun `the white focus stands out from any resting fill`() {
        colors.forEach { color ->
            assertTrue(contrast(color.dpadRestingFill(), Color.White) >= 2f, "Focus too faint on $color")
        }
    }

    @Test
    fun `a color dark enough is kept`() {
        assertEquals(Color.Blue, Color.Blue.dpadRestingFill())
    }

    @Test
    fun `the fill is opaque, even from a translucent color`() {
        assertEquals(1f, Color.Red.copy(alpha = 0.4f).dpadRestingFill().alpha)
    }
}
