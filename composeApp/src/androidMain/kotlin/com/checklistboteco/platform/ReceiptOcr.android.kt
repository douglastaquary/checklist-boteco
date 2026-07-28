package com.checklistboteco.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberReceiptOcrLauncher(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
): (ReceiptMediaSource) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoFile = remember {
        File(context.cacheDir, "receipt_capture_${System.currentTimeMillis()}.jpg")
    }
    val photoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    fun processUri(uri: Uri) {
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { recognizeUri(context, uri) }
                require(text.isNotBlank()) { "Nenhum texto encontrado na imagem." }
                onTextRecognized(text)
            }.onFailure { error ->
                onError(error.message ?: "Falha no OCR")
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) processUri(photoUri) else onError("Captura cancelada")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processUri(uri) else onError("Seleção cancelada")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processUri(uri) else onError("Seleção cancelada")
    }

    return remember(takePicture, pickImage, pickFile) {
        { source ->
            when (source) {
                ReceiptMediaSource.CAMERA -> takePicture.launch(photoUri)
                ReceiptMediaSource.GALLERY -> pickImage.launch("image/*")
                ReceiptMediaSource.FILE -> pickFile.launch("*/*")
            }
        }
    }
}

private suspend fun recognizeUri(context: android.content.Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri).orEmpty()
    val bitmap = if (mime.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) {
        renderPdfFirstPage(context, uri)
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Não foi possível abrir a imagem")
    }
    return recognizeBitmap(bitmap)
}

private fun renderPdfFirstPage(context: android.content.Context, uri: Uri): Bitmap {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Não foi possível abrir o PDF")
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            require(renderer.pageCount > 0) { "PDF vazio" }
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private suspend fun recognizeBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { result ->
            cont.resume(result.text.orEmpty())
        }
        .addOnFailureListener { error ->
            cont.resumeWithException(error)
        }
}
