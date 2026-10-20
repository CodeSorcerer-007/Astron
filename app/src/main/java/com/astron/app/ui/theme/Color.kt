package com.astron.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Complete Dynamic Color Token System for Astron.
 * Seamlessly transitions between Swiss Precision Light and Cosmic Obsidian Dark mode.
 */
data class AstronColors(
    val canvas: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceHover: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean
)

val LightAstronColors = AstronColors(
    canvas = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF1F5F9),
    surfaceHover = Color(0xFFE2E8F0),
    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFF1F5F9),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    isDark = false
)

val DarkAstronColors = AstronColors(
    canvas = Color(0xFF060913),        // Deep Cosmic Obsidian Void
    surface = Color(0xFF0D1424),       // Glassmorphic dark card surface
    surfaceSubtle = Color(0xFF141F36), // Elevated sub-surface / pill
    surfaceHover = Color(0xFF1E2D4A),  // Hover / Pressed state
    border = Color(0xFF1E2D4A),        // High-tech subtle glow border
    borderSubtle = Color(0xFF141F36),
    textPrimary = Color(0xFFF8FAFC),   // Brilliant crisp white
    textSecondary = Color(0xFF94A3B8), // Refined cool slate
    textTertiary = Color(0xFF64748B),  // Muted cosmic dust
    isDark = true
)

val LocalAstronColors = staticCompositionLocalOf { LightAstronColors }

// Semantic Composables Mapping - Automatically adjusts throughout the app
val CanvasLight: Color
    @Composable
    get() = LocalAstronColors.current.canvas

val SurfaceLight: Color
    @Composable
    get() = LocalAstronColors.current.surface

val SurfaceSubtle: Color
    @Composable
    get() = LocalAstronColors.current.surfaceSubtle

val SurfaceHover: Color
    @Composable
    get() = LocalAstronColors.current.surfaceHover

val BorderLight: Color
    @Composable
    get() = LocalAstronColors.current.border

val BorderSubtle: Color
    @Composable
    get() = LocalAstronColors.current.borderSubtle

val SpaceBlack: Color
    @Composable
    get() = LocalAstronColors.current.canvas

val SpaceDark: Color
    @Composable
    get() = LocalAstronColors.current.surface

val SpaceSurface: Color
    @Composable
    get() = LocalAstronColors.current.surfaceSubtle

val SpaceSurfaceHover: Color
    @Composable
    get() = LocalAstronColors.current.surfaceHover

val SpaceBorder: Color
    @Composable
    get() = LocalAstronColors.current.border

// Brand Accents
val CosmicCyan = Color(0xFF2563EB)       // Precision Royal Cobalt Blue
val CosmicCyanDark = Color(0xFF1D4ED8)   // Deep Cobalt
val ElectricBlue = Color(0xFF3B82F6)     // Vibrant Blue
val StellarViolet = Color(0xFF7C3AED)    // Deep Indigo / Violet
val EmeraldVerified = Color(0xFF059669)  // Verified Emerald Green
val RadiantAmber = Color(0xFFD97706)     // Alert Amber
val SolarGold = RadiantAmber             // Solar Gold / Alert Amber alias
val CrimsonError = Color(0xFFDC2626)     // Danger Red

// High-Contrast Typography Colors
val TextPrimary: Color
    @Composable
    get() = LocalAstronColors.current.textPrimary

val TextSecondary: Color
    @Composable
    get() = LocalAstronColors.current.textSecondary

val TextTertiary: Color
    @Composable
    get() = LocalAstronColors.current.textTertiary
