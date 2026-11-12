package io.music_assistant.client.tv.ui

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
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
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.tv.ExitOnBackBehavior
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvMenuEntry
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.TvUserActivity
import io.music_assistant.client.tv.accounts.TvAccountManager
import io.music_assistant.client.ui.compose.common.ProvideDpadFocusMemory
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
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
import io.music_assistant.client.ui.compose.library.LibraryListScreen
import io.music_assistant.client.ui.compose.library.LibraryListViewModel
import io.music_assistant.client.ui.compose.library.series.SeriesListScreen
import io.music_assistant.client.ui.compose.library.series.SeriesListViewModel
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

/** Where the content area of the TV app is. [id] keeps stacked pages of the same kind distinct. */
private sealed interface TvRoute : NavKey {
    data object Home : TvRoute
    data object Search : TvRoute
    data object Queue : TvRoute
    data class Library(val mediaType: MediaType) : TvRoute
    data object AiRadio : TvRoute
    data object Series : TvRoute
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
 * library, item details) in the content area, and the Now playing mode over all of it. Now playing
 * holds the playback controls: there is no player bar, so the pages keep the whole height.
 *
 * The Now playing mode is drawn over the app, which stays composed underneath: closing it lands
 * exactly where the user left the app. Neither mode owns the playback, which runs in the playback
 * service and the shared local player, so switching never interrupts the music.
 */
