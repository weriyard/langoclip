package com.langoclip.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralised design tokens — palette, shapes, spacing, typography.
 *
 * Replaces the previous dynamic Material You theming with a fixed brand identity (warm-neutral
 * background + terracotta accent). The app no longer adapts to the wallpaper because consistency
 * across devices matters more here than per-device personalisation.
 */

// ── Brand palette (warm neutrals + terracotta) ──────────────────────────────

object BrandColors {
    // Light theme
    val Bg = Color(0xFFFAF7F2)            // page background
    val Surface1 = Color(0xFFFFFFFF)      // cards
    val Surface2 = Color(0xFFF3EFE7)      // section containers
    val Surface3 = Color(0xFFECE6DA)      // nested surfaces
    val OnSurface = Color(0xFF1A1815)
    val OnSurfaceMute = Color(0xFF6B655A)
    val Outline = Color(0xFFD6CFC1)
    val OutlineStrong = Color(0xFFA89E89)

    val Primary = Color(0xFFB85C2A)       // warm terracotta
    val PrimarySoft = Color(0xFFFBE9DF)
    val OnPrimary = Color(0xFFFFFFFF)

    // Dark theme — same palette shifted dark
    val BgDark = Color(0xFF1B1815)
    val Surface1Dark = Color(0xFF24201C)
    val Surface2Dark = Color(0xFF2D2823)
    val Surface3Dark = Color(0xFF383128)
    val OnSurfaceDark = Color(0xFFF2EDE5)
    val OnSurfaceMuteDark = Color(0xFFA89E89)
    val OutlineDark = Color(0xFF55493B)
    val OutlineStrongDark = Color(0xFF6B655A)

    val PrimaryDark = Color(0xFFE08454)
    val PrimarySoftDark = Color(0xFF3D2317)
    val OnPrimaryDark = Color(0xFF1A1815)

    val Error = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFE57373)
}

val BrandLightColors: ColorScheme = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = BrandColors.OnPrimary,
    primaryContainer = BrandColors.PrimarySoft,
    onPrimaryContainer = BrandColors.Primary,
    background = BrandColors.Bg,
    onBackground = BrandColors.OnSurface,
    surface = BrandColors.Bg,
    onSurface = BrandColors.OnSurface,
    surfaceVariant = BrandColors.Surface2,
    onSurfaceVariant = BrandColors.OnSurfaceMute,
    surfaceContainerLowest = BrandColors.Surface1,
    surfaceContainerLow = BrandColors.Surface1,
    surfaceContainer = BrandColors.Surface2,
    surfaceContainerHigh = BrandColors.Surface3,
    outline = BrandColors.OutlineStrong,
    outlineVariant = BrandColors.Outline,
    error = BrandColors.Error,
)

val BrandDarkColors: ColorScheme = darkColorScheme(
    primary = BrandColors.PrimaryDark,
    onPrimary = BrandColors.OnPrimaryDark,
    primaryContainer = BrandColors.PrimarySoftDark,
    onPrimaryContainer = BrandColors.PrimaryDark,
    background = BrandColors.BgDark,
    onBackground = BrandColors.OnSurfaceDark,
    surface = BrandColors.BgDark,
    onSurface = BrandColors.OnSurfaceDark,
    surfaceVariant = BrandColors.Surface2Dark,
    onSurfaceVariant = BrandColors.OnSurfaceMuteDark,
    surfaceContainerLowest = BrandColors.Surface1Dark,
    surfaceContainerLow = BrandColors.Surface1Dark,
    surfaceContainer = BrandColors.Surface2Dark,
    surfaceContainerHigh = BrandColors.Surface3Dark,
    outline = BrandColors.OutlineStrongDark,
    outlineVariant = BrandColors.OutlineDark,
    error = BrandColors.ErrorDark,
)

// ── Shapes — consistent rounded corners across the app ─────────────────────

val BrandShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ── Spacing tokens — use these instead of ad-hoc dp values ─────────────────

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// ── Typography — tighter letter-spacing + slightly heavier weights for the
//                tonal hierarchy the design needs. System SansSerif for now;
//                next iteration could swap in Inter via Google Fonts. ────────

val BrandTypography: Typography = Typography().run {
    val tight = (-0.02).sp
    val tightSm = (-0.01).sp
    copy(
        displayLarge = displayLarge.copy(letterSpacing = tight, fontWeight = FontWeight.SemiBold),
        displayMedium = displayMedium.copy(letterSpacing = tight, fontWeight = FontWeight.SemiBold),
        displaySmall = displaySmall.copy(letterSpacing = tight, fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(letterSpacing = tight, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(letterSpacing = tight, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(letterSpacing = tightSm, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(letterSpacing = tightSm, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(letterSpacing = tightSm, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(letterSpacing = tightSm, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(letterSpacing = 0.05.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(letterSpacing = 0.06.sp, fontWeight = FontWeight.Medium),
    )
}
