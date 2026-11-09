package corsica.universita.objetscampus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette officielle de la PWA "Objets Trouves du Campus" (TP/style.css)
val CampusBlue = Color(0xFF2563EB)
val CampusBlueDark = Color(0xFF1D4ED8)
val CampusBlueLight = Color(0xFFDBEAFE)
val CampusEmerald = Color(0xFF10B981)
val CampusEmeraldLight = Color(0xFFD1FAE5)
val CampusAmber = Color(0xFFF59E0B)
val CampusAmberLight = Color(0xFFFEF3C7)
val CampusSlateBg = Color(0xFFF8FAFC)
val CampusSlateDarkBg = Color(0xFF0F172A)

private val LightColorScheme = lightColorScheme(
    primary = CampusBlue,
    onPrimary = Color.White,
    primaryContainer = CampusBlueLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = CampusEmerald,
    onSecondary = Color.White,
    secondaryContainer = CampusEmeraldLight,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = CampusAmber,
    onTertiary = Color.White,
    tertiaryContainer = CampusAmberLight,
    onTertiaryContainer = Color(0xFF92400E),
    background = CampusSlateBg,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = CampusSlateDarkBg,
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

@Composable
fun ObjetsCampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
