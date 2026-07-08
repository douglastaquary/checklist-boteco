import SwiftUI
import Models
import Env
import Persistence
import DesignSystem

struct MainTabView: View {
  let context: MainTabContext
  let user: User
  @StateObject private var tabRouter = TabRouter()

  @State private var selectedTab: AppTab = .checklist
  @State private var loadedTabs: Set<AppTab> = [.checklist]
  @State private var isMorePresented = false

  private var tabs: [AppTab] {
    AppTab.available(for: user)
  }

  private var primaryTabs: [AppTab] {
    tabs.count <= 4 ? tabs : Array(tabs.prefix(3))
  }

  private var overflowTabs: [AppTab] {
    tabs.count <= 4 ? [] : Array(tabs.dropFirst(3))
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
        .toolbar(.hidden, for: .tabBar)
        .tabItem { tab.label }
        .tag(tab)
      }
    }
    .toolbar(.hidden, for: .tabBar)
    .safeAreaInset(edge: .bottom, spacing: 0) {
      BecoTabBar(
        items: primaryTabs.map {
          BecoTabBarItem(id: $0, title: $0.title, systemImage: $0.iconName)
        },
        selected: selectedTab,
        hasOverflow: !overflowTabs.isEmpty,
        overflowSelected: overflowTabs.contains(selectedTab),
        onSelect: select,
        onMore: { isMorePresented = true }
      )
    }
    .sheet(isPresented: $isMorePresented) {
      NavigationStack {
        List(overflowTabs) { tab in
          Button {
            select(tab)
            isMorePresented = false
          } label: {
            Label(tab.title, systemImage: tab.iconName)
              .foregroundStyle(BecoTokens.ColorToken.ink)
          }
        }
        .navigationTitle("Mais módulos")
        .toolbar {
          ToolbarItem(placement: .cancellationAction) {
            Button("Fechar") { isMorePresented = false }
          }
        }
      }
      .presentationDetents([.medium])
    }
    .tint(AppColors.primary)
    .onAppear {
      if !tabs.contains(selectedTab) { selectedTab = tabs.first ?? .checklist }
      loadedTabs.insert(selectedTab)
    }
    .onChange(of: selectedTab) { tab in
      loadedTabs.insert(tab)
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
    }
  }

  private func select(_ tab: AppTab) {
    selectedTab = tab
    loadedTabs.insert(tab)
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
        userClient: dependencies.userClient,
        dashboardClient: dependencies.dashboardClient,
        authToken: dependencies.authToken,
        remoteUserId: dependencies.remoteUserId,
        deviceId: dependencies.deviceId,
        onLogout: onLogout
      ),
      user: user
    )
  }
}
