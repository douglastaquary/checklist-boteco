import SwiftUI
import Models
import Env
import Persistence

struct MainTabView: View {
  let context: MainTabContext
  let user: User
  @StateObject private var tabRouter = TabRouter()

  @State private var selectedTab: AppTab = .checklist

  private var tabs: [AppTab] {
    AppTab.available(for: user)
  }

  var body: some View {
    TabView(selection: $selectedTab) {
      ForEach(tabs) { tab in
        NavigationStack(path: tabRouter.binding(for: tab)) {
          tab.makeContentView(context: context, user: user, tabRouter: tabRouter)
            .navigationDestination(for: AppTabRoute.self) { route in
              route.destination(context: context)
            }
        }
        .tabItem { tab.label }
        .tag(tab)
      }
    }
    .onAppear {
      if !tabs.contains(selectedTab) { selectedTab = tabs.first ?? .checklist }
    }
  }
}

extension MainTabView {
  init(dependencies: AppDependenciesHolder, user: User, onLogout: @escaping () -> Void) {
    self.init(
      context: MainTabContext(
        repository: dependencies.repository,
        session: dependencies.session,
        syncController: dependencies.syncController,
        inventoryClient: dependencies.inventoryClient,
        authToken: dependencies.authToken,
        remoteUserId: dependencies.remoteUserId,
        deviceId: dependencies.deviceId,
        onLogout: onLogout
      ),
      user: user
    )
  }
}
