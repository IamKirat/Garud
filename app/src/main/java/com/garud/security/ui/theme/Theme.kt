package com.garud.security.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.garud.security.GarudApplication
import com.garud.security.data.ThemeMode

val GarudPureOffWhite = Color(0xFFFFFFFF)
val GarudElectricYellow = Color(0xFF286247)
val GarudCreamVanilla = Color(0xFFF7F6F2)
val GarudSandBeige = Color(0xFFE5E3DC)
val GarudDeepEspresso = Color(0xFF20211F)
val GarudCharcoalSlate = Color(0xFF70716C)
val GarudWarmCream = GarudCreamVanilla
val GarudNearBlack = GarudDeepEspresso
val GarudForestGreen = Color(0xFF286247)
val GarudMutedGray = GarudCharcoalSlate
val GarudAmber = Color(0xFF946315)
val GarudWarningRed = Color(0xFFB33A32)
val GarudSurfaceLight = GarudPureOffWhite
val GarudSurfaceVariantLight = GarudCreamVanilla
val GarudBorderLight = GarudSandBeige
val GarudDarkBg = Color(0xFF171916)
val GarudDarkSurface = Color(0xFF242824)
val GarudDarkSurfaceVariant = Color(0xFF353A35)
val GarudPurple = Color(0xFF8C6BE8)

private val LightColorScheme = lightColorScheme(
    primary = GarudForestGreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EFE8), onPrimaryContainer = GarudNearBlack,
    secondary = GarudCharcoalSlate, onSecondary = Color.White,
    tertiary = GarudWarningRed, onTertiary = Color.White,
    background = GarudCreamVanilla, onBackground = GarudNearBlack,
    surface = Color.White, onSurface = GarudNearBlack,
    surfaceVariant = Color(0xFFF0EFEA), onSurfaceVariant = GarudCharcoalSlate,
    outline = GarudSandBeige
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF83B698), onPrimary = Color(0xFF102319),
    primaryContainer = Color(0xFF294A37), onPrimaryContainer = Color(0xFFD4E9DA),
    secondary = Color(0xFFC4C8C0), onSecondary = Color(0xFF272B27),
    tertiary = Color(0xFFFFB4AA), onTertiary = Color(0xFF690005),
    background = GarudDarkBg, onBackground = Color(0xFFE4E4DE),
    surface = GarudDarkSurface, onSurface = Color(0xFFE4E4DE),
    surfaceVariant = GarudDarkSurfaceVariant, onSurfaceVariant = Color(0xFFC3C7BF),
    outline = Color(0xFF62675F)
)

@Composable
fun GarudTheme(content: @Composable () -> Unit) {
    val themeMode by GarudApplication.instance.preferences.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, content = content)
}
