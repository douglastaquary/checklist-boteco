import DesignSystem
import Env
import SwiftUI

public struct ChangePasswordView: View {
  @EnvironmentObject private var session: AppSession
  @State private var newPassword = ""
  @State private var confirmation = ""
  @State private var localError: String?
  @State private var isSubmitting = false

  private let onPasswordChanged: () -> Void

  public init(onPasswordChanged: @escaping () -> Void) {
    self.onPasswordChanged = onPasswordChanged
  }

  public var body: some View {
    VStack(spacing: 20) {
      Spacer()

      VStack(spacing: 8) {
        Text("Crie sua nova senha")
          .font(.system(size: 28, weight: .bold))
          .foregroundStyle(Color(.label))
          .multilineTextAlignment(.center)
        Text("Este é o primeiro acesso ou sua senha foi resetada.")
          .font(.subheadline)
          .foregroundStyle(Color(.secondaryLabel))
          .multilineTextAlignment(.center)
      }

      VStack(alignment: .leading, spacing: 10) {
        SecureField("Nova senha", text: $newPassword)
          .textContentType(.newPassword)
          .padding()
          .background(Color(.secondarySystemBackground))
          .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

        ForEach(PasswordPolicy.rules(for: newPassword)) { rule in
          Text("\(rule.satisfied ? "✓" : "•") \(rule.message)")
            .font(.caption)
            .foregroundStyle(rule.satisfied ? AppColors.primary : Color(.systemRed))
        }

        if PasswordPolicy.isValid(newPassword) {
          Text("✓ Senha ok")
            .font(.caption)
            .foregroundStyle(AppColors.primary)
        }
      }

      VStack(alignment: .leading, spacing: 8) {
        SecureField("Confirmar senha", text: $confirmation)
          .textContentType(.newPassword)
          .padding()
          .background(Color(.secondarySystemBackground))
          .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

        if !confirmation.isEmpty {
          Text(confirmation == newPassword ? "✓ Confirmação ok" : "A confirmação deve ser igual à nova senha.")
            .font(.caption)
            .foregroundStyle(confirmation == newPassword ? AppColors.primary : Color(.systemRed))
        }
      }

      if let localError {
        Text(localError)
          .font(.caption)
          .foregroundStyle(Color(.systemRed))
          .multilineTextAlignment(.center)
      }

      Button(action: submit) {
        Text(isSubmitting ? "Salvando..." : "Salvar nova senha")
          .font(.headline)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 16)
      }
      .buttonStyle(.plain)
      .foregroundStyle(.white)
      .background(AppColors.primary)
      .clipShape(Capsule())
      .disabled(!canSubmit || isSubmitting)
      .opacity(canSubmit && !isSubmitting ? 1 : 0.45)

      Spacer()
    }
    .padding(.horizontal, 24)
    .background(Color(.systemBackground).ignoresSafeArea())
  }

  private var canSubmit: Bool {
    PasswordPolicy.isValid(newPassword) && confirmation == newPassword
  }

  private func submit() {
    guard canSubmit else { return }
    localError = nil
    isSubmitting = true
    Task {
      do {
        _ = try await session.changeRequiredPassword(newPassword: newPassword)
        await MainActor.run {
          isSubmitting = false
          onPasswordChanged()
        }
      } catch {
        await MainActor.run {
          isSubmitting = false
          localError = error.localizedDescription
        }
      }
    }
  }
}
