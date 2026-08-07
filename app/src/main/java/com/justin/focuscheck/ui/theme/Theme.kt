package com.justin.focuscheck.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = FocusBlueDark,
    onPrimary = OnFocusBlueDark,
    primaryContainer = FocusBlueContainerDark,
    onPrimaryContainer = OnFocusBlueContainerDark,

    secondary = FocusGreenDark,
    onSecondary = OnFocusGreenDark,
    secondaryContainer = FocusGreenContainerDark,
    onSecondaryContainer = OnFocusGreenContainerDark,

    tertiary = FocusAmberDark,
    onTertiary = OnFocusAmberDark,
    tertiaryContainer = FocusAmberContainerDark,
    onTertiaryContainer = OnFocusAmberContainerDark,

    error = FocusErrorDark,
    onError = OnFocusErrorDark,
    errorContainer = FocusErrorContainerDark,
    onErrorContainer = OnFocusErrorContainerDark,

    background = FocusBackgroundDark,
    onBackground = OnFocusBackgroundDark,
    surface = FocusSurfaceDark,
    onSurface = OnFocusSurfaceDark,
    surfaceVariant = FocusSurfaceVariantDark,
    onSurfaceVariant = OnFocusSurfaceVariantDark,
    outline = FocusOutlineDark,
    outlineVariant = FocusOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = FocusBlueLight,
    onPrimary = OnFocusBlueLight,
    primaryContainer = FocusBlueContainerLight,
    onPrimaryContainer = OnFocusBlueContainerLight,

    secondary = FocusGreenLight,
    onSecondary = OnFocusGreenLight,
    secondaryContainer = FocusGreenContainerLight,
    onSecondaryContainer = OnFocusGreenContainerLight,

    tertiary = FocusAmberLight,
    onTertiary = OnFocusAmberLight,
    tertiaryContainer = FocusAmberContainerLight,
    onTertiaryContainer = OnFocusAmberContainerLight,

    error = FocusErrorLight,
    onError = OnFocusErrorLight,
    errorContainer = FocusErrorContainerLight,
    onErrorContainer = OnFocusErrorContainerLight,

    background = FocusBackgroundLight,
    onBackground = OnFocusBackgroundLight,
    surface = FocusSurfaceLight,
    onSurface = OnFocusSurfaceLight,
    surfaceVariant = FocusSurfaceVariantLight,
    onSurfaceVariant = OnFocusSurfaceVariantLight,
    outline = FocusOutlineLight,
    outlineVariant = FocusOutlineVariantLight
)

private val FocusCheckShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FocusCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /*
     * Disabled by default so Focus Check has the same visual
     * identity on every supported Android device. Set this to
     * true later if device-derived dynamic colors are desired.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor &&
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S -> {

            val context = LocalContext.current

            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FocusCheckShapes,
        content = content
    )
}