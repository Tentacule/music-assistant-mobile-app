package io.music_assistant.client.tv.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.tv.R
import io.music_assistant.client.tv.TvPreferences
import io.music_assistant.client.tv.services.TvVolumeAccessibilityService
import org.koin.compose.koinInject
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Text as TvText

/**
 * Whether the volume keys may control a remote player. Off by default: the keys then change the
 * volume of this device. On a TV the keys reach an app only through an accessibility service, which
 * only the user can turn on, so the card explains it and offers to open the system page.
 */
@Composable
fun VolumeKeysCard() {
    val prefs: TvPreferences = koinInject()
    val context = LocalContext.current
    val enabled by prefs.remoteVolumeKeys.collectAsStateWithLifecycle()
    var serviceOn by remember { mutableStateOf(context.isVolumeServiceOn()) }
    var opened by remember { mutableStateOf<OpenedSettings?>(null) }

    // The user comes back from the system page with the service on or off.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceOn = context.isVolumeServiceOn() }

    SettingsCard(stringResource(R.string.tv_remote_volume)) {
        Text(stringResource(R.string.tv_remote_volume_hint), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.tv_remote_volume_switch))
            Switch(checked = enabled, onCheckedChange = { prefs.setRemoteVolumeKeys(it) })
        }
        val status = when {
            enabled && serviceOn -> R.string.tv_remote_volume_ready
            enabled -> R.string.tv_remote_volume_setup
            serviceOn -> R.string.tv_remote_volume_service_unused
            else -> null
        }
        status?.let { Text(stringResource(it, stringResource(R.string.app_name))) }
        // Also with the service on: it is the only way to turn it off again.
        if (enabled || serviceOn) {
            val goOnHint = stringResource(R.string.tv_remote_volume_open_general)
            TvOutlinedButton(
                onClick = {
                    opened = context.openAccessibilitySettings()
                    // The card is hidden behind the settings, where the user needs this.
                    if (opened == OpenedSettings.GENERAL) {
                        Toast.makeText(context, goOnHint, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                TvText(stringResource(R.string.tv_remote_volume_open))
            }
        }
        when (opened) {
            OpenedSettings.GENERAL -> Text(stringResource(R.string.tv_remote_volume_open_general))
            OpenedSettings.NONE -> Text(
                stringResource(R.string.tv_remote_volume_open_failed),
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
    }
}

private val Context.volumeService: ComponentName
    get() = ComponentName(this, TvVolumeAccessibilityService::class.java)

/** Read from the setting: the service declares no feedback type, so the manager's lists leave it out. */
private fun Context.isVolumeServiceOn(): Boolean =
    Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        .orEmpty()
        .split(':')
        .any { ComponentName.unflattenFromString(it) == volumeService }

private enum class OpenedSettings { ACCESSIBILITY, GENERAL, NONE }

/**
 * The page of the service where the system has one, else the list. Some devices (NVIDIA Shield)
 * have neither: only the general settings can be opened, and the user goes on from there.
 */
private fun Context.openAccessibilitySettings(): OpenedSettings {
    val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val pages: List<Intent> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        details.putExtra(Intent.EXTRA_COMPONENT_NAME, volumeService.flattenToString())
        listOf(details, list)
    } else {
        listOf(list)
    }
    return when {
        pages.any(::openIfReal) -> OpenedSettings.ACCESSIBILITY
        openIfReal(Intent(Settings.ACTION_SETTINGS)) -> OpenedSettings.GENERAL
        else -> OpenedSettings.NONE
    }
}

/**
 * Google's stub package answers settings actions that a TV does not have: it opens without error
 * and only shows "no app can perform this action", so it has to be ruled out before starting.
 */
private fun Context.openIfReal(page: Intent): Boolean {
    val handler = packageManager.resolveActivity(page, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
    if (handler == null || handler == SETTINGS_STUB_PACKAGE) return false
    return runCatching { startActivity(page) }.isSuccess
}

private const val SETTINGS_STUB_PACKAGE = "com.google.android.tv.frameworkpackagestubs"
