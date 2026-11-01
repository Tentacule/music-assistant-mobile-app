package io.music_assistant.client.tv.ui

import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvLyrics
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.audio.VisualizerTap
import io.music_assistant.client.tv.ui.visualizer.VisualizerBackdrop
import io.music_assistant.client.tv.ui.visualizer.VisualizerStyle
import io.music_assistant.client.tv.ui.visualizer.artworkVisualizer
import io.music_assistant.client.tv.ui.visualizer.rememberAudioFrames
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.CrossfadeIcon
import io.music_assistant.client.ui.compose.common.rememberAnimatedPlayerColors
import io.music_assistant.client.ui.compose.common.rememberExtractedColorsSource
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * The full-screen Now playing mode: the artwork and the track info, nothing to navigate.
 *
 * Remote: Back closes the artist list, hides the controls, or returns to the app where the user
 * left it. OK shows the controls (previous,
 * play/pause, next) for a few seconds; with them shown, Left/Right move between them and OK
 * presses one. The media keys work as everywhere in the app. Every key is consumed here, so the
 * focus never wanders into the app hidden underneath.
 *
 * Holding OK opens a menu in place of the controls: on a track coming next, play it next, move it
 * (Left/Right, OK drops it, Back cancels) or remove it; on the controls, add the current track again
 * or its album to the queue.
 *
 * The Lyrics control (shown when the track has lyrics, or the episode a transcript) switches to
 * the lyrics mode ([NowPlayingLyrics]); the choice is kept for the next tracks.
 */
