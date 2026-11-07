package io.music_assistant.client.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.accounts.TvAccount
import io.music_assistant.client.tv.accounts.TvAccountManager
import org.koin.compose.koinInject
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * The quick switch between the saved accounts, users of one server or of several. An account with
 * a PIN asks for it first. [onBack] is null when there is no screen to go back to.
 */
@Composable
fun TvAccountsScreen(onBack: (() -> Unit)?, onAddAccount: () -> Unit) {
    val manager: TvAccountManager = koinInject()
    val accounts by manager.accounts.collectAsStateWithLifecycle()
    val active by manager.active.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<TvAccount?>(null) }

    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    fun open(account: TvAccount) = when {
        account.id == active?.id && onBack != null -> onBack()
        account.hasPin && account.id != active?.id -> pinFor = account
        else -> manager.switchTo(account)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.tv_accounts_who), style = MaterialTheme.typography.displaySmall)
        AccountRow(accounts = accounts, activeId = active?.id, onOpen = ::open, onAdd = onAddAccount)
        onBack?.let { back -> TvOutlinedButton(onClick = back) { TvText(stringResource(R.string.tv_back)) } }
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

/** The accounts as tiles, the current one first, and the tile that adds an account. */
@Composable
fun AccountRow(
    accounts: List<TvAccount>,
    activeId: String?,
    onOpen: (TvAccount) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ordered = accounts.sortedByDescending { it.id == activeId }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(ordered, key = { it.id }) { account ->
            AccountTile(
                account = account,
                current = account.id == activeId,
                onClick = { onOpen(account) },
                modifier = if (account == ordered.first()) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        onAdd?.let { add ->
            item(key = "add") {
                Tile(
                    onClick = add,
                    title = stringResource(R.string.tv_accounts_add),
                    subtitle = null,
                    modifier = if (ordered.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    TvIcon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountTile(account: TvAccount, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val subtitle = when {
        account.token == null -> stringResource(R.string.tv_accounts_expired)
        current -> stringResource(R.string.tv_accounts_current, account.serverLabel)
        else -> account.serverLabel
    }
    Tile(onClick = onClick, title = account.label, subtitle = subtitle, modifier = modifier, locked = account.hasPin) {
        TvText(
            account.label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
    }
}

@Composable
private fun Tile(
    onClick: () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    avatar: @Composable () -> Unit,
) {
    TvFocusSurface(onClick = onClick, modifier = modifier.width(200.dp), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { avatar() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (locked) {
                    TvIcon(Icons.Default.Lock, contentDescription = stringResource(R.string.tv_pin_protected), modifier = Modifier.size(16.dp))
                }
                TvText(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                TvText(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
