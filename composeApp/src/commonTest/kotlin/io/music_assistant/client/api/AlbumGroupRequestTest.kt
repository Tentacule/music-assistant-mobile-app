package io.music_assistant.client.api

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumGroupRequestTest {
    @Test
    fun libraryListUsesTheDeduplicatedEndpoint() {
        assertEquals("music/album_groups/library_items", Request.Album.listLibrary().command)
    }

    @Test
    fun artistAlbumsUseTheDeduplicatedEndpoint() {
        assertEquals("music/album_groups/artist_albums", Request.Artist.getAlbums("1", "library").command)
    }

    @Test
    fun setDefaultSendsTheLibraryId() {
        val request = Request.Album.setGroupDefault("42")

        assertEquals("music/album_groups/set_default", request.command)
        assertEquals("42", request.args?.get("item_id")?.jsonPrimitive?.content)
    }
}
