package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.tv.NowPlayingDelay
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.ui.compose.auth.AuthenticationPanel
import io.music_assistant.client.ui.compose.settings.SendspinSettingsViewModel
import io.music_assistant.client.ui.compose.settings.SettingsViewModel
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.FilterChip as TvFilterChip
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * The TV settings: the server, the sign-in, the name of the TV player and the Now playing delay.
 * Built on the phone settings ViewModels; no QR code or OAuth sign-in (no camera, often no browser).
 */
@Composable
fun TvSettingsScreen(canGoBack: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val connectAttemptInFlight by viewModel.connectAttemptInFlight.collectAsStateWithLifecycle()

    BackHandler(enabled = canGoBack) { onBack() }

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tv_settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (canGoBack) {
                    TvOutlinedButton(onClick = onBack) { TvText(stringResource(R.string.tv_back)) }
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
                if (connected.dataConnectionState is DataConnectionState.Authenticated) {
                    PlayerNameCard()
                    NowPlayingDelayCard()
                }
            }
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

/** The name the TV player gets in Music Assistant. */
@Composable
private fun PlayerNameCard() {
    val sendspin = koinViewModel<SendspinSettingsViewModel>()
    val draft by sendspin.draft.collectAsStateWithLifecycle()
    val isSavable by sendspin.isSavable.collectAsStateWithLifecycle()
    val isConfirmationPending by sendspin.isConfirmationPending.collectAsStateWithLifecycle()
    // Renaming the player while it plays asks for a confirmation on the phone; the TV applies it.
    LaunchedEffect(isConfirmationPending) { if (isConfirmationPending) sendspin.confirm() }

    SettingsCard(stringResource(R.string.tv_player)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { name -> sendspin.update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.tv_player_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TvButton(onClick = sendspin::save, enabled = isSavable) { TvText(stringResource(R.string.tv_save)) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NowPlayingDelayCard() {
    val prefs: TvPreferences = koinInject()
    val delay by prefs.nowPlayingDelay.collectAsStateWithLifecycle()
    SettingsCard(stringResource(R.string.tv_now_playing_delay)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NowPlayingDelay.entries.forEach { option ->
                TvFilterChip(
                    selected = option == delay,
                    onClick = { prefs.setNowPlayingDelay(option) },
                ) { TvText(stringResource(option.label())) }
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
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
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
