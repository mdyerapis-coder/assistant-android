package com.mdyerapis.sable.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette: terracotta primary, warm neutral surfaces, dark-first default.
// Source: ui-ux-pro-max colors.csv (AI/Chatbot Platform). All pairs meet 4.5:1.

private val BrandLight = lightColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFC9A227),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F4EF),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF5F4EF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF8A8A8A),
    surfaceContainer = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFE08A6B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFC9A227),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF171717),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFF8A8A8A),
    surfaceContainer = Color(0xFF212121),
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