@Composable
fun NowPlayingScreen(
    playerData: PlayerData?,
    homeScreenViewModel: HomeScreenViewModel,
    onAction: (PlayerAction) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    // Opens an artist or the album of the current track (closing the mode).
    onNavigate: (AppMediaItem) -> Unit,
    keys: NowPlayingKeys,
) {
    val info = playerData?.nowPlayingInfo()
    val position by rememberPositionSec(homeScreenViewModel, playerData)
    val track = playerData?.queueInfo?.currentItem?.track as? Track
    val artists = track?.artists.orEmpty()
    val album = track?.album

    val prefs: TvPreferences = koinInject()
    val tap: VisualizerTap = koinInject()
    val tvLyrics: TvLyrics = koinInject()
    val lyricsMode by prefs.lyricsMode.collectAsStateWithLifecycle()
    // The lyrics of the track or the transcript of the episode; null while loading and when none.
    val trackLyrics by produceState<Lyrics?>(null, track?.provider, track?.itemId) {
        value = track?.let { tvLyrics.lyrics(it) }
    }
    val episode = playerData?.queueInfo?.currentItem?.track as? PodcastEpisode
    val transcript = episode?.let {
        LaunchedEffect(it.provider, it.itemId) { homeScreenViewModel.loadTranscript(it) }
        remember(it.provider, it.itemId) { homeScreenViewModel.transcript(it) }
            .collectAsStateWithLifecycle().value.dataOrNull
    }
    val lyrics = trackLyrics ?: transcript
    val showLyrics = lyricsMode && lyrics != null
    // The artist's photo, the backdrop of the lyrics mode; loaded only in that mode.
    val leadArtist = artists.firstOrNull()?.takeIf { showLyrics }
    val artistImage by produceState<String?>(null, leadArtist?.provider, leadArtist?.itemId) {
        value = leadArtist?.let { tvLyrics.artistImageUrl(it) }
    }
    val visualizer by prefs.visualizer.collectAsStateWithLifecycle()
    val frame by rememberAudioFrames(tap, enabled = visualizer != VisualizerStyle.NONE)
    val accent = rememberAnimatedPlayerColors(
        imageUrl = info?.imageUrl,
        fallback = MaterialTheme.colorScheme.primary,
        source = rememberExtractedColorsSource(),
    ).value.controlTint
    // The visualization's name, shown a moment after the remote changes it.
    var visualizerLabelShown by remember { mutableStateOf(false) }
    LaunchedEffect(visualizer, visualizerLabelShown) {
        if (visualizerLabelShown) {
            delay(VISUALIZER_LABEL_MS)
            visualizerLabelShown = false
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    // The artist list shown in place of the controls when the track has several artists.
    var choosingArtist by remember { mutableStateOf(false) }
    var artistIndex by remember { mutableIntStateOf(0) }
    // Back first closes the artist list, then hides the controls, then leaves the mode.
    // The upcoming tracks, shown under the controls with Down; OK plays the one selected.
    var queueOpen by remember { mutableStateOf(false) }
    var queueIndex by remember { mutableIntStateOf(0) }
    val upcoming = remember(playerData?.queueItems, playerData?.queueInfo?.currentIndex) {
        val items = playerData?.queueItems.orEmpty()
        val from = (playerData?.queueInfo?.currentIndex ?: -1) + 1
        if (from in items.indices) items.subList(from, items.size) else emptyList()
    }
    // The upcoming tracks as shown: the server's order, or the local one of a move or a removal
    // until the server's queue update replaces it.
    var localOrder by remember(upcoming) { mutableStateOf<List<QueueTrack>?>(null) }
    val shown = localOrder ?: upcoming
    // A track being moved with Left/Right; [moveFrom] is where it was, to cancel.
    var moving by remember { mutableStateOf(false) }
    var moveFrom by remember { mutableIntStateOf(0) }
    // The menu of a held OK, shown in place of the controls.
    var menu by remember { mutableStateOf<NpMenu?>(null) }
    var menuIndex by remember { mutableIntStateOf(0) }
    // A short confirmation ("Added to the queue"), shown under the artwork.
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(VISUALIZER_LABEL_MS)
            notice = null
        }
    }
    LaunchedEffect(upcoming) {
        // The server changed the queue under a move: its local order is gone, so is the move.
        moving = false
    }
    LaunchedEffect(shown.size) { queueIndex = queueIndex.coerceIn(0, (shown.size - 1).coerceAtLeast(0)) }
    val queueListState = rememberLazyListState()
    LaunchedEffect(queueOpen, queueIndex) {
        if (queueOpen && shown.isNotEmpty()) {
            queueListState.animateScrollToItem((queueIndex - 1).coerceAtLeast(0))
        }
    }
    // Back (and Up) step out one level: the queue, the artist list, the controls, then the mode.
    fun cancelMove() {
        localOrder = null
        queueIndex = moveFrom
        moving = false
    }
    BackHandler {
        when {
            menu != null -> menu = null
            moving -> cancelMove()
            queueOpen -> queueOpen = false
            choosingArtist -> choosingArtist = false
            controlsVisible -> controlsVisible = false
            else -> onClose()
        }
    }
    // The selected control, by key: the Artist and Album controls come and go with the track.
    var selectedKey by remember { mutableStateOf(KEY_PLAY_PAUSE) }
    var lastInteraction by remember { mutableStateOf(0L) }
    // An OK press seen by this mode, handled on its release.
    var selectPressed by remember { mutableStateOf(false) }
    var selectPresses by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, choosingArtist, queueOpen, lastInteraction) {
        if (controlsVisible) {
            // Longer while browsing the queue: there is more to read.
            delay(if (queueOpen) QUEUE_HIDE_DELAY_MS else CONTROLS_HIDE_DELAY_MS)
            if (moving) cancelMove()
            menu = null
            controlsVisible = false
            choosingArtist = false
            queueOpen = false
        }
    }

    val playing = info?.isPlaying == true
    val controls = buildList {
        add(
            NpControl(
                KEY_SHUFFLE,
                Icons.Default.Shuffle,
                stringResource(R.string.tv_shuffle),
                info?.shuffle == true || info?.isDynamic == true,
            ) {
                onAction(PlayerAction.ToggleShuffle(current = info?.shuffle == true))
            },
        )
        add(
            NpControl(KEY_PREVIOUS, Icons.Default.SkipPrevious, stringResource(R.string.tv_previous)) {
            onAction(PlayerAction.Previous)
        },
        )
        add(
            NpControl(
                KEY_PLAY_PAUSE,
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (playing) R.string.tv_pause else R.string.tv_play),
            ) { onAction(PlayerAction.TogglePlayPause) },
        )
        add(
            NpControl(
                KEY_NEXT,
                Icons.Default.SkipNext,
                stringResource(R.string.tv_next),
            ) { onAction(PlayerAction.Next) },
        )
        add(
            NpControl(
                KEY_REPEAT,
                if (info?.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                stringResource(R.string.tv_repeat),
                info?.repeatMode.let { it != null && it != RepeatMode.OFF },
            ) { onAction(PlayerAction.ToggleRepeatMode(current = info?.repeatMode ?: RepeatMode.OFF)) },
        )
        info?.autoplay?.let { autoplay ->
            add(
                NpControl(
                    KEY_AUTOPLAY,
                    Icons.Default.AllInclusive,
                    stringResource(R.string.tv_autoplay),
                    autoplay || info.isDynamic,
                ) {
                    onAction(PlayerAction.ToggleDontStopTheMusic(current = autoplay))
                },
            )
        }
        info?.crossfade?.let { crossfade ->
            add(
                NpControl(KEY_CROSSFADE, CrossfadeIcon, stringResource(R.string.tv_crossfade), crossfade) {
                    onAction(PlayerAction.ToggleCrossfade(current = crossfade))
                },
            )
        }
        add(
            NpControl(
                KEY_QUEUE,
                Icons.AutoMirrored.Filled.QueueMusic,
                stringResource(R.string.tv_nav_queue),
            ) { onOpenQueue() },
        )
        add(
            NpControl(
                KEY_VISUALIZER,
                Icons.Default.GraphicEq,
                stringResource(R.string.tv_visualizer),
                visualizer != VisualizerStyle.NONE,
            ) {
                prefs.setVisualizer(visualizer.next())
                visualizerLabelShown = true
            },
        )
        if (lyrics != null) {
            add(
                NpControl(KEY_LYRICS, Icons.Outlined.Lyrics, stringResource(R.string.tv_lyrics), lyricsMode) {
                    prefs.setLyricsMode(!lyricsMode)
                },
            )
        }
        if (artists.isNotEmpty()) {
            add(
                NpControl(KEY_ARTIST, Icons.Default.Person, stringResource(R.string.tv_go_to_artist)) {
                    if (artists.size == 1) {
                        onNavigate(artists.first())
                    } else {
                        artistIndex = 0
                        choosingArtist = true
                    }
                },
            )
        }
        album?.let {
            add(NpControl(KEY_ALBUM, Icons.Default.Album, stringResource(R.string.tv_go_to_album)) { onNavigate(it) })
        }
    }
    val selectedIndex = controls.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 }
        ?: controls.indexOfFirst { it.key == KEY_PLAY_PAUSE }

    // Now playing never takes the focus: it stays on the element of the app underneath, so closing
    // the mode lands exactly there. The screen hands the remote's keys to [keys] meanwhile; every
    // key is consumed here, except Back (the BackHandler) and the media keys (the activity).
    val queueId = playerData?.queueInfo?.id
    fun playUpcoming() {
        val item = shown.getOrNull(queueIndex) ?: return
        queueId ?: return
        homeScreenViewModel.queueAction(QueueAction.PlayQueueItem(queueId, item.id))
        queueOpen = false
    }

    // Moves and removals show at once; the server's queue update follows. The positions count from
    // the first upcoming track: the server takes only the shift between them.
    fun moveUpcoming(from: Int, to: Int) {
        val item = shown.getOrNull(from) ?: return
        queueId ?: return
        if (from != to) homeScreenViewModel.queueAction(QueueAction.MoveItem(queueId, item.id, from, to))
        localOrder = shown.toMutableList().apply { add(to, removeAt(from)) }
        queueIndex = to
    }

    fun removeUpcoming(item: QueueTrack) {
        queueId ?: return
        homeScreenViewModel.queueAction(QueueAction.RemoveItems(queueId, listOf(item.id)))
        localOrder = shown - item
    }

    val dispatcher: PlayDispatcher = koinInject()
    val addedNotice = stringResource(R.string.tv_np_added)
    fun addToQueue(uri: String, option: QueueOption) {
        val target = playerData?.queueOrPlayerId ?: return
        dispatcher.play(PlayRequest(queueOrPlayerId = target, media = listOf(uri), option = option))
        notice = addedNotice
    }

    val playNextLabel = stringResource(R.string.tv_np_play_next)
    val moveLabel = stringResource(R.string.tv_np_move)
    val removeLabel = stringResource(R.string.tv_np_remove)
    val againLabel = stringResource(R.string.tv_np_track_again)
    val addAlbumLabel = stringResource(R.string.tv_np_add_album)
    val menuEntries: List<NpMenuEntry> = when (menu) {
        NpMenu.QUEUE_ITEM -> shown.getOrNull(queueIndex)?.let { item ->
            buildList {
                if (queueIndex > 0) add(NpMenuEntry(playNextLabel) { moveUpcoming(queueIndex, 0) })
                if (shown.size > 1) {
                    add(
                        NpMenuEntry(moveLabel) {
                            moveFrom = queueIndex
                            localOrder = shown
                            moving = true
                        },
                    )
                }
                add(NpMenuEntry(removeLabel) { removeUpcoming(item) })
            }
        }.orEmpty()

        NpMenu.ADD -> buildList {
            track?.uri?.let { uri -> add(NpMenuEntry(againLabel) { addToQueue(uri, QueueOption.NEXT) }) }
            album?.uri?.let { uri -> add(NpMenuEntry(addAlbumLabel) { addToQueue(uri, QueueOption.ADD) }) }
        }

        null -> emptyList()
    }
    val canAdd = track?.uri != null || album?.uri != null

    // What a held OK does: the menu of the selected upcoming track, or the add menu on the controls.
    fun longSelect() {
        when {
            !controlsVisible -> controlsVisible = true
            menu != null || moving || choosingArtist -> Unit
            queueOpen -> if (shown.isNotEmpty()) {
                menuIndex = 0
                menu = NpMenu.QUEUE_ITEM
            }

            canAdd -> {
                menuIndex = 0
                menu = NpMenu.ADD
            }
        }
    }

    // What OK does where the remote is: show the controls, run a menu entry, drop a moved track,
    // play a queued track, open an artist, or press a control.
    fun select() {
        when {
            !controlsVisible -> controlsVisible = true
            menu != null -> {
                val entry = menuEntries.getOrNull(menuIndex)
                menu = null
                entry?.onPress?.invoke()
            }

            moving -> {
                moving = false
                val item = shown.getOrNull(queueIndex)
                if (item != null && queueId != null && moveFrom != queueIndex) {
                    homeScreenViewModel.queueAction(QueueAction.MoveItem(queueId, item.id, moveFrom, queueIndex))
                }
            }

            queueOpen -> playUpcoming()
            choosingArtist -> artists.getOrNull(artistIndex)?.let(onNavigate)
            else -> controls.getOrNull(selectedIndex)?.onPress?.invoke()
        }
    }

    val handleKey: (KeyEvent) -> Boolean = handler@{ event ->
        val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (event.type != KeyEventType.KeyDown) {
            // OK acts on its release: a control that closes the mode (queue, artist, album) would
            // otherwise leave the release to the element focused underneath, which would fire too.
            if (select && selectPressed) {
                selectPressed = false
                select()
                lastInteraction = System.nanoTime()
            }
            return@handler event.key != Key.Back
        }
        when (event.key) {
            Key.Back, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaNext,
            Key.MediaPrevious, Key.MediaStop, Key.MediaFastForward, Key.MediaRewind,
            -> return@handler false

            // The activity holds OK back until its release, then replays the press: a short one with
            // its release at once, a long one without it (see TvMainActivity). So a press whose
            // release has not come after the long-press timeout was held.
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!selectPressed) {
                selectPressed = true
                selectPresses++
            }

            Key.DirectionUp -> when {
                !controlsVisible -> controlsVisible = true
                menu != null -> menu = null
                moving -> cancelMove()
                queueOpen -> queueOpen = false
                choosingArtist -> choosingArtist = false
                // Up from the controls goes back to the plain view, like Back.
                else -> controlsVisible = false
            }

            Key.DirectionDown -> when {
                !controlsVisible -> controlsVisible = true
                menu != null || moving -> Unit
                !queueOpen && !choosingArtist -> {
                    queueIndex = 0
                    queueOpen = true
                }
            }

            Key.DirectionLeft -> when {
                !controlsVisible -> controlsVisible = true
                menu != null -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
                moving -> if (queueIndex > 0) {
                    localOrder = shown.toMutableList().apply { add(queueIndex - 1, removeAt(queueIndex)) }
                    queueIndex--
                }

                queueOpen -> queueIndex = (queueIndex - 1).coerceAtLeast(0)
                choosingArtist -> artistIndex = (artistIndex - 1).coerceAtLeast(0)
                else -> controls.getOrNull(selectedIndex - 1)?.let { selectedKey = it.key }
            }

            Key.DirectionRight -> when {
                !controlsVisible -> controlsVisible = true
                menu != null -> menuIndex = (menuIndex + 1).coerceAtMost((menuEntries.size - 1).coerceAtLeast(0))
                moving -> if (queueIndex < shown.lastIndex) {
                    localOrder = shown.toMutableList().apply { add(queueIndex + 1, removeAt(queueIndex)) }
                    queueIndex++
                }

                queueOpen -> queueIndex = (queueIndex + 1).coerceAtMost((shown.size - 1).coerceAtLeast(0))
                choosingArtist -> artistIndex = (artistIndex + 1).coerceAtMost(artists.lastIndex)
                else -> controls.getOrNull(selectedIndex + 1)?.let { selectedKey = it.key }
            }

            else -> controlsVisible = true
        }
        lastInteraction = System.nanoTime()
        true
    }
    LaunchedEffect(selectPresses) {
        if (selectPresses == 0) return@LaunchedEffect
        delay(ViewConfiguration.getLongPressTimeout().toLong())
        if (selectPressed) {
            selectPressed = false
            longSelect()
            lastInteraction = System.nanoTime()
        }
    }
    SideEffect { keys.handler = handleKey }
    DisposableEffect(keys) { onDispose { keys.handler = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The artwork (the artist's photo in the lyrics mode), dimmed, fills the screen behind everything.
        (if (showLyrics) artistImage ?: info?.imageUrl else info?.imageUrl)?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = BACKDROP_ALPHA,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))),
                ),
        )
        VisualizerBackdrop(visualizer, frame, accent, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp)) {
            if (showLyrics && lyrics != null) {
                NowPlayingLyrics(lyrics, info, position, Modifier.weight(1f).fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .artworkVisualizer(visualizer, frame, accent)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp))
                        info?.imageUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    NowPlayingTexts(info, position, Modifier.weight(1f))
                }
            }

            AnimatedVisibility(visible = visualizerLabelShown || notice != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    notice ?: stringResource(R.string.tv_visualizer_current, stringResource(visualizer.label())),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // The controls: one row across the bottom of the screen, like the Android TV players.
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (menu != null) {
                        menuEntries.forEachIndexed { index, entry ->
                            ChoiceChip(entry.label, selected = index == menuIndex)
                        }
                    } else if (choosingArtist) {
                        artists.forEachIndexed { index, artist ->
                            ChoiceChip(artist.displayName, selected = index == artistIndex)
                        }
                    } else {
                        // The row holds up to a dozen controls (artist, album, lyrics come and go): they
                        // shrink to fit the width rather than run off the screen.
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val count = controls.size.coerceAtLeast(1)
                            val size = ((maxWidth - CONTROL_GAP * (count - 1)) / count).coerceAtMost(CONTROL_SIZE)
                            Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
                                controls.forEachIndexed { index, control ->
                                    ControlButton(
                                        control.icon,
                                        control.label,
                                        index == selectedIndex && !queueOpen,
                                        control.active,
                                        size,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Down from the controls: the tracks coming next, OK plays one.
            AnimatedVisibility(visible = controlsVisible && queueOpen, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    UpcomingRow(shown, queueIndex, queueListState, moving)
                    if (shown.isNotEmpty()) {
                        HintText(stringResource(if (moving) R.string.tv_np_move_hint else R.string.tv_np_queue_hint))
                    }
                }
            }
            AnimatedVisibility(
                visible = controlsVisible && !queueOpen && !choosingArtist && menu == null && canAdd,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                HintText(stringResource(R.string.tv_np_add_hint))
            }
        }
    }
}

