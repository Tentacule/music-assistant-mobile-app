package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * The list with each album shown once: the versions of an album, the albums of one MusicBrainz
 * release group, collapse into one entry at the position of the first version, with
 * [Album.versionCount] set. An album without a release group and other items stay as they are.
 */
fun List<AppMediaItem>.collapseVersions(): List<AppMediaItem> {
    val groups = filterIsInstance<Album>().groupBy { it.releaseGroupId }
    val shown = mutableSetOf<String>()
    return mapNotNull { item ->
        val key = (item as? Album)?.releaseGroupId ?: return@mapNotNull item
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
