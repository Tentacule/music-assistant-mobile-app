package io.music_assistant.client.data.model.client

import kotlinx.serialization.Serializable

/**
 * Which albums an album list keeps, sent as `series_filter`: all of them, the albums that are
 * part of no series, or the albums that are part of one (the series rows when grouped).
 */
@Serializable
enum class AlbumSeriesFilter(val serverValue: String) {
    ALL("all"),
    ALBUMS("albums"),
    SERIES("series"),
}
