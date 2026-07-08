package com.checklistboteco.platform

import androidx.compose.runtime.Composable

/**
 * Retorna um callback para abrir o seletor de documentos CSV.
 * Em sucesso, chama [onResult] com nome do arquivo e conteúdo UTF-8.
 */
@Composable
expect fun rememberCsvDocumentPicker(onResult: (fileName: String, content: String) -> Unit): () -> Unit
