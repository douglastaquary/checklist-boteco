import Foundation
import Network

public struct ReceiptSessionSubmitResponse: Decodable, Sendable {
  public let sessionId: String
  public let status: String
  public let importedRows: Int
  public let duplicateRows: Int
  public let rejectedRows: Int
  public let totalInCents: Int64
}

public struct PurchaseImportBatch: Decodable, Sendable {
  public let id: String
  public let status: String
  public let suggestedMapping: [String: String]
  public let headers: [String]
  public let totalRows: Int
  public let importedRows: Int
  public let errors: [PurchaseImportError]
}

public struct PurchaseImportError: Decodable, Sendable {
  public let row: Int
  public let field: String
  public let message: String
}

public final class PurchaseClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func submitReceiptSession(token: String, session: ReceiptSession) async throws -> ReceiptSessionSubmitResponse {
    let body = ReceiptSessionSubmitRequest(
      datasetId: "purchases",
      purchaseDate: session.purchaseDate,
      location: session.location,
      supplier: session.supplier,
      paymentMethod: session.paymentMethod,
      items: session.allItems.map {
        ReceiptSessionItemDTO(
          description: $0.description,
          category: $0.category,
          quantity: $0.quantity,
          unitPriceInCents: $0.unitPriceInCents,
          totalInCents: $0.totalInCents
        )
      }
    )
    return try await api.request(
      path: "/api/purchases/receipt-sessions/submit",
      method: "POST",
      token: token,
      body: body
    )
  }

  public func previewImport(token: String, fileName: String, csv: String) async throws -> PurchaseImportBatch {
    try await api.request(
      path: "/api/purchases/imports/preview",
      method: "POST",
      token: token,
      body: PreviewBody(fileName: fileName, csv: csv)
    )
  }

  public func commitImport(
    token: String,
    importId: String,
    mapping: [String: String],
    preserveColumns: [String]
  ) async throws -> PurchaseImportBatch {
    try await api.request(
      path: "/api/purchases/imports/\(importId)/commit",
      method: "POST",
      token: token,
      body: CommitBody(datasetId: "purchases", mapping: mapping, preserveColumns: preserveColumns)
    )
  }
}

private struct PreviewBody: Encodable {
  let fileName: String
  let csv: String
}

private struct CommitBody: Encodable {
  let datasetId: String
  let mapping: [String: String]
  let preserveColumns: [String]
}

private struct ReceiptSessionSubmitRequest: Encodable {
  let datasetId: String
  let purchaseDate: String?
  let location: String
  let supplier: String?
  let paymentMethod: String?
  let items: [ReceiptSessionItemDTO]
}

private struct ReceiptSessionItemDTO: Encodable {
  let description: String
  let category: String
  let quantity: Double
  let unitPriceInCents: Int64
  let totalInCents: Int64
}
