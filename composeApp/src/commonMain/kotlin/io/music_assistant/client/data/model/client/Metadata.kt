package io.music_assistant.client.data.model.client

/**
 * Client-side metadata view of [io.music_assistant.client.data.model.server.ServerMetadata].
 *
 * Only carries fields the app actually reads; everything else the server sends
 * is dropped at the mapping boundary in `MediaItemFactory`.
 */
data class Metadata(
    val explicit: Boolean,
    val images: List<ImageInfo>,
    val releaseDate: String?,
    val chapters: List<Chapter>,
    // Embedded lyrics the server may ship with the item; when present they spare a
    // metadata/get_track_lyrics round-trip. [lrcLyrics] is timestamped LRC, [lyrics] plain.
    val lyrics: String?,
    val lrcLyrics: String?,
    // Whether the server can fetch a transcript of a podcast episode; null when the provider
    // cannot tell, so the transcript is still asked for.
    val hasTranscript: Boolean? = null,
    // The long texts of the item details page, as the provider sends them (Markdown, HTML or
    // both): an artist's biography, or the description of a playlist, podcast or audiobook.
    val description: String? = null,
    // An album's review; the page falls back to [description] when it is absent.
    val review: String? = null,
    // The series the item is part of, e.g. the album series of an album.
    val collections: List<SeriesEntry> = emptyList(),
    // Albums: the year of the first release of the album (all editions), when known.
    val originalYear: Int? = null,
)

/**
 * An item's place in a series: the series [title] and the item's number in it, as the
 * server gives it ("3", "1.5", "IV"), or null when the series does not number it.
 */
data class SeriesEntry(
    val title: String,
    val sequence: String?,
) {
    /** "Series #3", or the bare title when the item has no number in it. */
    val label: String get() = sequence?.let { "$title #$it" } ?: title
}
