package io.music_assistant.client.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.accounts.PIN_LENGTH
import io.music_assistant.client.tv.accounts.TvAccount
import kotlinx.coroutines.delay
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * A PIN keypad for the remote: the arrows and OK on the digits, or the number keys of the remote.
 * [onPin] gets the PIN as soon as it has [PIN_LENGTH] digits.
 */
@Composable
fun TvPinPad(
    title: String,
    subtitle: String?,
    error: String?,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    var digits by remember { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    fun type(digit: Char) {
        if (!enabled || digits.length >= PIN_LENGTH) return
        digits += digit
        if (digits.length == PIN_LENGTH) {
            val pin = digits
            digits = ""
            onPin(pin)
        }
    }

    fun erase() {
        digits = digits.dropLast(1)
    }

    Column(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val digit = event.key.digit()
            when {
                digit != null -> type(digit).let { true }
                event.key == Key.Backspace || event.key == Key.Delete -> erase().let { true }
                else -> false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { index ->
                Box(
                    Modifier
                        .size(18.dp)
                        .background(
                            if (index < digits.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        val rows = listOf("123", "456", "789")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { colIndex, digit ->
                        PinKey(
                            onClick = { type(digit) },
                            modifier = if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(firstKey) else Modifier,
                        ) { TvText(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(KEY_SIZE))
                PinKey(onClick = { type('0') }) { TvText("0", style = MaterialTheme.typography.headlineSmall) }
                PinKey(onClick = ::erase) {
                    TvIcon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.tv_pin_erase))
                }
            }
        }
        actions()
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.size(KEY_SIZE),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private fun Key.digit(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

private val KEY_SIZE = 60.dp

/**
 * Asks for the PIN of [account]. After [MAX_ATTEMPTS] wrong PINs in a row, the keypad waits
 * [LOCKOUT_MS] before the next try.
 */
@Composable
fun TvPinCheck(
    account: TvAccount,
    title: String,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var lockedOut by remember { mutableStateOf(false) }
    val wrong = stringResource(R.string.tv_pin_wrong)
    val wait = stringResource(R.string.tv_pin_wait)
    LaunchedEffect(lockedOut) {
        if (lockedOut) {
            delay(LOCKOUT_MS)
            lockedOut = false
            failures = 0
            error = null
        }
    }
    TvPinPad(
        title = title,
        subtitle = account.label,
        error = error,
        enabled = !lockedOut,
        modifier = modifier,
        actions = actions,
        onPin = { pin ->
            if (account.checkPin(pin)) {
                onSuccess()
            } else {
                failures++
                lockedOut = failures >= MAX_ATTEMPTS
                error = if (lockedOut) wait else wrong
            }
        },
    )
}

/** Asks for a new PIN twice. */
@Composable
fun TvPinSetup(onPin: (String) -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.tv_pin_mismatch)
    TvPinPad(
        title = stringResource(if (first == null) R.string.tv_pin_new else R.string.tv_pin_confirm),
        subtitle = null,
        error = error,
        modifier = modifier,
        actions = actions,
        onPin = { pin ->
            when (val expected = first) {
                null -> {
                    first = pin
                    error = null
                }
                pin -> onPin(pin)
                else -> {
                    first = null
                    error = mismatch
                }
            }
        },
    )
}

/** A full-screen dialog over the TV app, for a PIN pad. */
@Composable
fun TvPinDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
                TvOutlinedButton(onClick = onDismiss) { TvText(stringResource(R.string.tv_cancel)) }
            }
        }
    }
}

private const val MAX_ATTEMPTS = 3
private const val LOCKOUT_MS = 30_000L
