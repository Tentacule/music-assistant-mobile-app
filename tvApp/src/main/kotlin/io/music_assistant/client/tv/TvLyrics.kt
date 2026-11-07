package io.music_assistant.client.tv

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository

/**
 * What the lyrics mode of Now playing loads from the server: the lyrics of a track and the photo of
 * its artist (the backdrop). A queue item rarely carries either: its track has no embedded lyrics
 * and its artists come without images. The lyrics found and the artist images are kept for the
 * last [CACHE_SIZE] tracks and artists, so a track replayed or Now playing reopened asks nothing.
 * A track without lyrics is not kept: they can come later (a lyrics provider enabled, a lookup
 * from the web app), so the next opening asks again.
 */
class TvLyrics(private val items: MediaItemRepository) {
    private val log = Logger.withTag("TvLyrics")
    private val lyricsCache = lruMap<String, Lyrics>()
    private val artistImageCache = lruMap<String, String?>()

    suspend fun lyrics(track: Track): Lyrics? {
        track.lyrics?.let { return it }
        val key = track.uri ?: "${track.provider}/${track.itemId}"
        synchronized(lyricsCache) { lyricsCache[key]?.let { return it } }
        val lyrics = items.fetchTrackLyrics(track)
            .onFailure { log.w(it) { "Lyrics of $key failed to load" } }
            .getOrNull() ?: return null
        synchronized(lyricsCache) { lyricsCache[key] = lyrics }
        return lyrics
    }

    suspend fun artistImageUrl(artist: Artist): String? {
        artist.imageUrlOrNull()?.let { return it }
        val key = artist.uri ?: "${artist.provider}/${artist.itemId}"
        synchronized(artistImageCache) { if (key in artistImageCache) return artistImageCache[key] }
        val full = items.fetchMediaItem(Request.Artist.get(artist.itemId, artist.provider))
            .getOrElse { return null }
        val url = (full as? Artist)?.imageUrlOrNull()
        synchronized(artistImageCache) { artistImageCache[key] = url }
        return url
    }

    /** Forgets what the previous account or server loaded. */
    fun clear() {
        synchronized(lyricsCache) { lyricsCache.clear() }
        synchronized(artistImageCache) { artistImageCache.clear() }
    }

    // A wide fan art fills a TV screen best; the square photo comes next.
    private fun Artist.imageUrlOrNull(): String? =
        images[ImageType.FANART]?.url ?: images[ImageType.THUMB]?.url ?: image(ImageType.MAIN)?.url

}

/** A map that keeps the last [CACHE_SIZE] entries read or written. */
internal fun <K, V> lruMap(): MutableMap<K, V> = object : LinkedHashMap<K, V>(CACHE_SIZE, LOAD_FACTOR, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > CACHE_SIZE
}

private const val CACHE_SIZE = 32
private const val LOAD_FACTOR = 0.75f
