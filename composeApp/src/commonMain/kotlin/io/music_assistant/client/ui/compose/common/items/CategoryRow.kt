package io.music_assistant.client.ui.compose.common.items

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.DisplayString
import io.music_assistant.client.ui.compose.common.DpadRememberedFocus
import io.music_assistant.client.ui.compose.common.LocalDpadNavigation
import io.music_assistant.client.ui.compose.common.NoDpadStateLayer
import io.music_assistant.client.ui.compose.common.dpadFocusOutline
import io.music_assistant.client.ui.compose.common.providers.ProviderIconFetcher
import io.music_assistant.client.ui.compose.common.toDisplayString
import io.music_assistant.client.ui.compose.grid.GridItem
import io.music_assistant.client.ui.compose.grid.gridItemMinSize
import io.music_assistant.client.ui.compose.item.ItemList
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_view_all
import musicassistantclient.composeapp.generated.resources.cd_view_all
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T, U> CategoryRow(
    data: DataState<T>,
    itemCategoryProvider: (T) -> ItemCategory<U>,
    onNavigateClick: (AppMediaItem) -> Unit,
    containerItem: AppMediaItem? = null,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (U) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: ProviderIconFetcher,
    viewAllColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data is DataState.Data) {
        CategoryRow(
            itemCategory = itemCategoryProvider(data.data),
            onNavigateClick = onNavigateClick,
            containerItem = containerItem,
            onNavigateToList = onNavigateToList,
            onOptionSelected = onOptionSelected,
            onPlayClick = onPlayClick,
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            progressActions = progressActions,
            providerIconFetcher = providerIconFetcher,
            viewAllColor = viewAllColor,
        )
    } else if (data is DataState.Loading) {
        val placeholderColor by rememberInfiniteTransition().animateColor(
            initialValue = Color.Gray.copy(alpha = 0.1f),
            targetValue = Color.Gray.copy(alpha = 0.3f),
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse,
            ),
        )

        RowWithTitle(
            title = {
                PlaceHolderText(Modifier.widthIn(gridItemMinSize()), placeholderColor, LocalTextStyle.current)
            },
            actions = {},
            row = {
                repeat(PLACEHOLDER_ITEMS) {
                    item {
                        PlaceholderGridItem(placeholderColor)
                    }
                }
            },
        )
    }
}

@Composable
fun <T> CategoryRow(
    itemCategory: ItemCategory<T>,
    onNavigateClick: (AppMediaItem) -> Unit,
    containerItem: AppMediaItem? = null,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (T) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: ProviderIconFetcher,
    viewAllColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (itemCategory.items.isEmpty() && itemCategory.filter == null) {
        return
    }

    val title = itemCategory.title.string()
    // "View all" is at the far right of the title line, where Up from most tiles never lands: under
    // a D-pad, Up from any tile goes to it, or to the filter of a row without it.
    val actionFocus = remember { FocusRequester() }
    val hasAction = itemCategory.list != null || itemCategory.filter != null
    CategoryRow(
        title = title,
        upFocus = actionFocus.takeIf { hasAction && LocalDpadNavigation.current },
        actions = {
            if (itemCategory.filter != null) {
                FilterSelector(
                    label = itemCategory.filter.label.string(),
                    rowTitle = title,
                    onOptionSelected = onOptionSelected,
                    options = itemCategory.filter.options,
                    optionLabels = { itemCategory.filter.labelTransform(it).string() },
                    contentDescriptionResource = itemCategory.filter.contentDescription,
                    modifier = if (itemCategory.list == null) Modifier.focusRequester(actionFocus) else Modifier,
                )
            }

            if (itemCategory.list != null) {
                ViewAllButton(
                    rowTitle = title,
                    onNavigateToList = onNavigateToList,
                    itemList = itemCategory.list,
                    color = viewAllColor,
                    modifier = Modifier.focusRequester(actionFocus),
                )
            }
        },
        onNavigateClick = onNavigateClick,
        containerItem = containerItem,
        onPlayClick = onPlayClick,
        mediaItems = itemCategory.items,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
        providerIconFetcher = providerIconFetcher,
        rowTag = itemCategory.tag,
    )
}

