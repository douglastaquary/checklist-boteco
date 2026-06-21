import Foundation

public enum InventoryCategory: String, Codable, CaseIterable, Sendable {
  case alcoolico = "ALCOOLICO"
  case naoAlcoolico = "NAO_ALCOOLICO"
}

public enum StorageCondition: String, Codable, CaseIterable, Sendable {
  case gelado = "GELADO"
  case natural = "NATURAL"
}

public struct InventoryCountDraft: Identifiable, Equatable, Sendable {
  public let id: Int64
  public var name: String
  public var quantity: Double
  public var category: InventoryCategory
  public var volume: Double
  public var volumeUnit: String
  public var salePriceInCents: Int64
  public var costPriceInCents: Int64?
  public var storageCondition: StorageCondition

  public init(
    id: Int64 = 0,
    name: String,
    quantity: Double,
    category: InventoryCategory,
    volume: Double,
    volumeUnit: String,
    salePriceInCents: Int64,
    costPriceInCents: Int64? = nil,
    storageCondition: StorageCondition
  ) {
    self.id = id
    self.name = name
    self.quantity = quantity
    self.category = category
    self.volume = volume
    self.volumeUnit = volumeUnit
    self.salePriceInCents = salePriceInCents
    self.costPriceInCents = costPriceInCents
    self.storageCondition = storageCondition
  }
}

public enum InventoryCountValidator {
  public static func validate(_ value: InventoryCountDraft) -> [String] {
    var errors: [String] = []
    if value.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      errors.append("Nome obrigatório")
    }
    if value.quantity < 0 { errors.append("Quantidade não pode ser negativa") }
    if value.volume <= 0 { errors.append("Volume deve ser maior que zero") }
    if !["ML", "G"].contains(value.volumeUnit.uppercased()) {
      errors.append("Unidade deve ser ML ou G")
    }
    if value.salePriceInCents < 0 { errors.append("Valor de venda inválido") }
    if let cost = value.costPriceInCents, cost < 0 { errors.append("Preço de custo inválido") }
    return errors
  }
}
