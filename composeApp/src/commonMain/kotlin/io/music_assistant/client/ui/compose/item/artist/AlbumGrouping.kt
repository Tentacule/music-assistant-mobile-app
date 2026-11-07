package io.music_assistant.client.ui.compose.item.artist

import io.music_assistant.client.data.model.client.AlbumType
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
 * with no known type, and the albums of a type whose group is not shown.
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

/**
 * How an artist page groups its albums. [order] is the shown groups, in display order.
 * [AlbumGroup.OTHER] comes last if [order] does not place it.
 *
 * Fixed for now ([Default]). A later setting gives [enabled] and [order].
 */
data class AlbumGrouping(
    val enabled: Boolean,
    val order: List<AlbumGroup>,
) {
    fun groupOf(album: Album): AlbumGroup =
        album.albumType?.let { type -> order.firstOrNull { it.albumType == type } } ?: AlbumGroup.OTHER

    /** The albums of [items] by group, in display order. An empty group is left out. */
    fun group(items: List<AppMediaItem>): List<Pair<AlbumGroup, List<Album>>> {
        val byGroup = items.filterIsInstance<Album>().groupBy(::groupOf)
        return (order + AlbumGroup.OTHER).distinct().mapNotNull { group -> byGroup[group]?.let { group to it } }
    }

    companion object {
        val Default = AlbumGrouping(enabled = true, order = AlbumGroup.entries)
    }
}
