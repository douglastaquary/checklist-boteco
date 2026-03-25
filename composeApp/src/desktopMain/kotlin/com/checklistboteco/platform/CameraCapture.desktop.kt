package com.checklistboteco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

@Composable
actual fun CameraCaptureTrigger(
    trigger: Boolean,
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(trigger) {
        if (trigger) {
            val result = withContext(Dispatchers.Swing) {
                JFileChooser().apply {
                    dialogTitle = "Selecione uma imagem da atividade"
                    fileFilter = FileNameExtensionFilter("Imagens", "jpg", "jpeg", "png")
                }.let { chooser ->
                    chooser.showOpenDialog(null) to chooser.selectedFile?.absolutePath
                }
            }
            when {
                result.first == JFileChooser.APPROVE_OPTION && result.second != null ->
                    onImageCaptured(result.second!!)
                else -> onCancel()
            }
        }
    }
}
