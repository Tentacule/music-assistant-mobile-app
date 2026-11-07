package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthState
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.accounts.TvAccount
import io.music_assistant.client.tv.accounts.TvAccountManager
import io.music_assistant.client.tv.discovery.DiscoveredServer
import io.music_assistant.client.tv.discovery.TvServerDiscovery
import io.music_assistant.client.tv.discovery.lanAddressFor
import io.music_assistant.client.tv.login.HomeAssistantTvLogin
import io.music_assistant.client.tv.login.LoginPageTexts
import io.music_assistant.client.tv.login.QrCodeImage
import io.music_assistant.client.tv.login.TvLoginWebServer
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.connectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * The sign-in of the TV, in three steps: the server (found on the network, saved, or typed), the
 * method (a Music Assistant account, or Home Assistant when the server offers it), then the
 * sign-in itself, from a phone through a QR code or on the TV.
 */
@Composable
fun TvLoginScreen() {
    val serviceClient: ServiceClient = koinInject()
    val manager: TvAccountManager = koinInject()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()
    val returnTo by manager.returnTo.collectAsStateWithLifecycle()
    val settings: SettingsRepository = koinInject()
    val target by settings.connectionInfo.collectAsStateWithLifecycle()

    BackHandler(enabled = returnTo != null) { manager.cancelAdd() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (returnTo != null) R.string.tv_accounts_add else R.string.tv_login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                returnTo?.let {
                    TvOutlinedButton(onClick = manager::cancelAdd) {
                        TvText(stringResource(R.string.tv_login_back_to, it.label))
                    }
                }
            }
            when (val state = sessionState) {
                is SessionState.Disconnected -> ServerPicker(manager)
                SessionState.Connecting, is SessionState.Reconnecting -> Connecting(
                    label = target?.host.orEmpty(),
                    onCancel = serviceClient::disconnectByUser,
                )
                is SessionState.Connected -> when (state.dataConnectionState) {
                    is DataConnectionState.AwaitingAuth -> SignIn(state, onChangeServer = serviceClient::disconnectByUser)
                    else -> Connecting(state.serverInfo?.name.orEmpty(), serviceClient::disconnectByUser)
                }
            }
        }
    }
}

@Composable
private fun Connecting(label: String, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.tv_connecting, label), modifier = Modifier.weight(1f))
        TvOutlinedButton(onClick = onCancel) { TvText(stringResource(R.string.tv_cancel)) }
    }
}

// --- Step 1: the server ---

@Composable
private fun ServerPicker(manager: TvAccountManager) {
    val context = LocalContext.current
    val accounts by manager.accounts.collectAsStateWithLifecycle()
    val active by manager.active.collectAsStateWithLifecycle()
    val returnTo by manager.returnTo.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<TvAccount?>(null) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val discovery = remember { TvServerDiscovery(context.applicationContext) }
    val discovered by produceState<List<DiscoveredServer>?>(null) {
        discovery.servers().collect { value = it }
    }

    // While the user adds an account, the saved accounts are one Back away: not listed here.
    if (accounts.isNotEmpty() && returnTo == null) {
        Section(stringResource(R.string.tv_login_saved_accounts)) {
            AccountRow(
                accounts = accounts,
                activeId = active?.id,
                onOpen = { account -> if (account.hasPin) pinFor = account else manager.switchTo(account) },
                onAdd = null,
            )
        }
    }

    Section(stringResource(R.string.tv_login_found_servers)) {
        val servers = discovered.orEmpty()
        if (servers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.tv_login_searching))
            }
        }
        val firstServer = remember { FocusRequester() }
        LaunchedEffect(servers.isNotEmpty()) {
            if (servers.isNotEmpty() && (accounts.isEmpty() || returnTo != null)) runCatching { firstServer.requestFocus() }
        }
        servers.forEachIndexed { index, server ->
            ServerRow(
                icon = if (server.homeAssistantAddon) Icons.Default.Home else Icons.Default.Dns,
                title = server.label,
                subtitle = listOfNotNull(
                    "${server.host}:${server.port}",
                    server.version?.let { stringResource(R.string.tv_login_version, it) },
                    stringResource(R.string.tv_login_ha_addon).takeIf { server.homeAssistantAddon },
                ).joinToString(" · "),
                onClick = { manager.connectForLogin(server.connectionInfo, server.serverId) },
                modifier = if (index == 0) Modifier.focusRequester(firstServer) else Modifier,
            )
        }
    }

    Section(stringResource(R.string.tv_login_manual)) {
        if (manual) {
            ManualServerForm(onConnect = { manager.connectForLogin(it) })
        } else {
            TvOutlinedButton(onClick = { manual = true }) { TvText(stringResource(R.string.tv_login_enter_address)) }
        }
    }

    pinFor?.let { account ->
        TvPinDialog(onDismiss = { pinFor = null }) {
            TvPinCheck(
                account = account,
                title = stringResource(R.string.tv_pin_enter),
                onSuccess = {
                    pinFor = null
                    manager.switchTo(account)
                },
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ServerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvIcon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                TvText(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvText(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ManualServerForm(onConnect: (ConnectionInfo) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(Defaults.PORT.toString()) }
    var isTls by rememberSaveable { mutableStateOf(false) }
    val connectFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { connectFocus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.tv_server_host)) },
            placeholder = { Text(Defaults.URI) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).tvTextFieldFocus(),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text(stringResource(R.string.tv_server_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { connectFocus.requestFocus() }),
            modifier = Modifier.width(140.dp).tvTextFieldFocus(),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tv_server_tls), modifier = Modifier.weight(1f))
        Switch(checked = isTls, onCheckedChange = { isTls = it })
    }
    TvButton(
        onClick = {
            port.trim().toIntOrNull()?.let {
                onConnect(ConnectionInfo(host.trim().ifBlank { Defaults.URI }, it, isTls))
            }
        },
        enabled = port.trim().toIntOrNull() != null,
        modifier = Modifier.focusRequester(connectFocus),
    ) { TvText(stringResource(R.string.tv_connect)) }
}

