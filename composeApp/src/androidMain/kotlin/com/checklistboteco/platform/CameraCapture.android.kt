package com.checklistboteco.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface CaptureMode {
    data object Camera : CaptureMode
    data object Gallery : CaptureMode
}

@Composable
actual fun CameraCaptureTrigger(
    trigger: Boolean,
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val currentOnImageCaptured = rememberUpdatedState(onImageCaptured)
    val currentOnCancel = rememberUpdatedState(onCancel)
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingMode by remember { mutableStateOf<CaptureMode?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        when {
            success && !path.isNullOrBlank() -> currentOnImageCaptured.value(path)
            success -> pendingMode = CaptureMode.Gallery
            else -> currentOnCancel.value()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        pendingMode = null
        if (uri == null) {
            currentOnCancel.value()
            return@rememberLauncherForActivityResult
        }
        val path = copyUriToCache(context, uri)
        if (path != null) {
            currentOnImageCaptured.value(path)
        } else {
            currentOnCancel.value()
        }
    }

    LaunchedEffect(pendingMode) {
        when (pendingMode) {
            CaptureMode.Gallery -> galleryLauncher.launch("image/*")
            else -> Unit
        }
    }

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect

        if (shouldUseGalleryFallback(context)) {
            pendingMode = CaptureMode.Gallery
            return@LaunchedEffect
        }

        try {
            val photoFile = createImageFile(context)
            pendingCameraPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            pendingCameraPath = null
            pendingMode = CaptureMode.Gallery
        }
    }
}

internal fun shouldUseGalleryFallback(context: Context): Boolean {
    return isRunningOnEmulator() || !isCameraCaptureAvailable(context)
}

internal fun isRunningOnEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.contains("emulator", ignoreCase = true)
        || Build.MODEL.contains("Emulator", ignoreCase = true)
        || Build.MODEL.contains("Android SDK built for", ignoreCase = true)
        || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
        || Build.HARDWARE.contains("goldfish", ignoreCase = true)
        || Build.HARDWARE.contains("ranchu", ignoreCase = true)
}

internal fun isCameraCaptureAvailable(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        return false
    }
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    return intent.resolveActivity(context.packageManager) != null
}

private fun copyUriToCache(context: Context, uri: Uri): String? {
    return try {
        val dest = createImageFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = File(context.cacheDir, "checklist_photos")
    storageDir.mkdirs()
    return File.createTempFile("CHECKLIST_${timeStamp}_", ".jpg", storageDir)
}
