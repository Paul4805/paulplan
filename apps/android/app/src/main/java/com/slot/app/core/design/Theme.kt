package com.slot.app.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

data class TagPalette(
    val indigo: Color,
    val sky: Color,
    val violet: Color,
    val amber: Color,
    val rose: Color,
    val emerald: Color,
)

val LocalTagPalette = staticCompositionLocalOf {
    TagPalette(
        Tokens.Tag.Light.indigo,
        Tokens.Tag.Light.sky,
        Tokens.Tag.Light.violet,
        Tokens.Tag.Light.amber,
        Tokens.Tag.Light.rose,
        Tokens.Tag.Light.emerald,
    )
}

private val LightScheme = lightColorScheme(
    primary = Tokens.Light.accentSolid,
    onPrimary = Tokens.Light.accentFg,
    primaryContainer = Tokens.Light.accentSubtle,
    onPrimaryContainer = Tokens.Light.accentText,
    background = Tokens.Light.canvas,
    onBackground = Tokens.Light.textPrimary,
    surface = Tokens.Light.surface,
    onSurface = Tokens.Light.textPrimary,
    surfaceVariant = Tokens.Light.surfaceElevated,
    onSurfaceVariant = Tokens.Light.textSecondary,
    outline = Tokens.Light.border,
    outlineVariant = Tokens.Light.borderStrong,
    error = Tokens.Light.danger,
    onError = Tokens.Light.accentFg,
)

private val DarkScheme = darkColorScheme(
    primary = Tokens.Dark.accentSolid,
    onPrimary = Tokens.Dark.accentFg,
    primaryContainer = Tokens.Dark.accentSubtle,
    onPrimaryContainer = Tokens.Dark.accentText,
    background = Tokens.Dark.canvas,
    onBackground = Tokens.Dark.textPrimary,
    surface = Tokens.Dark.surface,
    onSurface = Tokens.Dark.textPrimary,
    surfaceVariant = Tokens.Dark.surfaceElevated,
    onSurfaceVariant = Tokens.Dark.textSecondary,
    outline = Tokens.Dark.border,
    outlineVariant = Tokens.Dark.borderStrong,
    error = Tokens.Dark.danger,
    onError = Tokens.Dark.accentFg,
)

private val SlotTypography = Typography(
    displaySmall = Tokens.Type.display.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Tokens.Type.xxl.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Tokens.Type.xl.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Tokens.Type.lg.copy(fontWeight = FontWeight.Medium),
    titleMedium = Tokens.Type.md.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Tokens.Type.md,
    bodyMedium = Tokens.Type.base,
    bodySmall = Tokens.Type.sm,
    labelLarge = Tokens.Type.base.copy(fontWeight = FontWeight.Medium),
    labelMedium = Tokens.Type.sm.copy(fontWeight = FontWeight.Medium),
    labelSmall = Tokens.Type.xs.copy(fontWeight = FontWeight.Medium),
)

private val SlotShapes = Shapes(
    small = RoundedCornerShape(Tokens.Radius.chip),
    medium = RoundedCornerShape(Tokens.Radius.card),
    large = RoundedCornerShape(Tokens.Radius.sheet),
)

@Composable
fun SlotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) {
        TagPalette(
            Tokens.Tag.Dark.indigo,
            Tokens.Tag.Dark.sky,
            Tokens.Tag.Dark.violet,
            Tokens.Tag.Dark.amber,
            Tokens.Tag.Dark.rose,
            Tokens.Tag.Dark.emerald,
        )
    } else {
        TagPalette(
            Tokens.Tag.Light.indigo,
            Tokens.Tag.Light.sky,
            Tokens.Tag.Light.violet,
            Tokens.Tag.Light.amber,
            Tokens.Tag.Light.rose,
            Tokens.Tag.Light.emerald,
        )
    }

    CompositionLocalProvider(LocalTagPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SlotTypography,
            shapes = SlotShapes,
            content = content,
        )
    }
}
