package com.checklistboteco.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberReceiptOcrLauncher(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
): (ReceiptMediaSource) -> Unit {
    return { onError("OCR de comprovantes disponível apenas no Android neste build.") }
}
