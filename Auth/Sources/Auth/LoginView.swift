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
  @State private var phase: LoginPhase = .credentials
  @State private var biometricInProgress = false
  @State private var twoFactorCode = ""
  @State private var twoFactorHint: String?
  @State private var localError: String?

  @FocusState private var focusedField: Field?

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
        if phase == .biometricUnlock {
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
            .disabled(biometricInProgress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .focused($focusedField, equals: .username)
          SecureField("Senha", text: $password)
            .disabled(phase == .biometricUnlock || biometricInProgress)
            .focused($focusedField, equals: .password)
          Toggle(rememberToggleTitle, isOn: $rememberLogin)
        }

        if phase == .twoFactor {
          Section("Verificação") {
            if let twoFactorHint {
              Text(twoFactorHint).font(.footnote).foregroundStyle(.secondary)
            }
            TextField("Código de 6 dígitos", text: $twoFactorCode)
              .keyboardType(.numberPad)
              .focused($focusedField, equals: .twoFactorCode)
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
          Button(phase == .twoFactor ? "Confirmar dispositivo" : "Entrar") {
            Task { await submit() }
          }
          .buttonStyle(PrimaryButtonStyle())
          .disabled(
            phase == .biometricUnlock
              || biometricInProgress
              || feedback.isLoading
              || (phase == .twoFactor && twoFactorCode.count != 6)
          )
          Button("Novo usuário", action: onRegisterTap)
        }
      }
      .navigationTitle("Checklist Boteco")
      .task { await restoreSavedLogin(autoUnlock: true) }
      .onChange(of: phase) { newPhase in
        switch newPhase {
        case .twoFactor: focusedField = .twoFactorCode
        case .credentials: focusedField = .username
        case .biometricUnlock: focusedField = nil
        }
      }
    }
  }

  private var rememberToggleTitle: String {
    #if targetEnvironment(simulator)
    "Lembrar login"
    #else
    #if DEBUG
    "Lembrar login"
    #else
    "Lembrar login (Face ID)"
    #endif
    #endif
  }

  private func restoreSavedLogin(autoUnlock: Bool) async {
    let metadata = credentialStore.loadMetadata()
    rememberLogin = metadata.remember
    username = metadata.username
    password = metadata.password
    phase = .credentials
    twoFactorCode = ""
    twoFactorHint = nil
    if metadata.requiresBiometricUnlock {
      password = ""
      phase = .biometricUnlock
      if autoUnlock { await unlockSavedLogin() }
    }
  }

  private func unlockSavedLogin() async {
    biometricInProgress = true
    localError = nil
    defer { biometricInProgress = false }
    do {
      let credentials = try await credentialStore.unlock()
      username = credentials.username
      password = credentials.password
      phase = .credentials
    } catch {
      phase = .biometricUnlock
      localError = error.localizedDescription
    }
  }

  private func submit() async {
    localError = nil
    do {
      if phase == .twoFactor {
        _ = try await session.verifyTwoFactor(code: twoFactorCode, password: password)
      } else {
        let result = try await session.loginRemote(email: username, password: password)
        if result.requiresTwoFactor {
          phase = .twoFactor
          twoFactorCode = result.developmentCode ?? ""
          twoFactorHint = result.developmentCode.map { "Código de desenvolvimento: \($0)" }
            ?? result.deliveryHint
            ?? "Confirme este dispositivo"
          return
        }
      }
      do {
        try credentialStore.save(username: username, password: password, remember: rememberLogin)
      } catch {
        rememberLogin = false
        localError = "Login concluído, mas não foi salvo neste aparelho: \(error.localizedDescription)"
      }
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

private enum LoginPhase: Equatable {
  case credentials
  case biometricUnlock
  case twoFactor
}

private enum Field: Hashable {
  case username
  case password
  case twoFactorCode
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