@Composable
private fun NowPlayingTexts(info: NowPlayingInfo?, position: Double, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = info?.title ?: stringResource(R.string.tv_nothing_playing),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        info?.artist?.let {
            Text(
                it,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info?.album?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(24.dp))
        info?.durationSec?.let { duration ->
            LinearProgressIndicator(
                progress = { (position / duration).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(position), color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f))
            }
        }
        if (info?.isDynamic == true) {
            SmartMixBadge(textStyle = MaterialTheme.typography.titleMedium, iconSize = 22.dp)
        }
        info?.upNext?.let {
            Text(
                stringResource(R.string.tv_up_next, it),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class NpMenu { QUEUE_ITEM, ADD }

/** One entry of the menu a held OK opens. */
private class NpMenuEntry(val label: String, val onPress: () -> Unit)

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        textAlign = TextAlign.Center,
    )
}

/** One control of the Now playing row; [active] marks a mode that is on (shuffle, repeat). */
private class NpControl(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val onPress: () -> Unit,
)

/**
 * One round control. [selected] is the remote's position (white disc); [active] marks a mode that
 * is on (shuffle, repeat), in the accent color, so its state reads at a glance.
 */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    active: Boolean = false,
    size: Dp = CONTROL_SIZE,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    selected -> Color.White
                    active -> accent.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.15f)
                },
            )
            .border(
                2.dp,
                when {
                    active -> accent
                    selected -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                selected && active -> accent
                selected -> Color.Black
                else -> Color.White
            },
            modifier = Modifier.size(size * CONTROL_ICON_RATIO),
        )
    }
}

