package com.tongzhouweiji.baichuan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BaichuanBlue,
    onPrimary = Color.White,
    primaryContainer = BaichuanBlue.copy(alpha = 0.12f),
    onPrimaryContainer = BaichuanBlueDark,
    secondary = ChatGreen,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = BaichuanBlue,
    onPrimary = Color.White,
    primaryContainer = BaichuanBlue.copy(alpha = 0.20f),
    onPrimaryContainer = Color.White,
    secondary = ChatGreen,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DangerRed,
)

@Composable
fun BaichuanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? com.tongzhouweiji.baichuan.BaichuanApplication
    val base = if (darkTheme) DarkColors else LightColors
    val override = app?.container?.themePrimary
    MaterialTheme(
        colorScheme = if (override != null) base.copy(primary = override) else base,
        typography = BaichuanTypography,
        content = content,
    )
}
