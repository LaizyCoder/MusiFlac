package com.laizycoder.musiflac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Global MusiFlac theme.
 *
 * All application screens should use MaterialTheme colors instead of
 * defining their own background/card/text colors. This keeps Dark,
 * Light and System Default visually consistent everywhere.
 */

private val DarkColorScheme = darkColorScheme(
    primary = MusiFlacPrimary,
    onPrimary = Color(0xFF332A45),

    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF302D36),

    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492631),

    background = MusiFlacDarkBackground,
    onBackground = MusiFlacDarkOnBackground,

    surface = MusiFlacDarkSurface,
    onSurface = MusiFlacDarkOnSurface,

    surfaceVariant = MusiFlacDarkSurfaceVariant,
    onSurfaceVariant = MusiFlacDarkOnSurfaceVariant,

    outline = MusiFlacDarkOutline,

    surfaceContainer = MusiFlacDarkSurfaceContainer,
    surfaceContainerLow = Color(0xFF1A181B),
    surfaceContainerHigh = Color(0xFF262427),
    surfaceContainerHighest = Color(0xFF2D2B2E),

    inverseSurface = Color(0xFFE7E1E8),
    inverseOnSurface = Color(0xFF302D31),
    inversePrimary = MusiFlacPrimaryDark
)

private val AmoledColorScheme = darkColorScheme(
    primary = MusiFlacPrimary,
    onPrimary = Color(0xFF332A45),

    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF302D36),

    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492631),

    background = Color.Black,
    onBackground = Color.White,

    surface = Color.Black,
    onSurface = Color.White,

    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFB8B8B8),

    outline = Color(0xFF2A2A2A),

    surfaceContainer = Color(0xFF080808),
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF151515),

    inverseSurface = Color(0xFFE7E1E8),
    inverseOnSurface = Color(0xFF302D31),
    inversePrimary = MusiFlacPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = MusiFlacPrimaryDark,
    onPrimary = Color.White,

    secondary = Color(0xFF625B71),
    onSecondary = Color.White,

    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,

    background = MusiFlacLightBackground,
    onBackground = MusiFlacLightOnBackground,

    surface = MusiFlacLightSurface,
    onSurface = MusiFlacLightOnSurface,

    surfaceVariant = MusiFlacLightSurfaceVariant,
    onSurfaceVariant = MusiFlacLightOnSurfaceVariant,

    outline = MusiFlacLightOutline
)

@Composable
fun MusiFlacTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    /*
     * MusiFlac deliberately does not use Android dynamic colors.
     * This guarantees that Dark and System Default (when the system is dark)
     * use the exact same MusiFlac palette on every device.
     */
    val colorScheme = when {
        amoled -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
