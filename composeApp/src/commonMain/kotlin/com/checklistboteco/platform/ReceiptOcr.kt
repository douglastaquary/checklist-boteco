package com.checklistboteco.platform

import androidx.compose.runtime.Composable

enum class ReceiptMediaSource {
    CAMERA,
    GALLERY,
    FILE
}

@Composable
expect fun rememberReceiptOcrLauncher(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
): (ReceiptMediaSource) -> Unit
