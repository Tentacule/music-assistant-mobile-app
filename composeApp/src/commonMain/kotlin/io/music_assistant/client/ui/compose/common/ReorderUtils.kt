package io.music_assistant.client.ui.compose.common

// Toggling ON moves the item to the bottom of the enabled section.
// Toggling OFF moves the item to the top of the disabled section.
internal fun <T> moveToEnabledBoundary(
    items: List<Pair<T, Boolean>>,
    target: T,
    newEnabled: Boolean,
): List<Pair<T, Boolean>> {
    val mutable = items.toMutableList()
    val idx = mutable.indexOfFirst { it.first == target }
    if (idx < 0) return items
    mutable.removeAt(idx)
    val insertIndex = mutable.count { it.second }
    mutable.add(insertIndex, target to newEnabled)
    return mutable
}

// Moves an enabled item by [delta] places inside the enabled section; the key equivalent of the
// drag. A disabled item, or a move past either end of the section, changes nothing.
internal fun <T> moveWithinEnabled(
    items: List<Pair<T, Boolean>>,
    target: T,
    delta: Int,
): List<Pair<T, Boolean>> {
    val from = items.indexOfFirst { it.first == target }
    val to = from + delta
    val enabledCount = items.count { it.second }
    if (from !in 0 until enabledCount || to !in 0 until enabledCount) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

// Stable partition: enabled items first, each section keeps its order. A reconciled config
// appends a new item enabled after the disabled ones, and reordering needs one enabled block.
internal fun <T> List<Pair<T, Boolean>>.enabledFirst(): List<Pair<T, Boolean>> =
    sortedByDescending { it.second }
