package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumVersionsTest {
    private fun artist(name: String) = Artist(
        itemId = name,
        provider = "library",
        name = name,
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
    )

    private fun album(
        id: String,
        name: String = "Abbey Road",
        releaseGroupId: String? = ABBEY_ROAD,
        version: String? = null,
        year: Int? = null,
        barcode: String? = null,
        metadata: Metadata? = null,
    ) = Album(
        itemId = id,
        provider = "library",
        name = name,
        providerMappings = null,
        metadata = metadata,
        favorite = null,
        uri = null,
        images = emptyMap(),
        version = version,
        year = year,
        artists = listOf(artist("The Beatles")),
        barcode = barcode,
        releaseGroupId = releaseGroupId,
    )

    @Test
    fun versionsCollapseAtTheFirstPositionIntoTheOriginal() {
        val deluxe = album("deluxe", version = "Deluxe", year = 2019)
        val help = album("help", name = "Help!", releaseGroupId = "help-group")
        val original = album("original", year = 1969)
        val remaster = album("remaster", version = "Remastered", year = 2009)
        val other: AppMediaItem = artist("Not an album")

        val collapsed = listOf(deluxe, help, other, original, remaster).collapseVersions()

        assertEquals(listOf(original.copy(versionCount = 3), help, other), collapsed)
    }

    @Test
    fun theReleaseGroupDecidesAndNotTheName() {
        val renamed = album("a", name = "Abbey Road", version = "", year = 1969)
        val differentTitle = album("b", name = "A completely different title", version = "Deluxe")
        val sameTitleOtherGroup = album("c", name = "Abbey Road", releaseGroupId = "other-group")

        assertEquals(
            listOf(renamed.copy(versionCount = 2), sameTitleOtherGroup),
            listOf(renamed, differentTitle, sameTitleOtherGroup).collapseVersions(),
        )
    }

    @Test
    fun albumsWithoutAReleaseGroupAreNeverGrouped() {
        val first = album("a", releaseGroupId = null)
        val second = album("b", releaseGroupId = null)

        assertEquals(listOf(first, second), listOf(first, second).collapseVersions())
    }

    @Test
    fun withoutAnUnversionedOneTheOldestStandsForTheAlbum() {
        val newer = album("a", version = "Deluxe", year = 2019)
        val older = album("b", version = "Remastered", year = 2009)

        assertEquals(listOf(older.copy(versionCount = 2)), listOf(newer, older).collapseVersions())
    }

    @Test
    fun theFactsTakeTheDayOfTheReleaseDateOrTheYear() {
        val metadata = Metadata(
            explicit = false,
            images = emptyList(),
            releaseDate = "2019-09-27T00:00:00",
            chapters = emptyList(),
            lyrics = null,
            lrcLyrics = null,
            label = "Apple Records",
        )

        assertEquals(
            AlbumVersionFacts("2019-09-27", "Deluxe", "Apple Records", "0602508007"),
            album("1", version = " Deluxe ", year = 2019, barcode = "0602508007", metadata = metadata)
                .versionFacts(),
        )
        assertEquals(
            AlbumVersionFacts("1969", null, null, null),
            album("2", version = "", year = 1969).versionFacts(),
        )
    }

    private companion object {
        const val ABBEY_ROAD = "abbey-road-group"
    }
}
