package dev.rdime.rainfern.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RainfernColorScheme = darkColorScheme(
    primary = Moss,
    secondary = RainBlue,
    tertiary = HorizonTeal,
    background = DeepPine,
    surface = StormWash,
    surfaceVariant = CardNight,
    onPrimary = DeepPine,
    onSecondary = DeepPine,
    onBackground = FernMist,
    onSurface = FernMist,
    onSurfaceVariant = CloudSilver,
)

@Composable
fun RainfernTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RainfernColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
