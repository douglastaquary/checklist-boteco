package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.checklistboteco.presentation.designsystem.components.BecoEmptyState
import com.checklistboteco.presentation.designsystem.components.BecoFilterOption
import com.checklistboteco.presentation.designsystem.components.BecoSegmentedFilter
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.presentation.screen.inventory.InventoryAuditResultSheet
import com.checklistboteco.presentation.screen.inventory.InventoryAuditSheet
import com.checklistboteco.presentation.viewmodel.AuditSheetStep
import com.checklistboteco.presentation.viewmodel.InventoryCountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryCountScreen(
    viewModel: InventoryCountViewModel,
    canCreate: Boolean,
    canViewInsights: Boolean,
    canManageAdministrativeStock: Boolean,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showAddProduct by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    val administrativeMode = state.administrativeMode
    val canCreateInMode = if (administrativeMode) canManageAdministrativeStock else canCreate
    val canOpenAudit = canViewInsights || canManageAdministrativeStock || isAdmin
    val canApplyAudit = canManageAdministrativeStock || canViewInsights || isAdmin
    val addProductSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(canManageAdministrativeStock, canCreate) {
        if (canManageAdministrativeStock && !canCreate) {
            viewModel.setAdministrativeMode(true)
        }
    }

    val title = when {
        administrativeMode -> "Estoque admin"
        else -> "Contagem"
    }
    val subtitle = when {
        administrativeMode -> "Contagem administrativa"
        canCreateInMode -> "Abertura"
        else -> "Somente insights"
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (canCreateInMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddProduct = true },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Adicionar") }
                )
            }
        },
        bottomBar = {
            if (canCreateInMode) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirmSubmit = true },
                        enabled = state.items.isNotEmpty() && !state.sending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            if (state.sending) {
                                "Enviando…"
                            } else if (administrativeMode) {
                                "Revisar e enviar"
                            } else {
                                "Revisar e enviar"
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canOpenAudit) {
                        TextButton(onClick = viewModel::openAuditSheet) {
                            Text("Auditoria")
                        }
                    }
                }
            }

            if (canManageAdministrativeStock && canCreate) {
                item {
                    BecoSegmentedFilter(
                        options = listOf(
                            BecoFilterOption(false, "Abertura"),
                            BecoFilterOption(true, "Estoque admin")
                        ),
                        selected = administrativeMode,
                        onSelected = viewModel::setAdministrativeMode
                    )
                }
            }

            item {
                Text(
                    when {
                        canCreateInMode && administrativeMode ->
                            "Soma ao saldo acumulado de estoque. Após a auditoria diária, as vendas são abatidas."
                        canCreateInMode ->
                            "Itens ficam no aparelho até o envio em lote."
                        else ->
                            "Acesso somente aos insights e à auditoria."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canCreateInMode) {
                if (state.items.isEmpty()) {
                    item {
                        BecoEmptyState(
                            title = "Nenhum produto adicionado",
                            message = "Toque em Adicionar para começar a contagem."
                        )
                    }
                } else {
                    item {
                        Text("Itens da contagem", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.items, key = { it.id }) { item ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = BecoSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        inventoryCountItemSummary(
                                            quantity = item.quantity,
                                            storageCondition = item.storageCondition,
                                            volume = item.volume,
                                            volumeUnit = item.volumeUnit
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.remove(item.id) }) { Text("Remover") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            state.message?.let {
                item { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    if (showAddProduct && canCreateInMode) {
        ModalBottomSheet(
            onDismissRequest = { showAddProduct = false },
            sheetState = addProductSheetState
        ) {
            AddInventoryProductSheetContent(
                isAdmin = isAdmin,
                onDismiss = { showAddProduct = false },
                onAdd = viewModel::add
            )
        }
    }

    state.auditSheetStep?.let { step ->
        if (step !is AuditSheetStep.Done || !state.showAuditResult) {
            InventoryAuditSheet(
                step = step,
                importBatch = state.auditImportBatch,
                importFileName = state.auditImportFileName,
                canApplyAudit = canApplyAudit,
                onDismiss = viewModel::closeAuditSheet,
                onConfirmAudit = viewModel::confirmAudit,
                onUploadCsv = viewModel::uploadSalesCsv
            )
        }
    }

    if (state.showAuditResult) {
        val doneStep = state.auditSheetStep as? AuditSheetStep.Done
        if (doneStep != null) {
            InventoryAuditResultSheet(
                result = doneStep.result,
                onDismiss = viewModel::closeAuditResult
            )
        }
    }

    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(if (administrativeMode) "Confirmar estoque" else "Confirmar contagem") },
            text = {
                Text(
                    if (administrativeMode) {
                        "Confirma o envio da contagem administrativa? O saldo acumulado será atualizado."
                    } else {
                        "Os valores estão corretos? Após o envio, a contagem não poderá ser editada."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmSubmit = false
                    viewModel.submit()
                }) {
                    Text("Sim, enviar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSubmit = false }) {
                    Text("Revisar")
                }
            }
        )
    }
}
