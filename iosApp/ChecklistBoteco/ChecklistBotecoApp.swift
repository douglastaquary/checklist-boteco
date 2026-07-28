import SwiftUI
import Auth
import DesignSystem
import Env
import Network

@main
struct ChecklistBotecoApp: App {
  @StateObject private var launch: AppLaunchState

  init() {
    let launch = AppLaunchState()
    _launch = StateObject(wrappedValue: launch)

    if case .ready(let holder) = launch.status {
      BackgroundSyncScheduler.register(syncController: holder.syncController)
    }
  }

  var body: some Scene {
    WindowGroup {
      AppLaunchGate(launch: launch)
    }
  }
}

@MainActor
final class AppLaunchState: ObservableObject {
  enum Status {
    case ready(AppDependenciesHolder)
    case failed(String)
  }

  let status: Status

  init() {
    do {
      let deps = try AppDependencies()
      status = .ready(AppDependenciesHolder(deps: deps))
    } catch {
      status = .failed(error.localizedDescription)
    }
  }
}

struct AppLaunchGate: View {
  @ObservedObject var launch: AppLaunchState

  var body: some View {
    switch launch.status {
    case .ready(let holder):
      RootView(holder: holder)
        .withAppDependencyGraph(
          session: holder.session,
          syncController: holder.syncController
        )
        .environmentObject(AppTheme.shared)
    case .failed(let message):
      VStack(spacing: 16) {
        Text("Não foi possível iniciar o app")
          .font(.headline)
        Text(message)
          .font(.footnote)
          .multilineTextAlignment(.center)
          .foregroundColor(.secondary)
      }
      .padding()
    }
  }
}

@MainActor
final class AppDependenciesHolder {
  let repository: ChecklistRepository
  let session: AppSession
  let syncController: SyncController
  let apiClient: APIClient?
  let userClient: UserClient?
  let workClockClient: WorkClockClient?
  let dashboardClient: DashboardClient?
  let inventoryClient: InventoryClient?
  let purchaseClient: PurchaseClient?
  let aiChatClient: AIChatClient?
  let deviceId: String

  init(deps: AppDependencies) {
    repository = deps.repository
    session = deps.session
    syncController = deps.syncController
    apiClient = deps.apiClient
    userClient = deps.userClient
    workClockClient = deps.workClockClient
    dashboardClient = deps.dashboardClient
    inventoryClient = deps.inventoryClient
    purchaseClient = deps.purchaseClient
    aiChatClient = deps.aiChatClient
    deviceId = deps.deviceId
  }
}

private enum AuthScreen: Equatable {
  case login
  case register
  case changePassword
  case main
}

struct RootView: View {
  let holder: AppDependenciesHolder
  @ObservedObject private var session: AppSession
  @ObservedObject private var sessionExpiredCenter = SessionExpiredCenter.shared
  @State private var authScreen: AuthScreen = .login
  @State private var sessionExpiredMessage: String?

  init(holder: AppDependenciesHolder) {
    self.holder = holder
    _session = ObservedObject(wrappedValue: holder.session)
  }

  var body: some View {
    ZStack {
      Group {
        switch authScreen {
        case .changePassword:
          ChangePasswordView {
            sessionExpiredMessage = nil
            authScreen = .main
          }
        case .main where session.currentUser != nil:
          MainTabView(dependencies: holder, user: session.currentUser!) { logout() }
        case .register:
          NavigationStack {
            RegisterUserView(
              repository: holder.repository,
              userClient: holder.userClient,
              authToken: session.authToken
            )
              .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                  Button("Voltar") { authScreen = .login }
                }
              }
          }
        default:
          LoginView(
            onLoginSuccess: {
              sessionExpiredMessage = nil
              authScreen = session.currentUser?.mustChangePassword == true ? .changePassword : .main
            },
            onRegisterTap: { authScreen = .register },
            sessionExpiredMessage: sessionExpiredMessage
          )
        }
      }
      GlobalFeedbackOverlay()
    }
    .task {
      await session.restorePersistedSessionIfPossible()
      if session.isLoggedIn {
        authScreen = session.currentUser?.mustChangePassword == true ? .changePassword : .main
      }
    }
    .onReceive(sessionExpiredCenter.$latestEvent) { event in
      guard let event else { return }
      sessionExpiredMessage = event.reason
      authScreen = .login
      try? session.invalidateSession(reason: event.reason)
      sessionExpiredCenter.reset()
    }
    .tint(AppColors.primary)
    .preferredColorScheme(.light)
  }

  private func logout() {
    authScreen = .login
    Task { @MainActor in
      try? session.logout()
    }
  }
}

import Persistence
import InventoryFeature
import PurchasesFeature

extension AppDependenciesHolder {
  var authToken: String? { session.authToken }
  var remoteUserId: String? { session.remoteUserId }
}
