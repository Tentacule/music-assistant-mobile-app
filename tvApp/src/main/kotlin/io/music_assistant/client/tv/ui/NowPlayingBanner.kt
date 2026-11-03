package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.tv.R

/** The image of the banner layout, full screen and darkened toward the bottom for the track info. */
@Composable
fun BannerBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    imageUrl ?: return
    Box(modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    GRADIENT_START to Color.Transparent,
                    GRADIENT_MIDDLE to Color.Black.copy(alpha = GRADIENT_MIDDLE_ALPHA),
                    1f to Color.Black.copy(alpha = GRADIENT_END_ALPHA),
                ),
            ),
        )
    }
}

/**
 * The banner layout: the track info at the bottom left. [artworkModifier] carries the visualization
 * of the artwork. Without [waveform], the timeline is a plain progress bar.
 */
@Composable
fun NowPlayingBanner(
    info: NowPlayingInfo?,
    waveform: List<Float>?,
    positionSec: Double,
    artworkModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Box(
                modifier = artworkModifier
                    .size(ARTWORK_SIZE)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(72.dp))
                info?.imageUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    info?.title ?: stringResource(R.string.tv_nothing_playing),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                info?.artist?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = SECONDARY_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                info?.durationSec?.let { duration ->
                    Timeline(waveform, positionSec, duration)
                }
            }
        }
    }
}

@Composable
private fun Timeline(waveform: List<Float>?, positionSec: Double, durationSec: Double) {
    val progress = (positionSec / durationSec).toFloat().coerceIn(0f, 1f)
    // The same height with or without bins, so the layout stays still when they arrive.
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(WAVEFORM_HEIGHT), contentAlignment = Alignment.Center) {
        if (waveform != null) {
            WaveformTimeline(waveform, progress, Modifier.fillMaxSize())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(formatTime(positionSec), color = Color.White.copy(alpha = SECONDARY_ALPHA))
        Spacer(Modifier.weight(1f))
        Text(formatTime(durationSec), color = Color.White.copy(alpha = SECONDARY_ALPHA))
    }
}

private val ARTWORK_SIZE = 240.dp
private val WAVEFORM_HEIGHT = 72.dp
private const val SECONDARY_ALPHA = 0.8f
// Dark enough at the middle of the screen for the title, which rises when the controls open.
private const val GRADIENT_START = 0.15f
private const val GRADIENT_MIDDLE = 0.45f
private const val GRADIENT_MIDDLE_ALPHA = 0.6f
private const val GRADIENT_END_ALPHA = 0.92f
