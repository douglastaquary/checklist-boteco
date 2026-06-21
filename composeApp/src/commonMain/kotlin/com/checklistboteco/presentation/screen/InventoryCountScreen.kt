package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var confirm by remember { mutableStateOf(false) }
    val administrativeMode = state.administrativeMode
    val canCreateInMode = if (administrativeMode) canManageAdministrativeStock else canCreate
    val canApplyAudit = canManageAdministrativeStock || canViewInsights || isAdmin

    LaunchedEffect(canManageAdministrativeStock, canCreate) {
        if (canManageAdministrativeStock && !canCreate) {
            viewModel.setAdministrativeMode(true)
        }
    }

    if (showAddProduct && canCreateInMode) {
        AddInventoryProductScreen(
            isAdmin = isAdmin,
            onBack = { showAddProduct = false },
            onAdd = viewModel::add,
            modifier = modifier
        )
        return
    }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (canManageAdministrativeStock && canCreate) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !administrativeMode,
                        onClick = { viewModel.setAdministrativeMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Abertura") }
                    SegmentedButton(
                        selected = administrativeMode,
                        onClick = { viewModel.setAdministrativeMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Estoque admin") }
                }
            }

            Text(
                if (administrativeMode) "Contagem administrativa de estoque" else "Contagem de abertura",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                when {
                    canCreateInMode && administrativeMode ->
                        "Soma ao saldo acumulado de estoque. Após a auditoria diária, as vendas são abatidas."
                    canCreateInMode ->
                        "Itens ficam no aparelho até o envio em lote."
                    else ->
                        "Acesso somente aos insights e à auditoria."
                }
            )

            if (canCreateInMode) {
                OutlinedButton(
                    onClick = { showAddProduct = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Adicionar produto")
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        Card {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                TextButton(onClick = { viewModel.remove(item.id) }) {
                                    Text("Remover")
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            if (canCreateInMode) {
                Button(
                    onClick = { confirm = true },
                    enabled = state.items.isNotEmpty() && !state.sending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.sending) {
                            "Enviando…"
                        } else if (administrativeMode) {
                            "Revisar e enviar estoque"
                        } else {
                            "Revisar e enviar todos"
                        }
                    )
                }
            }

            if (canViewInsights || canApplyAudit) {
                HorizontalDivider()
                Button(
                    onClick = viewModel::loadAudit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gerar auditoria de hoje")
                }
                state.audit?.let { audit ->
                    Text(
                        "Contado ${audit.totalOpening} · Vendido ${audit.totalSold} · Saldo ${audit.totalRemaining}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    audit.items.take(10).forEach { item ->
                        Text("${item.status} · ${item.product}: ${item.theoreticalRemaining} restantes")
                    }
                }
                if (canApplyAudit) {
                    OutlinedButton(
                        onClick = viewModel::applyAudit,
                        enabled = !state.sending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar auditoria e abater vendas")
                    }
                    if (state.balances.isNotEmpty()) {
                        Text("Saldo administrativo", style = MaterialTheme.typography.titleMedium)
                        state.balances.take(10).forEach { balance ->
                            Text("${balance.productName}: ${balance.quantity}")
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
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
                    confirm = false
                    viewModel.submit()
                }) {
                    Text("Sim, enviar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("Revisar")
                }
            }
        )
    }
}
