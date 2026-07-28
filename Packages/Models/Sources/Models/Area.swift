import Foundation

public enum Area: String, CaseIterable, Codable, Sendable {
  case atendimento = "ATENDIMENTO"
  case cozinha = "COZINHA"
  case estoque = "ESTOQUE"
  case limpeza = "LIMPEZA"

  public var displayName: String {
    switch self {
    case .atendimento: return "Atendimento"
    case .cozinha: return "Cozinha"
    case .estoque: return "Estoque"
    case .limpeza: return "Limpeza"
    }
  }

  public static func from(_ value: String) -> Area {
    Area(rawValue: value.uppercased()) ?? .atendimento
  }
}
