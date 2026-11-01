package io.music_assistant.client.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.TvUserActivity
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.providers.providerIconFetcher
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreen
import io.music_assistant.client.ui.compose.home.HomeScreenState
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.selectedPlayer
import io.music_assistant.client.ui.compose.item.ItemDetailsScreen
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.ItemListScreen
import io.music_assistant.client.ui.compose.item.ItemListViewModel
import io.music_assistant.client.ui.compose.item.ViewModeViewModel
import io.music_assistant.client.ui.compose.item.opensItemDetails
import io.music_assistant.client.ui.compose.library.AiRadioScreen
import io.music_assistant.client.ui.compose.library.AiRadioViewModel
import io.music_assistant.client.ui.compose.library.BrowseScreen
import io.music_assistant.client.ui.compose.library.BrowseViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategoriesViewModel
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.LibraryListScreen
import io.music_assistant.client.ui.compose.library.LibraryListViewModel
import io.music_assistant.client.ui.compose.library.icon
import io.music_assistant.client.ui.compose.library.stringResource
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import io.music_assistant.client.ui.compose.search.GlobalSearchRequest
import io.music_assistant.client.ui.compose.search.SearchScreen
import io.music_assistant.client.ui.compose.search.SearchScreenState
import io.music_assistant.client.ui.compose.search.SearchViewModel
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.compose.resources.stringResource as composeStringResource

/** Where the content area of the TV app is. [id] keeps stacked pages of the same kind distinct. */
private sealed interface TvRoute : NavKey {
    data object Home : TvRoute
    data object Search : TvRoute
    data object Queue : TvRoute
    data class Library(val mediaType: MediaType) : TvRoute
    data object AiRadio : TvRoute
    data class Browse(val path: String?, val title: String?, val id: Long = nextRouteId()) : TvRoute
    data class ItemDetails(
        val itemId: String,
        val mediaType: MediaType,
        val providerId: String,
        val id: Long = nextRouteId(),
    ) : TvRoute
    data class ItemPage(
        val title: String,
        val itemList: ItemList,
        val clickContext: ClickContext,
        val id: Long = nextRouteId(),
    ) : TvRoute
}

private val routeIds = AtomicLong()
private fun nextRouteId() = routeIds.incrementAndGet()

/**
 * The main screen of the TV app: the collapsible menu, the pages of the phone app (home, search,
 * library, item details) in the content area, the player bar, and the Now playing mode over all of it.
 *
 * The Now playing mode is drawn over the app, which stays composed underneath: closing it lands
 * exactly where the user left the app. Neither mode owns the playback, which runs in the playback
 * service and the shared local player, so switching never interrupts the music.
 */
