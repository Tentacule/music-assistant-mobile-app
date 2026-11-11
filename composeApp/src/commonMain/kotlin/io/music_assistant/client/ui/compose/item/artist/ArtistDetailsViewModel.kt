package io.music_assistant.client.ui.compose.item.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AlbumGroup
import io.music_assistant.client.data.model.client.AlbumGrouping
import io.music_assistant.client.data.model.client.ArtistReleases
import io.music_assistant.client.data.model.client.deduplicateByReleaseGroup
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase.ArtistItems
import io.music_assistant.client.data.model.client.items.MediaItemDataMediator
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.model.server.supportsArtistAppearsOnAndDiscography
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.select
import io.music_assistant.client.ui.compose.item.toRequests
import io.music_assistant.client.ui.compose.item.withAlbumGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The rows of an artist page. A singer has [albums], [singles], [appearsOn], [discography] and
 * [topTracks]. An author or a narrator has [library] and [all]. The rows of the other kind
 * stay empty. With the album grouping on, [groupRows] replace [albums] and [singles]: one row for
 * each [AlbumGroup], with the same sources.
 */
class ArtistDetailsViewModel(
    private val artist: Artist,
    private val mediaItemRepository: MediaItemRepository,
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val schemaVersion: () -> Int?,
) : ViewModel() {
    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    // An author or a narrator has audiobooks where a singer has albums, and no top tracks.
    private val audiobookType = artist.artistType?.takeIf { artist.isAudiobookArtist }

    val albums = SourceRow()
    val singles = SourceRow()

    /** The album grouping of the artist page, or null when it is off. */
    val albumGrouping: StateFlow<AlbumGrouping?> = settingsRepository.albumGrouping
        .map { grouping -> grouping.takeIf { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.albumGrouping.value.takeIf { it.enabled })

    // One edition of each release group, like the album pages: the others are in the Versions tab.
    val groupRows: Map<AlbumGroup, SourceRow> = AlbumGroup.entries.associateWith {
        SourceRow { items -> items.filterIsInstance<Album>().deduplicateByReleaseGroup() }
    }
    val topTracks = SourceRow { items -> items.filterIsInstance<Track>() }
    val all = SourceRow { items -> items.filterIsInstance<Audiobook>() }

    private val libraryItems = itemList(mediaItemRepository)
    val library = fixedRow(
        libraryItems,
        audiobookType?.let { ItemList.ArtistLibraryAudiobooks(artist.itemId, it) },
    ) { items -> items.filterIsInstance<Audiobook>() }

    private val appearsOnItems = itemList(mediaItemRepository)
    val appearsOn = fixedRow(appearsOnItems, ItemList.ArtistAppearsOn(artist.itemId))

    private val discographyItems = itemList(mediaItemRepository)
    val discography = fixedRow(discographyItems, ItemList.ArtistDiscography(artist.itemId))

    private var currentArtist = artist

    init {
        loadSections(artist)

        // A hidden group moves its albums to Other, so the group rows choose their sources again.
        viewModelScope.launch {
            settingsRepository.albumGrouping.drop(1).distinctUntilChanged().collect { loadReleases(currentArtist) }
        }

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
        currentArtist = artist
        audiobookType?.let { type ->
            viewModelScope.launch {
                if (artist.isInLibrary) {
                    val request = Request.Artist.getAudiobooks(
                        artist.itemId,
                        artist.provider,
                        type.serverValue,
                        inLibraryOnly = true,
                    )
                    libraryItems.set(listOf(request))
                } else {
                    libraryItems.setEmpty()
                }
            }
            viewModelScope.launch {
                val options = artist.providerGroups().map { ItemList.ArtistAudiobooks(it, type) }
                all.show(fetchArtistItemsUseCase.run(options))
            }
            (listOf(albums, singles, topTracks) + groupRows.values).forEach { it.show(null) }
            listOf(appearsOnItems, discographyItems).forEach { it.setEmpty() }
            return
        }

        libraryItems.setEmpty()
        all.show(null)

        loadReleases(artist)

        // The server lists both for a library artist only.
        val libraryListings = artist.isInLibrary && supportsArtistAppearsOnAndDiscography(schemaVersion())
        listOf(
            appearsOnItems to Request.Artist.getAppearsOn(artist.itemId),
            discographyItems to Request.Artist.getDiscography(artist.itemId),
        ).forEach { (items, request) ->
            viewModelScope.launch {
                if (libraryListings) items.set(listOf(request)) else items.setEmpty()
            }
        }

        viewModelScope.launch {
            val providers = if (artist.isInLibrary) {
                providerRepository.providers.first { it !is DataState.Loading }.dataOrNull.orEmpty()
            } else {
                emptyList()
            }
            topTracks.show(fetchArtistItemsUseCase.run(artist.topTracksOptions(providers)))
        }
    }

    private fun loadReleases(artist: Artist) {
        if (audiobookType != null) return
        viewModelScope.launch {
            // The release rows and the group rows list the same sources, so each source is fetched once.
            val sources = listOfNotNull(ItemList.ArtistLibrary(artist.itemId).takeIf { artist.isInLibrary }) +
                artist.providerGroups().map { ItemList.ArtistAlbums(it) }
            val rows = ArtistReleases.entries.map { releases -> { source: ItemList -> source.withReleases(releases) } } +
                AlbumGroup.entries.map { group -> { source: ItemList -> source.withAlbumGroup(group) } }
            val results = fetchArtistItemsUseCase.run(sources, rows, settingsRepository.albumGrouping.value)
            albums.show(results[0])
            singles.show(results[1])
            AlbumGroup.entries.forEachIndexed { index, group ->
                groupRows.getValue(group).show(results[ArtistReleases.entries.size + index])
            }
        }
    }

    /** A row without a source picker. */
    private fun fixedRow(
        items: MediaItemDataMediator,
        itemList: ItemList?,
        transform: (List<AppMediaItem>) -> List<AppMediaItem> = { it },
    ): StateFlow<DataState<Section<AppMediaItem>>> = items.asFlow()
        .map { state -> state.map { Section(transform(it).take(ARTIST_SECTION_LIMIT), itemList) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    /** A row with a source picker: it shows the items of the selected [ItemList]. */
    inner class SourceRow(
        private val transform: (List<AppMediaItem>) -> List<AppMediaItem> = { it },
    ) {
        private val items = itemList(mediaItemRepository)
        private val providerFilter = MutableStateFlow<Section.ProviderFilter?>(null)

        val section: StateFlow<DataState<Section<AppMediaItem>>> = items.asFlow()
            .combine(providerFilter) { state, filter ->
                state.map {
                    val selected = filter?.current?.select(it, settingsRepository.albumGrouping.value) ?: it
                    Section(transform(selected).take(ARTIST_SECTION_LIMIT), filter?.current, filter)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

        internal fun show(result: ArtistItems?) {
            result?.let {
                providerFilter.value = Section.ProviderFilter(it.itemList, it.options)
                items.set(it.items, it.itemList.toRequests())
            } ?: items.setEmpty()
        }

        fun select(itemList: ItemList) {
            providerFilter.update { it?.copy(current = itemList) }
            viewModelScope.launch { items.set(itemList.toRequests()) }
        }
    }

    data class Section<T : AppMediaItem>(
        val items: List<T>,
        val itemList: ItemList?,
        val providerFilter: ProviderFilter? = null,
    ) {
        data class ProviderFilter(
            val current: ItemList,
            val options: List<ItemList>,
        )
    }
}

private fun ItemList.withReleases(releases: ArtistReleases): ItemList = when (this) {
    is ItemList.ArtistAlbums -> copy(releases = releases)
    is ItemList.ArtistLibrary -> copy(releases = releases)
    else -> this
}
