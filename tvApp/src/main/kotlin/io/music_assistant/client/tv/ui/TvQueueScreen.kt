package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.Queue

/**
 * The queue of the TV player, as on the phone: OK on a track plays it, a long OK opens its menu
 * (play next, move, remove; Move then takes Up/Down, OK confirms, Back cancels). The list
 * scrolls to the current track by itself.
 */
@Composable
fun TvQueueScreen(
    playerData: PlayerData?,
    homeScreenViewModel: HomeScreenViewModel,
    onGoToLibrary: () -> Unit,
) {
    val queueId = playerData?.queueOrPlayerId
    val livePosition = remember(queueId) { queueId?.let(homeScreenViewModel::observePosition) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tv_nav_queue),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (playerData?.queueInfo?.isDynamicPlaylist == true) SmartMixBadge()
        }
        Queue(
            modifier = Modifier.fillMaxWidth().weight(1f),
            queue = playerData?.queue ?: DataState.NoData(),
            onGoToLibrary = onGoToLibrary,
            isQueueExpanded = true,
            isCurrentPage = true,
            contentPadding = PaddingValues(bottom = 16.dp),
            queueAction = homeScreenViewModel::queueAction,
            livePositionFlow = livePosition,
            showMoveActions = true,
        )
    }
}
