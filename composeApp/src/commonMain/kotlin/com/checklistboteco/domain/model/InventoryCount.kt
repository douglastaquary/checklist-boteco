package com.checklistboteco.domain.model

data class InventoryCountDraft(
    val id: Long = 0,
    val name: String,
    val quantity: Double,
    val category: InventoryCategory,
    val volume: Double,
    val volumeUnit: String,
    val salePriceInCents: Long,
    val costPriceInCents: Long? = null,
    val storageCondition: StorageCondition
)

enum class InventoryCategory { ALCOOLICO, NAO_ALCOOLICO }
enum class StorageCondition { GELADO, NATURAL }

object InventoryCountValidator {
    fun validate(value:InventoryCountDraft):List<String> = buildList {
        if(value.name.isBlank()) add("Nome obrigatório")
        if(value.quantity < 0) add("Quantidade não pode ser negativa")
        if(value.volume <= 0) add("Volume deve ser maior que zero")
        if(value.volumeUnit !in setOf("ML","G")) add("Unidade deve ser ML ou G")
        if(value.salePriceInCents < 0) add("Valor de venda inválido")
        if(value.costPriceInCents != null && value.costPriceInCents < 0) add("Preço de custo inválido")
    }
}
