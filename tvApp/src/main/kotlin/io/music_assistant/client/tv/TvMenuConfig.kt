package io.music_assistant.client.tv

import io.music_assistant.client.ui.compose.library.LibraryCategory

/**
 * The entries that the user can show, hide and order in the TV menu. Settings is not one of them:
 * it stays last and always shows, as it is the only way back to this choice.
 */
enum class TvMenuEntry(val category: LibraryCategory? = null) {
    HOME,
    NOW_PLAYING,
    SEARCH,
    ARTISTS(LibraryCategory.ARTISTS),
    ALBUMS(LibraryCategory.ALBUMS),
    TRACKS(LibraryCategory.TRACKS),
    QUEUE,
    PLAYLISTS(LibraryCategory.PLAYLISTS),
    AUDIOBOOKS(LibraryCategory.AUDIOBOOKS),
    PODCASTS(LibraryCategory.PODCASTS),
    RADIOS(LibraryCategory.RADIOS),
    GENRES(LibraryCategory.GENRES),
    BROWSE(LibraryCategory.BROWSE),
    AI_RADIO(LibraryCategory.AI_RADIO),
    PLAYER,
}

data class TvMenuItem(val entry: TvMenuEntry, val visible: Boolean = true)

/** The menu of a new install: every entry, in the order of [TvMenuEntry]. */
val defaultTvMenu: List<TvMenuItem> = TvMenuEntry.entries.map { TvMenuItem(it) }

/**
 * Reads a stored menu. An unknown or repeated entry is dropped, so a removed entry does no harm,
 * and an entry that the stored menu does not know yet comes last and visible.
 */
fun parseTvMenu(stored: String?): List<TvMenuItem> {
    val items = stored.orEmpty()
        .split(ITEM_SEPARATOR)
        .mapNotNull { item ->
            val name = item.substringBefore(VALUE_SEPARATOR)
            TvMenuEntry.entries.firstOrNull { it.name == name }
                ?.let { TvMenuItem(it, visible = item.substringAfter(VALUE_SEPARATOR, VISIBLE) == VISIBLE) }
        }
        .distinctBy { it.entry }
    val known = items.map { it.entry }.toSet()
    return items + defaultTvMenu.filter { it.entry !in known }
}

fun List<TvMenuItem>.encodeTvMenu(): String = joinToString(ITEM_SEPARATOR) {
    "${it.entry.name}$VALUE_SEPARATOR${if (it.visible) VISIBLE else HIDDEN}"
}

fun List<TvMenuItem>.toggled(entry: TvMenuEntry): List<TvMenuItem> =
    map { if (it.entry == entry) it.copy(visible = !it.visible) else it }

/**
 * Moves [entry] by one place among the entries that [isListed] accepts, so that a move always
 * shows on screen even when an entry that this server does not offer sits between two others.
 * At either end of the list, nothing changes.
 */
fun List<TvMenuItem>.moved(
    entry: TvMenuEntry,
    delta: Int,
    isListed: (TvMenuEntry) -> Boolean = { true },
): List<TvMenuItem> {
    val listed = indices.filter { isListed(this[it].entry) }
    val position = listed.indexOfFirst { this[it].entry == entry }
    val from = listed.getOrNull(position)
    val to = listed.getOrNull(position + delta)
    if (position < 0 || from == null || to == null) return this
    return toMutableList().also {
        it[from] = this[to]
        it[to] = this[from]
    }
}

private const val ITEM_SEPARATOR = ","
private const val VALUE_SEPARATOR = ":"
private const val VISIBLE = "1"
private const val HIDDEN = "0"