@Composable
fun TvMainScreen(openSettings: () -> Unit) {
    val homeScreenViewModel: HomeScreenViewModel = koinViewModel()
    val actionsViewModel: ActionsViewModel = koinViewModel()
    val viewModeViewModel: ViewModeViewModel = koinViewModel()
    val providerViewModel: ProviderViewModel = koinViewModel()
    val libraryCategoriesViewModel: LibraryCategoriesViewModel = koinViewModel()
    val dataSource: MainDataSource = koinInject()
    val prefs: TvPreferences = koinInject()
    val userActivity: TvUserActivity = koinInject()
    val toastState = rememberToastState()

    val localPlayer by dataSource.localPlayer.collectAsStateWithLifecycle()
    val playersState by homeScreenViewModel.playersState.collectAsStateWithLifecycle()

    // The TV plays on itself by default: each launch starts on the local player. The player picker
    // can then send the music to any other player; the reused phone pages (which play on the
    // selected player), the player bar, Now playing and the queue all follow the choice.
    LaunchedEffect(playersState, localPlayer) {
        if (localPlayerSelectedThisLaunch) return@LaunchedEffect
        val local = localPlayer ?: return@LaunchedEffect
        val data = playersState as? HomeScreenViewModel.PlayersState.Data ?: return@LaunchedEffect
        if (data.selectedPlayer?.playerId != local.playerId) homeScreenViewModel.selectPlayer(local.player)
        localPlayerSelectedThisLaunch = true
    }
    val playersData = playersState as? HomeScreenViewModel.PlayersState.Data
    val selectedPlayer = playersData?.selectedPlayer
    // The local player's own state (optimistic, from the Sendspin adapter) when it is the one chosen.
    val activePlayer = if (selectedPlayer == null || selectedPlayer.isLocal) localPlayer ?: selectedPlayer else selectedPlayer
    var pickingPlayer by remember { mutableStateOf(false) }
    // Traces the queue's size as playback goes, to see the server top a smart mix or autoplay queue
    // up (it does when fewer than 5 tracks remain after the current one).
    val queueSize = activePlayer?.queueItems?.size
    val queueIndex = activePlayer?.queueInfo?.currentIndex
    LaunchedEffect(queueSize, queueIndex) {
        val info = activePlayer?.queueInfo ?: return@LaunchedEffect
        Logger.withTag("TvQueue").i {
            "Queue ${info.id}: $queueSize items, current $queueIndex, " +
                "remaining ${queueSize?.let { size -> queueIndex?.let { size - it - 1 } }}, " +
                "dynamic=${info.isDynamicPlaylist} autoplay=${info.autoPlayEnabled}"
        }
    }
    val playerName = activePlayer?.let { player ->
        if (player.isLocal) stringResource(R.string.tv_this_tv, player.player.name) else player.player.name
    }

    val dynamicLockedMessage = stringResource(R.string.tv_dynamic_queue_locked)
    val onPlayerAction: (PlayerAction) -> Unit = { action ->
        activePlayer?.let { player ->
            val locked = action is PlayerAction.ToggleShuffle || action is PlayerAction.ToggleRepeatMode ||
                action is PlayerAction.ToggleDontStopTheMusic
            // The server refuses these on a smart mix; say why instead of letting the toggle bounce back.
            if (locked && player.queueInfo?.isDynamicPlaylist == true) {
                toastState.showToast(dynamicLockedMessage, ToastDuration.LONG)
            } else {
                dataSource.playerAction(player, action)
            }
        }
    }

    // Server errors (a refused command...) as toasts over every screen, as on the phone.
    val errorBus: ErrorMessageBus = koinInject()
    LaunchedEffect(errorBus) {
        errorBus.messages.collect { message ->
            toastState.showToast(message.take(MAX_ERROR_LENGTH), ToastDuration.LONG)
        }
    }

    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<TvRoute>(TvRoute.Home) }
    var pendingSearch by remember { mutableStateOf<GlobalSearchRequest?>(null) }

    // A page change removes the focused element, and Compose then hands the focus to the first
    // focusable of the screen: the menu, which expands. Instead the focus goes into the content,
    // to the element focused there before (back from Now playing) or to the first one of a new page.
    // The new page may still be loading, hence the retries until the content takes the focus.
    val contentFocus = remember { FocusRequester() }
    val playerBarFocus = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    val currentRoute = backStack.lastOrNull()
    // Also bumped when the system, not the user, puts the focus in the menu (at start-up, or when
    // the focused element goes away).
    var focusContentRequest by remember { mutableIntStateOf(0) }
    // Now playing leaves the focus where it was (it reads the keys through [nowPlayingKeys]), so
    // closing it returns to the element that had the focus, in the page, the player bar or the menu.
    val nowPlayingKeys = remember { NowPlayingKeys() }
    val closeNowPlaying: () -> Unit = { nowPlaying = false }
    LaunchedEffect(currentRoute, focusContentRequest) {
        if (nowPlaying) return@LaunchedEffect
        repeat(FOCUS_ATTEMPTS) {
            delay(FOCUS_RETRY_MS)
            if (contentHasFocus) return@LaunchedEffect
            val moved = runCatching { contentFocus.requestFocus() }.getOrDefault(false)
            Logger.withTag("TvFocus").d { "Focus to content: $moved (attempt ${it + 1})" }
        }
    }

    UnsupportedInstallDismissalKeeper(homeScreenViewModel, prefs)

    // Idle remote during playback: switch to the Now playing mode by itself, like a screensaver.
    val lastKeyAt by userActivity.lastKeyAt.collectAsStateWithLifecycle()
    val idleDelay by prefs.nowPlayingDelay.collectAsStateWithLifecycle()
    val isPlaying = activePlayer?.player?.isPlaying == true
    // Not over an open menu or dialog: those are windows above the app, which then has no window
    // focus. The mode waits until they close.
    val view = LocalView.current
    LaunchedEffect(lastKeyAt, idleDelay, isPlaying, nowPlaying) {
        val millis = idleDelay.millis ?: return@LaunchedEffect
        if (!isPlaying || nowPlaying) return@LaunchedEffect
        delay(millis)
        while (!view.hasWindowFocus()) delay(WINDOW_FOCUS_POLL_MS)
        nowPlaying = true
    }

    fun navigateToItem(item: AppMediaItem) {
        when {
            item is RecommendationFolder && item.isParentLink -> backStack.removeLastOrNull()
            item is RecommendationFolder -> backStack += TvRoute.Browse(item.path ?: item.uri, item.displayName)
            item.opensItemDetails -> backStack += TvRoute.ItemDetails(item.itemId, item.mediaType, item.provider)
        }
    }

    fun goToRoot(route: TvRoute) {
        backStack.clear()
        backStack += route
    }

    val nowPlayingInfo = activePlayer?.nowPlayingInfo()
    LaunchedEffect(userActivity) {
        userActivity.nowPlayingToggles.collect { if (nowPlaying) closeNowPlaying() else nowPlaying = true }
    }

    val categories by libraryCategoriesViewModel.state.collectAsStateWithLifecycle()
    val railItems = buildList {
        add(TvRailItem("home", Icons.Default.Home, stringResource(R.string.tv_nav_home), 0) { goToRoot(TvRoute.Home) })
        // Right under Home, so the player is two key presses away from any page.
        add(
            TvRailItem(
                key = "now_playing",
                icon = Icons.Default.MusicNote,
                label = stringResource(R.string.tv_nav_now_playing),
                group = 0,
                artworkUrl = nowPlayingInfo?.imageUrl,
            ) { nowPlaying = true },
        )
        // Jumps into the player bar at the bottom, however long the page above it is.
        if (nowPlayingInfo != null) {
            add(
                TvRailItem(
                    key = "player_controls",
                    icon = Icons.Default.PlayCircle,
                    label = stringResource(R.string.tv_nav_player_controls),
                    group = 0,
                ) { runCatching { playerBarFocus.requestFocus() } },
            )
        }
        add(
            TvRailItem(
                "search",
                Icons.Default.Search,
                stringResource(R.string.tv_nav_search),
                0,
            ) { goToRoot(TvRoute.Search) },
        )
        categories.categories.filter { it.enabled }.forEach { state ->
            val category = state.libraryCategory
            // On the TV, the Tracks entry is the queue: a whole-library track list is of little use
            // with a remote, the tracks that play are.
            if (category == LibraryCategory.TRACKS) {
                add(
                    TvRailItem(
                        key = "queue",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = stringResource(R.string.tv_nav_queue),
                        group = 1,
                    ) { goToRoot(TvRoute.Queue) },
                )
                return@forEach
            }
            val route = when (category) {
                LibraryCategory.BROWSE -> TvRoute.Browse(path = null, title = null)
                LibraryCategory.AI_RADIO -> TvRoute.AiRadio
                else -> category.mediaType?.let { TvRoute.Library(it) }
            } ?: return@forEach
            add(
                TvRailItem(category.name, category.icon(), composeStringResource(category.stringResource()), 1) {
                    goToRoot(route)
                },
            )
        }
        add(
            TvRailItem(
                key = "player",
                icon = Icons.Default.Speaker,
                label = stringResource(R.string.tv_nav_player),
                group = 2,
            ) { pickingPlayer = true },
        )
        add(
            TvRailItem(
                "settings",
                Icons.Default.Settings,
                stringResource(R.string.tv_nav_settings),
                2,
                onClick = openSettings,
            ),
        )
    }
    val selectedKey = when (val root = backStack.firstOrNull()) {
        TvRoute.Home -> "home"
        TvRoute.Search -> "search"
        TvRoute.Queue -> "queue"
        is TvRoute.Library -> LibraryCategory.entries.firstOrNull { it.mediaType == root.mediaType }?.name
        TvRoute.AiRadio -> LibraryCategory.AI_RADIO.name
        is TvRoute.Browse -> LibraryCategory.BROWSE.name
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> nowPlaying && (nowPlayingKeys.handler?.invoke(event) ?: false) },
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = TvRailCollapsedWidth)) {
            // Like Plex: what plays, at the top right of every page (Up from the page's top bar).
            nowPlayingInfo?.let { info ->
                NowPlayingPill(
                    info = info,
                    playerName = playerName.orEmpty(),
                    onClick = { nowPlaying = true },
                    modifier = Modifier.align(Alignment.End).padding(top = 16.dp, end = 48.dp),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusRequester(contentFocus)
                    .focusRestorer()
                    .focusGroup(),
            ) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        val padding = PaddingValues()
                        val onBack: () -> Unit = { backStack.removeLastOrNull() }
                        entry<TvRoute.Home> {
                            HomeScreen(
                                homeScreenViewModel = homeScreenViewModel,
                                contentPadding = padding,
                                onNavigateClick = ::navigateToItem,
                                providerIconFetcher = providerViewModel.providerIconFetcher(),
                                actionsViewModel = actionsViewModel,
                                state = HomeScreenState.create(),
                                onOpenSettings = openSettings,
                            )
                        }
                        entry<TvRoute.Search> {
                            SearchScreen(
                                searchViewModel = koinViewModel<SearchViewModel>(),
                                onNavigateToItem = { id, type, provider ->
                                    backStack += TvRoute.ItemDetails(id, type, provider)
                                },
                                actionsViewModel = actionsViewModel,
                                providerViewModel = providerViewModel,
                                contentPadding = padding,
                                state = SearchScreenState.create(),
                                pendingSearch = pendingSearch,
                                onSearchConsumed = { pendingSearch = null },
                            )
                        }
                        entry<TvRoute.Library> { route ->
                            LibraryListScreen(
                                libraryListViewModel = koinViewModel<LibraryListViewModel> {
                                    parametersOf(route.mediaType)
                                },
                                viewModeViewModel = viewModeViewModel,
                                contentPadding = padding,
                                actionsViewModel = actionsViewModel,
                                onBack = onBack,
                                onGlobalSearch = { query ->
                                    pendingSearch = GlobalSearchRequest(query, route.mediaType)
                                    goToRoot(TvRoute.Search)
                                },
                                onNavigateClick = ::navigateToItem,
                            )
                        }
                        entry<TvRoute.Queue> {
                            TvQueueScreen(
                                playerData = activePlayer,
                                homeScreenViewModel = homeScreenViewModel,
                                onGoToLibrary = { goToRoot(TvRoute.Home) },
                            )
                        }
                        entry<TvRoute.AiRadio> {
                            AiRadioScreen(
                                viewModel = koinViewModel<AiRadioViewModel>(),
                                contentPadding = padding,
                                onBack = onBack,
                            )
                        }
                        entry<TvRoute.Browse> { route ->
                            BrowseScreen(
                                browseViewModel = koinViewModel<BrowseViewModel> { parametersOf(route.path) },
                                title = route.title,
                                contentPadding = padding,
                                actionsViewModel = actionsViewModel,
                                onNavigateClick = ::navigateToItem,
                                onBack = onBack,
                            )
                        }
                        entry<TvRoute.ItemDetails> { route ->
                            ItemDetailsScreen(
                                itemDetailsViewModel = koinViewModel<ItemDetailsViewModel> {
                                    parametersOf(route.itemId, route.mediaType, route.providerId)
                                },
                                viewModeViewModel = viewModeViewModel,
                                actionsViewModel = actionsViewModel,
                                providerViewModel = providerViewModel,
                                onBack = onBack,
                                onNavigateToItem = { id, type, provider ->
                                    backStack += TvRoute.ItemDetails(id, type, provider)
                                },
                                onNavigateToList = { title, itemList, clickContext ->
                                    backStack += TvRoute.ItemPage(title, itemList, clickContext)
                                },
                                contentPadding = padding,
                            )
                        }
                        entry<TvRoute.ItemPage> { route ->
                            ItemListScreen(
                                title = route.title,
                                mediaType = route.itemList.mediaType,
                                sortContext = route.itemList.sortContext,
                                itemListViewModel = koinViewModel<ItemListViewModel> {
                                    parametersOf(route.itemList)
                                },
                                viewModeViewModel = viewModeViewModel,
                                actionsViewModel = actionsViewModel,
                                onNavigateClick = ::navigateToItem,
                                onBack = onBack,
                                contentPadding = padding,
                                clickContext = route.clickContext,
                            )
                        }
                    },
                )
            }
            TvPlayerBar(
                playerData = activePlayer,
                homeScreenViewModel = homeScreenViewModel,
                onAction = onPlayerAction,
                onOpenNowPlaying = { nowPlaying = true },
                onPickPlayer = { pickingPlayer = true },
                onOpenQueue = { goToRoot(TvRoute.Queue) },
                playPauseFocus = playerBarFocus,
            )
        }

        TvNavigationRail(
            items = railItems,
            selectedKey = selectedKey,
            modifier = Modifier.align(Alignment.CenterStart),
            acceptsFocus = {
                // Only D-pad Left (from the edge of the content) opens the menu.
                val asked = userActivity.lastKeyCode == KeyEvent.KEYCODE_DPAD_LEFT
                if (!asked) focusContentRequest++
                asked
            },
        )
        }

        if (nowPlaying) {
            NowPlayingScreen(
                playerData = activePlayer,
                homeScreenViewModel = homeScreenViewModel,
                onAction = onPlayerAction,
                onClose = closeNowPlaying,
                onNavigate = { item ->
                    closeNowPlaying()
                    navigateToItem(item)
                },
                keys = nowPlayingKeys,
                onOpenQueue = {
                    closeNowPlaying()
                    goToRoot(TvRoute.Queue)
                },
            )
        }
        if (pickingPlayer) {
            TvPlayerPicker(
                players = playersData?.playerData.orEmpty(),
                selectedPlayerId = selectedPlayer?.playerId,
                onPick = { homeScreenViewModel.selectPlayer(it.player) },
                onDismiss = { pickingPlayer = false },
            )
        }
        ToastHost(toastState = toastState)
    }

    // Back on the root of a section returns to Home before leaving the app.
    BackHandler(enabled = !nowPlaying && backStack.size == 1 && backStack.first() != TvRoute.Home) {
        goToRoot(TvRoute.Home)
    }
}

