package io.music_assistant.client.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvMenuConfigTest {
    @Test
    fun noStoredMenuGivesTheDefault() {
        assertEquals(defaultTvMenu, parseTvMenu(null))
        assertEquals(defaultTvMenu, parseTvMenu(""))
    }

    @Test
    fun encodingRoundTrips() {
        val menu = defaultTvMenu.moved(TvMenuEntry.SEARCH, -1).toggled(TvMenuEntry.GENRES)
        assertEquals(menu, parseTvMenu(menu.encodeTvMenu()))
    }

    @Test
    fun unknownAndRepeatedEntriesAreDroppedAndMissingOnesComeLastAndVisible() {
        val menu = parseTvMenu("PLAYER:0,GONE:1,PLAYER:1,HOME:1")
        assertEquals(TvMenuEntry.PLAYER, menu[0].entry)
        assertFalse(menu[0].visible)
        assertEquals(TvMenuEntry.HOME, menu[1].entry)
        assertEquals(TvMenuEntry.entries.size, menu.size)
        assertEquals(TvMenuEntry.NOW_PLAYING, menu[2].entry)
        assertEquals(true, menu[2].visible)
    }

    @Test
    fun moveStopsAtBothEnds() {
        assertEquals(defaultTvMenu, defaultTvMenu.moved(TvMenuEntry.HOME, -1))
        assertEquals(defaultTvMenu, defaultTvMenu.moved(TvMenuEntry.PLAYER, 1))
    }

    @Test
    fun moveSkipsAnEntryThatIsNotListed() {
        val moved = defaultTvMenu.moved(TvMenuEntry.PLAYER, -1) { it != TvMenuEntry.AI_RADIO }
        val order = moved.map { it.entry }
        assertEquals(order.indexOf(TvMenuEntry.BROWSE), order.lastIndex)
        assertEquals(order.indexOf(TvMenuEntry.PLAYER), order.indexOf(TvMenuEntry.AI_RADIO) - 1)
    }
}
