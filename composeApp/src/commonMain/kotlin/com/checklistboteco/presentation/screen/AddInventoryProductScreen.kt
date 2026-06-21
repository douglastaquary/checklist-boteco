package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.InventoryCategory
import com.checklistboteco.domain.model.InventoryCountDraft
import com.checklistboteco.domain.model.InventoryCountValidator
import com.checklistboteco.domain.model.StorageCondition
import com.checklistboteco.presentation.util.BrazilianCurrencyField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryProductScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onAdd: (
        name: String,
        quantity: Double,
        category: InventoryCategory,
        volume: Double,
        volumeUnit: String,
        salePriceInCents: Long,
        costPriceInCents: Long?,
        storageCondition: StorageCondition
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("600") }
    var volumeUnit by remember { mutableStateOf("ML") }
    var saleCents by remember { mutableLongStateOf(0L) }
    var costCents by remember { mutableLongStateOf(0L) }
    var category by remember { mutableStateOf(InventoryCategory.ALCOOLICO) }
    var condition by remember { mutableStateOf(StorageCondition.GELADO) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar produto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it; error = null },
                    label = { Text("Quantidade") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it; error = null },
                    label = { Text("Volume") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = volumeUnit == "ML",
                        onClick = { volumeUnit = "ML" },
                        label = { Text("ml") }
                    )
                    FilterChip(
                        selected = volumeUnit == "G",
                        onClick = { volumeUnit = "G" },
                        label = { Text("gramas") }
                    )
                }
                BrazilianCurrencyField(
                    cents = saleCents,
                    onCentsChange = { saleCents = it; error = null },
                    label = "Valor de venda",
                    modifier = Modifier.fillMaxWidth()
                )
                if (isAdmin) {
                    BrazilianCurrencyField(
                        cents = costCents,
                        onCentsChange = { costCents = it; error = null },
                        label = "Valor de custo (opcional)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == InventoryCategory.ALCOOLICO,
                        onClick = { category = InventoryCategory.ALCOOLICO },
                        label = { Text("Alcoólico") }
                    )
                    FilterChip(
                        selected = category == InventoryCategory.NAO_ALCOOLICO,
                        onClick = { category = InventoryCategory.NAO_ALCOOLICO },
                        label = { Text("Não alcoólico") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = condition == StorageCondition.GELADO,
                        onClick = { condition = StorageCondition.GELADO },
                        label = { Text("Gelado") }
                    )
                    FilterChip(
                        selected = condition == StorageCondition.NATURAL,
                        onClick = { condition = StorageCondition.NATURAL },
                        label = { Text("Natural") }
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(
                onClick = {
                    val parsedQuantity = quantity.toDoubleOrNull()
                    val parsedVolume = volume.toDoubleOrNull()
                    if (parsedQuantity == null || parsedVolume == null) {
                        error = "Quantidade e volume devem ser numéricos."
                        return@Button
                    }
                    val cost = if (isAdmin && costCents > 0L) costCents else null
                    val draft = InventoryCountDraft(
                        name = name.trim(),
                        quantity = parsedQuantity,
                        category = category,
                        volume = parsedVolume,
                        volumeUnit = volumeUnit,
                        salePriceInCents = saleCents,
                        costPriceInCents = cost,
                        storageCondition = condition
                    )
                    val validationErrors = InventoryCountValidator.validate(draft)
                    if (validationErrors.isNotEmpty()) {
                        error = validationErrors.first()
                        return@Button
                    }
                    onAdd(
                        draft.name,
                        draft.quantity,
                        draft.category,
                        draft.volume,
                        draft.volumeUnit,
                        draft.salePriceInCents,
                        draft.costPriceInCents,
                        draft.storageCondition
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adicionar à contagem")
            }
        }
    }
}

internal fun inventoryCountItemSummary(
    quantity: Double,
    storageCondition: StorageCondition,
    volume: Double,
    volumeUnit: String
): String {
    val qty = if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()
    val conditionLabel = when (storageCondition) {
        StorageCondition.GELADO -> "Gelado"
        StorageCondition.NATURAL -> "Natural"
    }
    val unitLabel = when (volumeUnit.uppercase()) {
        "ML" -> "ml"
        "G" -> "g"
        else -> volumeUnit.lowercase()
    }
    return "Total: $qty · $conditionLabel · $volume $unitLabel"
}
