package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.remote.RemoteAdminStockBalance
import com.checklistboteco.data.remote.RemoteInventoryAudit
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class InventoryCountUiState(
    val items: List<InventoryCountDraft> = emptyList(),
    val administrativeMode: Boolean = false,
    val sending: Boolean = false,
    val message: String? = null,
    val audit: RemoteInventoryAudit? = null,
    val balances: List<RemoteAdminStockBalance> = emptyList(),
    val auditAlreadyApplied: Boolean = false
)

class InventoryCountViewModel(
    private val repository: ChecklistRepository,
    private val api: BackendApiClient?,
    private val token: String?,
    private val scope: CoroutineScope
) {
    private val administrativeMode = MutableStateFlow(false)
    private val _state = MutableStateFlow(InventoryCountUiState())
    val state: StateFlow<InventoryCountUiState> = _state.asStateFlow()

    init {
        scope.launch {
            administrativeMode.flatMapLatest { admin ->
                repository.inventoryCountDraft(admin).map { items -> admin to items }
            }.collect { (admin, items) ->
                _state.update { it.copy(administrativeMode = admin, items = items) }
            }
        }
    }

    fun setAdministrativeMode(value: Boolean) {
        administrativeMode.value = value
    }

    fun add(
        name: String,
        quantity: Double,
        category: InventoryCategory,
        volume: Double,
        unit: String,
        salePrice: Long,
        costPrice: Long?,
        condition: StorageCondition
    ) {
        repository.addInventoryCountDraft(
            InventoryCountDraft(
                name = name,
                quantity = quantity,
                category = category,
                volume = volume,
                volumeUnit = unit,
                salePriceInCents = salePrice,
                costPriceInCents = costPrice,
                storageCondition = condition
            ),
            administrative = administrativeMode.value
        )
    }

    fun remove(id: Long) = repository.deleteInventoryCountDraft(id)

    fun submit() {
        val values = _state.value.items
        if (values.isEmpty()) return
        val admin = _state.value.administrativeMode
        scope.launch {
            _state.update { it.copy(sending = true, message = null) }
            runCatching {
                requireNotNull(api) { "Backend não configurado" }
                require(!token.isNullOrBlank()) { "Faça login novamente" }
                val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                if (admin) api.submitAdminStockCount(token, date, values) else api.submitInventoryCount(token, date, values)
                repository.clearInventoryCountDraft(admin)
            }.onSuccess {
                _state.update {
                    it.copy(
                        sending = false,
                        message = if (admin) {
                            "Contagem administrativa enviada e saldo atualizado."
                        } else {
                            "Contagem enviada e bloqueada para edição."
                        }
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(sending = false, message = error.message ?: "Falha ao enviar") }
            }
        }
    }

    fun loadAudit() {
        scope.launch {
            val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            runCatching {
                requireNotNull(api)
                require(!token.isNullOrBlank())
                api.inventoryDailyAudit(token, date)
            }.onSuccess { value ->
                _state.update { it.copy(audit = value, message = null) }
            }.onFailure { error ->
                _state.update { it.copy(message = error.message) }
            }
        }
    }

    fun applyAudit() {
        scope.launch {
            val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            _state.update { it.copy(sending = true, message = null) }
            runCatching {
                requireNotNull(api)
                require(!token.isNullOrBlank())
                api.applyDailyAudit(token, date)
            }.onSuccess { response ->
                _state.update {
                    it.copy(
                        sending = false,
                        audit = response.audit,
                        balances = response.balances,
                        auditAlreadyApplied = response.alreadyApplied,
                        message = if (response.alreadyApplied) {
                            "Auditoria já havia sido aplicada ao estoque administrativo."
                        } else {
                            "Vendas abatidas do estoque administrativo conforme planilha."
                        }
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(sending = false, message = error.message ?: "Falha ao aplicar auditoria") }
            }
        }
    }
}
