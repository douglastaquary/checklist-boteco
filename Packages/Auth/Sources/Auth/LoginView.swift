#if os(iOS)
import SwiftUI
import DesignSystem
import Env
import Models
import Network
import Persistence

public enum LoginPhase: Equatable {
  case credentials
  case biometricUnlock
  case twoFactor
}

public struct LoginView: View {
  @EnvironmentObject private var session: AppSession
  @ObservedObject private var feedback = NetworkFeedback.shared

  private let credentialStore: CredentialStoreProtocol
  private let onLoginSuccess: () -> Void
  private let onRegisterTap: () -> Void
  private let sessionExpiredMessage: String?
  private let skipsRestore: Bool

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
    onRegisterTap: @escaping () -> Void,
    sessionExpiredMessage: String? = nil,
    debugPhase: LoginPhase? = nil,
    debugTwoFactorHint: String? = nil,
    debugUsername: String = "",
    debugLocalError: String? = nil,
    skipsRestore: Bool = false
  ) {
    self.credentialStore = credentialStore
    self.onLoginSuccess = onLoginSuccess
    self.onRegisterTap = onRegisterTap
    self.sessionExpiredMessage = sessionExpiredMessage
    self.skipsRestore = skipsRestore || debugPhase != nil
    _phase = State(initialValue: debugPhase ?? .credentials)
    _twoFactorHint = State(initialValue: debugTwoFactorHint)
    _username = State(initialValue: debugUsername)
    _localError = State(initialValue: sessionExpiredMessage ?? debugLocalError)
  }

  public var body: some View {
    NavigationStack {
      Form {
        Section {
          VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
            Text("Beco da Praia")
              .font(.subheadline.weight(.semibold))
              .foregroundStyle(BecoTokens.ColorToken.brand)
            Text("Bem-vindo")
              .font(.largeTitle.bold())
              .foregroundStyle(BecoTokens.ColorToken.ink)
            Text("Acesse sua rotina operacional")
              .foregroundStyle(BecoTokens.ColorToken.muted)
          }
          .padding(.vertical, BecoTokens.Spacing.sm)
          .themedListRowBackground()
        }
        .listRowSeparator(.hidden)

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
          BecoButton(
            phase == .twoFactor ? "Confirmar dispositivo" : "Entrar",
            isLoading: feedback.isLoading
          ) {
            Task { await submit() }
          }
          .disabled(
            phase == .biometricUnlock
              || biometricInProgress
              || feedback.isLoading
              || (phase == .twoFactor && twoFactorCode.count != 6)
          )
          Button("Novo usuário", action: onRegisterTap)
        }
      }
      .themedFormStyle()
      .navigationTitle("")
      .navigationBarHidden(true)
      .task {
        guard !skipsRestore else { return }
        await restoreSavedLogin(autoUnlock: true)
      }
      .onChange(of: phase) { newPhase in
        switch newPhase {
        case .twoFactor: focusedField = .twoFactorCode
        case .credentials: focusedField = .username
        case .biometricUnlock: focusedField = nil
        }
      }
      .onChange(of: sessionExpiredMessage) { message in
        guard let message, !message.isEmpty else { return }
        returnToCredentialsAfterDeviceFailure(message: message)
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
      if session.currentUser?.mustChangePassword != true {
        do {
          try credentialStore.save(username: username, password: password, remember: rememberLogin)
        } catch {
          rememberLogin = false
          localError = "Login concluído, mas não foi salvo neste aparelho: \(error.localizedDescription)"
        }
      }
      onLoginSuccess()
    } catch {
      let message: String
      if let api = error as? APIError {
        message = api.errorDescription ?? AppErrorMapper.toUserMessage(error)
        feedback.showError(message)
      } else {
        message = error.localizedDescription
      }
      if phase == .twoFactor {
        returnToCredentialsAfterDeviceFailure(message: message)
      } else {
        localError = message
      }
    }
  }

  /// After a failed device confirmation, hide the 2FA step and return to password login.
  private func returnToCredentialsAfterDeviceFailure(message: String) {
    session.clearPendingDeviceVerification()
    phase = .credentials
    twoFactorCode = ""
    twoFactorHint = nil
    localError = message
    focusedField = .password
  }
}

private enum Field: Hashable {
  case username
  case password
  case twoFactorCode
}

public struct RegisterUserView: View {
  @Environment(\.dismiss) private var dismiss
  private let repository: ChecklistRepository
  private let userClient: UserClient?
  private let authToken: String?

  @State private var firstName = ""
  @State private var lastName = ""
  @State private var email = ""
  @State private var password = ""
  @State private var confirmPassword = ""
  @State private var message: String?

  public init(
    repository: ChecklistRepository,
    userClient: UserClient? = nil,
    authToken: String? = nil
  ) {
    self.repository = repository
    self.userClient = userClient
    self.authToken = authToken
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
        Task { await register() }
      }
    }
    .navigationTitle("Novo usuário")
    .toolbar {
      ToolbarItem(placement: .cancellationAction) {
        Button("Voltar") { dismiss() }
      }
    }
  }

  private func register() async {
    guard PasswordPolicy.isValid(password) else {
      message = "A senha deve ter ao menos 6 caracteres, maiúscula, número e caractere especial"
      return
    }
    guard password == confirmPassword else {
      message = "Senhas não conferem"
      return
    }
    let fullName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
    if let userClient, let authToken, !authToken.isEmpty {
      do {
        let remote = try await userClient.createUser(
          token: authToken,
          name: fullName,
          email: email,
          password: password,
          workSector: .atendimento
        )
        try repository.upsertRemoteUser(remote)
        message = "Cadastro realizado com sucesso"
      } catch {
        message = error.localizedDescription
      }
      return
    }
    if userClient != nil {
      message = "Com a API ativa, solicite cadastro ao administrador."
      return
    }
    do {
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

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
#endif
