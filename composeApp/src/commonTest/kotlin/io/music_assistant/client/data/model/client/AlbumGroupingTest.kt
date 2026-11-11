package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.select
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
    private val albums = listOf(live, lp1, unknown, single, lp2)

    @Test
    fun groupsInDisplayOrderAndKeepsTheServerOrderInAGroup() {
        assertEquals(
            listOf(
                AlbumGroup.ALBUMS to listOf(lp1, lp2),
                AlbumGroup.SINGLES to listOf(single),
                AlbumGroup.LIVE to listOf(live),
                AlbumGroup.OTHER to listOf(unknown),
            ),
            AlbumGrouping.Default.group(albums),
        )
    }

    @Test
    fun aHiddenGroupGoesToOther() {
        val grouping = AlbumGrouping.Default.toggled(AlbumGroup.SINGLES)

        assertEquals(
            listOf(
                AlbumGroup.ALBUMS to listOf(lp1, lp2),
                AlbumGroup.LIVE to listOf(live),
                AlbumGroup.OTHER to listOf(unknown, single),
            ),
            grouping.group(albums),
        )
    }

    @Test
    fun otherCannotBeHidden() {
        assertEquals(AlbumGrouping.Default, AlbumGrouping.Default.toggled(AlbumGroup.OTHER))
    }

    @Test
    fun aMovedGroupChangesTheRowOrder() {
        val grouping = AlbumGrouping.Default.moved(AlbumGroup.LIVE, -1).moved(AlbumGroup.LIVE, -1)

        assertEquals(
            listOf(AlbumGroup.ALBUMS, AlbumGroup.LIVE, AlbumGroup.EPS, AlbumGroup.SINGLES),
            grouping.order.take(4),
        )
        assertEquals(AlbumGrouping.Default, AlbumGrouping.Default.moved(AlbumGroup.ALBUMS, -1))
    }

    @Test
    fun theStoredFormReadsBack() {
        val grouping = AlbumGrouping.Default.toggled(AlbumGroup.EPS).moved(AlbumGroup.OTHER, -1)

        assertEquals(grouping, AlbumGrouping.parse(enabled = true, storedGroups = grouping.encodeGroups()))
    }

    @Test
    fun anUnknownGroupIsDroppedAndAMissingGroupComesLastAndShown() {
        val grouping = AlbumGrouping.parse(enabled = false, storedGroups = "LIVE:0,GONE:1,LIVE:1,ALBUMS:1")

        assertEquals(false, grouping.enabled)
        assertEquals(
            listOf(AlbumGroupPref(AlbumGroup.LIVE, shown = false), AlbumGroupPref(AlbumGroup.ALBUMS)) +
                listOf(
                    AlbumGroup.EPS,
                    AlbumGroup.SINGLES,
                    AlbumGroup.COMPILATIONS,
                    AlbumGroup.SOUNDTRACKS,
                    AlbumGroup.OTHER,
                ).map { AlbumGroupPref(it) },
            grouping.groups,
        )
    }

    @Test
    fun aGroupListShowsTheAlbumsOfItsGroupOnly() {
        val list = ItemList.ArtistLibrary("artist").withAlbumGroup(AlbumGroup.ALBUMS)

        assertEquals(listOf(lp1, lp2), list.select(albums, AlbumGrouping.Default))
        // Without a group, the list keeps its releases: every album but the singles and EPs.
        assertEquals(listOf(live, lp1, unknown, lp2), ItemList.ArtistLibrary("artist").select(albums))
    }

    @Test
    fun aHiddenGroupMovesItsAlbumsToOther() {
        val grouping = AlbumGrouping.Default.toggled(AlbumGroup.LIVE)
        val other = ItemList.ArtistLibrary("artist").withAlbumGroup(AlbumGroup.OTHER)

        assertEquals(listOf(live, unknown), other.select(albums, grouping))
    }
}
