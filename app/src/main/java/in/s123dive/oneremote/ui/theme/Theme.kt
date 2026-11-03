package `in`.s123dive.oneremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7CD4FD),
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D62),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFB3CAD5),
    tertiary = Color(0xFF3DDC84),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E3E6),
    surfaceVariant = Color(0xFF1C2228),
    onSurfaceVariant = Color(0xFFBFC8CF),
    error = Color(0xFFFFB4AB),
)

/** Dark by default: a remote is used in a dim room. A light option is a later setting. */
@Composable
fun OneRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
