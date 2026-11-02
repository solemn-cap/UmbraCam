package com.umbracam.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val NeonPurple = Color(0xFFB546FF)
val NeonBackground = Color(0xFF08060F)
val NeonPanelColor = Color(0xFF1C1829)
val NeonOutline = Color(0xFF544367)
private val SuiteFont = FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_semibold, FontWeight.SemiBold))
private val SuiteTypography = Typography().let { t -> Typography(
    displayLarge=t.displayLarge.copy(fontFamily=SuiteFont), displayMedium=t.displayMedium.copy(fontFamily=SuiteFont), displaySmall=t.displaySmall.copy(fontFamily=SuiteFont),
    headlineLarge=t.headlineLarge.copy(fontFamily=SuiteFont), headlineMedium=t.headlineMedium.copy(fontFamily=SuiteFont), headlineSmall=t.headlineSmall.copy(fontFamily=SuiteFont),
    titleLarge=t.titleLarge.copy(fontFamily=SuiteFont,fontWeight=FontWeight.SemiBold), titleMedium=t.titleMedium.copy(fontFamily=SuiteFont,fontWeight=FontWeight.SemiBold), titleSmall=t.titleSmall.copy(fontFamily=SuiteFont,fontWeight=FontWeight.SemiBold),
    bodyLarge=t.bodyLarge.copy(fontFamily=SuiteFont), bodyMedium=t.bodyMedium.copy(fontFamily=SuiteFont), bodySmall=t.bodySmall.copy(fontFamily=SuiteFont),
    labelLarge=t.labelLarge.copy(fontFamily=SuiteFont,fontWeight=FontWeight.SemiBold), labelMedium=t.labelMedium.copy(fontFamily=SuiteFont), labelSmall=t.labelSmall.copy(fontFamily=SuiteFont)) }

@Composable
fun UmbraCamTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary=Color(0xFFD4A4FF), onPrimary=Color(0xFF260A41), primaryContainer=Color(0xFF5B198A), onPrimaryContainer=Color.White,
        secondary=Color(0xFFCAB8E1), onSecondary=Color(0xFF24192F), secondaryContainer=Color(0xFF342442), onSecondaryContainer=Color.White,
        background=NeonBackground, onBackground=Color(0xFFF9F5FF), surface=NeonBackground, onSurface=Color(0xFFF9F5FF),
        surfaceVariant=NeonPanelColor, onSurfaceVariant=Color(0xFFD3C9DD), outline=NeonOutline, error=Color(0xFFFFA9B0)
    ), typography=SuiteTypography) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,content=content)
    }
}
