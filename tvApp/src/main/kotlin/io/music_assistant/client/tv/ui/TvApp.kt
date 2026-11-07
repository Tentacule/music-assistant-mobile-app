package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.accounts.TvAccountManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText
import io.music_assistant.client.ui.compose.common.ProvideDislikeSupport
import io.music_assistant.client.ui.compose.common.ProvideGrantedScopes
import io.music_assistant.client.ui.compose.common.ProvidePlayMediaSupport
import io.music_assistant.client.ui.compose.common.items.ProvideClickActionPrefs
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import org.koin.compose.koinInject

/** Root of the TV app: the shared theme and providers, then settings or the main screen. */
@Composable
fun TvApp() {
    AppLifecycleObserver()
    val settings: SettingsRepository = koinInject()
    // The TV is always its own player: the local (Sendspin) player is on from the first launch.
    LaunchedEffect(Unit) {
        if (!settings.sendspinEnabled.value) settings.setSendspinEnabled(true)
    }
    TvTheme {
        // A Surface, not a bare background: it also gives the text its on-background color.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                ProvideGrantedScopes {
                    ProvideDislikeSupport {
                        ProvidePlayMediaSupport {
                            ProvideClickActionPrefs {
                                TvRoot()
                            }
                        }
                    }
                }
                TvVolumeOverlay()
            }
        }
    }
}

private enum class TvScreen { LOADING, LOCKED, SWITCHING, LOGIN, ACCOUNTS, SETTINGS, MAIN }

@Composable
private fun TvRoot() {
    val serviceClient: ServiceClient = koinInject()
    val authManager: AuthenticationManager = koinInject()
    val accounts: TvAccountManager = koinInject()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()
    val locked by accounts.locked.collectAsStateWithLifecycle()
    val switching by accounts.switching.collectAsStateWithLifecycle()
    val generation by accounts.generation.collectAsStateWithLifecycle()
    val active by accounts.active.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var accountsOpen by rememberSaveable { mutableStateOf(false) }
    // Once signed in, a reconnection or a trip to the background keeps the main screen (and so
    // the browsing position and the Now playing mode) instead of falling back to the sign-in.
    var wasAuthenticated by remember { mutableStateOf(false) }
    var autoLoginSettled by remember { mutableStateOf(!authManager.willAutoLoginOnLaunch) }

    val authenticated = (sessionState as? SessionState.Connected)
        ?.dataConnectionState is DataConnectionState.Authenticated
    LaunchedEffect(authenticated) { if (authenticated) wasAuthenticated = true }
    LaunchedEffect(sessionState) {
        val terminal = when (val s = sessionState) {
            is SessionState.Connected ->
                s.dataConnectionState is DataConnectionState.Authenticated ||
                s.authProcessState is AuthProcessState.Failed
            is SessionState.Disconnected -> s !is SessionState.Disconnected.Backgrounded
            else -> false
        }
        if (terminal) autoLoginSettled = true
        // A sign-out or a disconnection by the user goes back to the sign-in.
        if (sessionState is SessionState.Disconnected && sessionState !is SessionState.Disconnected.Backgrounded) {
            wasAuthenticated = false
        }
    }
    // A new user opens on the main screen, not on the settings or the account picker of the last one.
    LaunchedEffect(generation) {
        settingsOpen = false
        accountsOpen = false
    }

    val keepsMain = wasAuthenticated && when (val s = sessionState) {
        is SessionState.Reconnecting, SessionState.Connecting, is SessionState.Disconnected.Backgrounded -> true
        is SessionState.Connected -> s.authProcessState !is AuthProcessState.Failed &&
            s.authProcessState != AuthProcessState.LoggedOut
        else -> false
    }
    val screen = when {
        locked && accountsOpen -> TvScreen.ACCOUNTS
        locked -> TvScreen.LOCKED
        switching != null -> TvScreen.SWITCHING
        !autoLoginSettled -> TvScreen.LOADING
        accountsOpen && authenticated -> TvScreen.ACCOUNTS
        settingsOpen && authenticated -> TvScreen.SETTINGS
        authenticated || keepsMain -> TvScreen.MAIN
        else -> TvScreen.LOGIN
    }

    // The screens keep their ViewModels for one user only: a switch drops everything they loaded.
    key(generation) {
        UserScopedViewModels {
            when (screen) {
                TvScreen.LOADING -> LoadingScreen(stringResource(R.string.tv_loading))
                TvScreen.SWITCHING -> LoadingScreen(
                    stringResource(R.string.tv_accounts_switching, switching?.label.orEmpty()),
                )
                TvScreen.LOCKED -> active?.let { account ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TvPinCheck(
                            account = account,
                            title = stringResource(R.string.tv_pin_unlock),
                            onSuccess = accounts::unlock,
                            actions = {
                                TvOutlinedButton(onClick = { accountsOpen = true }) {
                                    TvText(stringResource(R.string.tv_account_switch))
                                }
                            },
                        )
                    }
                } ?: LaunchedEffect(Unit) { accounts.unlock() }
                TvScreen.LOGIN -> TvLoginScreen()
                TvScreen.ACCOUNTS -> TvAccountsScreen(
                    onBack = { accountsOpen = false },
                    onAddAccount = {
                        accountsOpen = false
                        accounts.unlock()
                        accounts.startAddAccount()
                    },
                )
                TvScreen.SETTINGS -> TvSettingsScreen(
                    canGoBack = authenticated,
                    onBack = { settingsOpen = false },
                    openAccounts = { accountsOpen = true },
                    onAddAccount = accounts::startAddAccount,
                )
                TvScreen.MAIN -> TvMainScreen(
                    openSettings = { settingsOpen = true },
                    openAccounts = { accountsOpen = true },
                )
            }
        }
    }
}

/**
 * Gives the screens below a ViewModel store of their own, cleared when they leave the composition,
 * so a ViewModel never outlives the user it loaded data for.
 */
@Composable
private fun UserScopedViewModels(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}

@Composable
private fun LoadingScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Tells the connection when the app is shown or hidden, as the phone app does. */
@Composable
private fun AppLifecycleObserver() {
    val serviceClient: ServiceClient = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> serviceClient.onAppForeground()
                Lifecycle.Event.ON_STOP -> serviceClient.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
