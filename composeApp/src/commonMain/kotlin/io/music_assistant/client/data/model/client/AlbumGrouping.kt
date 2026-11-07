package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import kotlinx.serialization.Serializable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.artist_group_albums
import musicassistantclient.composeapp.generated.resources.artist_group_compilations
import musicassistantclient.composeapp.generated.resources.artist_group_eps
import musicassistantclient.composeapp.generated.resources.artist_group_live
import musicassistantclient.composeapp.generated.resources.artist_group_other
import musicassistantclient.composeapp.generated.resources.artist_group_singles
import musicassistantclient.composeapp.generated.resources.artist_group_soundtracks
import org.jetbrains.compose.resources.StringResource

/**
 * One row of albums on an artist page. A group holds one [AlbumType]. [OTHER] holds the albums
 * with no known type, and the albums of a type whose group is hidden.
 */
@Serializable
enum class AlbumGroup(val albumType: AlbumType?) {
    ALBUMS(AlbumType.ALBUM),
    EPS(AlbumType.EP),
    SINGLES(AlbumType.SINGLE),
    LIVE(AlbumType.LIVE),
    COMPILATIONS(AlbumType.COMPILATION),
    SOUNDTRACKS(AlbumType.SOUNDTRACK),
    OTHER(null),
    ;

    /** [OTHER] cannot be hidden: the albums of a hidden group move to it. */
    val canHide: Boolean get() = this != OTHER
}

fun AlbumGroup.title(): StringResource = when (this) {
    AlbumGroup.ALBUMS -> Res.string.artist_group_albums
    AlbumGroup.EPS -> Res.string.artist_group_eps
    AlbumGroup.SINGLES -> Res.string.artist_group_singles
    AlbumGroup.LIVE -> Res.string.artist_group_live
    AlbumGroup.COMPILATIONS -> Res.string.artist_group_compilations
    AlbumGroup.SOUNDTRACKS -> Res.string.artist_group_soundtracks
    AlbumGroup.OTHER -> Res.string.artist_group_other
}

data class AlbumGroupPref(val group: AlbumGroup, val shown: Boolean = true)

/**
 * How an artist page groups its albums. [enabled] turns the grouping on. [groups] holds every
 * group once, in display order, with its shown state.
 */
data class AlbumGrouping(
    val enabled: Boolean = true,
    val groups: List<AlbumGroupPref> = AlbumGroup.entries.map { AlbumGroupPref(it) },
) {
    /** The shown groups, in display order. */
    val order: List<AlbumGroup> = groups.filter { it.shown || !it.group.canHide }.map { it.group }

    fun groupOf(album: Album): AlbumGroup =
        album.albumType?.let { type -> order.firstOrNull { it.albumType == type } } ?: AlbumGroup.OTHER

    /** The albums of [items] by group, in display order. An empty group is left out. */
    fun group(items: List<AppMediaItem>): List<Pair<AlbumGroup, List<Album>>> {
        val byGroup = items.filterIsInstance<Album>().groupBy(::groupOf)
        return (order + AlbumGroup.OTHER).distinct().mapNotNull { group -> byGroup[group]?.let { group to it } }
    }

    fun toggled(group: AlbumGroup): AlbumGrouping = copy(
        groups = groups.map { if (it.group == group && group.canHide) it.copy(shown = !it.shown) else it },
    )

    /** Moves [group] by one place. At either end of the list, nothing changes. */
    fun moved(group: AlbumGroup, delta: Int): AlbumGrouping {
        val from = groups.indexOfFirst { it.group == group }
        val to = from + delta
        if (from < 0 || to !in groups.indices) return this
        return copy(
            groups = groups.toMutableList().also {
                it[from] = groups[to]
                it[to] = groups[from]
            },
        )
    }

    /** The stored form of [groups]: `NAME:1` or `NAME:0` for each group, comma-separated. */
    fun encodeGroups(): String = groups.joinToString(ITEM_SEPARATOR) {
        "${it.group.name}$VALUE_SEPARATOR${if (it.shown) SHOWN else HIDDEN}"
    }

    companion object {
        val Default = AlbumGrouping()

        /**
         * Reads the stored groups. An unknown or repeated group is dropped, and a group that the
         * stored list does not know yet comes last and shown.
         */
        fun parse(enabled: Boolean, storedGroups: String?): AlbumGrouping {
            val stored = storedGroups.orEmpty()
                .split(ITEM_SEPARATOR)
                .mapNotNull { item ->
                    AlbumGroup.entries.firstOrNull { it.name == item.substringBefore(VALUE_SEPARATOR) }
                        ?.let { AlbumGroupPref(it, shown = item.substringAfter(VALUE_SEPARATOR, SHOWN) == SHOWN) }
                }
                .distinctBy { it.group }
            val known = stored.map { it.group }.toSet()
            return AlbumGrouping(
                enabled = enabled,
                groups = stored + AlbumGroup.entries.filter { it !in known }.map { AlbumGroupPref(it) },
            )
        }

        private const val ITEM_SEPARATOR = ","
        private const val VALUE_SEPARATOR = ":"
        private const val SHOWN = "1"
        private const val HIDDEN = "0"
    }
}
