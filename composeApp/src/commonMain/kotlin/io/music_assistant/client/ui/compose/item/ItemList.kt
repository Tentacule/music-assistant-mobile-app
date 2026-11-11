package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AlbumGroup
import io.music_assistant.client.data.model.client.AlbumGrouping
import io.music_assistant.client.data.model.client.ArtistReleases
import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import kotlinx.serialization.Serializable

@Serializable
sealed interface ItemList {
    val mediaType: MediaType
    val providerDomain: String

    /** Keys the remembered sort, so each artist list keeps its own. */
    val sortContext: SubItemContext

    /** The [releases] of an artist on one provider instance, or its [albumGroup] when set. */
    @Serializable
    data class ArtistAlbums(
        val providerMappings: List<ProviderMapping>,
        val releases: ArtistReleases = ArtistReleases.ALBUMS,
        val albumGroup: AlbumGroup? = null,
    ) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = providerMappings.first().providerDomain
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_ALL_ALBUMS

        constructor(providerMapping: ProviderMapping) : this(providerMappings = listOf(providerMapping))
    }

    @Serializable
    data class ArtistTopTracks(val providerMappings: List<ProviderMapping>) : ItemList {
        override val mediaType: MediaType = MediaType.TRACK
        override val providerDomain: String = providerMappings.first().providerDomain
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_TOP_TRACKS
    }

    /**
     * Top tracks of a library artist. The server merges the artist's music providers and the
     * metadata providers (e.g. Last.fm); [providerFilter] narrows that to one provider instance.
     */
    @Serializable
    data class ArtistLibraryTopTracks(val artistId: String, val providerFilter: String? = null) : ItemList {
        override val mediaType: MediaType = MediaType.TRACK
        override val providerDomain: String = providerFilter ?: ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_TOP_TRACKS
    }

    /** The library [releases] of a library artist, or its [albumGroup] when set. */
    @Serializable
    data class ArtistLibrary(
        val artistId: String,
        val releases: ArtistReleases = ArtistReleases.ALBUMS,
        val albumGroup: AlbumGroup? = null,
    ) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_LIBRARY_ALBUMS
    }

    /** The albums a library artist appears on without being an album artist (server 2.11). */
    @Serializable
    data class ArtistAppearsOn(val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_APPEARS_ON
    }

    /** The MusicBrainz discography of a library artist (server 2.11). */
    @Serializable
    data class ArtistDiscography(val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
        override val providerDomain: String = ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_DISCOGRAPHY
    }

    /** The audiobooks of an author or a narrator on one provider instance. */
    @Serializable
    data class ArtistAudiobooks(val providerMappings: List<ProviderMapping>, val artistType: ArtistType) : ItemList {
        override val mediaType: MediaType = MediaType.AUDIOBOOK
        override val providerDomain: String = providerMappings.first().providerDomain
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_AUDIOBOOKS
    }

    /** The library audiobooks of a library author or narrator. */
    @Serializable
    data class ArtistLibraryAudiobooks(val artistId: String, val artistType: ArtistType) : ItemList {
        override val mediaType: MediaType = MediaType.AUDIOBOOK
        override val providerDomain: String = ServerMediaItem.LIBRARY_PROVIDER
        override val sortContext: SubItemContext get() = SubItemContext.ARTIST_AUDIOBOOKS
    }
}

/** The album group of an artist album list, or null for a list of every item. */
val ItemList.albumGroup: AlbumGroup?
    get() = when (this) {
        is ItemList.ArtistAlbums -> albumGroup
        is ItemList.ArtistLibrary -> albumGroup
        else -> null
    }

/** This list narrowed to the albums of [group]. A list that is not an artist album list stays as it is. */
fun ItemList.withAlbumGroup(group: AlbumGroup): ItemList = when (this) {
    is ItemList.ArtistAlbums -> copy(albumGroup = group)
    is ItemList.ArtistLibrary -> copy(albumGroup = group)
    else -> this
}

fun ItemList.toRequests(): List<Request> {
    return when (this) {
        is ItemList.ArtistAlbums -> {
            this.providerMappings.map {
                Request.Artist.getAlbums(it.itemId, it.providerInstance)
            }
        }

        is ItemList.ArtistTopTracks -> {
            this.providerMappings.map {
                Request.Artist.getTopTracks(it.itemId, it.providerInstance)
            }
        }

        is ItemList.ArtistLibraryTopTracks -> listOf(
            Request.Artist.getTopTracks(
                this.artistId,
                ServerMediaItem.LIBRARY_PROVIDER,
                this.providerFilter,
            ),
        )

        is ItemList.ArtistLibrary -> listOf(
            Request.Artist.getAlbums(
                this.artistId,
                ServerMediaItem.LIBRARY_PROVIDER,
            ),
        )

        is ItemList.ArtistAppearsOn -> listOf(Request.Artist.getAppearsOn(this.artistId))

        is ItemList.ArtistDiscography -> listOf(Request.Artist.getDiscography(this.artistId))

        is ItemList.ArtistAudiobooks -> this.providerMappings.map {
            Request.Artist.getAudiobooks(it.itemId, it.providerInstance, this.artistType.serverValue)
        }

        is ItemList.ArtistLibraryAudiobooks -> listOf(
            Request.Artist.getAudiobooks(
                this.artistId,
                ServerMediaItem.LIBRARY_PROVIDER,
                this.artistType.serverValue,
                inLibraryOnly = true,
            ),
        )
    }
}

/**
 * The items of this list among the items that its [toRequests] answer. A release list keeps its own
 * album types. A list with an [albumGroup] keeps the albums that [grouping] puts in that group.
 */
fun ItemList.select(items: List<AppMediaItem>, grouping: AlbumGrouping = AlbumGrouping.Default): List<AppMediaItem> =
    albumGroup?.let { group -> items.filter { it is Album && grouping.groupOf(it) == group } } ?: when (this) {
        is ItemList.ArtistAlbums -> items.filter(releases::contains)
        is ItemList.ArtistLibrary -> items.filter(releases::contains)
        else -> items
    }
