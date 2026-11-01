package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import kotlinx.coroutines.delay

/**
 * The full-screen Now playing mode: the artwork and the track info, nothing to navigate.
 *
 * Remote: Back hides the controls, or returns to the app where the user left it. OK shows the controls (previous,
 * play/pause, next) for a few seconds; with them shown, Left/Right move between them and OK
 * presses one. The media keys work as everywhere in the app. Every key is consumed here, so the
 * focus never wanders into the app hidden underneath.
 */
@Composable
fun NowPlayingScreen(
    playerData: PlayerData?,
    homeScreenViewModel: HomeScreenViewModel,
    onAction: (PlayerAction) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    keys: NowPlayingKeys,
) {
    val info = playerData?.nowPlayingInfo()
    val position by rememberPositionSec(homeScreenViewModel, playerData)

    var controlsVisible by remember { mutableStateOf(false) }
    // Back first hides the controls, then leaves the mode.
    BackHandler {
        if (controlsVisible) controlsVisible = false else onClose()
    }
    var selected by remember { mutableIntStateOf(CONTROL_PLAY_PAUSE) }
    var lastInteraction by remember { mutableStateOf(0L) }
    LaunchedEffect(controlsVisible, lastInteraction) {
        if (controlsVisible) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    fun press(control: Int) {
        if (control == CONTROL_QUEUE) {
            onOpenQueue()
            return
        }
        onAction(
            when (control) {
                CONTROL_SHUFFLE -> PlayerAction.ToggleShuffle(current = info?.shuffle == true)
                CONTROL_PREVIOUS -> PlayerAction.Previous
                CONTROL_NEXT -> PlayerAction.Next
                CONTROL_REPEAT -> PlayerAction.ToggleRepeatMode(current = info?.repeatMode ?: RepeatMode.OFF)
                else -> PlayerAction.TogglePlayPause
            },
        )
    }

    // Now playing never takes the focus: it stays on the element of the app underneath, so closing
    // the mode lands exactly there. The screen hands the remote's keys to [keys] meanwhile; every
    // key is consumed here, except Back (the BackHandler) and the media keys (the activity).
    val handleKey: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler event.key != Key.Back
        when (event.key) {
            // Back goes to the BackHandler (hide the controls, or close); the media keys reach the activity.
            Key.Back, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaNext,
            Key.MediaPrevious, Key.MediaStop, Key.MediaFastForward, Key.MediaRewind,
            -> return@handler false

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                if (controlsVisible) press(selected) else controlsVisible = true

            // The controls are a grid of CONTROLS_PER_ROW columns: Left/Right move in a row,
            // Up/Down between the rows.
            Key.DirectionLeft -> if (controlsVisible) {
                if (selected % CONTROLS_PER_ROW > 0) selected -= 1
            } else {
                controlsVisible = true
            }

            Key.DirectionRight -> if (controlsVisible) {
                if (selected % CONTROLS_PER_ROW < CONTROLS_PER_ROW - 1) selected += 1
            } else {
                controlsVisible = true
            }

            Key.DirectionUp -> if (controlsVisible) {
                if (selected >= CONTROLS_PER_ROW) selected -= CONTROLS_PER_ROW
            } else {
                controlsVisible = true
            }

            Key.DirectionDown -> if (controlsVisible) {
                if (selected + CONTROLS_PER_ROW <= CONTROL_QUEUE) selected += CONTROLS_PER_ROW
            } else {
                controlsVisible = true
            }

            else -> controlsVisible = true
        }
        lastInteraction = System.nanoTime()
        true
    }
    SideEffect { keys.handler = handleKey }
    DisposableEffect(keys) { onDispose { keys.handler = null } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),

    ) {
        // The artwork, dimmed, fills the screen behind everything.
        info?.imageUrl?.let {
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

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = info?.title ?: stringResource(R.string.tv_nothing_playing),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    // With the controls shown, the texts keep one line each, so the two rows of
                    // buttons always fit under them.
                    maxLines = if (controlsVisible) 1 else TITLE_MAX_LINES,
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
                    SmartMixBadge(
                        textStyle = MaterialTheme.typography.titleMedium,
                        iconSize = 22.dp,
                    )
                }
                info?.upNext?.takeIf { !controlsVisible }?.let {
                    Text(
                        stringResource(R.string.tv_up_next, it),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    // Two rows of equal buttons: the transport, then the modes and the queue.
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ControlButton(
                                Icons.Default.SkipPrevious,
                                stringResource(R.string.tv_previous),
                                selected == CONTROL_PREVIOUS,
                            )
                            ControlButton(
                                if (info?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                stringResource(if (info?.isPlaying == true) R.string.tv_pause else R.string.tv_play),
                                selected == CONTROL_PLAY_PAUSE,
                            )
                            ControlButton(
                                Icons.Default.SkipNext,
                                stringResource(R.string.tv_next),
                                selected == CONTROL_NEXT,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ControlButton(
                                Icons.Default.Shuffle,
                                stringResource(R.string.tv_shuffle),
                                selected == CONTROL_SHUFFLE,
                                active = info?.shuffle == true || info?.isDynamic == true,
                            )
                            ControlButton(
                                if (info?.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                stringResource(R.string.tv_repeat),
                                selected == CONTROL_REPEAT,
                                active = info?.repeatMode.let { it != null && it != RepeatMode.OFF },
                            )
                            ControlButton(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                stringResource(R.string.tv_nav_queue),
                                selected == CONTROL_QUEUE,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One round control. [selected] is the remote's position (white disc); [active] marks a mode that
 * is on (shuffle, repeat), in the accent color, so its state reads at a glance.
 */
@Composable
private fun ControlButton(icon: ImageVector, label: String, selected: Boolean, active: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            // Never squeezed by a long title: the column gives the texts less room instead.
            .requiredSize(72.dp)
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
            modifier = Modifier.size(40.dp),
        )
    }
}

// Grid positions, row by row: the transport first, then the modes and the queue.
private const val CONTROLS_PER_ROW = 3
private const val CONTROL_PREVIOUS = 0
private const val CONTROL_PLAY_PAUSE = 1
private const val CONTROL_NEXT = 2
private const val CONTROL_SHUFFLE = 3
private const val CONTROL_REPEAT = 4
private const val CONTROL_QUEUE = 5
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val TITLE_MAX_LINES = 3
private const val BACKDROP_ALPHA = 0.5f

/** Where the screen sends the remote's keys while Now playing is shown, since it never takes the focus. */
class NowPlayingKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
}
