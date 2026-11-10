package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.ui.compose.common.icons.BookshelfIcon

/**
 * A server-built group of library items: an audiobook series or an album series. It exists
 * only in a `collapse_collections` listing or a `get_collection` answer: no provider mappings,
 * no favorite, no own artwork. [items] keep the server order (the series sequence). Playing
 * the [uri] of an audiobook series lets the server pick the first not-fully-played item; an
 * album series plays its albums in the series order.
 */
data class MediaCollection(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    val items: List<AppMediaItem>,
) : AppMediaItem() {
    // No favorite state, so the copy is the item itself.
    override fun withFavorite(favorite: Boolean?) = this
    override val providerMappings: List<ProviderMapping>? = null
    override val metadata: Metadata? = null
    override val favorite: Boolean? = null
    override val sortName: String? = null
    override val mediaType: MediaType = MediaType.COLLECTION
    override val defaultIcon = BookshelfIcon

    /** The type of the items, from the id the server builds: `<media type>___<title>`. */
    val itemMediaType: MediaType?
        get() = MediaType.fromServer(itemId.substringBefore(ID_SEPARATOR, missingDelimiterValue = ""))

    /** True for a series of albums, false for an audiobook series. */
    val isAlbumSeries: Boolean get() = itemMediaType == MediaType.ALBUM

    override fun equals(other: Any?): Boolean =
        other is MediaCollection && super.equals(other) && items == other.items

    override fun hashCode(): Int = super.hashCode() + items.hashCode()

    companion object {
        // The server names a collection "<media type>___<collection title>".
        private const val ID_SEPARATOR = "___"

        /** True for the id of an album series, false for an audiobook series. */
        fun isAlbumSeriesId(itemId: String): Boolean = itemId.startsWith(albumSeriesId(""))

        /** The id the server gives the album series titled [title]. */
        fun albumSeriesId(title: String): String = "${MediaType.ALBUM.serverValue}$ID_SEPARATOR$title"

        /** A stand-in for the album series titled [title], enough to open its page. */
        fun albumSeries(title: String): MediaCollection = MediaCollection(
            itemId = albumSeriesId(title),
            provider = ServerMediaItem.LIBRARY_PROVIDER,
            name = title,
            uri = null,
            images = emptyMap(),
            items = emptyList(),
        )
    }
}
