package com.checklistboteco.presentation.screen.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checklistboteco.data.remote.RemoteApplyDailyAudit
import com.checklistboteco.data.remote.RemoteInventoryAudit
import com.checklistboteco.data.remote.RemoteInventoryAuditItem
import com.checklistboteco.domain.model.InventoryAuditItemSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryAuditResultSheet(
    result: RemoteApplyDailyAudit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val audit = result.audit
    val snapshots = remember(audit) {
        audit?.items.orEmpty().map { item -> item.toSnapshot(audit) }
    }
    val pageCount = 1 + snapshots.size
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (result.alreadyApplied) "Auditoria já aplicada" else "Auditoria concluída",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (pageCount > 1) {
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceAtMost(pageCount - 1),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { },
                        enabled = false,
                        text = { Text("Resumo") }
                    )
                    snapshots.forEachIndexed { index, snapshot ->
                        Tab(
                            selected = pagerState.currentPage == index + 1,
                            onClick = { },
                            enabled = false,
                            text = {
                                Text(
                                    snapshot.product.take(12),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> AuditSummaryPage(result = result, audit = audit)
                    else -> AuditItemDetailPage(snapshot = snapshots[page - 1])
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Página ${pagerState.currentPage + 1} de $pageCount — deslize para o lado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AuditSummaryPage(
    result: RemoteApplyDailyAudit,
    audit: RemoteInventoryAudit?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (result.alreadyApplied) {
            Text(
                "A auditoria deste dia já havia sido aplicada ao estoque administrativo.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        audit?.let {
            Text("Data: ${it.date}", style = MaterialTheme.typography.bodyMedium)
            Text("Local: ${it.location}", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Total abertura: ${it.totalOpening}", style = MaterialTheme.typography.titleMedium)
            Text("Total vendido: ${it.totalSold}", style = MaterialTheme.typography.titleMedium)
            Text("Total saldo: ${it.totalRemaining}", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            it.items.take(15).forEach { item ->
                Text("${item.status} · ${item.product}: ${item.theoreticalRemaining} restantes")
            }
        }
        if (result.balances.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Saldo administrativo", style = MaterialTheme.typography.titleMedium)
            result.balances.take(15).forEach { balance ->
                Text("${balance.productName}: ${balance.quantity}")
            }
        }
    }
}

@Composable
private fun AuditItemDetailPage(snapshot: InventoryAuditItemSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(snapshot.product, style = MaterialTheme.typography.headlineSmall)
        Text("Data: ${snapshot.auditDate}")
        Text("Local: ${snapshot.location}")
        Text("Status: ${snapshot.status}")
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Abertura: ${formatAuditNumber(snapshot.openingQuantity)}")
        Text("Vendido: ${formatAuditNumber(snapshot.soldQuantity)}")
        Text("Saldo teórico: ${formatAuditNumber(snapshot.theoreticalRemaining)}")
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Total abertura: ${formatAuditNumber(snapshot.totalOpening)}")
        Text("Total vendido: ${formatAuditNumber(snapshot.totalSold)}")
        Text("Total saldo: ${formatAuditNumber(snapshot.totalRemaining)}")
        if (snapshot.notes.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Observações", style = MaterialTheme.typography.titleSmall)
            Text(snapshot.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun RemoteInventoryAuditItem.toSnapshot(audit: RemoteInventoryAudit?): InventoryAuditItemSnapshot {
    return InventoryAuditItemSnapshot(
        product = product,
        auditDate = audit?.date.orEmpty(),
        location = audit?.location.orEmpty(),
        status = status,
        notes = notes,
        openingQuantity = openingQuantity,
        soldQuantity = soldQuantity,
        theoreticalRemaining = theoreticalRemaining,
        totalOpening = audit?.totalOpening ?: 0.0,
        totalSold = audit?.totalSold ?: 0.0,
        totalRemaining = audit?.totalRemaining ?: 0.0
    )
}

private fun formatAuditNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
