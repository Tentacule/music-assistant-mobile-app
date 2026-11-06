package io.music_assistant.client.tv

/**
 * The controls the user can show, hide and reorder in the Now playing mode.
 * Contextual entries (LYRICS, ARTIST, ALBUM) are additionally gated on the current track.
 */
enum class NpControlEntry {
    SHUFFLE, PREVIOUS, PLAY_PAUSE, NEXT, REPEAT,
    AUTOPLAY, CROSSFADE, QUEUE, VISUALIZER,
    LYRICS, ARTIST, ALBUM,
}

/** LYRICS, ARTIST and ALBUM are only shown when the current track provides them. */
val NpControlEntry.isContextual: Boolean
    get() = this == NpControlEntry.LYRICS || this == NpControlEntry.ARTIST || this == NpControlEntry.ALBUM

data class NpControlItem(val entry: NpControlEntry, val visible: Boolean = true)

val defaultNpControls: List<NpControlItem> = NpControlEntry.entries.map { NpControlItem(it) }

fun parseNpControls(stored: String?): List<NpControlItem> {
    val items = stored.orEmpty()
        .split(ITEM_SEPARATOR)
        .mapNotNull { item ->
            val name = item.substringBefore(VALUE_SEPARATOR)
            NpControlEntry.entries.firstOrNull { it.name == name }
                ?.let { NpControlItem(it, visible = item.substringAfter(VALUE_SEPARATOR, VISIBLE) == VISIBLE) }
        }
        .distinctBy { it.entry }
    val known = items.map { it.entry }.toSet()
    return items + defaultNpControls.filter { it.entry !in known }
}

fun List<NpControlItem>.encodeNpControls(): String = joinToString(ITEM_SEPARATOR) {
    "${it.entry.name}$VALUE_SEPARATOR${if (it.visible) VISIBLE else HIDDEN}"
}

fun List<NpControlItem>.toggledControl(entry: NpControlEntry): List<NpControlItem> =
    map { if (it.entry == entry) it.copy(visible = !it.visible) else it }

fun List<NpControlItem>.movedControl(entry: NpControlEntry, delta: Int): List<NpControlItem> {
    val from = indexOfFirst { it.entry == entry }.takeIf { it >= 0 } ?: return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (from == to) return this
    return toMutableList().also {
        it[from] = this[to]
        it[to] = this[from]
    }
}

private const val ITEM_SEPARATOR = ","
private const val VALUE_SEPARATOR = ":"
private const val VISIBLE = "1"
private const val HIDDEN = "0"
