package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.PurchaseApiClient
import com.checklistboteco.receipt.CategoryGroup
import com.checklistboteco.receipt.ReceiptProcessor
import com.checklistboteco.receipt.ReceiptSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchasesUiState(
    val session: ReceiptSession = ReceiptSession(),
    val groups: List<CategoryGroup> = emptyList(),
    val isProcessing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val showMediaSourceSheet: Boolean = false,
    val showCsvPicker: Boolean = false
) {
    val canSave: Boolean get() = session.allItems.isNotEmpty() && !isUploading
    val totalLabel: String get() = formatBrl(session.totalInCents)
}

class PurchasesViewModel(
    private val purchaseApiClient: PurchaseApiClient?,
    private val authToken: String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(PurchasesUiState())
    val uiState: StateFlow<PurchasesUiState> = _uiState.asStateFlow()

    fun openMediaSource() {
        _uiState.update { it.copy(showMediaSourceSheet = true, errorMessage = null, successMessage = null) }
    }

    fun dismissMediaSource() {
        _uiState.update { it.copy(showMediaSourceSheet = false) }
    }

    fun openCsvPicker() {
        _uiState.update { it.copy(showCsvPicker = true, errorMessage = null, successMessage = null) }
    }

    fun dismissCsvPicker() {
        _uiState.update { it.copy(showCsvPicker = false) }
    }

    fun ingestOcrText(ocrText: String) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    showMediaSourceSheet = false,
                    errorMessage = null,
                    successMessage = null
                )
            }
            runCatching {
                val scan = ReceiptProcessor.parseReceipt(ocrText)
                require(scan.items.isNotEmpty()) { "Não foi possível extrair itens do comprovante." }
                val session = ReceiptProcessor.mergeScan(_uiState.value.session, scan)
                val groups = ReceiptProcessor.buildGroups(session)
                _uiState.update {
                    it.copy(
                        session = session,
                        groups = groups,
                        isProcessing = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = error.message ?: "Falha ao processar comprovante"
                    )
                }
            }
        }
    }

    fun saveSession() {
        val api = purchaseApiClient
        scope.launch {
            _uiState.update {
                it.copy(isUploading = true, uploadProgress = 0.15f, errorMessage = null, successMessage = null)
            }
            runCatching {
                require(!authToken.isNullOrBlank()) { "Faça login novamente" }
                requireNotNull(api) { "API de compras indisponível" }
                _uiState.update { it.copy(uploadProgress = 0.45f) }
                val response = api.submitReceiptSession(authToken!!, _uiState.value.session)
                _uiState.update { it.copy(uploadProgress = 1f) }
                response
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        session = ReceiptSession(),
                        groups = emptyList(),
                        successMessage = "Dados enviados com sucesso (${response.importedRows} itens)."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        errorMessage = error.message ?: "Falha ao salvar dados"
                    )
                }
            }
        }
    }

    fun uploadCsv(fileName: String, csv: String) {
        val api = purchaseApiClient
        scope.launch {
            _uiState.update {
                it.copy(
                    showCsvPicker = false,
                    isUploading = true,
                    uploadProgress = 0.2f,
                    errorMessage = null,
                    successMessage = null
                )
            }
            runCatching {
                require(!authToken.isNullOrBlank()) { "Faça login novamente" }
                requireNotNull(api) { "API de compras indisponível" }
                val preview = api.previewImport(authToken!!, fileName, csv)
                require(preview.errors.isEmpty() || preview.suggestedMapping.size >= 4) {
                    preview.errors.firstOrNull()?.message ?: "CSV inválido"
                }
                _uiState.update { it.copy(uploadProgress = 0.6f) }
                val mapping = preview.suggestedMapping
                val committed = api.commitImport(
                    token = authToken!!,
                    importId = preview.id,
                    mapping = mapping,
                    preserveColumns = preview.headers
                )
                require(committed.status == "COMMITTED") { "Importação não confirmada" }
                committed
            }.onSuccess { batch ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        successMessage = "Dados enviados com sucesso (${batch.importedRows} linhas)."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        errorMessage = error.message ?: "Falha ao enviar CSV"
                    )
                }
            }
        }
    }

    fun clearSession() {
        _uiState.update {
            it.copy(session = ReceiptSession(), groups = emptyList(), successMessage = null, errorMessage = null)
        }
    }

    companion object {
        fun formatBrl(cents: Long): String {
            val reais = cents / 100
            val frac = kotlin.math.abs(cents % 100).toString().padStart(2, '0')
            return "R$ $reais,$frac"
        }
    }
}

private fun formatBrl(cents: Long): String = PurchasesViewModel.formatBrl(cents)
