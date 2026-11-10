package io.music_assistant.client.ui.compose.series

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.server.ServerAlbumSeriesInfo

/**
 * Koin singleton that holds the album list for a MusicBrainz series page.
 * [Album] is not @Serializable so it cannot travel through the Navigation3 back stack;
 * the library list stores albums here before navigating, and the series screen reads them.
 */
class MbSeriesNavigationState {
    private val _albumsBySeriesId = mutableMapOf<String, List<Pair<Album, ServerAlbumSeriesInfo>>>()

    val albumsBySeriesId: Map<String, List<Pair<Album, ServerAlbumSeriesInfo>>>
        get() = _albumsBySeriesId

    fun store(seriesId: String, albums: List<Pair<Album, ServerAlbumSeriesInfo>>) {
        _albumsBySeriesId[seriesId] = albums
    }
}
