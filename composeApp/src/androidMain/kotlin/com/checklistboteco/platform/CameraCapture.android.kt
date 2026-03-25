package com.checklistboteco.platform

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider

@Composable
actual fun CameraCaptureTrigger(
    trigger: Boolean,
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val currentOnImageCaptured = rememberUpdatedState(onImageCaptured)
    val currentOnCancel = rememberUpdatedState(onCancel)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // O arquivo foi salvo - precisamos passar o path de outra forma
            // O TakePicture usa Uri como input, então precisamos guardar o path
            currentOnImageCaptured.value(lastCapturedPath ?: "")
        } else {
            currentOnCancel.value()
        }
    }

    LaunchedEffect(trigger) {
        if (trigger) {
            try {
                val photoFile = createImageFile(context)
                lastCapturedPath = photoFile.absolutePath
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                launcher.launch(uri)
            } catch (e: Exception) {
                currentOnCancel.value()
            }
        }
    }
}

private var lastCapturedPath: String? = null

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = File(context.cacheDir, "checklist_photos")
    storageDir.mkdirs()
    return File.createTempFile("CHECKLIST_${timeStamp}_", ".jpg", storageDir)
}
