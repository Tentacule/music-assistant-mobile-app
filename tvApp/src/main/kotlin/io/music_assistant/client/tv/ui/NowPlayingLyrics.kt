package io.music_assistant.client.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.LrcLine
import io.music_assistant.client.data.model.client.Lyrics
import kotlin.math.abs

/**
 * The lyrics mode of Now playing, after the Roon TV look: large centered lines over the artist's
 * photo, the sung line in bold white, the lines around it fading with their distance, and a band
 * across the bottom with the artwork, the title, the artist and the progress.
 *
 * Synced lyrics follow [positionSec]; plain lyrics scroll through the song at its pace, since they
 * carry no timing.
 */
@Composable
fun NowPlayingLyrics(
    lyrics: Lyrics,
    info: NowPlayingInfo?,
    positionSec: Double,
    lyricsOffsetSec: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().fadeEdges()) {
            val syncedPosition = positionSec + lyricsOffsetSec
            when (lyrics) {
                is Lyrics.Synced -> SyncedLines(lyrics.lines, syncedPosition)
                is Lyrics.Plain -> PlainText(lyrics.text, syncedPosition, info?.durationSec)
            }
        }
        InfoBand(info, positionSec)
    }
}

@Composable
private fun SyncedLines(lines: List<LrcLine>, positionSec: Double) {
    val currentIndex = remember(lines, positionSec) {
        val ms = (positionSec * MILLIS_PER_SECOND).toLong()
        lines.indexOfLast { it.timeMs <= ms }
    }
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The sung line sits a third of the way down, so the next lines show under it: the list
        // places the item it scrolls to just after its top padding, and the bottom padding lets the
        // last line reach that place too.
        LaunchedEffect(currentIndex) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            contentPadding = PaddingValues(top = maxHeight * ANCHOR_FRACTION, bottom = maxHeight),
            verticalArrangement = Arrangement.spacedBy(LINE_SPACING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(lines) { index, line ->
                // Before the first line, the first one is the next to come.
                val distance = if (currentIndex < 0) index + 1 else abs(index - currentIndex)
                val color by animateColorAsState(
                    Color.White.copy(alpha = alphaAt(distance, sung = index == currentIndex)),
                    animationSpec = tween(LINE_FADE_MS),
                    label = "lyricLine",
                )
                // A blank LRC line is an instrumental break.
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = LINE_SIZE,
                    lineHeight = LINE_HEIGHT,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                )
            }
        }
    }
}

/** Fades the content out at the top and the bottom, so a line leaves the view instead of being cut. */
private fun Modifier.fadeEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                EDGE_FADE to Color.Black,
                1f - EDGE_FADE to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private fun alphaAt(distance: Int, sung: Boolean): Float = when {
    sung -> 1f
    distance == 1 -> NEAR_ALPHA
    distance == 2 -> MID_ALPHA
    else -> FAR_ALPHA
}

@Composable
private fun PlainText(text: String, positionSec: Double, durationSec: Double?) {
    val scroll = rememberScrollState()
    val fraction = durationSec?.let { (positionSec / it).coerceIn(0.0, 1.0) } ?: 0.0
    LaunchedEffect(fraction, scroll.maxValue) {
        scroll.animateScrollTo((scroll.maxValue * fraction).toInt(), tween(PLAIN_SCROLL_MS))
    }
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        lineHeight = PLAIN_LINE_HEIGHT,
        color = Color.White.copy(alpha = PLAIN_ALPHA),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll, enabled = false)
            .padding(horizontal = 48.dp, vertical = 24.dp),
    )
}

/** The bottom band: artwork, title and artist, progress with the times. */
@Composable
private fun InfoBand(info: NowPlayingInfo?, positionSec: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(BAND_COLOR, BAND_COLOR.copy(alpha = 0.35f))))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            Modifier.size(BAND_ART_SIZE).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
            info?.imageUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                info?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info?.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            info?.durationSec?.let { duration ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (positionSec / duration).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatTime(positionSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000
private const val EDGE_FADE = 0.12f
private const val ANCHOR_FRACTION = 0.3f
private val LINE_SIZE = 40.sp
private val LINE_HEIGHT = 50.sp
private val LINE_SPACING = 28.dp
private const val LINE_FADE_MS = 400
private const val NEAR_ALPHA = 0.45f
private const val MID_ALPHA = 0.25f
private const val FAR_ALPHA = 0.1f
private val PLAIN_LINE_HEIGHT = 44.sp
private const val PLAIN_ALPHA = 0.85f
private const val PLAIN_SCROLL_MS = 1_000
private val BAND_ART_SIZE = 112.dp
private val BAND_COLOR = Color.Black.copy(alpha = 0.6f)
