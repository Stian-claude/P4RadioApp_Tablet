package no.p4radio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary           = Color(0xFF00E5FF),
    onPrimary         = Color(0xFF003640),
    primaryContainer  = Color(0xFF004E5F),
    onPrimaryContainer= Color(0xFF9EEFFD),
    secondary         = Color(0xFF6C5DD3),
    background        = Color(0xFF0D0D0D),
    surface           = Color(0xFF1A1A1A),
    surfaceVariant    = Color(0xFF1E1E2E),
    onBackground      = Color(0xFFE0E0E0),
    onSurface         = Color(0xFFE0E0E0),
    onSurfaceVariant  = Color(0xFFBDBDBD),
)

@Composable
fun P4RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
