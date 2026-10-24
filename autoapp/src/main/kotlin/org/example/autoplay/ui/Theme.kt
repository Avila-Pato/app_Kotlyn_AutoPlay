package org.example.autoplay.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colors outside the Material scheme, used for connection states. */
object AppColors {
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
}

private val colorScheme = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C2E5C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE6E9F0),
    surface = Color(0xFF0B0F17),
    onSurface = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF9AA4B8),
    surfaceContainerLowest = Color(0xFF080B12),
    surfaceContainerLow = Color(0xFF0F1420),
    surfaceContainer = Color(0xFF141B29),
    surfaceContainerHigh = Color(0xFF1B2333),
    surfaceContainerHighest = Color(0xFF232D40),
    outline = Color(0xFF3A465E),
    outlineVariant = Color(0xFF263044),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2B0006),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AutoPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
}
