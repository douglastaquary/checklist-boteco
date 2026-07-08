package com.checklistboteco.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberCsvDocumentPicker(onResult: (fileName: String, content: String) -> Unit): () -> Unit {
    return {}
}