/** An artist to pick, when the track has several. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .height(CONTROL_SIZE)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val KEY_SHUFFLE = "shuffle"
private const val KEY_PREVIOUS = "previous"
private const val KEY_PLAY_PAUSE = "play_pause"
private const val KEY_NEXT = "next"
private const val KEY_REPEAT = "repeat"
private const val KEY_QUEUE = "queue"
private const val KEY_AUTOPLAY = "autoplay"
private const val KEY_CROSSFADE = "crossfade"
private const val KEY_ARTIST = "artist"
private const val KEY_ALBUM = "album"
private const val KEY_VISUALIZER = "visualizer"
private const val KEY_LYRICS = "lyrics"
private const val VISUALIZER_LABEL_MS = 2_000L
private val CONTROL_SIZE = 64.dp
private val CONTROL_GAP = 20.dp
private const val CONTROL_ICON_RATIO = 0.53f
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val TITLE_MAX_LINES = 3
private const val BACKDROP_ALPHA = 0.5f

/** Where the screen sends the remote's keys while Now playing is shown, since it never takes the focus. */
class NowPlayingKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
}

/** The name of a visualization, for the remote and the settings. */
fun VisualizerStyle.label(): Int = when (this) {
    VisualizerStyle.NONE -> R.string.tv_visualizer_none
    VisualizerStyle.PULSE -> R.string.tv_visualizer_pulse
    VisualizerStyle.BARS -> R.string.tv_visualizer_bars
    VisualizerStyle.WAVE -> R.string.tv_visualizer_wave
    VisualizerStyle.HALO -> R.string.tv_visualizer_halo
    VisualizerStyle.RADIAL -> R.string.tv_visualizer_radial
}

