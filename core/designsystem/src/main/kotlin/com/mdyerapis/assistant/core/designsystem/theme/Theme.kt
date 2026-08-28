package com.mdyerapis.assistant.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette: violet primary, warm slate neutrals, cyan secondary accent.
// Source: ui-ux-pro-max colors.csv (AI/Chatbot Platform). All pairs meet 4.5:1.

private val BrandLight = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFFDB2777),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF831843),
    background = Color(0xFFFCFCFE),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCFCFE),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE8E7F0),
    onSurfaceVariant = Color(0xFF49455A),
    surfaceContainer = Color(0xFFF1F0F7),
    surfaceContainerHigh = Color(0xFFEBE9F3),
    surfaceContainerHighest = Color(0xFFE5E3EE),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    inversePrimary = Color(0xFFD6BCFF),
    outline = Color(0xFF7A758B),
    outlineVariant = Color(0xFFCBC4DA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFD6BCFF),
    onPrimary = Color(0xFF42008F),
    primaryContainer = Color(0xFF5A1FB4),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004F5B),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFF9A8D4),
    onTertiary = Color(0xFF6D0F3C),
    tertiaryContainer = Color(0xFF8F2454),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1EA),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1EA),
    surfaceVariant = Color(0xFF484552),
    onSurfaceVariant = Color(0xFFCBC4D3),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    inverseSurface = Color(0xFFE6E1EA),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6D28D9),
    outline = Color(0xFF948F9D),
    outlineVariant = Color(0xFF484552),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
