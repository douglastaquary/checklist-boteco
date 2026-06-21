package com.checklistboteco.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.checklistboteco.platform.AppNetworkFeedback

@Composable
fun GlobalAppFeedback(modifier: Modifier = Modifier) {
    val isLoading by AppNetworkFeedback.isLoading.collectAsState()
    val errorMessage by AppNetworkFeedback.errorDialog.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = AppNetworkFeedback::dismissError,
            title = { Text("Não foi possível concluir") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = AppNetworkFeedback::dismissError) {
                    Text("Entendi")
                }
            }
        )
    }
}
