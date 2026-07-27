package com.checklistboteco.data.remote

import com.checklistboteco.platform.ApiException
import com.checklistboteco.receipt.ReceiptLineItem
import com.checklistboteco.receipt.ReceiptSession
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class PurchaseApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: io.ktor.client.HttpClient
) {
    suspend fun submitReceiptSession(token: String, session: ReceiptSession): ReceiptSessionSubmitResponse {
        val request = ReceiptSessionSubmitRequestDto(
            datasetId = "purchases",
            purchaseDate = session.purchaseDate,
            location = session.location,
            supplier = session.supplier,
            paymentMethod = session.paymentMethod,
            items = session.allItems.map { it.toDto() }
        )
        return httpClient.post("$baseUrl/api/purchases/receipt-sessions/submit") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun previewImport(token: String, fileName: String, csv: String): PurchaseImportBatchDto {
        return httpClient.post("$baseUrl/api/purchases/imports/preview") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PurchasePreviewRequestDto(fileName = fileName, csv = csv))
        }.body()
    }

    suspend fun commitImport(
        token: String,
        importId: String,
        mapping: Map<String, String>,
        preserveColumns: List<String> = emptyList()
    ): PurchaseImportBatchDto {
        return httpClient.post("$baseUrl/api/purchases/imports/$importId/commit") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                PurchaseCommitRequestDto(
                    datasetId = "purchases",
                    mapping = mapping,
                    preserveColumns = preserveColumns
                )
            )
        }.body()
    }

    companion object {
        fun fromEnvironment(): PurchaseApiClient? {
            val configuredUrl = BackendEnvironment.baseUrl.trim().trimEnd('/')
            if (configuredUrl.isBlank()) return null
            return PurchaseApiClient(configuredUrl, createAppHttpClient())
        }
    }
}

@Serializable
data class ReceiptSessionSubmitResponse(
    val sessionId: String = "",
    val status: String = "",
    val importedRows: Int = 0,
    val duplicateRows: Int = 0,
    val rejectedRows: Int = 0,
    val totalInCents: Long = 0
)

@Serializable
data class PurchaseImportBatchDto(
    val id: String = "",
    val status: String = "",
    val suggestedMapping: Map<String, String> = emptyMap(),
    val headers: List<String> = emptyList(),
    val totalRows: Int = 0,
    val importedRows: Int = 0,
    val errors: List<PurchaseImportErrorDto> = emptyList()
)

@Serializable
data class PurchaseImportErrorDto(
    val row: Int = 0,
    val field: String = "",
    val message: String = ""
)

@Serializable
private data class ReceiptSessionSubmitRequestDto(
    val datasetId: String = "purchases",
    val purchaseDate: String? = null,
    val location: String = "Beco da Praia",
    val supplier: String? = null,
    val paymentMethod: String? = null,
    val items: List<ReceiptSessionItemDto> = emptyList()
)

@Serializable
private data class ReceiptSessionItemDto(
    val description: String,
    val category: String,
    val quantity: Double,
    val unitPriceInCents: Long,
    val totalInCents: Long
)

@Serializable
private data class PurchasePreviewRequestDto(val fileName: String, val csv: String)

@Serializable
private data class PurchaseCommitRequestDto(
    val datasetId: String = "purchases",
    val mapping: Map<String, String> = emptyMap(),
    val preserveColumns: List<String> = emptyList()
)

private fun ReceiptLineItem.toDto() = ReceiptSessionItemDto(
    description = description,
    category = category,
    quantity = quantity,
    unitPriceInCents = unitPriceInCents,
    totalInCents = totalInCents
)
