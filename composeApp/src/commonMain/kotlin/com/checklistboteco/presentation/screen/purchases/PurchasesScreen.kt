package com.checklistboteco.presentation.screen.purchases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.checklistboteco.platform.ReceiptMediaSource
import com.checklistboteco.platform.rememberCsvDocumentPicker
import com.checklistboteco.platform.rememberReceiptOcrLauncher
import com.checklistboteco.presentation.viewmodel.PurchasesViewModel
import com.checklistboteco.receipt.CategoryGroup
import com.checklistboteco.receipt.ReceiptLineItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: PurchasesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val launchOcr = rememberReceiptOcrLauncher(
        onTextRecognized = viewModel::ingestOcrText,
        onError = { /* surfaced via state in later calls */ }
    )
    val launchCsv = rememberCsvDocumentPicker { fileName, content ->
        viewModel.uploadCsv(fileName, content)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PurchasesHeader(
                onScanClick = viewModel::openMediaSource,
                menuExpanded = menuExpanded,
                onMenuToggle = { menuExpanded = it },
                onSendCsv = {
                    menuExpanded = false
                    launchCsv()
                }
            )

            if (state.isUploading) {
                LinearProgressIndicator(
                    progress = { state.uploadProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            state.successMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            state.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.isProcessing -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.groups.isEmpty() -> {
                        PurchasesEmptyState(modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    "Total da sessão: ${state.totalLabel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(state.groups, key = { it.category }) { group ->
                                CategorySection(group)
                            }
                            item { Spacer(modifier = Modifier.height(88.dp)) }
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::saveSession,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if (state.isUploading) "Enviando..." else "Salvar dados")
            }
        }
    }

    if (state.showMediaSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissMediaSource,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Adicionar comprovante", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    viewModel.dismissMediaSource()
                    launchOcr(ReceiptMediaSource.CAMERA)
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Câmera")
                }
                TextButton(onClick = {
                    viewModel.dismissMediaSource()
                    launchOcr(ReceiptMediaSource.GALLERY)
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Fotos")
                }
                TextButton(onClick = {
                    viewModel.dismissMediaSource()
                    launchOcr(ReceiptMediaSource.FILE)
                }) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Arquivos")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PurchasesHeader(
    onScanClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onSendCsv: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Compras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Comprovantes e CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = onScanClick) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Escanear comprovante")
            }
            Box {
                IconButton(onClick = { onMenuToggle(true) }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Mais opções")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuToggle(false) }) {
                    DropdownMenuItem(
                        text = { Text("Enviar CSV") },
                        onClick = onSendCsv,
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchasesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Nenhuma sessão de comprovantes de compras iniciada",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Toque no ícone de câmera para escanear um comprovante ou use os três pontos para enviar um CSV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CategorySection(group: CategoryGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (group.isTopSpend) {
                    Text(
                        "maior gasto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                PurchasesViewModel.formatBrl(group.subtotalInCents),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        group.items.forEach { item ->
            ItemRow(item)
        }
    }
}

@Composable
private fun ItemRow(item: ReceiptLineItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${item.quantity} × ${PurchasesViewModel.formatBrl(item.unitPriceInCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(PurchasesViewModel.formatBrl(item.totalInCents), style = MaterialTheme.typography.bodyMedium)
    }
}
