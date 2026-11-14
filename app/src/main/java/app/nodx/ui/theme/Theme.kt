package app.nodx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * Brand palette, sampled from the nodx logo tile:
 *   tile gradient  #17171F → #07080D  (violet-tinted near-black)
 *   glyph white    #F4F4F6
 *   accent         #883DEA (bright edge #B073F5)
 */

private val Accent = Color(0xFF883DEA)
private val AccentBright = Color(0xFFB073F5)
private val AccentDeep = Color(0xFF6C28C9)
private val TileTop = Color(0xFF17171F)
private val TileBottom = Color(0xFF0A0B10)
private val GlyphWhite = Color(0xFFF4F4F6)

private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFDBFF),
    onPrimaryContainer = Color(0xFF28005E),
    secondary = Color(0xFF625582),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DEFF),
    onSecondaryContainer = Color(0xFF1D1140),
    tertiary = Color(0xFF6B3C86),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E0FF),
    onTertiaryContainer = Color(0xFF26003A),
    surface = Color(0xFFFDF9FE),
    onSurface = Color(0xFF1C1B1E),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF4A454E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF1EBF4),
    surfaceContainerHigh = Color(0xFFECE6EF),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceDim = Color(0xFFDED8E0),
    surfaceBright = Color(0xFFFDF9FE),
    surfaceTint = Color(0xFFFDF9FE),
    outline = Color(0xFF7B7580),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = AccentBright,
    onPrimary = Color(0xFF2A0064),
    primaryContainer = Color(0xFF4C1DA6),
    onPrimaryContainer = Color(0xFFEFDBFF),
    secondary = Color(0xFFCFC2EA),
    onSecondary = Color(0xFF35275A),
    secondaryContainer = Color(0xFF4C3D71),
    onSecondaryContainer = Color(0xFFE9DEFF),
    tertiary = Color(0xFFE3C1F2),
    onTertiary = Color(0xFF3B1259),
    tertiaryContainer = Color(0xFF5B2178),
    onTertiaryContainer = Color(0xFFF6E1FF),
    surface = TileBottom,
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF2A2830),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFF07080D),
    surfaceContainerLow = Color(0xFF0E0F14),
    surfaceContainer = Color(0xFF12131A),
    surfaceContainerHigh = TileTop,
    surfaceContainerHighest = Color(0xFF1D1E26),
    surfaceDim = Color(0xFF07080D),
    surfaceBright = Color(0xFF3A3843),
    surfaceTint = TileBottom,
    outline = Color(0xFF95909B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** The logo's chiseled geometry: tighter radii than Material defaults. */
private val NodxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Material 3 theming. Default: brand (logo) scheme. Dynamic color —
 * wallpaper-derived Material You — is an explicit opt-in in Settings. */
@Composable
fun NodxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        shapes = NodxShapes,
        content = content,
    )
}
