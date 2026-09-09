package com.geo.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

object GeoSpacing {
    val Tiny=4.dp
    val Small=8.dp
    val Medium=12.dp
    val Section=16.dp
    val Page=20.dp
    val Large=24.dp
    val Huge=32.dp
    val IconSmall=20.dp
    val Icon=24.dp
}

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
    primaryContainer = GeoRangeHighlight,
    onPrimaryContainer = GeoText,
    secondaryContainer = GeoRangeHighlight,
    onSecondaryContainer = GeoText,
    tertiaryContainer = Color(0xFFF0E5E1),
    onTertiaryContainer = GeoText,
    tertiary = GeoExpense,
    background = GeoBackground,
    onBackground = GeoText,
    surface = GeoBackground,
    onSurface = GeoText,
    surfaceVariant = Color(0xFFEEEDEA),
    surfaceContainerLowest = GeoCard,
    surfaceContainerLow = Color(0xFFF1F0ED),
    surfaceContainer = GeoMutedFill,
    surfaceContainerHigh = Color(0xFFE8E7E3),
    surfaceContainerHighest = Color(0xFFE2E2DF),
    onSurfaceVariant = GeoSecondary,
    outline = Color(0xFF8B9096),
    outlineVariant = GeoDivider,
    error = GeoExpense,
)

private val GeoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

private val GeoTypography=Typography(
    displayLarge=TextStyle(fontSize=48.sp,lineHeight=56.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp),
    displaySmall=TextStyle(fontSize=34.sp,lineHeight=42.sp,fontWeight=FontWeight.Medium),
    headlineSmall=TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=22.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=19.sp),
)

@Composable
fun GeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeoColors,
        typography = GeoTypography,
        shapes = GeoShapes,
        content = content,
    )
}
