import SwiftUI
import Models
import DesignSystem

enum InventoryDraftFormMode: Equatable {
  case create
  case edit(InventoryCountDraft)

  var navigationTitle: String {
    switch self {
    case .create: return "Adicionar produto"
    case .edit: return "Editar produto"
    }
  }

  var saveButtonTitle: String {
    switch self {
    case .create: return "Adicionar"
    case .edit: return "Salvar"
    }
  }

  fileprivate var existingId: Int64 {
    if case .edit(let draft) = self { return draft.id }
    return 0
  }

  fileprivate var seed: InventoryCountDraft {
    switch self {
    case .create:
      return InventoryCountDraft(
        name: "",
        quantity: 0,
        category: .alcoolico,
        volume: 600,
        volumeUnit: "ML",
        salePriceInCents: 0,
        storageCondition: .gelado
      )
    case .edit(let draft):
      return draft
    }
  }
}

struct InventoryDraftFormSheet: View {
  let mode: InventoryDraftFormMode
  let showCostField: Bool
  let onSave: (InventoryCountDraft) -> Void
  let onCancel: () -> Void

  @State private var name: String
  @State private var quantityText: String
  @State private var volumeText: String
  @State private var volumeUnit: String
  @State private var salePriceText: String
  @State private var costPriceText: String
  @State private var category: InventoryCategory
  @State private var storageCondition: StorageCondition
  @State private var validationError: String?

  init(
    mode: InventoryDraftFormMode,
    showCostField: Bool,
    onSave: @escaping (InventoryCountDraft) -> Void,
    onCancel: @escaping () -> Void
  ) {
    self.mode = mode
    self.showCostField = showCostField
    self.onSave = onSave
    self.onCancel = onCancel
    let seed = mode.seed
    _name = State(initialValue: seed.name)
    _quantityText = State(initialValue: InventoryDraftFormatting.quantity(seed.quantity))
    _volumeText = State(initialValue: InventoryDraftFormatting.quantity(seed.volume))
    _volumeUnit = State(initialValue: seed.volumeUnit.uppercased())
    _salePriceText = State(initialValue: InventoryDraftFormatting.currency(seed.salePriceInCents))
    _costPriceText = State(initialValue: InventoryDraftFormatting.currency(seed.costPriceInCents ?? 0))
    _category = State(initialValue: seed.category)
    _storageCondition = State(initialValue: seed.storageCondition)
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          TextField("Nome", text: $name)
            .themedListRowBackground()
          TextField("Quantidade", text: $quantityText)
            .keyboardType(.decimalPad)
            .themedListRowBackground()
        } header: {
          themedSectionHeader("Produto")
        }

        Section {
          TextField("Volume", text: $volumeText)
            .keyboardType(.decimalPad)
            .themedListRowBackground()
          Picker("Unidade", selection: $volumeUnit) {
            Text("ml").tag("ML")
            Text("gramas").tag("G")
          }
          .themedListRowBackground()
        } header: {
          themedSectionHeader("Volume")
        }

        Section {
          TextField("Valor de venda (R$)", text: $salePriceText)
            .keyboardType(.decimalPad)
            .themedListRowBackground()
          if showCostField {
            TextField("Valor de custo (R$)", text: $costPriceText)
              .keyboardType(.decimalPad)
              .themedListRowBackground()
          }
        } header: {
          themedSectionHeader("Preços")
        }

        Section {
          Picker("Categoria", selection: $category) {
            Text("Alcoólico").tag(InventoryCategory.alcoolico)
            Text("Não alcoólico").tag(InventoryCategory.naoAlcoolico)
          }
          .themedListRowBackground()
          Picker("Armazenamento", selection: $storageCondition) {
            Text("Gelado").tag(StorageCondition.gelado)
            Text("Natural").tag(StorageCondition.natural)
          }
          .themedListRowBackground()
        } header: {
          themedSectionHeader("Classificação")
        }

        if let validationError {
          Section {
            Text(validationError)
              .font(.footnote)
              .foregroundColor(.red)
              .themedListRowBackground()
          }
        }
      }
      .themedFormStyle()
      .navigationTitle(mode.navigationTitle)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Cancelar", action: onCancel)
        }
        ToolbarItem(placement: .confirmationAction) {
          Button(mode.saveButtonTitle, action: save)
            .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
      }
    }
  }

  private func save() {
    validationError = nil
    guard let quantity = InventoryDraftFormatting.parseDecimal(quantityText) else {
      validationError = "Quantidade deve ser numérica."
      return
    }
    guard let volume = InventoryDraftFormatting.parseDecimal(volumeText) else {
      validationError = "Volume deve ser numérico."
      return
    }
    guard let salePriceInCents = InventoryDraftFormatting.parseCurrency(salePriceText) else {
      validationError = "Valor de venda inválido."
      return
    }
    let costPriceInCents: Int64? = {
      guard showCostField else { return nil }
      let trimmed = costPriceText.trimmingCharacters(in: .whitespacesAndNewlines)
      guard !trimmed.isEmpty else { return nil }
      guard let cents = InventoryDraftFormatting.parseCurrency(trimmed) else {
        validationError = "Valor de custo inválido."
        return nil
      }
      return cents > 0 ? cents : nil
    }()
    if validationError != nil { return }

    let draft = InventoryCountDraft(
      id: mode.existingId,
      name: name.trimmingCharacters(in: .whitespacesAndNewlines),
      quantity: quantity,
      category: category,
      volume: volume,
      volumeUnit: volumeUnit,
      salePriceInCents: salePriceInCents,
      costPriceInCents: costPriceInCents,
      storageCondition: storageCondition
    )
    let errors = InventoryCountValidator.validate(draft)
    guard errors.isEmpty else {
      validationError = errors.joined(separator: "\n")
      return
    }
    onSave(draft)
  }
}

enum InventoryDraftFormatting {
  static func quantity(_ value: Double) -> String {
    if value == 0 { return "" }
    if value.truncatingRemainder(dividingBy: 1) == 0 {
      return String(format: "%.0f", value)
    }
    return String(value).replacingOccurrences(of: ".", with: ",")
  }

  static func currency(_ cents: Int64) -> String {
    guard cents > 0 else { return "" }
    let reais = Double(cents) / 100
    return String(format: "%.2f", reais).replacingOccurrences(of: ".", with: ",")
  }

  static func parseDecimal(_ text: String) -> Double? {
    let normalized = text
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .replacingOccurrences(of: ",", with: ".")
    guard !normalized.isEmpty else { return nil }
    return Double(normalized)
  }

  static func parseCurrency(_ text: String) -> Int64? {
    guard let value = parseDecimal(text) else { return nil }
    guard value >= 0 else { return nil }
    return Int64((value * 100).rounded())
  }

  static func summary(for draft: InventoryCountDraft) -> String {
    let qty = quantity(draft.quantity).isEmpty ? "0" : quantity(draft.quantity)
    let condition = draft.storageCondition == .gelado ? "Gelado" : "Natural"
    let unit = draft.volumeUnit.uppercased() == "G" ? "g" : "ml"
    return "Total: \(qty) · \(condition) · \(quantity(draft.volume)) \(unit)"
  }
}

#if os(iOS) && DEBUG
struct InventoryDraftFormSheet_Previews: PreviewProvider {
  static var previews: some View {
    InventoryDraftFormSheet(
      mode: .create,
      showCostField: true,
      onSave: { _ in },
      onCancel: {}
    )
    .environmentObject(AppTheme.shared)
  }
}
#endif
