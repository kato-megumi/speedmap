package com.example.speedometer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D8FF),
    secondary = Color(0xFFFFD180),
    background = Color(0xFF101418),
    surface = Color(0xFF181C20)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006688),
    secondary = Color(0xFF8B5A00)
)

@Composable
fun SpeedometerTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}