@Composable
fun TvMainScreen(openSettings: () -> Unit, openAccounts: () -> Unit) {
    val homeScreenViewModel: HomeScreenViewModel = koinViewModel()
    val actionsViewModel: ActionsViewModel = koinViewModel()
    val viewModeViewModel: ViewModeViewModel = koinViewModel()
    val providerViewModel: ProviderViewModel = koinViewModel()
    val dataSource: MainDataSource = koinInject()
    val prefs: TvPreferences = koinInject()
    val userActivity: TvUserActivity = koinInject()
    val toastState = rememberToastState()

    val localPlayer by dataSource.localPlayer.collectAsStateWithLifecycle()
    val playersState by homeScreenViewModel.playersState.collectAsStateWithLifecycle()

    // On first launch, restore the last used player if still available. Fall back to the TV default
    // from prefs, then to the local player. The local player is no longer forced on every launch.
    LaunchedEffect(playersState, localPlayer) {
        if (localPlayerSelectedThisLaunch) return@LaunchedEffect
        val data = playersState as? HomeScreenViewModel.PlayersState.Data ?: return@LaunchedEffect
        localPlayerSelectedThisLaunch = true
        // MainDataSource already restores lastSelectedPlayerId; only override when nothing resolved.
        if (data.selectedPlayer != null) return@LaunchedEffect
        val defaultId = prefs.defaultPlayerId.value
        val defaultPlayer = defaultId?.let { id ->
            data.playerData.firstOrNull { it.playerId == id && it.player.isSelectable }
        }
        val target = defaultPlayer?.player ?: localPlayer?.player ?: return@LaunchedEffect
        homeScreenViewModel.selectPlayer(target)
    }
    val playersData = playersState as? HomeScreenViewModel.PlayersState.Data
    val selectedPlayer = playersData?.selectedPlayer
    val activePlayer = activePlayer(selectedPlayer, localPlayer)
    var pickingPlayer by remember { mutableStateOf(false) }
    var pendingPlayerSwitch by remember { mutableStateOf<PlayerData?>(null) }
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
    var contentHasFocus by remember { mutableStateOf(false) }
    val currentRoute = backStack.lastOrNull()
    // Also bumped when the system, not the user, puts the focus in the menu (at start-up, or when
    // the focused element goes away).
    var focusContentRequest by remember { mutableIntStateOf(0) }
    // Now playing leaves the focus where it was (it reads the keys through [nowPlayingKeys]), so
    // closing it returns to the element that had the focus, in the page or the menu.
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
    // Playing means a track is under way on the player: a paused or stopped queue never opens the mode.
    val isPlaying = activePlayer?.let { it.player.isPlaying && it.queueInfo?.currentItem != null } == true
    val playingNow by rememberUpdatedState(isPlaying)
    // Opened by the idle remote, not by the user. Saved so that an activity restarted after a long
    // idle (stand-by, screensaver) does not come back on a Now playing the user never asked for.
    var nowPlayingByIdle by rememberSaveable { mutableStateOf(false) }
    var idleOpenedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        if (nowPlaying && nowPlayingByIdle) {
            nowPlaying = false
            nowPlayingByIdle = false
        }
    }
    LaunchedEffect(nowPlaying) { if (!nowPlaying) nowPlayingByIdle = false }
    // Not over an open menu or dialog: those are windows above the app, which then has no window
    // focus. The mode waits until they close.
    val view = LocalView.current
    LaunchedEffect(lastKeyAt, idleDelay, isPlaying, nowPlaying) {
        val millis = idleDelay.millis ?: return@LaunchedEffect
        if (!isPlaying || nowPlaying) return@LaunchedEffect
        delay(millis)
        while (!view.hasWindowFocus()) delay(WINDOW_FOCUS_POLL_MS)
        // Still playing once the window is back: a pause during the wait cancels the switch.
        if (!playingNow) return@LaunchedEffect
        nowPlaying = true
        nowPlayingByIdle = true
        idleOpenedAt = SystemClock.elapsedRealtime()
    }
    // A key in the mode makes it the user's own.
    LaunchedEffect(lastKeyAt) { if (nowPlayingByIdle && lastKeyAt > idleOpenedAt) nowPlayingByIdle = false }
    // The mode it opened leaves when the playback stops before the remote is touched again, so the
    // user never comes back to a paused Now playing, where OK would start the playback. The grace
    // period covers the short stop between two tracks.
    LaunchedEffect(nowPlayingByIdle, isPlaying) {
        if (nowPlayingByIdle && !isPlaying) {
            delay(IDLE_STOP_GRACE_MS)
            nowPlaying = false
        }
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

    val menu by prefs.menu.collectAsStateWithLifecycle()
    val accountManager: TvAccountManager = koinInject()
    val activeAccount by accountManager.active.collectAsStateWithLifecycle()
    val aiRadioAvailable by dataSource.aiRadioAvailable.collectAsStateWithLifecycle()
    val railItems = buildList {
        menu.filter { it.visible && it.entry.isOffered(aiRadioAvailable) }.forEach { (entry) ->
            add(
                TvRailItem(
                    key = entry.name,
                    icon = entry.icon(),
                    label = entry.label(),
                    group = 0,
                    // The artwork of what plays, on the way into Now playing.
                    artworkUrl = nowPlayingInfo?.imageUrl?.takeIf { entry == TvMenuEntry.NOW_PLAYING },
                ) {
                    when (entry) {
                        TvMenuEntry.NOW_PLAYING -> nowPlaying = true
                        TvMenuEntry.PLAYER -> pickingPlayer = true
                        TvMenuEntry.HOME -> goToRoot(TvRoute.Home)
                        TvMenuEntry.SEARCH -> goToRoot(TvRoute.Search)
                        TvMenuEntry.QUEUE -> goToRoot(TvRoute.Queue)
                        TvMenuEntry.BROWSE -> goToRoot(TvRoute.Browse(path = null, title = null))
                        TvMenuEntry.AI_RADIO -> goToRoot(TvRoute.AiRadio)
                        TvMenuEntry.SERIES -> goToRoot(TvRoute.Series)
                        else -> entry.category?.mediaType?.let { goToRoot(TvRoute.Library(it)) }
                    }
                },
            )
        }
        // The user of the app, which opens the quick switch between the saved accounts.
        add(
            TvRailItem(
                ACCOUNT_RAIL_KEY,
                Icons.Default.AccountCircle,
                activeAccount?.label ?: stringResource(R.string.tv_accounts_title),
                1,
                onClick = openAccounts,
            ),
        )
        // Always there and always last: it is the way back to the choice of the entries above.
        add(
            TvRailItem(
                SETTINGS_RAIL_KEY,
                Icons.Default.Settings,
                stringResource(R.string.tv_nav_settings),
                1,
                onClick = openSettings,
            ),
        )
    }
    val selectedEntry = when (val root = backStack.firstOrNull()) {
        TvRoute.Home -> TvMenuEntry.HOME
        TvRoute.Search -> TvMenuEntry.SEARCH
        TvRoute.Queue -> TvMenuEntry.QUEUE
        is TvRoute.Library -> TvMenuEntry.entries.firstOrNull { it.category?.mediaType == root.mediaType }
        TvRoute.AiRadio -> TvMenuEntry.AI_RADIO
        TvRoute.Series -> TvMenuEntry.SERIES
        is TvRoute.Browse -> TvMenuEntry.BROWSE
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> nowPlaying && (nowPlayingKeys.handler?.invoke(event) ?: false) },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = TvRailCollapsedWidth)
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
                        // With no bar below the pages, the end of a list stays inside the TV safe area.
                        val padding = PaddingValues(bottom = 27.dp)
                        val onBack: () -> Unit = { backStack.removeLastOrNull() }
                        entry<TvRoute.Home> {
                            ProvideDpadFocusMemory {
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
                            ProvideDpadFocusMemory {
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
                        entry<TvRoute.Series> {
                            ProvideDpadFocusMemory {
                                SeriesListScreen(
                                    viewModel = koinViewModel<SeriesListViewModel>(),
                                    actionsViewModel = actionsViewModel,
                                    contentPadding = padding,
                                    onNavigateClick = ::navigateToItem,
                                    onBack = onBack,
                                )
                            }
                        }
                        entry<TvRoute.Browse> { route ->
                            ProvideDpadFocusMemory {
                                BrowseScreen(
                                    browseViewModel = koinViewModel<BrowseViewModel> { parametersOf(route.path) },
                                    title = route.title,
                                    contentPadding = padding,
                                    actionsViewModel = actionsViewModel,
                                    onNavigateClick = ::navigateToItem,
                                    onBack = onBack,
                                )
                            }
                        }
                        entry<TvRoute.ItemDetails> { route ->
                            ProvideDpadFocusMemory {
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
                        }
                        entry<TvRoute.ItemPage> { route ->
                            ProvideDpadFocusMemory {
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
                        }
                    },
                )
            }

            TvNavigationRail(
                items = railItems,
                selectedKey = selectedEntry?.name,
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
                onPick = { newPlayer ->
                    val queueNonEmpty = activePlayer?.queueItems?.isNotEmpty() == true
                    val isNewPlayer = newPlayer.playerId != activePlayer?.playerId
                    if (queueNonEmpty && isNewPlayer) {
                        pendingPlayerSwitch = newPlayer
                    } else {
                        homeScreenViewModel.selectPlayer(newPlayer.player)
                    }
                },
                onDismiss = { pickingPlayer = false },
            )
        }
        pendingPlayerSwitch?.let { target ->
            val targetName = if (target.isLocal) {
                stringResource(R.string.tv_this_tv, target.player.name)
            } else {
                target.player.name
            }
            TvTransferQueueDialog(
                targetPlayerName = targetName,
                onTransfer = {
                    activePlayer?.let { src ->
                        homeScreenViewModel.queueAction(
                            QueueAction.Transfer(src.queueOrPlayerId, target.queueOrPlayerId, autoplay = true),
                        )
                    }
                    homeScreenViewModel.selectPlayer(target.player)
                    pendingPlayerSwitch = null
                },
                onSkip = {
                    homeScreenViewModel.selectPlayer(target.player)
                    pendingPlayerSwitch = null
                },
                onDismiss = { pendingPlayerSwitch = null },
            )
        }
        ToastHost(toastState = toastState)
    }

    val exitBehavior by prefs.exitOnBackBehavior.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    // Back on the root Home: check whether to show the exit dialog.
    BackHandler(enabled = !nowPlaying && backStack.size == 1 && backStack.first() == TvRoute.Home) {
        val localPlaying = localPlayer?.player?.isPlaying == true
        val anyPlaying = activePlayer?.player?.isPlaying == true
        val shouldAsk = when (exitBehavior) {
            ExitOnBackBehavior.NEVER_ASK -> false
            ExitOnBackBehavior.ASK_IF_LOCAL -> localPlaying
            ExitOnBackBehavior.ALWAYS_ASK -> anyPlaying
        }
        if (shouldAsk) showExitDialog = true else activity?.finish()
    }

    if (showExitDialog) {
        TvExitDialog(
            onKeepPlaying = {
                showExitDialog = false
                activity?.finish()
            },
            onStopAndQuit = {
                showExitDialog = false
                activePlayer?.let { dataSource.playerAction(it, PlayerAction.Stop) }
                activity?.finish()
            },
            onDismiss = { showExitDialog = false },
        )
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
private const val SETTINGS_RAIL_KEY = "settings"
private const val ACCOUNT_RAIL_KEY = "account"

// Once per process: the app starts on this TV, and keeps the player chosen afterwards.
private var localPlayerSelectedThisLaunch = false
private const val WINDOW_FOCUS_POLL_MS = 500L
private const val IDLE_STOP_GRACE_MS = 5_000L
private const val MAX_ERROR_LENGTH = 150
private const val FOCUS_RETRY_MS = 150L
