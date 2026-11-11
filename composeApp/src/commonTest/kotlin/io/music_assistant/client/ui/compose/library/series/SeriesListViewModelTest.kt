package io.music_assistant.client.ui.compose.library.series

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.FakeItemDetailsMainDataSource
import io.music_assistant.client.data.FakeItemDetailsServiceClient
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.SearchResultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesListViewModelTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A library with shown and hidden album series, where hiding a series moves it. */
    private class SeriesRepository(shown: List<String>, hidden: List<String>) : MediaItemRepository {
        val hiddenTitles = hidden.toMutableSet()
        val titles = (shown + hidden).toMutableList()
        val requests = mutableListOf<Request>()

        override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> {
            requests += request
            check(request.command == APICommands.MUSIC_ALBUM_GROUPS_SERIES) { "Unexpected $request" }
            val hidden = request.args?.get("hidden")?.jsonPrimitive?.content == "true"
            return Result.success(
                titles.filter { (it in hiddenTitles) == hidden }.map { MediaCollection.albumSeries(it) },
            )
        }

        override suspend fun setAlbumSeriesHidden(itemId: String, hidden: Boolean): Result<Unit> {
            val title = titles.first { MediaCollection.albumSeriesId(it) == itemId }
            if (hidden) hiddenTitles += title else hiddenTitles -= title
            return Result.success(Unit)
        }

        override suspend fun fetchAllMediaItems(request: Request): List<AppMediaItem>? =
            fetchMediaItems(request).getOrNull()

        override val itemChanges: SharedFlow<MediaItemChange> = MutableSharedFlow()
        override suspend fun search(request: Request): Result<SearchResultData> = error("unused")
        override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> = error("unused")
        override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> = error("unused")
        override suspend fun fetchEpisodeTranscript(episode: PodcastEpisode): Result<Lyrics?> = error("unused")
        override fun publishLocalChange(change: MediaItemChange) = Unit
    }

    private fun TestScope.createViewModel(repository: SeriesRepository): SeriesListViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return SeriesListViewModel(
            mediaItemRepository = repository,
            mainDataSource = FakeItemDetailsMainDataSource(),
            playDispatcher = PlayDispatcher(FakeItemDetailsServiceClient(), backgroundScope),
        )
    }

    private fun SeriesListViewModel.shownNames(): List<String>? =
        state.value.series.dataOrNull?.map { it.name }

    @Test
    fun listsTheShownSeriesAndSwitchesToTheHiddenOnes() = runTest {
        val viewModel = createViewModel(SeriesRepository(shown = listOf("Saga"), hidden = listOf("Cycle")))
        runCurrent()
        assertEquals(listOf("Saga"), viewModel.shownNames())

        viewModel.onShowHiddenToggled()
        runCurrent()
        assertEquals(listOf("Cycle"), viewModel.shownNames())
    }

    @Test
    fun hidingASeriesTakesItOutOfTheListAndShowingItBringsItBack() = runTest {
        val repository = SeriesRepository(shown = listOf("Saga", "Cycle"), hidden = emptyList())
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.onToggleHidden(MediaCollection.albumSeries("Saga"))
        runCurrent()
        assertEquals(listOf("Cycle"), viewModel.shownNames())
        assertEquals(setOf("Saga"), repository.hiddenTitles)

        viewModel.onShowHiddenToggled()
        runCurrent()
        assertEquals(listOf("Saga"), viewModel.shownNames())

        viewModel.onToggleHidden(MediaCollection.albumSeries("Saga"))
        runCurrent()
        assertEquals(emptyList(), viewModel.shownNames())
        assertEquals(emptySet(), repository.hiddenTitles)
    }
}
