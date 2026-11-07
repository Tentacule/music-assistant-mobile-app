package io.music_assistant.client.tv.accounts

import android.content.Context
import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.TvLyrics
import io.music_assistant.client.tv.TvWaveform
import io.music_assistant.client.tv.discovery.probeServerInfo
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The accounts of the TV: one user on one server each. The shared code keeps one token per server
 * and signs in with it when it connects, so a switch puts the token of the account in that slot,
 * disconnects (which clears the data of the previous user) and connects again. Every sign-in that
 * succeeds, whatever the method, saves or refreshes its account here.
 */
class TvAccountManager(
    private val context: Context,
    private val serviceClient: ServiceClient,
    private val authManager: AuthenticationManager,
    private val settings: SettingsRepository,
    private val lyrics: TvLyrics,
    private val waveform: TvWaveform,
) {
    private val log = Logger.withTag("TvAccounts")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = TvAccountStore(context)

    private val _accounts = MutableStateFlow(store.load())

    /** The saved accounts, the last used first. */
    val accounts: StateFlow<List<TvAccount>> = _accounts.asStateFlow()

    private val _activeId = MutableStateFlow(store.activeId)

    /** The account in use, also while it connects. */
    val active: StateFlow<TvAccount?> = combine(_accounts, _activeId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(scope, SharingStarted.Eagerly, _accounts.value.firstOrNull { it.id == _activeId.value })

    private val _switching = MutableStateFlow<TvAccount?>(null)

    /** The account that a switch connects to, until it is signed in or refused. */
    val switching: StateFlow<TvAccount?> = _switching.asStateFlow()

    private val _returnTo = MutableStateFlow<TvAccount?>(null)

    /** While the user adds an account: the account to go back to if they cancel. */
    val returnTo: StateFlow<TvAccount?> = _returnTo.asStateFlow()

    private val _locked = MutableStateFlow(
        _accounts.value.firstOrNull { it.id == store.activeId }
            ?.let { it.pinMode == PinMode.ON_SWITCH_AND_START && it.hasPin } == true,
    )

    /** True while the app waits for the PIN of the account it started on. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _generation = MutableStateFlow(0)

    /**
     * Changes whenever the user of the app changes. The screens keep their ViewModels for one
     * generation, so nothing that a ViewModel loaded for one user shows to the next.
     */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private var switchTimeout: Job? = null

    /** Whether the first sign-in state of this app run was checked (see [onSessionState]). */
    private var launchChecked = false

    init {
        migrateSharedTokens()
        scope.launch { serviceClient.sessionState.collect(::onSessionState) }
    }

    /**
     * Imports, once, the servers that the shared settings know with a token: the user of each is
     * unknown until its next sign-in.
     */
    private fun migrateSharedTokens() {
        if (store.migrated) return
        val imported = settings.connectionHistory.value
            .filter { it.type == ConnectionType.DIRECT }
            .mapNotNull { entry ->
                val serverId = entry.serverId ?: return@mapNotNull null
                val token = settings.getTokenForServer(serverId) ?: return@mapNotNull null
                val connection = entry.connectionInfo ?: return@mapNotNull null
                TvAccount(
                    serverId = serverId,
                    serverName = entry.serverName,
                    host = connection.host,
                    port = connection.port,
                    isTls = connection.isTls,
                    basePath = connection.basePath,
                    userId = null,
                    username = null,
                    displayName = null,
                    role = null,
                    token = token,
                )
            }
            .distinctBy { it.serverId }
        if (imported.isNotEmpty() && _accounts.value.isEmpty()) {
            log.i { "Imported ${imported.size} saved server(s)" }
            update(imported)
            if (_activeId.value == null) setActive(imported.first().id)
        }
        store.migrated = true
    }

    private fun onSessionState(state: SessionState) {
        val connected = state as? SessionState.Connected.Direct ?: return
        when (val data = connected.dataConnectionState) {
            is DataConnectionState.Authenticated -> {
                launchChecked = true
                val user = connected.user ?: return
                onSignedIn(connected.connectionInfo, data, user)
            }

            is DataConnectionState.AwaitingAuth -> {
                if (!launchChecked) {
                    launchChecked = true
                    // The app closed during the sign-in of another account: it starts on the server
                    // of that sign-in, without a token. Open the account in use instead.
                    val current = active.value
                    val serverId = data.serverInfo.serverId
                    if (data.authProcessState == AuthProcessState.NotStarted && _returnTo.value == null &&
                        settings.getTokenForServer(serverId) == null && current?.token != null
                    ) {
                        log.i { "Started on a sign-in to $serverId: back to ${current.id}" }
                        val wasLocked = _locked.value
                        switchTo(current)
                        _locked.value = wasLocked
                        return
                    }
                }
                val failed = data.authProcessState as? AuthProcessState.Failed ?: return
                // The server refused the saved token: the account stays, and asks for a sign-in.
                val target = _switching.value ?: active.value
                if (target != null && target.serverId == data.serverInfo.serverId && target.token != null) {
                    log.i { "Token of ${target.id} refused: ${failed.reason}" }
                    update(_accounts.value.map { if (it.id == target.id) it.copy(token = null) else it })
                }
                endSwitch()
            }

            DataConnectionState.AwaitingServerInfo -> Unit
        }
    }

    private fun onSignedIn(
        connection: ConnectionInfo,
        data: DataConnectionState.Authenticated,
        user: io.music_assistant.client.data.model.server.User,
    ) {
        val serverInfo = data.serverInfo
        val id = accountId(serverInfo.serverId, user.userId)
        val list = _accounts.value
        // The account itself, or the account that an earlier version saved for this token.
        val existing = list.firstOrNull { it.id == id }
            ?: list.firstOrNull { it.serverId == serverInfo.serverId && it.userId == null }
        val account = (existing ?: TvAccount(
            serverId = serverInfo.serverId,
            serverName = null,
            host = connection.host,
            port = connection.port,
            isTls = connection.isTls,
            basePath = connection.basePath,
            userId = null,
            username = null,
            displayName = null,
            role = null,
            token = null,
        )).copy(
            serverName = serverInfo.name ?: existing?.serverName,
            host = connection.host,
            port = connection.port,
            isTls = connection.isTls,
            basePath = connection.basePath,
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            role = user.role,
            token = data.token,
            lastUsedAt = System.currentTimeMillis(),
        )
        update(listOf(account) + list.filter { it !== existing && it.id != id })
        if (_activeId.value != id) {
            log.i { "Signed in as ${account.id}" }
            setActive(id)
        }
        _returnTo.value = null
        endSwitch()
    }

    /** Opens [account]. The caller asks for its PIN first. */
    fun switchTo(account: TvAccount) {
        val current = serviceClient.sessionState.value as? SessionState.Connected
        val signedInHere = current?.dataConnectionState is DataConnectionState.Authenticated
        _locked.value = false
        if (account.id == _activeId.value && signedInHere) {
            _returnTo.value = null
            return
        }
        log.i { "Switching to ${account.id}" }
        _returnTo.value = null
        _switching.value = account.takeIf { it.token != null }
        setActive(account.id)
        newGeneration()
        switchTimeout?.cancel()
        switchTimeout = scope.launch {
            delay(SWITCH_TIMEOUT_MS)
            if (_switching.value?.id == account.id) {
                log.w { "Switch to ${account.id} timed out" }
                _switching.value = null
            }
        }
        scope.launch { reconnect(account.connectionInfo, account.serverId, account.token) }
    }

    /**
     * Leaves the current account for the sign-in of another one. The current account stays saved,
     * and [cancelAdd] goes back to it.
     */
    fun startAddAccount() {
        _returnTo.value = active.value
        newGeneration()
        serviceClient.disconnectByUser()
    }

    fun cancelAdd() {
        val previous = _returnTo.value ?: return
        switchTo(previous)
    }

    /**
     * Connects to a server to sign in. While the user adds an account, the token of the server
     * is set aside first, so the server asks for a sign-in instead of opening the saved account.
     */
    fun connectForLogin(connection: ConnectionInfo, knownServerId: String? = null) {
        scope.launch {
            val serverId = knownServerId ?: probeServerInfo(connection)?.serverId
            reconnect(connection, serverId, token = null, clearToken = serverId != null)
        }
    }

    private suspend fun reconnect(
        connection: ConnectionInfo,
        serverId: String?,
        token: String?,
        clearToken: Boolean = true,
    ) {
        if (serviceClient.sessionState.value !is SessionState.Disconnected) {
            serviceClient.disconnectByUser()
            withTimeoutOrNull(DISCONNECT_WAIT_MS) {
                serviceClient.sessionState.first { it is SessionState.Disconnected }
            }
        }
        invalidateCaches()
        if (serverId != null && (token != null || clearToken)) settings.setTokenForServer(serverId, token)
        if (token != null) authManager.allowAutoLogin()
        settings.updateConnectionInfo(connection)
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                log.w { "No server info from ${connection.host} — connecting again" }
                serviceClient.disconnectByUser()
                withTimeoutOrNull(DISCONNECT_WAIT_MS) {
                    serviceClient.sessionState.first { it is SessionState.Disconnected }
                }
            }
            serviceClient.connect(connection)
            // A quick reconnection can lose the `server/hello` of the server, and the session then
            // waits for it forever: past a few seconds, connect again.
            val opened = withTimeoutOrNull(CONNECT_WAIT_MS) {
                serviceClient.sessionState.first { it is SessionState.Connected || it is SessionState.Disconnected }
            }
            if (opened !is SessionState.Connected) return
            val named = withTimeoutOrNull(SERVER_INFO_WAIT_MS) {
                serviceClient.sessionState.first { state ->
                    (state as? SessionState.Connected)?.dataConnectionState !is DataConnectionState.AwaitingServerInfo
                }
            }
            if (named != null) return
        }
    }

    /** Signs out of [account]: the server revokes the token of the account in use. */
    fun signOut(account: TvAccount) {
        val signedInHere = account.id == _activeId.value &&
            (serviceClient.sessionState.value as? SessionState.Connected)
                ?.dataConnectionState is DataConnectionState.Authenticated
        log.i { "Signing out of ${account.id}" }
        update(_accounts.value.filter { it.id != account.id })
        if (account.id == _activeId.value) setActive(null)
        if (_returnTo.value?.id == account.id) _returnTo.value = null
        if (signedInHere) {
            newGeneration()
            scope.launch { authManager.logout() }
        }
    }

    fun setPin(account: TvAccount, pin: String, mode: PinMode) =
        replace(account.withPin(pin, mode))

    fun setPinMode(account: TvAccount, mode: PinMode) = when {
        mode == PinMode.NONE -> replace(account.withoutPin())
        else -> replace(account.copy(pinMode = mode))
    }

    fun unlock() {
        _locked.value = false
    }

    private fun replace(account: TvAccount) =
        update(_accounts.value.map { if (it.id == account.id) account else it })

    private fun update(list: List<TvAccount>) {
        val sorted = list.sortedByDescending { it.lastUsedAt }
        _accounts.value = sorted
        store.save(sorted)
    }

    private fun setActive(id: String?) {
        _activeId.value = id
        store.activeId = id
    }

    private fun newGeneration() {
        _generation.value += 1
    }

    private fun endSwitch() {
        switchTimeout?.cancel()
        _switching.value = null
    }

    /** Forgets what the previous user loaded. The shared data clears on the disconnection itself. */
    private fun invalidateCaches() {
        lyrics.clear()
        waveform.clear()
        runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }
    }

    private companion object {
        const val SWITCH_TIMEOUT_MS = 45_000L
        const val DISCONNECT_WAIT_MS = 5_000L
        const val CONNECT_WAIT_MS = 20_000L
        const val SERVER_INFO_WAIT_MS = 5_000L
        const val CONNECT_ATTEMPTS = 3
    }
}
