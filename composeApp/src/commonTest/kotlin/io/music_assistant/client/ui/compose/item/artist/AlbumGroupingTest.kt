package io.music_assistant.client.ui.compose.item.artist

import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.shownItems
import io.music_assistant.client.ui.compose.item.withAlbumGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumGroupingTest {
    private fun album(id: String, type: AlbumType?) = Album(
        itemId = id,
        provider = "library",
        name = id,
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
        version = null,
        year = null,
        artists = emptyList(),
        albumType = type,
    )

    private val live = album("live", AlbumType.LIVE)
    private val lp1 = album("lp1", AlbumType.ALBUM)
    private val unknown = album("unknown", null)
    private val single = album("single", AlbumType.SINGLE)
    private val lp2 = album("lp2", AlbumType.ALBUM)

    @Test
    fun groupsInDisplayOrderAndKeepsTheServerOrderInAGroup() {
        val groups = AlbumGrouping.Default.group(listOf(live, lp1, unknown, single, lp2))

        assertEquals(
            listOf(
                AlbumGroup.ALBUMS to listOf(lp1, lp2),
                AlbumGroup.SINGLES to listOf(single),
                AlbumGroup.LIVE to listOf(live),
                AlbumGroup.OTHER to listOf(unknown),
            ),
            groups,
        )
    }

    @Test
    fun aTypeWithoutItsGroupGoesToOtherAndOtherComesLast() {
        val grouping = AlbumGrouping(enabled = true, order = listOf(AlbumGroup.LIVE, AlbumGroup.ALBUMS))

        assertEquals(
            listOf(
                AlbumGroup.LIVE to listOf(live),
                AlbumGroup.ALBUMS to listOf(lp1, lp2),
                AlbumGroup.OTHER to listOf(unknown, single),
            ),
            grouping.group(listOf(live, lp1, unknown, single, lp2)),
        )
    }

    @Test
    fun aGroupListShowsTheAlbumsOfItsGroupOnly() {
        val list = ItemList.ArtistLibrary("artist").withAlbumGroup(AlbumGroup.ALBUMS)

        assertEquals(listOf(lp1, lp2), list.shownItems(listOf(live, lp1, unknown, single, lp2)))
        assertEquals(
            listOf(live, lp1),
            ItemList.ArtistLibrary("artist").shownItems(listOf(live, lp1)),
        )
    }
}
