package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.remote.RemoteApplyDailyAudit
import com.checklistboteco.data.remote.RemoteImportBatch
import com.checklistboteco.data.remote.RemoteInventoryAudit
import com.checklistboteco.platform.AppErrorMapper
import com.checklistboteco.platform.AppNetworkFeedback
import com.checklistboteco.platform.RemoteSessionRequiredException
import com.checklistboteco.platform.requireRemoteToken
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class AuditSheetStep {
    data object CheckingSales : AuditSheetStep()
    data object UploadCsv : AuditSheetStep()
    data class ReadyToConfirm(val auditPreview: RemoteInventoryAudit?) : AuditSheetStep()
    data object Processing : AuditSheetStep()
    data class Done(val result: RemoteApplyDailyAudit) : AuditSheetStep()
    data class Error(val message: String) : AuditSheetStep()
}

internal fun shouldSkipCsvUpload(audit: RemoteInventoryAudit): Boolean {
    return audit.totalSold > 0 || audit.items.any { it.soldQuantity > 0 }
}

data class InventoryCountUiState(
    val items: List<InventoryCountDraft> = emptyList(),
    val administrativeMode: Boolean = false,
    val sending: Boolean = false,
    val message: String? = null,
    val auditSheetStep: AuditSheetStep? = null,
    val showAuditResult: Boolean = false,
    val auditImportBatch: RemoteImportBatch? = null,
    val auditImportFileName: String? = null
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
                val client = requireNotNull(api) { "Backend não configurado" }
                val authToken = requireRemoteToken(client, token)
                val date = currentDate()
                if (admin) client.submitAdminStockCount(authToken, date, values) else client.submitInventoryCount(authToken, date, values)
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
                if (error is RemoteSessionRequiredException) {
                    _state.update { it.copy(sending = false) }
                    return@onFailure
                }
                AppNetworkFeedback.showError(AppErrorMapper.toUserMessage(error))
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun openAuditSheet() {
        _state.update {
            it.copy(
                auditSheetStep = AuditSheetStep.CheckingSales,
                showAuditResult = false,
                auditImportBatch = null,
                auditImportFileName = null,
                message = null
            )
        }
        scope.launch {
            runCatching {
                val client = requireNotNull(api)
                val authToken = requireRemoteToken(client, token)
                client.inventoryDailyAudit(authToken, currentDate())
            }.onSuccess { audit ->
                _state.update {
                    it.copy(
                        auditSheetStep = if (shouldSkipCsvUpload(audit)) {
                            AuditSheetStep.ReadyToConfirm(audit)
                        } else {
                            AuditSheetStep.UploadCsv
                        }
                    )
                }
            }.onFailure { error ->
                if (error is RemoteSessionRequiredException) return@onFailure
                val message = AppErrorMapper.toUserMessage(error)
                AppNetworkFeedback.showError(message)
                _state.update { it.copy(auditSheetStep = AuditSheetStep.Error(message)) }
            }
        }
    }

    fun closeAuditSheet() {
        _state.update {
            it.copy(
                auditSheetStep = null,
                auditImportBatch = null,
                auditImportFileName = null
            )
        }
    }

    fun closeAuditResult() {
        _state.update { it.copy(showAuditResult = false, auditSheetStep = null) }
    }

    fun uploadSalesCsv(fileName: String, content: String) {
        _state.update {
            it.copy(
                auditSheetStep = AuditSheetStep.CheckingSales,
                auditImportFileName = fileName,
                auditImportBatch = null,
                message = null
            )
        }
        scope.launch {
            runCatching {
                val client = requireNotNull(api)
                val authToken = requireRemoteToken(client, token)
                val preview = client.salesImportPreview(authToken, fileName, content)
                if (preview.errors.isNotEmpty()) {
                    error(preview.errors.first().message.ifBlank { "Erro ao processar o CSV." })
                }
                val mapping = preview.suggestedMapping.ifEmpty { preview.mapping }
                val committed = client.salesImportCommit(authToken, preview.id, mapping)
                if (committed.errors.isNotEmpty()) {
                    error(committed.errors.first().message.ifBlank { "Erro ao importar vendas." })
                }
                committed to client.inventoryDailyAudit(authToken, currentDate())
            }.onSuccess { (batch, audit) ->
                _state.update {
                    it.copy(
                        auditImportBatch = batch,
                        auditSheetStep = AuditSheetStep.ReadyToConfirm(audit)
                    )
                }
            }.onFailure { error ->
                if (error is RemoteSessionRequiredException) return@onFailure
                val message = AppErrorMapper.toUserMessage(error)
                AppNetworkFeedback.showError(message)
                _state.update {
                    it.copy(
                        auditSheetStep = AuditSheetStep.UploadCsv,
                        message = message
                    )
                }
            }
        }
    }

    fun confirmAudit() {
        _state.update { it.copy(auditSheetStep = AuditSheetStep.Processing, message = null) }
        scope.launch {
            runCatching {
                val client = requireNotNull(api)
                val authToken = requireRemoteToken(client, token)
                client.applyDailyAudit(authToken, currentDate())
            }.onSuccess { response ->
                _state.update {
                    it.copy(
                        auditSheetStep = AuditSheetStep.Done(response),
                        showAuditResult = true,
                        message = if (response.alreadyApplied) {
                            "Auditoria já havia sido aplicada ao estoque administrativo."
                        } else {
                            "Vendas abatidas do estoque administrativo conforme planilha."
                        }
                    )
                }
            }.onFailure { error ->
                if (error is RemoteSessionRequiredException) return@onFailure
                val message = AppErrorMapper.toUserMessage(error)
                AppNetworkFeedback.showError(message)
                _state.update {
                    val previous = it.auditSheetStep
                    it.copy(
                        auditSheetStep = when (previous) {
                            is AuditSheetStep.ReadyToConfirm -> AuditSheetStep.Error(message)
                            else -> AuditSheetStep.Error(message)
                        }
                    )
                }
            }
        }
    }

    private fun currentDate(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
