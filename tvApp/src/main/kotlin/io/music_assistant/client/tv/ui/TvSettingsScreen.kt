package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.tv.ExitOnBackBehavior
import io.music_assistant.client.tv.NowPlayingDelay
import io.music_assistant.client.tv.NowPlayingStyle
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.ui.visualizer.VisualizerStyle
import io.music_assistant.client.ui.compose.auth.AuthenticationPanel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

private enum class TvSettingsTab { ACCOUNT, PLAYBACK, MENU, DISPLAY, APP }

/**
 * The TV settings, in tabs, for the signed-in user. The sign-in itself is [TvLoginScreen]; the first
 * tab manages the accounts.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    canGoBack: Boolean,
    onBack: () -> Unit,
    openAccounts: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    BackHandler(enabled = canGoBack) { onBack() }

    val signedIn = (sessionState as? SessionState.Connected)
        ?.dataConnectionState is DataConnectionState.Authenticated
    val tabs = if (signedIn) TvSettingsTab.entries else listOf(TvSettingsTab.ACCOUNT)
    var selectedTab by rememberSaveable { mutableStateOf(TvSettingsTab.ACCOUNT) }
    val currentTab = selectedTab.takeIf { it in tabs } ?: TvSettingsTab.ACCOUNT

    // Signed in, the first focus goes to the tab. Before that, the server form takes it itself.
    val currentTabFocus = remember { FocusRequester() }
    LaunchedEffect(signedIn) { if (signedIn) runCatching { currentTabFocus.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 27.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tv_settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (canGoBack) {
                    // Down leads to the open tab, not to whatever lies straight below in the content.
                    TvOutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.focusProperties { down = currentTabFocus },
                    ) { TvText(stringResource(R.string.tv_back)) }
                }
            }
            // A tab opens on focus, as in the Google TV apps. Coming back up from the content, the
            // focus returns to the open tab and not to the tab that happens to be above it.
            TabRow(selectedTabIndex = tabs.indexOf(currentTab), modifier = Modifier.focusRestorer()) {
                tabs.forEach { tab ->
                    key(tab) {
                        Tab(
                            selected = tab == currentTab,
                            onFocus = { selectedTab = tab },
                            onClick = { selectedTab = tab },
                            modifier = if (tab == currentTab) Modifier.focusRequester(currentTabFocus) else Modifier,
                        ) {
                            TvText(
                                stringResource(tab.title()),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            // Each tab scrolls on its own, and opens at its top.
            key(currentTab) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 27.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    when (currentTab) {
                        TvSettingsTab.ACCOUNT -> TvAccountSettingsTab(openAccounts = openAccounts, onAddAccount = onAddAccount)
                        TvSettingsTab.PLAYBACK -> TvPlaybackSettingsTab()
                        TvSettingsTab.MENU -> TvMenuSettingsTab()
                        TvSettingsTab.DISPLAY -> {
                            NowPlayingDelayCard()
                            NowPlayingStyleCard()
                            VisualizerCard()
                            LyricsOffsetCard()
                        }
                        TvSettingsTab.APP -> {
                            NowPlayingControlsCard()
                            ExitOnBackCard()
                            KeepScreenOnCard()
                            VolumeBroadcastFallbackCard()
                        }
                    }
                }
            }
        }
    }
}

private fun TvSettingsTab.title(): Int = when (this) {
    TvSettingsTab.ACCOUNT -> R.string.tv_tab_account
    TvSettingsTab.PLAYBACK -> R.string.tv_tab_playback
    TvSettingsTab.MENU -> R.string.tv_tab_menu
    TvSettingsTab.DISPLAY -> R.string.tv_tab_display
    TvSettingsTab.APP -> R.string.tv_tab_app
}

@Composable
private fun KeepScreenOnCard() {
    val prefs: TvPreferences = koinInject()
    val keepOn by prefs.keepScreenOn.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_keep_screen_on)) {
        Text(stringResource(R.string.tv_keep_screen_on_hint), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(if (keepOn) R.string.tv_keep_screen_on_enabled else R.string.tv_keep_screen_on_disabled))
            Switch(checked = keepOn, onCheckedChange = { prefs.setKeepScreenOn(it) })
        }
    }
}

@Composable
private fun NowPlayingDelayCard() {
    val prefs: TvPreferences = koinInject()
    val delay by prefs.nowPlayingDelay.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_now_playing_delay)) {
        Row(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingDelay.entries.forEach { option ->
                TvChoiceChip(
                    label = stringResource(option.label()),
                    selected = option == delay,
                    onClick = { prefs.setNowPlayingDelay(option) },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingStyleCard() {
    val prefs: TvPreferences = koinInject()
    val current by prefs.nowPlayingStyle.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_now_playing_style)) {
        Row(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingStyle.entries.forEach { style ->
                TvChoiceChip(
                    label = stringResource(style.label()),
                    selected = style == current,
                    onClick = { prefs.setNowPlayingStyle(style) },
                )
            }
        }
    }
}

@Composable
private fun VisualizerCard() {
    val prefs: TvPreferences = koinInject()
    val current by prefs.visualizer.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_visualizer)) {
        FlowRow(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VisualizerStyle.entries.forEach { style ->
                TvChoiceChip(
                    label = stringResource(style.label()),
                    selected = style == current,
                    onClick = { prefs.setVisualizer(style) },
                )
            }
        }
    }
}

@Composable
private fun LyricsOffsetCard() {
    val prefs: TvPreferences = koinInject()
    val offsetMs by prefs.lyricsOffsetMs.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_lyrics_offset)) {
        Text(stringResource(R.string.tv_lyrics_offset_hint), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                icon = Icons.Default.Remove,
                label = stringResource(R.string.tv_setting_less),
                onClick = { prefs.setLyricsOffsetMs(offsetMs - LYRICS_OFFSET_STEP_MS) },
                enabled = offsetMs > LYRICS_OFFSET_MIN_MS,
            )
            val label = when {
                offsetMs > 0 -> "+$offsetMs ms"
                offsetMs < 0 -> "$offsetMs ms"
                else -> "0 ms"
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            TvIconButton(
                icon = Icons.Default.Add,
                label = stringResource(R.string.tv_setting_more),
                onClick = { prefs.setLyricsOffsetMs(offsetMs + LYRICS_OFFSET_STEP_MS) },
                enabled = offsetMs < LYRICS_OFFSET_MAX_MS,
            )
        }
    }
}

private const val LYRICS_OFFSET_STEP_MS = 100
private const val LYRICS_OFFSET_MIN_MS = -1000
private const val LYRICS_OFFSET_MAX_MS = 1000

private fun NowPlayingStyle.label(): Int = when (this) {
    NowPlayingStyle.CLASSIC -> R.string.tv_np_style_classic
    NowPlayingStyle.BANNER -> R.string.tv_np_style_banner
}

private fun NowPlayingDelay.label(): Int = when (this) {
    NowPlayingDelay.OFF -> R.string.tv_delay_off
    NowPlayingDelay.SECONDS_30 -> R.string.tv_delay_30s
    NowPlayingDelay.MINUTE_1 -> R.string.tv_delay_1m
    NowPlayingDelay.MINUTES_5 -> R.string.tv_delay_5m
}

@Composable
private fun ExitOnBackCard() {
    val prefs: TvPreferences = koinInject()
    val current by prefs.exitOnBackBehavior.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_exit_on_back)) {
        Row(
            modifier = Modifier.tvHorizontalFocusRow(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExitOnBackBehavior.entries.forEach { option ->
                TvChoiceChip(
                    label = stringResource(option.label()),
                    selected = option == current,
                    onClick = { prefs.setExitOnBackBehavior(option) },
                )
            }
        }
    }
}

private fun ExitOnBackBehavior.label(): Int = when (this) {
    ExitOnBackBehavior.NEVER_ASK -> R.string.tv_exit_on_back_never_ask
    ExitOnBackBehavior.ASK_IF_LOCAL -> R.string.tv_exit_on_back_ask_if_local
    ExitOnBackBehavior.ALWAYS_ASK -> R.string.tv_exit_on_back_always_ask
}

@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
