package de.orgelbau.zeiterfassung

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Farben für Zustände: Läuft grün, Pause orange, Stopp rot (wie im Prototyp). */
data class ZustandsFarben(val laeuft: Color, val pause: Color, val stopp: Color)

private val HellZustand = ZustandsFarben(Color(0xFF1D7448), Color(0xFFA06508), Color(0xFFAD3427))
private val DunkelZustand = ZustandsFarben(Color(0xFF23885A), Color(0xFFB07510), Color(0xFFBC4538))

val LocalZustand = staticCompositionLocalOf { HellZustand }

// Kühles Grau-Grün mit Messing als Akzent
private val Hell = lightColorScheme(
    primary = Color(0xFF8C6210),
    onPrimary = Color.White,
    background = Color(0xFFEDF1EF),
    onBackground = Color(0xFF16211E),
    surface = Color.White,
    onSurface = Color(0xFF16211E),
    surfaceVariant = Color(0xFFE2E8E5),
    onSurfaceVariant = Color(0xFF56665F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8F7),
    surfaceContainer = Color(0xFFF0F3F1),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE2E8E5),
    outline = Color(0xFFC9D3CE),
    outlineVariant = Color(0xFFC9D3CE),
)

private val Dunkel = darkColorScheme(
    primary = Color(0xFFD8A748),
    onPrimary = Color(0xFF16211E),
    background = Color(0xFF0F1614),
    onBackground = Color(0xFFE5EDE9),
    surface = Color(0xFF18211E),
    onSurface = Color(0xFFE5EDE9),
    surfaceVariant = Color(0xFF202B28),
    onSurfaceVariant = Color(0xFF93A39D),
    surfaceContainerLowest = Color(0xFF0F1614),
    surfaceContainerLow = Color(0xFF151D1A),
    surfaceContainer = Color(0xFF18211E),
    surfaceContainerHigh = Color(0xFF1D2724),
    surfaceContainerHighest = Color(0xFF202B28),
    outline = Color(0xFF2D3A36),
    outlineVariant = Color(0xFF2D3A36),
)

@Composable
fun ZeitTheme(content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    CompositionLocalProvider(LocalZustand provides if (dunkel) DunkelZustand else HellZustand) {
        MaterialTheme(colorScheme = if (dunkel) Dunkel else Hell, content = content)
    }
}
