#if os(iOS)
import SwiftUI
import DesignSystem
import Env
import Models
import Network
import Persistence
public struct LoginView: View {
  @EnvironmentObject private var session: AppSession
  @ObservedObject private var feedback = NetworkFeedback.shared

  private let credentialStore: CredentialStoreProtocol
  private let onLoginSuccess: () -> Void
  private let onRegisterTap: () -> Void

  @State private var username = ""
  @State private var password = ""
  @State private var rememberLogin = false
  @State private var pendingBiometricUnlock = false
  @State private var biometricInProgress = false
  @State private var requiresTwoFactor = false
  @State private var twoFactorCode = ""
  @State private var localError: String?

  public init(
    credentialStore: CredentialStoreProtocol = KeychainCredentialStore(),
    onLoginSuccess: @escaping () -> Void,
    onRegisterTap: @escaping () -> Void
  ) {
    self.credentialStore = credentialStore
    self.onLoginSuccess = onLoginSuccess
    self.onRegisterTap = onRegisterTap
  }

  public var body: some View {
    NavigationStack {
      Form {
        if pendingBiometricUnlock {
          Section {
            Text("Login salvo neste aparelho. Confirme sua biometria para preencher usuário e senha.")
              .font(.footnote)
            Button {
              Task { await unlockSavedLogin() }
            } label: {
              Label(
                biometricInProgress ? "Aguardando biometria..." : "Usar Face ID",
                systemImage: "faceid"
              )
            }
            .disabled(biometricInProgress)
          }
        }

        Section("Acesso") {
          TextField("Usuário ou email", text: $username)
            .disabled(pendingBiometricUnlock || biometricInProgress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
          SecureField("Senha", text: $password)
            .disabled(pendingBiometricUnlock || biometricInProgress)
          Toggle("Lembrar login (Face ID)", isOn: $rememberLogin)
        }

        if requiresTwoFactor {
          Section("Verificação") {
            TextField("Código de 6 dígitos", text: $twoFactorCode)
              .keyboardType(.numberPad)
              .onChange(of: twoFactorCode) { newValue in
                let filtered = String(newValue.filter(\.isNumber).prefix(6))
                if filtered != newValue { twoFactorCode = filtered }
              }
          }
        }

        if let localError {
          Section {
            Text(localError).foregroundStyle(.red).font(.footnote)
          }
        }

        Section {
          Button(requiresTwoFactor ? "Confirmar dispositivo" : "Entrar") {
            Task { await submit() }
          }
          .buttonStyle(PrimaryButtonStyle())
          .disabled(
            pendingBiometricUnlock
              || biometricInProgress
              || feedback.isLoading
              || (requiresTwoFactor && twoFactorCode.count != 6)
          )
          Button("Novo usuário", action: onRegisterTap)
        }
      }
      .navigationTitle("Checklist Boteco")
      .task { await restoreSavedLogin(autoUnlock: true) }
      .onReceive(session.$currentUser) { user in
        if user != nil { onLoginSuccess() }
      }
    }
  }

  private func restoreSavedLogin(autoUnlock: Bool) async {
    let metadata = credentialStore.loadMetadata()
    rememberLogin = metadata.remember
    if metadata.requiresBiometricUnlock {
      pendingBiometricUnlock = true
      if autoUnlock { await unlockSavedLogin() }
      return
    }
    username = metadata.username
    password = metadata.password
  }

  private func unlockSavedLogin() async {
    biometricInProgress = true
    localError = nil
    defer { biometricInProgress = false }
    do {
      let credentials = try await credentialStore.unlock()
      username = credentials.username
      password = credentials.password
      pendingBiometricUnlock = false
    } catch {
      pendingBiometricUnlock = true
      localError = error.localizedDescription
    }
  }

  private func submit() async {
    localError = nil
    do {
      if requiresTwoFactor {
        _ = try await session.verifyTwoFactor(code: twoFactorCode, password: password)
      } else {
        let result = try await session.loginRemote(email: username, password: password)
        if result.requiresTwoFactor {
          requiresTwoFactor = true
          twoFactorCode = result.developmentCode ?? ""
          localError = result.developmentCode.map { "Código de desenvolvimento: \($0)" }
            ?? result.deliveryHint
            ?? "Confirme este dispositivo"
          return
        }
      }
      try credentialStore.save(username: username, password: password, remember: rememberLogin)
      onLoginSuccess()
    } catch {
      if let api = error as? APIError {
        feedback.showError(api.errorDescription ?? AppErrorMapper.toUserMessage(error))
      } else {
        localError = error.localizedDescription
      }
    }
  }
}

public struct RegisterUserView: View {
  @Environment(\.dismiss) private var dismiss
  private let repository: ChecklistRepository

  @State private var firstName = ""
  @State private var lastName = ""
  @State private var email = ""
  @State private var password = ""
  @State private var confirmPassword = ""
  @State private var message: String?

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    Form {
      TextField("Nome", text: $firstName)
      TextField("Sobrenome", text: $lastName)
      TextField("Email", text: $email).textInputAutocapitalization(.never)
      SecureField("Senha", text: $password)
      SecureField("Confirmar senha", text: $confirmPassword)
      if let message {
        Text(message).foregroundStyle(message.contains("sucesso") ? .green : .red)
      }
      Button("Cadastrar") {
        do {
          guard password == confirmPassword else {
            message = "Senhas não conferem"
            return
          }
          let fullName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
          _ = try repository.insertUser(
            User(
              id: 0,
              name: fullName,
              email: email,
              password: password,
              area: .atendimento,
              permissionLevel: .user,
              allowedAreas: [.atendimento],
              createdAt: Date.nowMillis
            )
          )
          message = "Cadastro realizado com sucesso"
        } catch {
          message = error.localizedDescription
        }
      }
    }
    .navigationTitle("Novo usuário")
    .toolbar {
      ToolbarItem(placement: .cancellationAction) {
        Button("Voltar") { dismiss() }
      }
    }
  }
}

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
#endif