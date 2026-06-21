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
        .environmentObject(holder.session)
        .environmentObject(NetworkFeedback.shared)
        .onAppear {
          #if os(iOS)
          BackgroundSyncScheduler.register(syncController: holder.syncController)
          BackgroundSyncScheduler.schedule()
          #endif
        }
    case .failed(let message):
      VStack(spacing: 16) {
        Text("Não foi possível iniciar o app")
          .font(.headline)
        Text(message)
          .font(.footnote)
          .multilineTextAlignment(.center)
          .foregroundStyle(.secondary)
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

struct RootView: View {
  @ObservedObject var holder: AppDependenciesHolder
  @EnvironmentObject private var session: AppSession
  @State private var isAuthenticated = false
  @State private var showRegister = false

  var body: some View {
    ZStack {
      Group {
        if isAuthenticated, session.currentUser != nil {
          MainTabView(dependencies: holder) { logout() }
        } else if showRegister {
          NavigationStack {
            RegisterUserView(repository: holder.repository)
              .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                  Button("Voltar") { showRegister = false }
                }
              }
          }
        } else {
          LoginView(
            onLoginSuccess: { isAuthenticated = true },
            onRegisterTap: { showRegister = true }
          )
        }
      }
      GlobalFeedbackOverlay()
    }
    .task { await holder.syncController.syncOnce() }
  }

  private func logout() {
    try? session.logout()
    isAuthenticated = false
    showRegister = false
  }
}

import Persistence
import InventoryFeature

extension AppDependenciesHolder {
  var authToken: String? { session.authToken }
}

extension MainTabView {
  init(dependencies: AppDependenciesHolder, onLogout: @escaping () -> Void) {
    self.init(
      repository: dependencies.repository,
      session: dependencies.session,
      syncController: dependencies.syncController,
      inventoryClient: dependencies.inventoryClient,
      authToken: dependencies.authToken,
      deviceId: dependencies.deviceId,
      onLogout: onLogout
    )
  }
}
