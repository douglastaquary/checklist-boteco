package com.checklistboteco.platform

import androidx.compose.runtime.Composable

// iOS: Por enquanto usa placeholder - implementação nativa requer UIKit/AVFoundation
@Composable
actual fun CameraCaptureTrigger(
    trigger: Boolean,
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    // TODO: Implementar com UIImagePickerController
    // Por ora, cancela automaticamente em iOS
    if (trigger) {
        onCancel()
    }
}
