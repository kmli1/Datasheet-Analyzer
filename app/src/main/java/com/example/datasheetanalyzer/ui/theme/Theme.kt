package com.example.datasheetanalyzer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ElectricOrange = Color(0xFFFF8A32)
private val ElectricColors = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = Color(0xFF211005),
    primaryContainer = Color(0xFF3B2314),
    onPrimaryContainer = Color(0xFFFFC496),
    secondary = Color(0xFFFFB36F),
    onSecondary = Color(0xFF241407),
    secondaryContainer = Color(0xFF30251D),
    onSecondaryContainer = Color(0xFFFFDEC3),
    tertiary = Color(0xFFE5C264),
    background = Color(0xFF0B0B0E),
    onBackground = Color(0xFFF3F0EB),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFF3F0EB),
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = Color(0xFFB9B6B0),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF252529),
    surfaceContainerHighest = Color(0xFF303035),
    outline = Color(0xFF68615A),
    outlineVariant = Color(0xFF37322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun DatasheetAnalyzerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElectricColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
