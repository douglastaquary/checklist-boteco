import SwiftUI
import Models
import Env
import Persistence
import DesignSystem

struct MainTabView: View {
  let context: MainTabContext
  let user: User
  @StateObject private var tabRouter = TabRouter()
  @StateObject private var tabBarVisibility = TabBarVisibilityController()

  @State private var selectedTab: AppTab = .checklist
  @State private var loadedTabs: Set<AppTab> = [.checklist]

  private var tabs: [AppTab] {
    AppTab.available(for: user)
  }

  var body: some View {
    TabView(selection: $selectedTab) {
      ForEach(tabs) { tab in
        Group {
          if loadedTabs.contains(tab) {
            navigationStack(for: tab)
          } else {
            Color.clear
          }
        }
        .tabItem { tab.label }
        .tag(tab)
      }
    }
    .tint(Color(red: 23 / 255, green: 23 / 255, blue: 23 / 255))
    .environmentObject(tabBarVisibility)
    .background(TabBarVisibilityBridge(isVisible: tabBarVisibility.isVisible))
    .onAppear {
      NativeTabBarAppearance.apply()
      if !tabs.contains(selectedTab) { selectedTab = tabs.first ?? .checklist }
      loadedTabs.insert(selectedTab)
    }
    .onChange(of: selectedTab) { tab in
      loadedTabs.insert(tab)
      tabBarVisibility.resetScrollTracking()
    }
    .onOpenURL { url in
      guard let link = AppDeepLink.parse(url) else { return }
      AppDeepLinkHandler.apply(
        link,
        user: user,
        selectedTab: &selectedTab,
        tabRouter: tabRouter
      )
      loadedTabs.insert(selectedTab)
      tabBarVisibility.resetScrollTracking()
    }
  }

  @ViewBuilder
  private func navigationStack(for tab: AppTab) -> some View {
    NavigationStack(path: tabRouter.binding(for: tab)) {
      tab.makeContentView(context: context, user: user, tabRouter: tabRouter)
        .navigationDestination(for: AppTabRoute.self) { route in
          route.destination(context: context)
        }
    }
  }
}

extension MainTabView {
  init(dependencies: AppDependenciesHolder, user: User, onLogout: @escaping () -> Void) {
    self.init(
      context: MainTabContext(
        repository: dependencies.repository,
        syncController: dependencies.syncController,
        inventoryClient: dependencies.inventoryClient,
        purchaseClient: dependencies.purchaseClient,
        workClockClient: dependencies.workClockClient,
        userClient: dependencies.userClient,
        dashboardClient: dependencies.dashboardClient,
        aiChatClient: dependencies.aiChatClient,
        authToken: dependencies.authToken,
        remoteUserId: dependencies.remoteUserId,
        deviceId: dependencies.deviceId,
        onLogout: onLogout
      ),
      user: user
    )
  }
}
