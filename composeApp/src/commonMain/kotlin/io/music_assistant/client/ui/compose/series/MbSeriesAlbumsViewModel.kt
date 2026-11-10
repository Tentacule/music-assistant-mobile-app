package io.music_assistant.client.ui.compose.series

import androidx.lifecycle.ViewModel
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.server.ServerAlbumSeriesInfo
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.data.play.PlayRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MbSeriesAlbumsViewModel(
    val seriesId: String,
    val seriesName: String,
    navigationState: MbSeriesNavigationState,
    private val mainDataSource: MainDataSource,
    private val playDispatcher: PlayDispatcher,
) : ViewModel() {

    enum class SortOrder { SERIES_ORDER, NAME, DATE }

    data class State(
        val seriesName: String,
        val albums: List<Pair<Album, ServerAlbumSeriesInfo>>,
        val sort: SortOrder = SortOrder.SERIES_ORDER,
    ) {
        val sortedAlbums: List<Album>
            get() = when (sort) {
                SortOrder.SERIES_ORDER -> albums
                    .sortedWith(compareBy { it.second.orderNumber?.toIntOrNull() ?: Int.MAX_VALUE })
                    .map { it.first }
                SortOrder.NAME -> albums.sortedBy { it.first.displayName }.map { it.first }
                SortOrder.DATE -> albums.sortedByDescending { it.first.year }.map { it.first }
            }
    }

    private val _state = MutableStateFlow(
        State(
            seriesName = seriesName,
            albums = navigationState.albumsBySeriesId[seriesId] ?: emptyList(),
        ),
    )
    val state = _state.asStateFlow()

    fun onSortChanged(sort: SortOrder) {
        _state.update { it.copy(sort = sort) }
    }

    fun onPlayClick(album: Album, mode: PlayMode) {
        val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return
        album.mediaUri?.let { uri ->
            playDispatcher.play(
                PlayRequest(
                    queueOrPlayerId = queueId,
                    media = listOf(uri),
                    option = mode.option,
                    endlessMixMode = false,
                    shuffle = mode.shuffle,
                    startFromBeginning = mode.startFromBeginning,
                ),
            )
        }
    }
}
