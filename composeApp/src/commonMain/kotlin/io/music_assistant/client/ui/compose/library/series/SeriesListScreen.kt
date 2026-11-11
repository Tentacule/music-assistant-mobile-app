package io.music_assistant.client.ui.compose.library.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.DpadRememberedFocus
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.items.ExtraMenuEntry
import io.music_assistant.client.ui.compose.common.items.MediaCollectionWithMenu
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.grid.gridItemMinSize
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.compose.search.SearchInput
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_close
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_quick_search
import musicassistantclient.composeapp.generated.resources.nav_series
import musicassistantclient.composeapp.generated.resources.search_query_label
import musicassistantclient.composeapp.generated.resources.series_hidden_empty
import musicassistantclient.composeapp.generated.resources.series_list_empty
import musicassistantclient.composeapp.generated.resources.series_load_failed
import musicassistantclient.composeapp.generated.resources.series_menu_hide
import musicassistantclient.composeapp.generated.resources.series_menu_show
import musicassistantclient.composeapp.generated.resources.series_show_hidden
import org.jetbrains.compose.resources.stringResource

/**
 * The album series of the library, as a grid. The top bar switches to the hidden series. The
 * menu of a series hides it from this list, or shows it again, for a user who can edit the library.
 */
@Composable
fun SeriesListScreen(
    viewModel: SeriesListViewModel,
    actionsViewModel: ActionsViewModel,
    contentPadding: PaddingValues,
    onNavigateClick: (MediaCollection) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastState()
    LaunchedEffect(Unit) { viewModel.toasts.collect { toastState.showToast(it) } }
    LaunchedEffect(Unit) { actionsViewModel.toasts.collect { toastState.showToast(it) } }

    TopBarLayout(
        topBar = {
            SeriesTopBar(
                showHidden = state.showHidden,
                onToggleShowHidden = viewModel::onShowHiddenToggled,
                searchQuery = state.searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onBack = onBack,
            )
        },
    ) {
        when (val series = state.series) {
            is DataState.Loading -> CenteredProgress()
            is DataState.Data -> if (series.data.isEmpty()) {
                val empty = if (state.showHidden) Res.string.series_hidden_empty else Res.string.series_list_empty
                ScreenMessage(stringResource(empty))
            } else {
                SeriesGrid(
                    series = series.data,
                    showHidden = state.showHidden,
                    contentPadding = contentPadding,
                    onNavigateClick = onNavigateClick,
                    onToggleHidden = viewModel::onToggleHidden.takeIf { LocalGrantedScopes.current.canEditLibrary },
                    onPlayClick = viewModel::onPlayClick,
                    actionsViewModel = actionsViewModel,
                )
            }
            else -> ScreenMessage(stringResource(Res.string.series_load_failed))
        }
        ToastHost(
            toastState = toastState,
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun SeriesGrid(
    series: List<MediaCollection>,
    showHidden: Boolean,
    contentPadding: PaddingValues,
    onNavigateClick: (MediaCollection) -> Unit,
    onToggleHidden: ((MediaCollection) -> Unit)?,
    onPlayClick: (MediaCollection, PlayMode) -> Unit,
    actionsViewModel: ActionsViewModel,
) {
    val toggleLabel = if (showHidden) Res.string.series_menu_show else Res.string.series_menu_hide
    ProvideClickActions(ClickContext.LIBRARY) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(minSize = gridItemMinSize()),
            contentPadding = contentPadding + PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(series, key = { it.itemId }) { item ->
                DpadRememberedFocus(rowKey = "series", itemKey = item.itemId) {
                    MediaCollectionWithMenu(
                        item = item,
                        viewMode = ViewMode.GRID,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = actionsViewModel,
                        extraEntry = onToggleHidden?.let { toggle ->
                            ExtraMenuEntry(toggleLabel) { toggle(item) }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesTopBar(
    showHidden: Boolean,
    onToggleShowHidden: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showSearch by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    TopAppBar(
        title = {
            if (showSearch) {
                SearchInput(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    placeholder = stringResource(Res.string.search_query_label),
                )
            } else {
                Text(stringResource(Res.string.nav_series))
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
            }
        },
        actions = {
            IconToggleButton(checked = showHidden, onCheckedChange = { onToggleShowHidden() }) {
                Icon(Icons.Default.VisibilityOff, stringResource(Res.string.series_show_hidden))
            }
            IconButton(
                onClick = {
                    if (showSearch) onSearchQueryChanged("")
                    showSearch = !showSearch
                },
            ) {
                if (showSearch) {
                    Icon(Icons.Default.Close, stringResource(Res.string.cd_close))
                } else {
                    Icon(Icons.Default.Search, stringResource(Res.string.library_quick_search))
                }
            }
        },
    )
}

@Composable
private fun ScreenMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
