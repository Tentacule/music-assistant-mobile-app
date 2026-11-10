package io.music_assistant.client.ui.compose.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.library.ItemListContent
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.sort_name
import musicassistantclient.composeapp.generated.resources.sort_series_order
import musicassistantclient.composeapp.generated.resources.sort_year
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MbSeriesAlbumsScreen(
    viewModel: MbSeriesAlbumsViewModel,
    actionsViewModel: ActionsViewModel,
    contentPadding: PaddingValues,
    onNavigateClick: (AppMediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TopBarLayout(
        topBar = {
            TopAppBar(
                title = { Text(state.seriesName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    SeriesSortChip(
                        current = state.sort,
                        onSortChanged = viewModel::onSortChanged,
                    )
                },
            )
        },
    ) {
        ItemListContent(
            data = DataState.Data(state.sortedAlbums),
            onNavigateClick = onNavigateClick,
            onPlayClick = { item, mode -> (item as? Album)?.let { viewModel.onPlayClick(it, mode) } },
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            contentPadding = contentPadding,
            viewMode = ViewMode.LIST,
            clickContext = ClickContext.LIBRARY,
        )
    }
}

@Composable
private fun RowScope.SeriesSortChip(
    current: MbSeriesAlbumsViewModel.SortOrder,
    onSortChanged: (MbSeriesAlbumsViewModel.SortOrder) -> Unit,
) {
    val labelOrder = stringResource(Res.string.sort_series_order)
    val labelName = stringResource(Res.string.sort_name)
    val labelYear = stringResource(Res.string.sort_year)

    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        MbSeriesAlbumsViewModel.SortOrder.SERIES_ORDER -> labelOrder
        MbSeriesAlbumsViewModel.SortOrder.NAME -> labelName
        MbSeriesAlbumsViewModel.SortOrder.DATE -> labelYear
    }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(label) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MbSeriesAlbumsViewModel.SortOrder.entries.forEach { order ->
                val itemLabel = when (order) {
                    MbSeriesAlbumsViewModel.SortOrder.SERIES_ORDER -> labelOrder
                    MbSeriesAlbumsViewModel.SortOrder.NAME -> labelName
                    MbSeriesAlbumsViewModel.SortOrder.DATE -> labelYear
                }
                DropdownMenuItem(
                    text = { Text(itemLabel) },
                    onClick = {
                        expanded = false
                        onSortChanged(order)
                    },
                )
            }
        }
    }
}
