package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * The key that the versions of one album share: the name without its edition suffix, and the
 * first artist. "Abbey Road", "Abbey Road (Remastered 2019)" and "Abbey Road [Deluxe Edition]" by
 * the same artist are one album. "Live" is not an edition word, so a live album stays apart.
 */
fun Album.versionKey(): String =
    "${normalizedAlbumName(name)}|${artists.firstOrNull()?.name?.lowercase()?.trim().orEmpty()}"

internal fun normalizedAlbumName(name: String): String {
    var current = name.lowercase().trim()
    while (true) {
        val stripped = EDITION_SUFFIXES.firstNotNullOfOrNull { suffix ->
            suffix.find(current)?.takeIf { match -> EDITION_WORD.containsMatchIn(match.value) }
                ?.let { current.removeRange(it.range).trim() }
        } ?: return current
        if (stripped.isEmpty()) return current
        current = stripped
    }
}

private val EDITION_SUFFIXES = listOf(
    Regex("""\s*\([^()]*\)$"""),
    Regex("""\s*\[[^\[\]]*]$"""),
    Regex("""\s+[-–]\s+[^-–]+$"""),
)
private val EDITION_WORD = Regex(
    """\b(deluxe|remaster|remastered|edition|expanded|anniversary|bonus|version|special|mono|stereo|reissue)\b""",
)

/**
 * The list with each album shown once: the versions of an album collapse into one entry, at the
 * position of the first version, with [Album.versionCount] set. Other items stay as they are.
 */
fun List<AppMediaItem>.collapseVersions(): List<AppMediaItem> {
    val groups = filterIsInstance<Album>().groupBy { it.versionKey() }
    val shown = mutableSetOf<String>()
    return mapNotNull { item ->
        if (item !is Album) return@mapNotNull item
        val key = item.versionKey()
        if (!shown.add(key)) return@mapNotNull null
        val versions = groups[key].orEmpty()
        if (versions.size < 2) item else versions.representative().copy(versionCount = versions.size)
    }
}

/** The version that stands for the album: the one without a version text, then the oldest. */
internal fun List<Album>.representative(): Album =
    sortedWith(
        compareBy<Album> { !it.version.isNullOrBlank() }.thenBy { it.year ?: Int.MAX_VALUE },
    ).first()

/** The facts that tell a version apart; null parts are unknown. */
data class AlbumVersionFacts(
    val releaseDate: String?,
    val version: String?,
    val label: String?,
    val barcode: String?,
)

fun Album.versionFacts(): AlbumVersionFacts = AlbumVersionFacts(
    // The server sends an ISO date-time; the day is enough.
    releaseDate = metadata?.releaseDate?.take(ISO_DATE_LENGTH)?.takeIf { it.isNotBlank() } ?: year?.toString(),
    version = version?.trim()?.takeIf { it.isNotEmpty() },
    label = metadata?.label?.trim()?.takeIf { it.isNotEmpty() },
    barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
)

private const val ISO_DATE_LENGTH = 10
