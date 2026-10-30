package com.desikitchen.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desikitchen.app.R

// Sai palette "Tangerine" (Sai OK 2026-10-30; mocks/desikitchen-palette-tangerine-mock.jpg + sai-discover-target.png).
// Replaces the retired SoftSage palette (#5BAF8A / #EAF6EE). Logo / launcher mark stay Ink — never fill the mark with these.
/** Tangerine #FE7A25 — accent only: top wash on Discover, clipboard icon, small highlights. Not for text on white (2.6:1). */
val Tangerine = Color(0xFFFE7A25)
/** Soft Pistachio #E9E8CA — soft fills: collection cards, Plus badge, selected-tab wash, secondary pills. */
val SoftPistachio = Color(0xFFE9E8CA)
/** Lighter pistachio tint for quiet grounds / upcoming-step segments. */
val SoftPistachioPale = Color(0xFFF6F6EA)
/** Retro Green #006732 — primary buttons, selected chips, See all, Material primary. White on it ≈ 7.0:1 (AA/AAA). */
val RetroGreen = Color(0xFF006732)
/** Pressed / deep Retro Green (icons + text on Pistachio ≈ 8:1). */
val RetroGreenDeep = Color(0xFF004D25)
val Ink = Color(0xFF17171A)
/** Muted body/meta text — #6E6E73 (≥4.5:1 on white; = onSurfaceVariant). Was #8E8E93 (3.3:1). */
val Muted = Color(0xFF6E6E73)
/** Pista-neutral hairline (was sage-tinted #E8EEEA). */
val Hairline = Color(0xFFE9E8DE)
/** Pista-neutral chip / unselected fill (was #F3F5F4). */
val Chip = Color(0xFFF4F4EC)
/** Clean white canvas. */
val Canvas = Color(0xFFFFFFFF)

// RETIRED SoftSage names — aliased to the Tangerine palette so existing screens compile and pick up the swap.
// Solid roles → RetroGreen (pair with White/onPrimary, never Ink); fills → SoftPistachio. Prefer the new names in new code.
val SoftSage = RetroGreen
val SoftSageDeep = RetroGreenDeep
val SoftSageFill = SoftPistachio
val SoftSagePale = SoftPistachioPale
val Sage = RetroGreen
val SageSoft = SoftPistachio
val SageDeep = RetroGreenDeep

// Legacy names — aliased to the Tangerine palette.
val Mustard = RetroGreen
val MustardDeep = RetroGreenDeep
val MustardCream = SoftPistachio
val MustardPale = SoftPistachioPale

// Kept for older screens that still reference them.
val Saffron = Tangerine
val Turmeric = Tangerine
val DeepGreen = RetroGreenDeep
val Cream = Color.White
val Chilli = Color(0xFFD9534F)
val Cardamom = SoftPistachio
val Masala = Ink

private val LightColors = lightColorScheme(
    primary = RetroGreen,
    onPrimary = Color.White, // White on RetroGreen ≈ 7.0:1. (Old rule "Ink on SoftSage" no longer applies: Ink on RetroGreen ≈ 2.6:1.)
    primaryContainer = SoftPistachio,
    onPrimaryContainer = Ink, // Ink on Pistachio ≈ 14:1.
    secondary = RetroGreenDeep,
    onSecondary = Color.White,
    secondaryContainer = SoftPistachio,
    onSecondaryContainer = RetroGreenDeep,
    tertiary = Tangerine,
    onTertiary = Ink, // Ink on Tangerine ≈ 6.8:1; white would be 2.6:1.
    tertiaryContainer = Color(0xFFFFE8D9),
    onTertiaryContainer = Color(0xFF5A2600),
    error = Chilli,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAF4),
    surfaceContainer = Color(0xFFF6F6EE),
    surfaceContainerHigh = Chip,
    surfaceContainerHighest = Color(0xFFEDEDE2),
    outline = Color(0xFFB9B9AE),
    outlineVariant = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD89A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = RetroGreenDeep,
    onPrimaryContainer = SoftPistachio,
    secondary = SoftPistachio,
    onSecondary = Color(0xFF2A2A16),
    secondaryContainer = Color(0xFF3A3A28),
    onSecondaryContainer = SoftPistachio,
    tertiary = Tangerine,
    onTertiary = Ink,
    background = Color(0xFF121214),
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF1C1C1F),
    surfaceContainerLowest = Color(0xFF1C1C1F),
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFC4C4CA),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerHigh = Color(0xFF242428),
    surfaceContainerHighest = Color(0xFF2E2E33),
    outlineVariant = Color(0xFF34343A),
)

/** Nunito — headings/display/title (OFL: assets/Nunito-OFL.txt). Sai OK 2026-10-29 type mock. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/** Inter — body/label (OFL: assets/Inter-OFL.txt). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Legacy alias — points at Nunito so any leftover Jakarta refs stay soft. */
val Jakarta = Nunito

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DesiKitchenTheme(
    /** Product lock: DesiKitchen is light-only; system dark mode is ignored. */
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors, // force-light (product lock); DarkColors kept unused for reference
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
