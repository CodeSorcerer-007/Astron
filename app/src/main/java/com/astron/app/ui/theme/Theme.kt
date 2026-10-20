package com.astron.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CosmicCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    error = CrimsonError
)

private val DarkColorScheme = darkColorScheme(
    primary = CosmicCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF141F36),
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    background = Color(0xFF060913),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0D1424),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF141F36),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E2D4A),
    error = CrimsonError
)

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun AstronTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val astronColors = if (darkTheme) DarkAstronColors else LightAstronColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            try {
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            } catch (_: Throwable) {}
        }
    }

    CompositionLocalProvider(
        LocalAstronColors provides astronColors,
        LocalTextStyle provides TextStyle(
            fontFamily = HelveticaFamily,
            color = astronColors.textPrimary
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AstronTypography,
            content = content
        )
    }
}
