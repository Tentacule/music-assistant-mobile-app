package io.music_assistant.client.ui.compose.library.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.ItemDetailsMainDataSource
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.series_hidden_failed
import org.jetbrains.compose.resources.getString

/**
 * The album series of the library (`music/album_groups/series`). [State.showHidden] lists the
 * series that a user hid instead of the shown ones. Hiding a series changes only this list.
 */
class SeriesListViewModel(
    private val mediaItemRepository: MediaItemRepository,
    private val mainDataSource: ItemDetailsMainDataSource,
    private val playDispatcher: PlayDispatcher,
) : ViewModel() {
    data class State(
        val showHidden: Boolean = false,
        val searchQuery: String = "",
        val series: DataState<List<MediaCollection>> = DataState.Loading(),
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onShowHiddenToggled() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        load()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        load()
    }

    /** Hides [series] from the list, or shows it again when the list holds the hidden series. */
    fun onToggleHidden(series: MediaCollection) {
        val hide = !_state.value.showHidden
        viewModelScope.launch {
            mediaItemRepository.setAlbumSeriesHidden(series.itemId, hide)
                .onSuccess {
                    // The series leaves the list it was in.
                    _state.update { state ->
                        state.copy(
                            series = state.series.dataOrNull
                                ?.let { list -> DataState.Data(list.filterNot { it.itemId == series.itemId }) }
                                ?: state.series,
                        )
                    }
                }
                .onFailure {
                    Logger.withTag(TAG).e(it) { "Could not change series ${series.itemId}" }
                    _toasts.emit(getString(Res.string.series_hidden_failed))
                }
        }
    }

    fun onPlayClick(item: AppMediaItem, mode: PlayMode) {
        val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return
        item.mediaUri?.let { mediaUri ->
            playDispatcher.play(
                PlayRequest(
                    queueOrPlayerId = queueId,
                    media = listOf(mediaUri),
                    option = mode.option,
                    shuffle = mode.shuffle,
                    startFromBeginning = mode.startFromBeginning,
                ),
            )
        }
    }

    private fun load() {
        val (showHidden, query) = _state.value.let { it.showHidden to it.searchQuery }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(series = DataState.Loading()) }
            val result = mediaItemRepository.fetchMediaItems(Request.Album.seriesList(showHidden, query))
            _state.update { state ->
                state.copy(
                    series = result.fold(
                        onSuccess = { items -> DataState.Data(items.filterIsInstance<MediaCollection>()) },
                        onFailure = {
                            Logger.withTag(TAG).e(it) { "Could not load the series" }
                            DataState.Error()
                        },
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "SeriesListViewModel"
    }
}
