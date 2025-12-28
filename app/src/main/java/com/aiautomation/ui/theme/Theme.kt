package com.aiautomation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPurple80,
    onPrimary = Grey900,
    primaryContainer = DarkPurple40,
    onPrimaryContainer = Grey50,
    secondary = DarkPink80,
    onSecondary = Grey900,
    secondaryContainer = DarkPink40,
    onSecondaryContainer = Grey50,
    tertiary = DarkPurpleGrey80,
    onTertiary = Grey900,
    error = ErrorRed,
    onError = Grey50,
    background = Grey900,
    onBackground = Grey50,
    surface = Grey800,
    onSurface = Grey50,
    surfaceVariant = Grey700,
    onSurfaceVariant = Grey300,
    outline = Grey600
)

private val LightColorScheme = lightColorScheme(
    primary = Purple80,
    onPrimary = Color.White,
    primaryContainer = Purple40,
    onPrimaryContainer = Color.White,
    secondary = Pink80,
    onSecondary = Color.White,
    secondaryContainer = Pink40,
    onSecondaryContainer = Color.White,
    tertiary = PurpleGrey80,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    background = Grey50,
    onBackground = Grey900,
    surface = Color.White,
    onSurface = Grey900,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,
    outline = Grey300
)

@Composable
fun AIAutomationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
