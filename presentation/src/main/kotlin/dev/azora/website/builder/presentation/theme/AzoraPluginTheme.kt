package dev.azora.website.builder.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.azora.sdk.core.theme.LocalAzoraPalette

/**
 * Bridges the host's theme-aware palette to a Material 3 [ColorScheme].
 *
 * Studio picks a [dev.azora.sdk.core.theme.palette.Palette] from the user's "System / Light / Dark"
 * preference and publishes it via [LocalAzoraPalette]. This plugin's UI is built with Material 3
 * widgets, so we derive a [ColorScheme] from that palette — otherwise everything would render in
 * Material's default purple scheme (or in the hardcoded dark neutrals), ignoring the theme.
 *
 * "System" works for free: the host already resolves it to the light or dark palette before this
 * composable runs; we just adapt to whichever background luminance we receive.
 */
@Composable
fun azoraColorScheme(): ColorScheme {
    val p = LocalAzoraPalette.current
    val dark = p.background.luminance() < 0.5f
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.content,
        secondary = p.secondary,
        onSecondary = p.content,
        tertiary = p.secondary,
        onTertiary = p.content,
        background = p.background,
        onBackground = p.content,
        surface = p.surfaceMid,
        onSurface = p.content,
        surfaceVariant = p.surfaceLow,
        onSurfaceVariant = p.contentMid,
        surfaceContainer = p.surfaceMid,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainerHigh = p.surfaceTop,
        outline = p.contentLow,
        outlineVariant = p.surfaceLow,
        error = p.error,
        onError = p.content,
    )
}

/** Wraps plugin content in a [MaterialTheme] derived from the host's current Azora palette. */
@Composable
fun AzoraMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = azoraColorScheme(), content = content)
}

/** Resolves a possibly-unspecified node-menu item color, falling back to the themed on-surface color. */
@Composable
fun Color.orOnSurface(): Color = if (this == Color.Unspecified) MaterialTheme.colorScheme.onSurface else this