/**
 * The tracks after the current one, as artwork cards; [selected] is the remote's position, lifted
 * and outlined in the accent color while [moving].
 */
@Composable
private fun UpcomingRow(items: List<QueueTrack>, selected: Int, state: LazyListState, moving: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.tv_queue_nothing_next),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        return
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isSelected = index == selected
            val track = item.track
            Column(
                modifier = Modifier
                    .width(UPCOMING_CARD_WIDTH)
                    .graphicsLayer {
                        val scale = when {
                            isSelected && moving -> UPCOMING_MOVING_SCALE
                            isSelected -> UPCOMING_SELECTED_SCALE
                            else -> 1f
                        }
                        scaleX = scale
                        scaleY = scale
                        translationY = if (isSelected && moving) -UPCOMING_MOVING_LIFT_DP * density else 0f
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(UPCOMING_CARD_WIDTH)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(
                            if (isSelected) 3.dp else 0.dp,
                            when {
                                isSelected && moving -> accent
                                isSelected -> Color.White
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    (track as? AppMediaItem)?.image(ImageType.THUMB)?.url?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    track.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val UPCOMING_CARD_WIDTH = 132.dp
private const val UPCOMING_SELECTED_SCALE = 1.08f
private const val UPCOMING_MOVING_SCALE = 1.12f
private const val UPCOMING_MOVING_LIFT_DP = 12f
private const val QUEUE_HIDE_DELAY_MS = 12_000L
