package io.music_assistant.client.ui.compose.item.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.model.client.items.set
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import io.music_assistant.client.ui.compose.common.mapData
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.toRequests
import io.music_assistant.client.ui.compose.item.withAlbumGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistDetailsViewModel(
    private val artist: Artist,
    mediaItemRepository: MediaItemRepository,
    private val providerRepository: ProviderRepository,
) : ViewModel() {
    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    // An author or a narrator has audiobooks where a singer has albums, and no top tracks.
    private val audiobookType = artist.artistType?.takeIf { artist.isAudiobookArtist }

    // Albums only: an author or a narrator has one row of audiobooks.
    private val albumGrouping = AlbumGrouping.Default.takeIf { it.enabled && audiobookType == null }

    private fun List<AppMediaItem>.sectionItems(): List<AppMediaItem> =
        filter { if (audiobookType != null) it is Audiobook else it is Album }.take(ARTIST_SECTION_LIMIT)

    private val libraryItems = itemList(mediaItemRepository)
    val library = libraryItems.asFlow()
        .mapData {
            sections(
                items = it,
                itemList = audiobookType
                    ?.let { type -> ItemList.ArtistLibraryAudiobooks(artist.itemId, type) }
                    ?: ItemList.ArtistLibrary(artist.itemId),
                grouping = albumGrouping,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val allItems = itemList(mediaItemRepository)
    private val allProviderFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val all = allItems.asFlow()
        .combine(allProviderFilter) { items, providerFilter ->
            items.map {
                sections(
                    items = it,
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                    // A library artist has its albums grouped in the library rows. Its discography stays one row.
                    grouping = albumGrouping?.takeUnless { artist.isInLibrary },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    /**
     * The rows of an album list: one row for each album group, or one row of every item without
     * [grouping]. The provider filter changes every row, and only the first row shows it.
     */
    private fun sections(
        items: List<AppMediaItem>,
        itemList: ItemList?,
        grouping: AlbumGrouping?,
        providerFilter: Section.ProviderFilter? = null,
    ): List<Section<AppMediaItem>> =
        grouping?.group(items)?.mapIndexed { index, (group, albums) ->
            Section(
                items = albums.take(ARTIST_SECTION_LIMIT),
                itemList = itemList?.withAlbumGroup(group),
                providerFilter = providerFilter.takeIf { index == 0 },
                albumGroup = group,
            )
        } ?: listOf(Section(items.sectionItems(), itemList, providerFilter))

    private val topTrackItems = itemList(mediaItemRepository)
    private val topTracksFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val topTracks = topTrackItems.asFlow()
        .combine(topTracksFilter) { items, providerFilter ->
            items.map {
                Section(
                    items = it
                        .filterIsInstance<Track>()
                        .take(ARTIST_SECTION_LIMIT),
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    init {
        loadSections(artist)

        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect {
                when (it) {
                    is MediaItemChange.Updated -> {
                        if (artist.hasAnyMappingFrom(it.item)) {
                            loadSections(it.item as Artist)
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun loadSections(artist: Artist) {
        viewModelScope.launch {
            if (artist.isInLibrary) {
                libraryItems.set(
                    audiobookType
                        ?.let { Request.Artist.getAudiobooks(artist.itemId, artist.provider, it.serverValue, inLibraryOnly = true) }
                        ?: Request.Artist.getAlbums(artist.itemId, artist.provider),
                )
            } else {
                libraryItems.setEmpty()
            }
        }

        viewModelScope.launch {
            val options = artist.providerGroups().map { group ->
                audiobookType?.let { ItemList.ArtistAudiobooks(group, it) } ?: ItemList.ArtistAlbums(group)
            }
            val artistItems = fetchArtistItemsUseCase.run(options)

            if (artistItems != null) {
                val (items, itemList, options) = artistItems
                allProviderFilter.value = Section.ProviderFilter(itemList, options)
                allItems.set(items, itemList.toRequests())
            } else {
                allItems.setEmpty()
            }
        }

        viewModelScope.launch {
            if (audiobookType != null) {
                topTrackItems.setEmpty()
                return@launch
            }
            val providers = if (artist.isInLibrary) {
                providerRepository.providers.first { it !is DataState.Loading }.dataOrNull.orEmpty()
            } else {
                emptyList()
            }
            val result = fetchArtistItemsUseCase.run(artist.topTracksOptions(providers))

            if (result != null) {
                val (items, itemList, options) = result
                topTracksFilter.value = Section.ProviderFilter(itemList, options)
                topTrackItems.set(items, itemList.toRequests())
            } else {
                topTrackItems.setEmpty()
            }
        }
    }

    fun loadAll(itemList: ItemList) {
        allProviderFilter.update {
            it?.copy(current = itemList)
        }

        viewModelScope.launch {
            allItems.set(itemList.toRequests())
        }
    }

    fun loadTopTracks(itemList: ItemList) {
        topTracksFilter.update { it?.copy(current = itemList) }

        viewModelScope.launch {
            topTrackItems.set(itemList.toRequests())
        }
    }

    data class Section<T : AppMediaItem>(
        val items: List<T>,
        val itemList: ItemList?,
        val providerFilter: ProviderFilter? = null,
        val albumGroup: AlbumGroup? = null,
    ) {
        data class ProviderFilter(
            val current: ItemList,
            val options: List<ItemList>,
        )
    }
}
