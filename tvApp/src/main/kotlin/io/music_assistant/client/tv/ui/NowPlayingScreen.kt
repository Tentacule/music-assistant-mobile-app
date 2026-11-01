package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import kotlinx.coroutines.delay

/**
 * The full-screen Now playing mode: the artwork and the track info, nothing to navigate.
 *
 * Remote: Back returns to the app, where the user left it. OK shows the controls (previous,
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
) {
    BackHandler(onBack = onClose)
    val info = playerData?.nowPlayingInfo()
    val position by rememberPositionSec(homeScreenViewModel, playerData)

    var controlsVisible by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(CONTROL_PLAY_PAUSE) }
    var lastInteraction by remember { mutableStateOf(0L) }
    LaunchedEffect(controlsVisible, lastInteraction) {
        if (controlsVisible) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun press(control: Int) {
        onAction(
            when (control) {
                CONTROL_PREVIOUS -> PlayerAction.Previous
                CONTROL_NEXT -> PlayerAction.Next
                else -> PlayerAction.TogglePlayPause
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key != Key.Back
                when (event.key) {
                    // Back closes the mode through the BackHandler; the media keys reach the activity.
                    Key.Back, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaNext,
                    Key.MediaPrevious, Key.MediaStop, Key.MediaFastForward, Key.MediaRewind,
                    -> return@onPreviewKeyEvent false

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                        if (controlsVisible) press(selected) else controlsVisible = true

                    Key.DirectionLeft -> if (controlsVisible) {
                        selected = (selected - 1).coerceAtLeast(CONTROL_PREVIOUS)
                    } else {
                        controlsVisible = true
                    }

                    Key.DirectionRight -> if (controlsVisible) {
                        selected = (selected + 1).coerceAtMost(CONTROL_NEXT)
                    } else {
                        controlsVisible = true
                    }

                    else -> controlsVisible = true
                }
                lastInteraction = System.nanoTime()
                true
            }
            .focusable(),
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                info?.artist?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.85f))
                }
                info?.album?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.7f))
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
                info?.upNext?.let {
                    Text(
                        stringResource(R.string.tv_up_next, it),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier.padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
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
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .border(2.dp, Color.White.copy(alpha = if (selected) 1f else 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.Black else Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

private const val CONTROL_PREVIOUS = 0
private const val CONTROL_PLAY_PAUSE = 1
private const val CONTROL_NEXT = 2
private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val BACKDROP_ALPHA = 0.5f
