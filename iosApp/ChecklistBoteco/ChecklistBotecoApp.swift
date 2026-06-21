import SwiftUI
import Auth
import DesignSystem
import Env
import Network

@main
struct ChecklistBotecoApp: App {
  @StateObject private var launch = AppLaunchState()

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
final class AppDependenciesHolder: ObservableObject {
  let repository: ChecklistRepository
  let session: AppSession
  let syncController: SyncController
  let apiClient: APIClient?
  let inventoryClient: InventoryClient?
  let deviceId: String

  init(deps: AppDependencies) {
    repository = deps.repository
    session = deps.session
    syncController = deps.syncController
    apiClient = deps.apiClient
    inventoryClient = deps.inventoryClient
    deviceId = deps.deviceId
  }
}

private enum AuthScreen: Equatable {
  case login
  case register
  case main
}

struct RootView: View {
  @ObservedObject var holder: AppDependenciesHolder
  @ObservedObject private var session: AppSession
  @State private var authScreen: AuthScreen = .login

  init(holder: AppDependenciesHolder) {
    self.holder = holder
    _session = ObservedObject(wrappedValue: holder.session)
  }

  var body: some View {
    ZStack {
      Group {
        switch authScreen {
        case .main where session.currentUser != nil:
          MainTabView(dependencies: holder, user: session.currentUser!) { logout() }
        case .register:
          NavigationStack {
            RegisterUserView(repository: holder.repository)
              .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                  Button("Voltar") { authScreen = .login }
                }
              }
          }
        default:
          LoginView(
            onLoginSuccess: { authScreen = .main },
            onRegisterTap: { authScreen = .register }
          )
        }
      }
      GlobalFeedbackOverlay()
    }
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

extension AppDependenciesHolder {
  var authToken: String? { session.authToken }
  var remoteUserId: String? { session.remoteUserId }
}
