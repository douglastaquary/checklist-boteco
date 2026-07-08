package com.checklistboteco.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.checklistboteco.presentation.designsystem.tokens.BecoColors
import com.checklistboteco.presentation.designsystem.tokens.BecoShapes
import com.checklistboteco.presentation.designsystem.tokens.BecoTypography

private val LightColorScheme = lightColorScheme(
    primary = BecoColors.Brand,
    onPrimary = Color.White,
    primaryContainer = BecoColors.Ink,
    onPrimaryContainer = Color.White,
    secondary = BecoColors.Muted,
    onSecondary = Color.White,
    background = BecoColors.Background,
    onBackground = BecoColors.Ink,
    surface = BecoColors.Surface,
    onSurface = BecoColors.Ink,
    surfaceVariant = BecoColors.Subtle,
    onSurfaceVariant = BecoColors.Muted,
    outline = BecoColors.Outline,
    error = BecoColors.Error,
    onError = Color.White
)

@Composable
fun ChecklistBotecoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = BecoTypography,
        shapes = BecoShapes,
        content = content
    )
}