@Composable
fun CategoryRow(
    title: String,
    actions: @Composable () -> Unit = {},
    upFocus: FocusRequester? = null,
    onNavigateClick: (AppMediaItem) -> Unit,
    containerItem: AppMediaItem? = null,
    onPlayClick: PlayHandler<AppMediaItem>,
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: ProviderIconFetcher,
    rowTag: String? = null,
) {
    val modifier = if (rowTag != null) {
        Modifier.testTag(rowTag)
    } else {
        Modifier
    }

    // Recommendation rows are server-curated and can repeat canonical item
    // Key by occurrence to avoid Compose's duplicate-key crash
    val itemKeys = remember(mediaItems) { mediaItems.lazyListOccurrenceKeys() }

    RowWithTitle(
        modifier = modifier,
        title = {
            Text(title)
        },
        actions = actions,
        upFocus = upFocus,
    ) {
        itemsIndexed(
            items = mediaItems,
            key = { index, _ -> itemKeys[index] },
            contentType = { _, item ->
                when (item) {
                    is Track -> "Track"
                    is Artist -> "Artist"
                    is Album -> "Album"
                    is Playlist -> "Playlist"
                    is Audiobook -> "Audiobook"
                    is MediaCollection -> "Collection"
                    is Podcast -> "Podcast"
                    is PodcastEpisode -> "Episode"
                    is RadioStation -> "RadioStation"
                    is Genre -> "Genre"
                    else -> "Unknown"
                }
            },
        ) { index, item ->
            DpadRememberedFocus(rowKey = rowTag ?: title, itemKey = itemKeys[index]) {
                when (item) {
                    is Artist -> ArtistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Album -> AlbumWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        navigateToItem = onNavigateClick,
                        containerItem = containerItem,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                        // Inside an artist the artist name repeats on every album, so show the year.
                        subtitle = if (containerItem is Artist) AlbumSubtitle.YEAR else AlbumSubtitle.ARTISTS,
                    )

                    is Playlist -> PlaylistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Podcast -> PodcastWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Track -> TrackWithMenu(
                        item = item,
                        navigateToItem = onNavigateClick,
                        containerItem = containerItem,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is PodcastEpisode -> PodcastEpisodeWithMenu(
                        item = item,
                        navigateToItem = onNavigateClick,
                        containerItem = containerItem,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Audiobook -> AudiobookWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    // Audiobookshelf "Continue series" / "Recent series" rows.
                    is MediaCollection -> MediaCollectionWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                    )

                    is RadioStation -> RadioWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Genre -> GenreWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun RowWithTitle(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    upFocus: FocusRequester? = null,
    row: LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth()
                // Room for the focus outline of "View all", which would be cut at the screen edge.
                .padding(start = 16.dp, end = if (LocalDpadNavigation.current) 16.dp else 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.titleMedium,
            ) {
                title()
            }

            Row {
                actions()
            }
        }

        val rowListState = rememberLazyListState()
        LazyRow(
            state = rowListState,
            // With a D-pad (Android TV, keyboard), Right on the last item stays there. Left the
            // row, the search would pick the nearest focusable to the right anywhere on screen,
            // often one scrolled out of view, and the focus would seem lost.
            modifier = Modifier
                .focusProperties {
                    onExit = {
                        when (requestedFocusDirection) {
                            FocusDirection.Right -> cancelFocusChange()
                            FocusDirection.Up -> upFocus?.requestFocus()
                        }
                    }
                }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            // A focused tile grows over its neighbors: a TV needs more room between them.
            horizontalArrangement = Arrangement.spacedBy(if (LocalDpadNavigation.current) 16.dp else 4.dp),
        ) {
            row()
        }
    }
}

data class ItemCategory<T>(
    val id: String,
    val title: DisplayString,
    val items: List<AppMediaItem>,
    val list: ItemList? = null,
    val filter: Filter<T>? = null,
    val lazyListKey: String = id,
    val tag: String? = null,
) {
    data class Filter<T>(
        val label: DisplayString,
        val options: List<T>,
        val labelTransform: (T) -> DisplayString,
        val contentDescription: StringResource? = null,
    )
}

@Composable
private fun ViewAllButton(
    rowTitle: String,
    onNavigateToList: (String, ItemList) -> Unit,
    itemList: ItemList,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val viewAllContentDescription = stringResource(Res.string.cd_view_all, rowTitle)
    NoDpadStateLayer {
        TextButton(
            modifier = modifier
                .dpadFocusOutline(RoundedCornerShape(50))
                .semantics {
                    contentDescription = viewAllContentDescription
                },
            onClick = {
                onNavigateToList(rowTitle, itemList)
            },
        ) {
            Text(
                text = stringResource(Res.string.action_view_all),
                color = color,
            )
        }
    }
}

@Composable
private fun <T> FilterSelector(
    label: String,
    rowTitle: String,
    onOptionSelected: (T) -> Unit,
    options: List<T>,
    optionLabels: @Composable (T) -> String,
    contentDescriptionResource: StringResource? = null,
    modifier: Modifier = Modifier,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }

        val chipContentDescription = if (contentDescriptionResource != null) {
            stringResource(
                contentDescriptionResource,
                rowTitle,
                label,
            )
        } else {
            null
        }

        FilterChip(
            modifier = modifier
                .semantics {
                    if (chipContentDescription != null) {
                        contentDescription = chipContentDescription
                    }
                },
            selected = true,
            onClick = { expanded = true },
            label = {
                Text(
                    label,
                    modifier = Modifier.widthIn(max = 96.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(optionLabels(it)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(it)
                    },
                )
            }
        }
    }
}

private const val PLACEHOLDER_ITEMS = 10

@Preview
@Composable
fun PreviewCategoryRowEmpty() {
    CategoryRow(
        title = "Category",
        onNavigateClick = {},
        onPlayClick = { _, _ -> },
        mediaItems = emptyList(),
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _, _ -> },
    )
}

@Preview
@Composable
fun PreviewCategoryLoading() {
    CategoryRow(
        data = DataState.Loading<List<AppMediaItem>>(),
        itemCategoryProvider = {
            ItemCategory<Nothing>(
                id = "blah",
                title = "blah".toDisplayString(),
                items = it,
            )
        },
        onNavigateClick = {},
        onPlayClick = { _, _ -> },
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _, _ -> },
    )
}

