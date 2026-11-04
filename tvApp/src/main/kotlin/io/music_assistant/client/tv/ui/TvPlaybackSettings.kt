package io.music_assistant.client.tv.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.ServerConfigEntry
import io.music_assistant.client.data.model.server.ServerConfigOption
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.R
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.selectedPlayer
import io.music_assistant.client.ui.compose.settings.SendspinSettingsViewModel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/** The player that the app acts on; the local player's own state (optimistic) when it is the chosen one. */
fun activePlayer(selected: PlayerData?, local: PlayerData?): PlayerData? =
    if (selected == null || selected.isLocal) local ?: selected else selected

private val QUEUES = TvConfigSource.Core("player_queues")
private val STREAMS = TvConfigSource.Core("streams")

/** One setting that the tab shows, if the server has it. Seconds show as a duration. */
private data class SettingRow(val key: String, @StringRes val title: Int, val seconds: Boolean = false)

// The server has no such settings for one player: duration, recency windows, same-album crossfade
// and the normalization modes exist only as server-wide values.
private val PLAYER_ROWS = listOf(
    SettingRow("crossfade_mode", R.string.tv_setting_crossfade_mode),
    SettingRow("autoplay_mode", R.string.tv_setting_autoplay_mode),
    SettingRow("autoplay_playlist", R.string.tv_setting_autoplay_playlist),
    SettingRow("smart_shuffle_enabled", R.string.tv_setting_smart_shuffle),
    SettingRow("smart_shuffle_optimize_smart_fades", R.string.tv_setting_smart_shuffle_fades),
    SettingRow("volume_normalization", R.string.tv_setting_normalization),
)

private val SERVER_QUEUE_ROWS = listOf(
    SettingRow("crossfade_enabled", R.string.tv_setting_crossfade_default),
    SettingRow("crossfade_mode", R.string.tv_setting_crossfade_mode),
    SettingRow("crossfade_duration", R.string.tv_setting_crossfade_duration),
    SettingRow("autoplay_enabled", R.string.tv_setting_autoplay_default),
    SettingRow("autoplay_mode", R.string.tv_setting_autoplay_mode),
    SettingRow("smart_shuffle_enabled", R.string.tv_setting_smart_shuffle),
    SettingRow("smart_shuffle_optimize_smart_fades", R.string.tv_setting_smart_shuffle_fades),
    SettingRow("smart_shuffle_song_recency", R.string.tv_setting_song_recency, seconds = true),
    SettingRow("smart_shuffle_artist_recency", R.string.tv_setting_artist_recency, seconds = true),
    SettingRow("smart_shuffle_duplicate_gap", R.string.tv_setting_duplicate_gap, seconds = true),
    SettingRow("volume_normalization", R.string.tv_setting_normalization),
)

// The other streams settings stay out: changing one that needs a reload cuts the music on all players.
private val SERVER_STREAM_ROWS = listOf(
    SettingRow("allow_crossfade_same_album", R.string.tv_setting_crossfade_same_album),
    SettingRow("volume_normalization_tracks", R.string.tv_setting_normalization_tracks),
    SettingRow("volume_normalization_radio", R.string.tv_setting_normalization_radio),
)

private val OPTION_LABELS = mapOf(
    "global" to R.string.tv_option_global,
    "enabled" to R.string.tv_option_on,
    "disabled" to R.string.tv_option_off,
    "standard_crossfade" to R.string.tv_option_crossfade_standard,
    "smart_crossfade" to R.string.tv_option_crossfade_smart,
    "auto" to R.string.tv_option_autoplay_auto,
    "similar" to R.string.tv_option_autoplay_similar,
    "library" to R.string.tv_option_autoplay_library,
    "playlist" to R.string.tv_option_autoplay_playlist,
    "dynamic" to R.string.tv_option_norm_dynamic,
    "measurement_only" to R.string.tv_option_norm_measurement,
    "fixed_gain" to R.string.tv_option_norm_fixed,
    "fallback_fixed_gain" to R.string.tv_option_norm_fallback_fixed,
    "fallback_dynamic" to R.string.tv_option_norm_fallback_dynamic,
)

