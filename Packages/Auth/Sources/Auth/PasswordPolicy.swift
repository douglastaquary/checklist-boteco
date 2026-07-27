import Foundation

struct PasswordRule: Identifiable, Equatable {
  let id: String
  let message: String
  let satisfied: Bool
}

enum PasswordPolicy {
  static func rules(for password: String) -> [PasswordRule] {
    [
      PasswordRule(id: "length", message: "Ao menos 6 caracteres", satisfied: password.count >= 6),
      PasswordRule(id: "uppercase", message: "Ao menos uma letra maiúscula", satisfied: password.contains { $0.isUppercase }),
      PasswordRule(id: "number", message: "Ao menos um número", satisfied: password.contains { $0.isNumber }),
      PasswordRule(id: "special", message: "Ao menos um caractere especial", satisfied: password.contains { !$0.isLetter && !$0.isNumber }),
    ]
  }

  static func isValid(_ password: String) -> Bool {
    rules(for: password).allSatisfy(\.satisfied)
  }
}
