package io.music_assistant.client.tv.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The TV theme: dark, with more contrast than the phone theme for a screen seen from the couch,
 * and a focus that stands out on every element.
 *
 * - The plain clickables of the reused phone pages get [TvFocusIndication]: a white outline and a
 *   tinted fill.
 * - The Material components (buttons, icon buttons, tabs, chips) draw their own state layer; it is
 *   raised from the faint phone value to a clearly visible white highlight. The primary color is a
 *   mid blue, so the highlight also shows on filled buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors) {
        CompositionLocalProvider(
            LocalIndication provides TvFocusIndication(border = FocusBorder, fill = TvColors.primary),
            LocalRippleConfiguration provides RippleConfiguration(color = Color.White, rippleAlpha = TvRippleAlpha),
            content = content,
        )
    }
}

private val FocusBorder = Color.White

private val TvRippleAlpha = RippleAlpha(
    draggedAlpha = 0.24f,
    focusedAlpha = 0.38f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.32f,
)

private val TvColors = darkColorScheme(
    primary = Color(0xFF1592C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B4A63),
    onPrimaryContainer = Color(0xFFD9F4FF),
    secondary = Color(0xFFB9C9DA),
    onSecondary = Color(0xFF0E1C2A),
    secondaryContainer = Color(0xFF2C3D50),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF2B1700),
    background = Color(0xFF0A0D11),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF0A0D11),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF1F262E),
    onSurfaceVariant = Color(0xFFCDD5DF),
    surfaceContainerLowest = Color(0xFF06080B),
    surfaceContainerLow = Color(0xFF11151A),
    surfaceContainer = Color(0xFF161B21),
    surfaceContainerHigh = Color(0xFF1E242B),
    surfaceContainerHighest = Color(0xFF272E36),
    outline = Color(0xFF8E98A3),
    outlineVariant = Color(0xFF3B444E),
)
