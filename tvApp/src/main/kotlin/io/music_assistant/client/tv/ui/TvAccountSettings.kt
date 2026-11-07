package io.music_assistant.client.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.accounts.PinMode
import io.music_assistant.client.tv.accounts.TvAccount
import io.music_assistant.client.tv.accounts.TvAccountManager
import io.music_assistant.client.utils.SessionState
import org.koin.compose.koinInject
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/** What a PIN dialog of the account tab is for. */
private sealed interface PinStep {
    /** The current PIN, before [then]. */
    data class Verify(val then: PinStep?, val mode: PinMode) : PinStep

    /** A new PIN, saved with [mode]. */
    data class Create(val mode: PinMode) : PinStep
}

/** The first settings tab: the signed-in account, its PIN, and the other saved accounts. */
@Composable
fun TvAccountSettingsTab(openAccounts: () -> Unit, onAddAccount: () -> Unit) {
    val manager: TvAccountManager = koinInject()
    val serviceClient: ServiceClient = koinInject()
    val accounts by manager.accounts.collectAsStateWithLifecycle()
    val active by manager.active.collectAsStateWithLifecycle()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()
    val account = active ?: return
    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    SettingsCard(stringResource(R.string.tv_account_current)) {
        Text(account.label, style = MaterialTheme.typography.titleMedium)
        val server = (sessionState as? SessionState.Connected)?.serverInfo
        Text(
            listOfNotNull(
                account.serverLabel,
                "${account.host}:${account.port}",
                server?.serverVersion?.let { stringResource(R.string.tv_login_version, it) },
                account.role,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.tvHorizontalFocusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(onClick = openAccounts) { TvText(stringResource(R.string.tv_account_switch)) }
            TvOutlinedButton(onClick = onAddAccount) { TvText(stringResource(R.string.tv_accounts_add)) }
            TvOutlinedButton(onClick = { confirmSignOut = true }) { TvText(stringResource(R.string.tv_account_sign_out)) }
        }
        if (confirmSignOut) {
            Text(stringResource(R.string.tv_account_sign_out_confirm), color = MaterialTheme.colorScheme.error)
            Row(modifier = Modifier.tvHorizontalFocusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = { manager.signOut(account) }) { TvText(stringResource(R.string.tv_account_sign_out)) }
                TvOutlinedButton(onClick = { confirmSignOut = false }) { TvText(stringResource(R.string.tv_cancel)) }
            }
        }
    }

    SettingsCard(stringResource(R.string.tv_pin_title)) {
        Text(stringResource(R.string.tv_pin_hint), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.tvHorizontalFocusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinMode.entries.forEach { mode ->
                TvChoiceChip(
                    label = stringResource(mode.label()),
                    selected = account.effectivePinMode == mode,
                    onClick = {
                        if (mode == account.effectivePinMode) return@TvChoiceChip
                        pinStep = when {
                            !account.hasPin -> PinStep.Create(mode)
                            else -> PinStep.Verify(then = null, mode = mode)
                        }
                    },
                )
            }
        }
        if (account.hasPin) {
            TvOutlinedButton(onClick = { pinStep = PinStep.Verify(PinStep.Create(account.pinMode), account.pinMode) }) {
                TvText(stringResource(R.string.tv_pin_change))
            }
        }
    }

    val others = accounts.filter { it.id != account.id }
    if (others.isNotEmpty()) {
        SettingsCard(stringResource(R.string.tv_account_others)) {
            others.forEach { other -> OtherAccountRow(other, onRemove = { manager.signOut(other) }) }
        }
    }

    pinStep?.let { step ->
        TvPinDialog(onDismiss = { pinStep = null }) {
            when (step) {
                is PinStep.Verify -> TvPinCheck(
                    account = account,
                    title = stringResource(R.string.tv_pin_current),
                    onSuccess = {
                        when (val next = step.then) {
                            null -> {
                                manager.setPinMode(account, step.mode)
                                pinStep = null
                            }
                            else -> pinStep = next
                        }
                    },
                )

                is PinStep.Create -> TvPinSetup(
                    onPin = { pin ->
                        manager.setPin(account, pin, step.mode)
                        pinStep = null
                    },
                )
            }
        }
    }
}

@Composable
private fun OtherAccountRow(account: TvAccount, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().tvHorizontalFocusRow(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(account.label, style = MaterialTheme.typography.titleSmall)
            Text(
                if (account.token == null) stringResource(R.string.tv_accounts_expired) else account.serverLabel,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (confirm) {
            TvButton(onClick = onRemove) { TvText(stringResource(R.string.tv_account_remove_confirm)) }
            TvOutlinedButton(onClick = { confirm = false }) { TvText(stringResource(R.string.tv_cancel)) }
        } else {
            TvOutlinedButton(onClick = { confirm = true }) { TvText(stringResource(R.string.tv_account_remove)) }
        }
    }
}

private val TvAccount.effectivePinMode: PinMode get() = if (hasPin) pinMode else PinMode.NONE

private fun PinMode.label(): Int = when (this) {
    PinMode.NONE -> R.string.tv_pin_mode_none
    PinMode.ON_SWITCH -> R.string.tv_pin_mode_switch
    PinMode.ON_SWITCH_AND_START -> R.string.tv_pin_mode_start
}
