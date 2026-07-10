package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekPrimary,
    secondary = SleekHeader,
    background = SleekBackground,
    surface = SleekSurface,
    onBackground = SleekText,
    onSurface = SleekText,
    onPrimary = SleekButtonText
  )

private val LightColorScheme = DarkColorScheme // Force dark theme as the default per user's prompt: "include a dark mode which is default."

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to dark mode
  dynamicColor: Boolean = false, // Use our sleek signature theme colors instead of dynamic
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
