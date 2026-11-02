// Compose layout values (sizes, durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.ItemDescription
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.imageloader.rememberArtworkRequest
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.localizedSubtitle
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.inactive
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.item_description_hint_close
import musicassistantclient.composeapp.generated.resources.item_description_hint_scroll
import org.jetbrains.compose.resources.stringResource

/**
 * The full text of [description] on the whole screen, for a TV. Text holds no focusable element, so
 * the page itself takes the focus and reads the keys: Up and Down scroll by a few lines, Left and
 * Right by a screen, and OK or Back closes it.
 */
@Composable
internal fun ItemDescriptionReader(
    description: ItemDescription,
    item: AppMediaItem,
    colors: PlayerColors,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }

        // Key repeats add to the target instead of restarting from wherever the animation is, so
        // holding a key scrolls steadily.
        var target by remember { mutableIntStateOf(0) }
        fun scrollBy(fractionOfScreen: Float) {
            target = (target + (scrollState.viewportSize * fractionOfScreen).toInt())
                .coerceIn(0, scrollState.maxValue)
            scope.launch {
                scrollState.animateScrollTo(target, tween(SCROLL_MS, easing = LinearOutSlowInEasing))
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    val down = event.type == KeyEventType.KeyDown
                    when (event.key) {
                        Key.DirectionDown -> if (down) scrollBy(LINES_STEP)
                        Key.DirectionUp -> if (down) scrollBy(-LINES_STEP)
                        Key.DirectionRight -> if (down) scrollBy(SCREEN_STEP)
                        Key.DirectionLeft -> if (down) scrollBy(-SCREEN_STEP)
                        // On the release: the window below would otherwise get it, with no press.
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!down) onDismiss()
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .focusable(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.dominant.inactive(), MaterialTheme.colorScheme.surface),
                        ),
                    )
                    // The safe area of a TV.
                    .padding(horizontal = 48.dp, vertical = 27.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                ReaderSide(
                    item = item,
                    canScroll = scrollState.maxValue > 0,
                    modifier = Modifier.width(SIDE_WIDTH).fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawWithContent {
                            drawContent()
                            val max = scrollState.maxValue
                            if (max in 1 until Int.MAX_VALUE) {
                                val viewport = size.height
                                val thumb = viewport * viewport / (viewport + max)
                                val top = (viewport - thumb) * scrollState.value / max
                                val width = 4.dp.toPx()
                                drawRoundRect(
                                    color = colors.controlTint,
                                    topLeft = Offset(size.width - width, top),
                                    size = Size(width, thumb),
                                    cornerRadius = CornerRadius(width / 2),
                                )
                            }
                        },
                ) {
                    Text(
                        text = stringResource(description.kind.stringResource()),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(
                        text = description.text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 32.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(end = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSide(item: AppMediaItem, canScroll: Boolean, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = MaterialTheme.colorScheme.background,
            iconColor = MaterialTheme.colorScheme.secondary,
            icon = TrackIcon,
        )
        AsyncImage(
            model = rememberArtworkRequest(item.image(ImageType.THUMB)?.url),
            placeholder = placeholder,
            fallback = placeholder,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(SIDE_WIDTH)
                .clip(if (item is Artist) CircleShape else RoundedCornerShape(16.dp)),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        item.localizedSubtitle()?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier.weight(1f))
        if (canScroll) {
            Text(
                text = stringResource(Res.string.item_description_hint_scroll),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.item_description_hint_close),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SIDE_WIDTH = 240.dp
private const val SCROLL_MS = 180
private const val LINES_STEP = 0.25f
private const val SCREEN_STEP = 0.9f
