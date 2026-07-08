package com.checklistboteco.presentation.screen.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checklistboteco.data.remote.RemoteImportBatch
import com.checklistboteco.platform.rememberCsvDocumentPicker
import com.checklistboteco.presentation.viewmodel.AuditSheetStep
import com.checklistboteco.presentation.viewmodel.InventoryCountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAuditSheet(
    step: AuditSheetStep,
    importBatch: RemoteImportBatch?,
    importFileName: String?,
    canApplyAudit: Boolean,
    onDismiss: () -> Unit,
    onConfirmAudit: () -> Unit,
    onUploadCsv: (fileName: String, content: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickCsv = rememberCsvDocumentPicker(onUploadCsv)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Auditoria diária", style = MaterialTheme.typography.headlineSmall)

            when (step) {
                AuditSheetStep.CheckingSales -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (importFileName != null) {
                                "Importando vendas…"
                            } else {
                                "Verificando vendas do dia…"
                            }
                        )
                    }
                }

                AuditSheetStep.UploadCsv -> {
                    Text(
                        "Não há vendas importadas para hoje. Selecione a planilha CSV para continuar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    importFileName?.let {
                        Text("Arquivo: $it", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = pickCsv,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selecionar CSV")
                    }
                    importBatch?.let { batch ->
                        Text(
                            "Prévia: ${batch.totalRows} linhas · ${batch.importedRows} importadas",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        batch.sampleRows.take(3).forEach { row ->
                            Text(
                                row.values.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        batch.errors.take(5).forEach { error ->
                            Text(
                                "Linha ${error.row}: ${error.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                is AuditSheetStep.ReadyToConfirm -> {
                    val audit = step.auditPreview
                    Text(
                        "Revise os totais antes de confirmar a auditoria.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    audit?.let {
                        Text("Contado ${it.totalOpening}", style = MaterialTheme.typography.titleMedium)
                        Text("Vendido ${it.totalSold}", style = MaterialTheme.typography.titleMedium)
                        Text("Saldo ${it.totalRemaining}", style = MaterialTheme.typography.titleMedium)
                    }
                    if (canApplyAudit) {
                        Button(
                            onClick = onConfirmAudit,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar auditoria")
                        }
                    } else {
                        Text(
                            "Você tem acesso somente leitura aos insights de auditoria.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AuditSheetStep.Processing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Processando auditoria…")
                }

                is AuditSheetStep.Done -> Unit

                is AuditSheetStep.Error -> {
                    Text(step.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}
