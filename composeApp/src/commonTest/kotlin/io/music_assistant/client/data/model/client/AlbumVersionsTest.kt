package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
        name: String,
        artist: String = "The Beatles",
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
        artists = listOf(artist(artist)),
        barcode = barcode,
    )

    @Test
    fun editionSuffixesShareTheKey() {
        val key = album("1", "Abbey Road").versionKey()

        assertEquals(key, album("2", "Abbey Road (Remastered 2019)").versionKey())
        assertEquals(key, album("3", "Abbey Road [Super Deluxe Edition]").versionKey())
        assertEquals(key, album("4", "Abbey Road - 50th Anniversary").versionKey())
        assertEquals(key, album("5", "ABBEY ROAD (Deluxe) [Remastered]").versionKey())
    }

    @Test
    fun aSubtitleAfterAColonIsAnotherAlbum() {
        // "Experience: Expanded" is a separate release, not a version of "Experience".
        assertNotEquals(
            album("1", "Experience").versionKey(),
            album("2", "Experience: Expanded: Remixes & B-Sides").versionKey(),
        )
        assertNotEquals(
            album("3", "Their Law").versionKey(),
            album("4", "Their Law: The Singles 1990-2005").versionKey(),
        )
    }

    @Test
    fun liveOtherWordsAndOtherArtistsKeepTheirOwnKey() {
        val key = album("1", "Abbey Road").versionKey()

        assertNotEquals(key, album("2", "Abbey Road (Live)").versionKey())
        assertNotEquals(key, album("3", "Abbey Road - Side Two").versionKey())
        assertNotEquals(key, album("4", "Abbey Road", artist = "George Benson").versionKey())
    }

    @Test
    fun aNameThatIsOnlyAnEditionStaysWhole() {
        assertEquals("(deluxe edition)", normalizedAlbumName("(Deluxe Edition)"))
    }

    @Test
    fun versionsCollapseAtTheFirstPositionIntoTheOriginal() {
        val deluxe = album("deluxe", "Abbey Road (Deluxe)", version = "Deluxe", year = 2019)
        val help = album("help", "Help!")
        val original = album("original", "Abbey Road", year = 1969)
        val remaster = album("remaster", "Abbey Road", version = "Remastered", year = 2009)
        val other: AppMediaItem = artist("Not an album")

        val collapsed = listOf(deluxe, help, other, original, remaster).collapseVersions()

        assertEquals(listOf(original.copy(versionCount = 3), help, other), collapsed)
    }

    @Test
    fun withoutAnUnversionedOneTheOldestStandsForTheAlbum() {
        val newer = album("a", "Abbey Road", version = "Deluxe", year = 2019)
        val older = album("b", "Abbey Road", version = "Remastered", year = 2009)

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
            album("1", "Abbey Road", version = " Deluxe ", year = 2019, barcode = "0602508007", metadata = metadata)
                .versionFacts(),
        )
        assertEquals(
            AlbumVersionFacts("1969", null, null, null),
            album("2", "Abbey Road", version = "", year = 1969).versionFacts(),
        )
    }
}
