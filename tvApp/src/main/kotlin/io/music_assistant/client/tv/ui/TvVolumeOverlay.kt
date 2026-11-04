package io.music_assistant.client.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import io.music_assistant.client.tv.TvVolumeController
import org.koin.compose.koinInject

/** Transient overlay that appears when the user adjusts the volume and fades out after 3 s. */
@Composable
fun TvVolumeOverlay(controller: TvVolumeController = koinInject()) {
    val info by controller.overlay.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val current = info ?: return@AnimatedVisibility
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 72.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val icon = when {
                    current.isMuted || current.level == 0f -> Icons.Default.VolumeOff
                    current.level < 0.34f -> Icons.Default.VolumeMute
                    current.level < 0.67f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                }
                TvIcon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                LinearProgressIndicator(
                    progress = { current.level },
                    modifier = Modifier.width(240.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                TvText(
                    text = "${(current.level * 100).toInt()} %",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
    }
}