// --- Steps 2 and 3: the method and the sign-in ---

private enum class LoginMethod { ACCOUNT, HOME_ASSISTANT, TOKEN }

@Composable
private fun SignIn(state: SessionState.Connected, onChangeServer: () -> Unit) {
    val authManager: AuthenticationManager = koinInject()
    val serviceClient: ServiceClient = koinInject()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val serverInfo = state.serverInfo
    val serverName = serverInfo?.name?.takeIf { it.isNotBlank() }
        ?: state.connectionInfo?.let { "${it.host}:${it.port}" }.orEmpty()
    val serverKey = serverInfo?.serverId

    var providers by remember(serverKey) { mutableStateOf<List<AuthProvider>?>(null) }
    LaunchedEffect(serverKey) { providers = authManager.getProviders().getOrNull() }
    val haProvider = providers?.firstOrNull { it.type == HOME_ASSISTANT_PROVIDER }
    var method by rememberSaveable(serverKey) { mutableStateOf(LoginMethod.ACCOUNT) }

    val loading = authState is AuthState.Loading ||
        (state.dataConnectionState as? DataConnectionState.AwaitingAuth)?.authProcessState == AuthProcessState.InProgress
    val error = (authState as? AuthState.Error)?.message
        ?: ((state.dataConnectionState as? DataConnectionState.AwaitingAuth)?.authProcessState as? AuthProcessState.Failed)?.reason

    // The page that the phone opens, for this server, while the sign-in shows.
    val texts = loginPageTexts()
    val scope = rememberCoroutineScope()
    // One page for the whole sign-in: the provider list, which loads after it, does not restart it.
    val currentHaProvider by rememberUpdatedState(haProvider)
    val webServer = remember(serverKey) {
        TvLoginWebServer(
            texts = texts,
            serverName = serverName,
            homeAssistant = { currentHaProvider != null },
            onCredentials = { username, password ->
                withContext(Dispatchers.Main) {
                    authManager.loginWithCredentials(BUILTIN_PROVIDER, username, password)
                    val after = serviceClient.sessionState.value as? SessionState.Connected
                    when (val data = after?.dataConnectionState) {
                        is DataConnectionState.Authenticated -> null
                        is DataConnectionState.AwaitingAuth ->
                            (data.authProcessState as? AuthProcessState.Failed)?.reason ?: texts.failure
                        else -> texts.failure
                    }
                }
            },
            homeAssistantUrl = { returnUrl ->
                withContext(Dispatchers.Main) {
                    currentHaProvider?.let { authManager.getOAuthUrl(it, returnUrl).getOrNull() }
                }
            },
            onHomeAssistantToken = { token -> scope.launch { authManager.handleOAuthCallback(token) } },
        )
    }
    var pageBase by remember(webServer) { mutableStateOf<String?>(null) }
    val serverHost = state.connectionInfo?.host
    DisposableEffect(webServer) {
        val port = webServer.start()
        // The address must be one that a phone next to the server reaches: the TV address on the
        // network of the server, not the address of another interface (a VPN, an emulator NAT).
        scope.launch {
            val ip = lanAddressFor(serverHost)
            pageBase = if (ip != null && port != null) "http://$ip:$port" else null
        }
        onDispose { webServer.stop() }
    }

    Text(stringResource(R.string.tv_login_server, serverName), style = MaterialTheme.typography.titleLarge)

    // The method: a Music Assistant account, or Home Assistant on a server that offers it.
    val methodFocus = remember { FocusRequester() }
    LaunchedEffect(serverKey) { runCatching { methodFocus.requestFocus() } }
    Row(modifier = Modifier.tvHorizontalFocusRow(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MethodCard(
            icon = Icons.Default.Person,
            title = stringResource(R.string.tv_login_method_account),
            selected = method != LoginMethod.HOME_ASSISTANT,
            // The token form belongs to this method: coming back to the card keeps it.
            onClick = { if (method == LoginMethod.HOME_ASSISTANT) method = LoginMethod.ACCOUNT },
            modifier = Modifier.focusRequester(methodFocus),
        )
        if (haProvider != null) {
            MethodCard(
                icon = Icons.Default.Home,
                title = stringResource(R.string.tv_login_method_ha),
                selected = method == LoginMethod.HOME_ASSISTANT,
                onClick = { method = LoginMethod.HOME_ASSISTANT },
            )
        }
    }

    when (method) {
        // The phone first, then the TV keyboard, one under the other.
        LoginMethod.ACCOUNT, LoginMethod.TOKEN -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            pageBase?.let { base ->
                PhoneCard(
                    url = "$base/?k=${webServer.secret}",
                    title = stringResource(R.string.tv_login_phone_title),
                    hint = stringResource(R.string.tv_login_phone_hint),
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.tv_login_on_tv), style = MaterialTheme.typography.titleMedium)
                    if (method == LoginMethod.TOKEN) {
                        TokenForm(enabled = !loading, onSubmit = { scope.launch { authManager.loginWithToken(it) } })
                        TvOutlinedButton(onClick = { method = LoginMethod.ACCOUNT }) {
                            TvText(stringResource(R.string.tv_login_use_password))
                        }
                    } else {
                        CredentialsForm(
                            enabled = !loading,
                            onSubmit = { username, password ->
                                scope.launch { authManager.loginWithCredentials(BUILTIN_PROVIDER, username, password) }
                            },
                        )
                        TvOutlinedButton(onClick = { method = LoginMethod.TOKEN }) {
                            TvText(stringResource(R.string.tv_login_use_token))
                        }
                    }
                }
            }
        }

        LoginMethod.HOME_ASSISTANT -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            pageBase?.let { base ->
                PhoneCard(
                    url = "$base/ha?k=${webServer.secret}",
                    title = stringResource(R.string.tv_login_ha_title),
                    hint = stringResource(R.string.tv_login_ha_hint),
                )
            }
            haProvider?.let { provider ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.tv_login_on_tv), style = MaterialTheme.typography.titleMedium)
                        HomeAssistantTvForm(
                            enabled = !loading,
                            authorizationUrl = {
                                authManager.getOAuthUrl(provider, pageBase?.let { "$it/ha/done" } ?: "http://localhost/")
                                    .getOrNull()
                            },
                            onToken = authManager::handleOAuthCallback,
                        )
                    }
                }
            }
        }
    }

    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.tv_login_signing_in))
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    TvOutlinedButton(onClick = onChangeServer) { TvText(stringResource(R.string.tv_login_change_server)) }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A method opens on focus, as the settings tabs do: the remote needs no extra OK.
    TvFocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier.width(320.dp).onFocusChanged { if (it.isFocused) onClick() },
        shape = RoundedCornerShape(16.dp),
        containerColor = if (selected) null else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvIcon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            TvText(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A QR code that the phone scans, with its address for a phone that cannot. */
@Composable
private fun PhoneCard(url: String, title: String, hint: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCodeImage(content = url, size = 200.dp, description = title)
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                Text(url.substringBefore("?"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CredentialsForm(enabled: Boolean, onSubmit: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val submitFocus = remember { FocusRequester() }
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(stringResource(R.string.tv_login_username)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.tv_login_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() }),
        modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
    )
    TvButton(
        onClick = { onSubmit(username.trim(), password) },
        enabled = enabled && username.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.focusRequester(submitFocus),
    ) { TvText(stringResource(R.string.tv_login)) }
}

/** Home Assistant on the TV: its username and password, then its two-factor code if it asks. */
@Composable
private fun HomeAssistantTvForm(
    enabled: Boolean,
    authorizationUrl: suspend () -> String?,
    onToken: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var login by remember { mutableStateOf<HomeAssistantTvLogin?>(null) }
    var step by remember { mutableStateOf<HomeAssistantTvLogin.Step>(HomeAssistantTvLogin.Step.Credentials()) }
    var busy by remember { mutableStateOf(false) }
    val submitFocus = remember { FocusRequester() }

    fun run(block: suspend () -> HomeAssistantTvLogin.Step) {
        busy = true
        scope.launch {
            val next = block()
            busy = false
            when (next) {
                is HomeAssistantTvLogin.Step.Done -> onToken(next.token)
                is HomeAssistantTvLogin.Step.Failed -> login = null
                else -> Unit
            }
            step = next
        }
    }

    fun signIn() = run {
        // A new sign-in each time: the server accepts the state of a sign-in once.
        val url = authorizationUrl() ?: return@run HomeAssistantTvLogin.Step.Failed(null)
        val started = HomeAssistantTvLogin(url).also { login = it }
        when (val first = started.start()) {
            is HomeAssistantTvLogin.Step.Credentials -> started.submitCredentials(username.trim(), password)
            else -> first
        }
    }

    val inputsEnabled = enabled && !busy
    val current = step
    if (current is HomeAssistantTvLogin.Step.Code) {
        Text(stringResource(R.string.tv_login_ha_code_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.tv_login_ha_code)) },
            singleLine = true,
            enabled = inputsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
        )
        TvButton(
            onClick = { login?.let { flow -> run { flow.submitCode(code) } } },
            enabled = inputsEnabled && code.isNotBlank(),
            modifier = Modifier.focusRequester(submitFocus),
        ) { TvText(stringResource(R.string.tv_login)) }
    } else {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.tv_login_username)) },
            singleLine = true,
            enabled = inputsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.tv_login_password)) },
            singleLine = true,
            enabled = inputsEnabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
        )
        TvButton(
            onClick = ::signIn,
            enabled = inputsEnabled && username.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.focusRequester(submitFocus),
        ) { TvText(stringResource(R.string.tv_login_ha_sign_in)) }
    }
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.tv_login_signing_in))
        }
    }
    val error = when (current) {
        is HomeAssistantTvLogin.Step.Credentials -> current.error?.let {
            stringResource(
                if (it == HomeAssistantTvLogin.Error.INVALID_CREDENTIALS) R.string.tv_login_ha_invalid else R.string.tv_login_ha_failed,
            )
        }
        is HomeAssistantTvLogin.Step.Code -> current.error?.let {
            stringResource(
                if (it == HomeAssistantTvLogin.Error.INVALID_CODE) R.string.tv_login_ha_invalid_code else R.string.tv_login_ha_failed,
            )
        }
        is HomeAssistantTvLogin.Step.Failed ->
            listOfNotNull(stringResource(R.string.tv_login_ha_failed), current.message).joinToString(" ")
        is HomeAssistantTvLogin.Step.Done -> null
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun TokenForm(enabled: Boolean, onSubmit: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    OutlinedTextField(
        value = token,
        onValueChange = { token = it },
        label = { Text(stringResource(R.string.tv_login_token)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().tvTextFieldFocus(),
    )
    TvButton(onClick = { onSubmit(token) }, enabled = enabled && token.isNotBlank()) {
        TvText(stringResource(R.string.tv_login))
    }
}

@Composable
private fun loginPageTexts() = LoginPageTexts(
    title = stringResource(R.string.tv_web_title),
    intro = stringResource(R.string.tv_web_intro),
    username = stringResource(R.string.tv_login_username),
    password = stringResource(R.string.tv_login_password),
    signIn = stringResource(R.string.tv_login),
    or = stringResource(R.string.tv_web_or),
    homeAssistant = stringResource(R.string.tv_web_ha),
    success = stringResource(R.string.tv_web_success),
    failure = stringResource(R.string.tv_web_failure),
    expired = stringResource(R.string.tv_web_expired),
)

private const val BUILTIN_PROVIDER = "builtin"
private const val HOME_ASSISTANT_PROVIDER = "homeassistant"

/**
 * Up and Down leave a text field, as they leave any other control: a text field keeps them for its
 * cursor, which traps the remote in it.
 */
@Composable
private fun Modifier.tvTextFieldFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            else -> false
        }
    }
}
