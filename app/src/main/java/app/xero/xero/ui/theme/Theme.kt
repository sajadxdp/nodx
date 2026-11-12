package app.xero.xero.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF0EA5A0)
private val TealDark = Color(0xFF0B7C78)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8ECE9),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6360),
    secondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFF5A5B7B),
    surface = Color(0xFFF6FBF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE4E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerHigh = Color(0xFFE7EDEB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5ADBD4),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFB8ECE9),
    secondary = Color(0xFFB0CCC8),
    secondaryContainer = Color(0xFF324B48),
    tertiary = Color(0xFFC2C3EE),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerHigh = Color(0xFF262B2A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Material 3 theming: dynamic color on Android 12+, Xero teal otherwise. */
@Composable
fun XeroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content,
    )
}