/**
 * The playback tab, split by what the server stores where: the settings of the chosen player (its
 * queue), the defaults and values shared by all players, and what only concerns this TV.
 */
@Composable
fun TvPlaybackSettingsTab() {
    val viewModel = koinViewModel<TvPlaybackSettingsViewModel>()
    val homeScreenViewModel: HomeScreenViewModel = koinViewModel()
    val dataSource: MainDataSource = koinInject()
    val scopes = LocalGrantedScopes.current
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val playersState by homeScreenViewModel.playersState.collectAsStateWithLifecycle()
    val local by dataSource.localPlayer.collectAsStateWithLifecycle()

    val playersData = playersState as? HomeScreenViewModel.PlayersState.Data
    val player = activePlayer(playersData?.selectedPlayer, local)
    val queueSource = (player?.queueInfo?.id ?: player?.player?.queueId)?.let { TvConfigSource.Queue(it) }
    var pickingPlayer by remember { mutableStateOf(false) }
    var pendingPlayerSwitch by remember { mutableStateOf<PlayerData?>(null) }

    LaunchedEffect(queueSource) { queueSource?.let(viewModel::load) }
    LaunchedEffect(scopes.canConfigureCore) {
        if (scopes.canConfigureCore) {
            viewModel.load(QUEUES)
            viewModel.load(STREAMS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (saveFailed) {
            Text(
                stringResource(R.string.tv_settings_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        SettingsCard(stringResource(R.string.tv_section_this_player)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    player?.player?.name ?: stringResource(R.string.tv_nothing_playing),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TvOutlinedButton(onClick = { pickingPlayer = true }) {
                    TvText(stringResource(R.string.tv_pick_player))
                }
            }
            Text(stringResource(R.string.tv_section_this_player_hint), style = MaterialTheme.typography.bodyMedium)
            player?.let { QueueToggles(it, dataSource) }
            if (scopes.canConfigurePlayers && queueSource != null) {
                SettingRows(PLAYER_ROWS, configs[queueSource].orEmpty()) { key, value ->
                    viewModel.save(queueSource, key, value)
                }
            }
        }

        val serverQueueEntries = configs[QUEUES].orEmpty()
        val serverStreamEntries = configs[STREAMS].orEmpty()
        if (scopes.canConfigureCore && (serverQueueEntries.isNotEmpty() || serverStreamEntries.isNotEmpty())) {
            SettingsCard(stringResource(R.string.tv_section_all_players)) {
                Text(stringResource(R.string.tv_section_all_players_hint), style = MaterialTheme.typography.bodyMedium)
                SettingRows(SERVER_QUEUE_ROWS, serverQueueEntries) { key, value -> viewModel.save(QUEUES, key, value) }
                SettingRows(
                    SERVER_STREAM_ROWS,
                    serverStreamEntries,
                ) { key, value -> viewModel.save(STREAMS, key, value) }
            }
        }

        ThisTvCard()
        DefaultPlayerCard()
    }

    if (pickingPlayer) {
        TvPlayerPicker(
            players = playersData?.playerData.orEmpty(),
            selectedPlayerId = playersData?.selectedPlayer?.playerId,
            onPick = { newPlayer ->
                val queueNonEmpty = player?.queueItems?.isNotEmpty() == true
                val isNewPlayer = newPlayer.playerId != player?.playerId
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
                player?.let { src ->
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
}

/** On/off of crossfade and autoplay: commands on the running queue, not config entries. */
@Composable
private fun QueueToggles(player: PlayerData, dataSource: MainDataSource) {
    val info = player.queueInfo ?: return
    info.crossfadeEnabled?.let { crossfade ->
        OnOffRow(stringResource(R.string.tv_crossfade), crossfade) {
            dataSource.playerAction(player, PlayerAction.ToggleCrossfade(current = crossfade))
        }
    }
    // The server manages autoplay on a smart mix and refuses the toggle.
    info.autoPlayEnabled?.takeUnless { info.isDynamicPlaylist }?.let { autoplay ->
        OnOffRow(stringResource(R.string.tv_autoplay), autoplay) {
            dataSource.playerAction(player, PlayerAction.ToggleDontStopTheMusic(current = autoplay))
        }
    }
}

@Composable
private fun OnOffRow(title: String, on: Boolean, onToggle: () -> Unit) {
    SettingLine(title) {
        TvChoiceChip(stringResource(R.string.tv_option_on), selected = on, onClick = { if (!on) onToggle() })
        TvChoiceChip(stringResource(R.string.tv_option_off), selected = !on, onClick = { if (on) onToggle() })
    }
}

@Composable
private fun SettingRows(
    rows: List<SettingRow>,
    entries: Map<String, ServerConfigEntry>,
    onChange: (String, JsonElement) -> Unit,
) {
    rows.forEach { row ->
        entries[row.key]
            ?.takeIf { !it.hidden && it.appliesWith(entries) }
            ?.let { entry -> SettingEditor(row, entry) { onChange(row.key, it) } }
    }
}

@Composable
private fun SettingEditor(row: SettingRow, entry: ServerConfigEntry, onChange: (JsonElement) -> Unit) {
    val range = entry.range?.takeIf { it.size == 2 }
    val current = entry.currentText
    when {
        entry.options.isNotEmpty() -> SettingLine(stringResource(row.title)) {
            entry.options.forEach { option ->
                TvChoiceChip(
                    label = optionLabel(option, row.seconds),
                    selected = option.valueText == current,
                    enabled = !option.disabled,
                    onClick = { onChange(option.value) },
                )
            }
        }

        entry.type == "boolean" -> OnOffRow(stringResource(row.title), current == "true") {
            onChange(JsonPrimitive(current != "true"))
        }

        range != null -> current?.toIntOrNull()?.let { value ->
            SettingLine(stringResource(row.title)) {
                TvIconButton(Icons.Default.Remove, stringResource(R.string.tv_setting_less), onClick = {
                    if (value > range[0]) onChange(JsonPrimitive(value - 1))
                })
                Text(
                    stringResource(R.string.tv_buffer_seconds, value),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                TvIconButton(Icons.Default.Add, stringResource(R.string.tv_setting_more), onClick = {
                    if (value < range[1]) onChange(JsonPrimitive(value + 1))
                })
            }
        }
    }
}

@Composable
private fun optionLabel(option: ServerConfigOption, seconds: Boolean): String {
    val value = option.valueText
    val duration = value?.toIntOrNull()?.takeIf { seconds }
    return when {
        duration != null -> durationLabel(duration)
        else -> OPTION_LABELS[value]?.let { stringResource(it) } ?: option.title ?: value.orEmpty()
    }
}

@Composable
private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.tv_option_off)
    seconds < SECONDS_PER_HOUR -> stringResource(R.string.tv_duration_minutes, seconds / SECONDS_PER_MINUTE)
    seconds < SECONDS_PER_DAY -> stringResource(R.string.tv_duration_hours, seconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.tv_duration_days, seconds / SECONDS_PER_DAY)
}

@Composable
private fun SettingLine(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** What only concerns the player of this TV: its name, buffer and audio format. Kept on the TV. */
@Composable
private fun ThisTvCard() {
    val sendspin = koinViewModel<SendspinSettingsViewModel>()
    val draft by sendspin.draft.collectAsStateWithLifecycle()
    val isSavable by sendspin.isSavable.collectAsStateWithLifecycle()
    val isConfirmationPending by sendspin.isConfirmationPending.collectAsStateWithLifecycle()
    // A change while the player plays asks for a confirmation on the phone; the TV applies it.
    LaunchedEffect(isConfirmationPending) { if (isConfirmationPending) sendspin.confirm() }

    SettingsCard(stringResource(R.string.tv_player)) {
        Text(stringResource(R.string.tv_section_this_tv_hint), style = MaterialTheme.typography.bodyMedium)
        val saveFocus = remember { FocusRequester() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> sendspin.update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.tv_player_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveFocus.requestFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 200.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                        ) {
                            saveFocus.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
            )
            TvButton(
                onClick = sendspin::save,
                enabled = isSavable,
                modifier = Modifier.focusRequester(saveFocus),
            ) { TvText(stringResource(R.string.tv_save)) }
        }
        // A format or buffer change reconnects the player, so it applies at once and not through Save.
        SettingLine(stringResource(R.string.tv_buffer)) {
            BUFFER_SECONDS_CHOICES.forEach { seconds ->
                TvChoiceChip(
                    label = stringResource(R.string.tv_buffer_seconds, seconds),
                    selected = seconds == draft.bufferSeconds,
                    onClick = {
                        sendspin.update { it.copy(bufferSeconds = seconds) }
                        sendspin.save()
                    },
                )
            }
        }
        Text(stringResource(R.string.tv_buffer_hint), style = MaterialTheme.typography.bodyMedium)
        SettingLine(stringResource(R.string.tv_setting_codec)) {
            SettingsRepository.CODECS.forEach { codec ->
                TvChoiceChip(
                    label = codec.name,
                    selected = codec == draft.codecPreference,
                    onClick = {
                        sendspin.update { it.copy(codecPreference = codec) }
                        sendspin.save()
                    },
                )
            }
        }
        if (draft.hiResAvailable()) {
            SettingLine(stringResource(R.string.tv_setting_bit_depth)) {
                BIT_DEPTHS.forEach { depth ->
                    TvChoiceChip(
                        label = stringResource(R.string.tv_bits, depth),
                        selected = depth == draft.effectiveBitDepth(),
                        onClick = {
                            sendspin.update { it.copy(bitDepth = depth) }
                            sendspin.save()
                        },
                    )
                }
            }
        }
    }
}

/** The player selected at startup when the last used one is unavailable. */
@Composable
private fun DefaultPlayerCard() {
    val prefs: TvPreferences = koinInject()
    val homeScreenViewModel: HomeScreenViewModel = koinViewModel()
    val playersState by homeScreenViewModel.playersState.collectAsStateWithLifecycle()
    val players = (playersState as? HomeScreenViewModel.PlayersState.Data)?.playerData.orEmpty()
    val defaultPlayerId by prefs.defaultPlayerId.collectAsStateWithLifecycle()
    val sorted = remember(players) { players.sortedByDescending { it.isLocal } }

    SettingsCard(stringResource(R.string.tv_default_player)) {
        Text(stringResource(R.string.tv_default_player_hint), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvChoiceChip(
                label = stringResource(R.string.tv_default_player_last_used),
                selected = defaultPlayerId == null,
                onClick = { prefs.setDefaultPlayerId(null) },
            )
            sorted.forEach { player ->
                TvChoiceChip(
                    label = if (player.isLocal) {
                        stringResource(R.string.tv_this_tv, player.player.name)
                    } else {
                        player.player.name
                    },
                    selected = player.playerId == defaultPlayerId,
                    onClick = { prefs.setDefaultPlayerId(player.playerId) },
                    enabled = player.player.isSelectable,
                )
            }
        }
    }
}

private val BUFFER_SECONDS_CHOICES = (SettingsRepository.BUFFER_SECONDS_MIN..SettingsRepository.BUFFER_SECONDS_MAX)
    .step(SettingsRepository.BUFFER_SECONDS_STEP)
    .toList()
private val BIT_DEPTHS = listOf(16, 24)
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86_400