/**
 * Keeps the dismissal of the "unsupported installation" warning across launches: replays a saved
 * dismissal, and saves one when the warning goes away while the server still reports it.
 */
@Composable
private fun UnsupportedInstallDismissalKeeper(homeScreenViewModel: HomeScreenViewModel, prefs: TvPreferences) {
    val serviceClient: ServiceClient = koinInject()
    val shownFor by homeScreenViewModel.unsupportedInstallServerId.collectAsStateWithLifecycle()
    var lastShown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shownFor) {
        val serverId = shownFor
        when {
            serverId != null && prefs.isUnsupportedInstallDismissed(serverId) ->
                homeScreenViewModel.dismissUnsupportedInstall(serverId)

            serverId != null -> lastShown = serverId

            else -> {
                val stillReported = (serviceClient.sessionState.value as? HasConnectionData)
                    ?.serverInfo?.takeIf { it.unsupportedInstall }?.serverId
                lastShown?.takeIf { it == stillReported }?.let(prefs::setUnsupportedInstallDismissed)
                lastShown = null
            }
        }
    }
}

private const val FOCUS_ATTEMPTS = 40

// Once per process: the app starts on this TV, and keeps the player chosen afterwards.
private var localPlayerSelectedThisLaunch = false
private const val WINDOW_FOCUS_POLL_MS = 500L
private const val MAX_ERROR_LENGTH = 150
private const val FOCUS_RETRY_MS = 150L
