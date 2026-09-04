package com.geo.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val GeoBackground = Color(0xFFF8F7F4)
val GeoText = Color(0xFF151A21)
val GeoSecondary = Color(0xFF6E737B)
val GeoIncome = Color(0xFF26786C)
val GeoExpense = Color(0xFFBA403A)
val GeoDivider = Color(0xFFE7E5E1)
val GeoCard = Color(0xFFFFFDFB)
val GeoMutedFill = Color(0xFFEEEDEA)
val GeoRangeHighlight = Color(0xFFE6EAEF)

private val GeoColors = lightColorScheme(
    primary = Color(0xFF3F5964),
    onPrimary = Color.White,
    secondary = GeoIncome,
    tertiary = GeoExpense,
    background = GeoBackground,
    onBackground = GeoText,
    surface = GeoBackground,
    onSurface = GeoText,
    surfaceVariant = Color(0xFFEEEDEA),
    onSurfaceVariant = GeoSecondary,
    outline = Color(0xFF8B9096),
    outlineVariant = GeoDivider,
    error = GeoExpense,
)

private val GeoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun GeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeoColors,
        typography = Typography(),
        shapes = GeoShapes,
        content = content,
    )
}
