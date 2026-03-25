package com.checklistboteco.platform

import androidx.compose.runtime.Composable

/**
 * Quando trigger é true, abre a câmera. Ao capturar, chama onImageCaptured com o path.
 * Ao cancelar, chama onCancel.
 */
@Composable
expect fun CameraCaptureTrigger(
    trigger: Boolean,
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
)
