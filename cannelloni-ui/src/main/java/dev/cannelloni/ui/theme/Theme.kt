package dev.cannelloni.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily

private val CannelloniColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    background = Black,
    surface = Black,
    onBackground = White,
    onSurface = White,
    surfaceVariant = DarkGray,
    onSurfaceVariant = GrayText
)

@Composable
fun CannelloniTheme(
    fontFamily: FontFamily = FontFamily.Default,
    iconFontFamily: FontFamily = fontFamily,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCannelloniFont provides fontFamily,
        LocalCannelloniIconFont provides iconFontFamily,
    ) {
        MaterialTheme(
            colorScheme = CannelloniColorScheme,
            typography = buildTypography(fontFamily),
            content = content
        )
    }
}
