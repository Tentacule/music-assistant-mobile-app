package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumDisplayYearTest {
    private fun metadata(originalYear: Int?) = Metadata(
        explicit = false,
        images = emptyList(),
        releaseDate = null,
        chapters = emptyList(),
        lyrics = null,
        lrcLyrics = null,
        originalYear = originalYear,
    )

    @Test
    fun anAlbumShowsItsFirstReleaseYear() {
        val reissue = AppMediaItemFixtures.album(year = 2011).copy(metadata = metadata(1973))

        assertEquals(1973, reissue.displayYear)
        assertEquals(2011, reissue.year)
    }

    @Test
    fun withoutAFirstReleaseYearTheEditionYearShows() {
        assertEquals(2011, AppMediaItemFixtures.album(year = 2011).displayYear)
        assertEquals(2011, AppMediaItemFixtures.album(year = 2011).copy(metadata = metadata(null)).displayYear)
    }
}
