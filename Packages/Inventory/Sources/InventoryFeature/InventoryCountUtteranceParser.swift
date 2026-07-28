import Foundation
import SwiftUI
import Models

struct InventoryUtteranceParseResult: Equatable {
  var draft: InventoryCountDraft
  var isCompleteEnough: Bool
  var highlightTokens: [String]
}

enum InventoryCountUtteranceParser {
  static func parse(_ raw: String) -> InventoryUtteranceParseResult {
    let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    var working = text.lowercased()
    var highlights: [String] = []

    var category: InventoryCategory = .alcoolico
    if let range = working.range(of: #"n[aã]o\s*alco[oó]lico"# , options: .regularExpression) {
      category = .naoAlcoolico
      highlights.append(String(working[range]))
      working.removeSubrange(range)
    } else if let range = working.range(of: #"alco[oó]lico"# , options: .regularExpression) {
      category = .alcoolico
      highlights.append(String(working[range]))
      working.removeSubrange(range)
    } else if working.contains("refrigerante") || working.contains("água") || working.contains("agua") {
      category = .naoAlcoolico
    }

    var storage: StorageCondition = .gelado
    if let range = working.range(of: "natural") {
      storage = .natural
      highlights.append("natural")
      working.removeSubrange(range)
    } else if let range = working.range(of: "gelado") {
      storage = .gelado
      highlights.append("gelado")
      working.removeSubrange(range)
    }

    var volume: Double = 600
    var volumeUnit = "ML"
    if let match = working.range(of: #"(\d+(?:[.,]\d+)?)\s*(ml|g)\b"#, options: .regularExpression) {
      let token = String(working[match])
      highlights.append(token)
      let parts = token.split(whereSeparator: { $0.isWhitespace })
      if let first = parts.first,
         let value = InventoryDraftFormatting.parseDecimal(String(first)) {
        volume = value
      }
      if token.lowercased().contains("g") && !token.lowercased().contains("ml") {
        volumeUnit = "G"
      }
      working.removeSubrange(match)
    }

    var quantity: Double = 0
    if let match = working.range(of: #"\b(\d+(?:[.,]\d+)?)\b"#, options: .regularExpression) {
      let token = String(working[match])
      if let value = InventoryDraftFormatting.parseDecimal(token) {
        quantity = value
        highlights.append(token)
      }
      working.removeSubrange(match)
    }

    let name = working
      .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .split(separator: " ")
      .map { part -> String in
        let lower = part.lowercased()
        guard !lower.isEmpty else { return "" }
        return lower.prefix(1).uppercased() + lower.dropFirst()
      }
      .joined(separator: " ")

    if !name.isEmpty {
      highlights.append(name.lowercased())
    }

    let draft = InventoryCountDraft(
      name: name,
      quantity: quantity,
      category: category,
      volume: volume,
      volumeUnit: volumeUnit,
      salePriceInCents: 0,
      storageCondition: storage
    )

    let isCompleteEnough = !name.isEmpty && quantity > 0
    return InventoryUtteranceParseResult(
      draft: draft,
      isCompleteEnough: isCompleteEnough,
      highlightTokens: Array(Set(highlights)).sorted()
    )
  }

  static func highlightedText(original: String, tokens: [String]) -> AttributedString {
    var attributed = AttributedString(original)
    attributed.font = .body
    attributed.foregroundColor = .white
    let lowerOriginal = original.lowercased()
    for token in tokens {
      let needle = token.lowercased()
      guard !needle.isEmpty else { continue }
      var searchStart = lowerOriginal.startIndex
      while let range = lowerOriginal.range(of: needle, range: searchStart..<lowerOriginal.endIndex) {
        if let attrStart = AttributedString.Index(range.lowerBound, within: attributed),
           let attrEnd = AttributedString.Index(range.upperBound, within: attributed) {
          attributed[attrStart..<attrEnd].font = .system(size: 17, weight: .semibold, design: .monospaced)
          attributed[attrStart..<attrEnd].foregroundColor = .white
          attributed[attrStart..<attrEnd].backgroundColor = Color.white.opacity(0.18)
        }
        searchStart = range.upperBound
      }
    }
    return attributed
  }
}
