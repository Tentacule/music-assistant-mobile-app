package io.music_assistant.client.ui.compose.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MoveWithinEnabledTest {
    private val items = listOf("A" to true, "B" to true, "C" to true, "D" to false)

    @Test
    fun `moves a row up`() {
        assertEquals(
            listOf("B" to true, "A" to true, "C" to true, "D" to false),
            moveWithinEnabled(items, "B", -1),
        )
    }

    @Test
    fun `moves a row down`() {
        assertEquals(
            listOf("B" to true, "A" to true, "C" to true, "D" to false),
            moveWithinEnabled(items, "A", 1),
        )
    }

    @Test
    fun `the first row stays first`() {
        assertEquals(items, moveWithinEnabled(items, "A", -1))
    }

    @Test
    fun `the last enabled row does not enter the disabled section`() {
        assertEquals(items, moveWithinEnabled(items, "C", 1))
    }

    @Test
    fun `a disabled row does not move`() {
        assertEquals(items, moveWithinEnabled(items, "D", -1))
    }

    @Test
    fun `an unknown row changes nothing`() {
        assertEquals(items, moveWithinEnabled(items, "X", 1))
    }
}