@Preview
@Composable
fun PreviewCategoryNoData() {
    CategoryRow(
        data = DataState.NoData<List<AppMediaItem>>(),
        itemCategoryProvider = {
            ItemCategory<Nothing>(
                id = "blah",
                title = "blah".toDisplayString(),
                items = it,
            )
        },
        onNavigateClick = {},
        onPlayClick = { _, _ -> },
        playlistActions = object : PlaylistActions {
            override suspend fun getEditablePlaylists(): List<Playlist> {
                TODO("Not yet implemented")
            }

            override fun addToPlaylist(itemUri: String?, playlist: Playlist) {
                TODO("Not yet implemented")
            }

            override suspend fun createPlaylist(name: String): Playlist? {
                TODO("Not yet implemented")
            }
        },
        libraryActions = object : LibraryActions {
            override fun onLibraryClick(item: AppMediaItem) {
                TODO("Not yet implemented")
            }

            override fun onFavoriteChange(item: AppMediaItem, favorite: Boolean?) {
                TODO("Not yet implemented")
            }
        },
        providerIconFetcher = { _, _, _ -> },
    )
}

@Composable
fun PlaceholderGridItem(
    color: Color,
) {
    GridItem(
        description = null,
        onClick = {  },
        onLongClick = {  },
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Spacer(Modifier.height(4.dp))

        PlaceHolderText(Modifier.fillMaxWidth(fraction = 0.9f), color, mediaItemTitleStyle())
        PlaceHolderText(Modifier.fillMaxWidth(fraction = 0.5f), color, mediaItemSubtitleStyle())
    }
}

@Composable
private fun PlaceHolderText(modifier: Modifier, color: Color, textStyle: TextStyle) {
    val padding = 2.dp
    val bodyMediumHeight = with(LocalDensity.current) {
        textStyle.lineHeight.toDp() - padding * 2
    }

    Box(
        modifier = modifier
            .padding(padding)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .height(bodyMediumHeight),
    )
}
