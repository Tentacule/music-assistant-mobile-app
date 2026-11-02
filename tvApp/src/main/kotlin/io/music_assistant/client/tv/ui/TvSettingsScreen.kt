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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.tv.NowPlayingDelay
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

private enum class TvSettingsTab { SERVER, PLAYBACK, MENU, DISPLAY }

/**
 * The TV settings, in tabs. Before sign-in there is only the server tab. Built on the phone settings
 * ViewModels; no QR code or OAuth sign-in (no camera, often no browser).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(canGoBack: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    BackHandler(enabled = canGoBack) { onBack() }

    val signedIn = (sessionState as? SessionState.Connected)
        ?.dataConnectionState is DataConnectionState.Authenticated
    val tabs = if (signedIn) TvSettingsTab.entries else listOf(TvSettingsTab.SERVER)
    var selectedTab by rememberSaveable { mutableStateOf(TvSettingsTab.SERVER) }
    val currentTab = selectedTab.takeIf { it in tabs } ?: TvSettingsTab.SERVER

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
                        TvSettingsTab.SERVER -> ServerTab(viewModel)
                        TvSettingsTab.PLAYBACK -> TvPlaybackSettingsTab()
                        TvSettingsTab.MENU -> TvMenuSettingsTab()
                        TvSettingsTab.DISPLAY -> {
                            NowPlayingDelayCard()
                            VisualizerCard()
                        }
                    }
                }
            }
        }
    }
}

private fun TvSettingsTab.title(): Int = when (this) {
    TvSettingsTab.SERVER -> R.string.tv_server
    TvSettingsTab.PLAYBACK -> R.string.tv_tab_playback
    TvSettingsTab.MENU -> R.string.tv_tab_menu
    TvSettingsTab.DISPLAY -> R.string.tv_tab_display
}

@Composable
private fun ServerTab(viewModel: SettingsViewModel) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val connectAttemptInFlight by viewModel.connectAttemptInFlight.collectAsStateWithLifecycle()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(Defaults.PORT.toString()) }
    var isTls by remember { mutableStateOf(false) }
    LaunchedEffect(savedConnectionInfo) {
        savedConnectionInfo?.let {
            host = it.host
            port = it.port.toString()
            isTls = it.isTls
        }
    }

    SettingsCard(stringResource(R.string.tv_server)) {
        when (val state = sessionState) {
            is SessionState.Disconnected -> ServerForm(
                host = host,
                port = port,
                isTls = isTls,
                onHostChange = { host = it },
                onPortChange = { port = it },
                onTlsChange = { isTls = it },
                // An empty address means the default one, as on the phone, so the button stays focusable.
                connectEnabled = port.toIntOrNull() != null && !connectAttemptInFlight,
                onConnect = {
                    viewModel.attemptConnection(host.trim().ifBlank { Defaults.URI }, port.trim(), isTls, "")
                },
            )

            SessionState.Connecting, is SessionState.Reconnecting -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.tv_connecting, host), modifier = Modifier.weight(1f))
                TvOutlinedButton(onClick = viewModel::disconnect) { TvText(stringResource(R.string.tv_cancel)) }
            }

            is SessionState.Connected -> {
                val serverName = state.serverInfo?.name?.takeIf { it.isNotBlank() }
                    ?: savedConnectionInfo?.let { "${it.host}:${it.port}" }.orEmpty()
                Text(stringResource(R.string.tv_connected_to, serverName))
                TvOutlinedButton(onClick = viewModel::disconnect) {
                    TvText(stringResource(R.string.tv_disconnect))
                }
            }
        }
    }

    (sessionState as? SessionState.Connected)?.let { connected ->
        SettingsCard(stringResource(R.string.tv_login)) {
            AuthenticationPanel(modifier = Modifier.fillMaxWidth(), user = connected.user)
        }
    }
}

@Composable
private fun ServerForm(
    host: String,
    port: String,
    isTls: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    connectEnabled: Boolean,
    onConnect: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text(stringResource(R.string.tv_server_host)) },
            placeholder = { Text(Defaults.URI) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text(stringResource(R.string.tv_server_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(140.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tv_server_tls), modifier = Modifier.weight(1f))
        Switch(checked = isTls, onCheckedChange = onTlsChange)
    }
    // The first focus goes to the button, not to a text field: on a TV, a focused field opens the
    // on-screen keyboard over the form.
    val connectFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { connectFocus.requestFocus() } }
    TvButton(
        onClick = onConnect,
        enabled = connectEnabled,
        modifier = Modifier.focusRequester(connectFocus),
    ) { TvText(stringResource(R.string.tv_connect)) }
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

private fun NowPlayingDelay.label(): Int = when (this) {
    NowPlayingDelay.OFF -> R.string.tv_delay_off
    NowPlayingDelay.SECONDS_30 -> R.string.tv_delay_30s
    NowPlayingDelay.MINUTE_1 -> R.string.tv_delay_1m
    NowPlayingDelay.MINUTES_5 -> R.string.tv_delay_5m
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
