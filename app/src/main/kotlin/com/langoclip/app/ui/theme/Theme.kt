package com.langoclip.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * App theme. Uses fixed BrandLightColors / BrandDarkColors instead of Material You dynamic
 * colors — brand identity wins over device personalisation here.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) BrandDarkColors else BrandLightColors,
        shapes = BrandShapes,
        typography = BrandTypography,
        content = content,
    )
}
