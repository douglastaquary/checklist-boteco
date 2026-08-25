import SwiftUI
import Models
import Env
import Persistence
import DesignSystem
import AdminFeatures

private enum MoreAdminSheet: String, Identifiable {
  case activities
  case permissions

  var id: String { rawValue }
}

struct MainTabView: View {
  let context: MainTabContext
  let user: User
  @StateObject private var tabRouter = TabRouter()
  @StateObject private var tabBarVisibility = TabBarVisibilityController()

  @State private var selectedTab: AppTab
  @State private var loadedTabs: Set<AppTab>
  @State private var moreAdminSheet: MoreAdminSheet?

  private var layout: AppTabLayout {
    AppTab.layout(for: user)
  }

  private var tabs: [AppTab] {
    layout.tabBarItems
  }

  init(context: MainTabContext, user: User) {
    self.context = context
    self.user = user
    let layout = AppTab.layout(for: user)
    let start = Self.screenshotTab(from: layout) ?? layout.startTab
    _selectedTab = State(initialValue: start)
    _loadedTabs = State(initialValue: [start])
  }

  /// Optional tab override for README screenshots (`BecoScreenshotTab` in UserDefaults).
  private static func screenshotTab(from layout: AppTabLayout) -> AppTab? {
    let raw = UserDefaults.standard.string(forKey: "BecoScreenshotTab")
    guard let raw, let tab = AppTab(rawValue: raw), layout.tabBarItems.contains(tab) else {
      return nil
    }
    UserDefaults.standard.removeObject(forKey: "BecoScreenshotTab")
    return tab
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
    .background(
      TabBarReselectObserver { index in
        guard tabs.indices.contains(index), tabs[index] == .more else { return }
        tabRouter.reset(.more)
      }
    )
    .onAppear {
      NativeTabBarAppearance.apply()
      if !tabs.contains(selectedTab) {
        selectedTab = layout.startTab
      }
      loadedTabs.insert(selectedTab)
    }
    .onChange(of: selectedTab) { tab in
      loadedTabs.insert(tab)
      tabBarVisibility.resetScrollTracking()
      if tab == .more {
        tabRouter.reset(.more)
      }
    }
    .onOpenURL { url in
      guard let link = AppDeepLink.parse(url) else { return }
      AppDeepLinkHandler.apply(
        link,
        user: user,
        layout: layout,
        selectedTab: &selectedTab,
        tabRouter: tabRouter
      )
      loadedTabs.insert(selectedTab)
      tabBarVisibility.resetScrollTracking()
    }
    .sheet(item: $moreAdminSheet) { sheet in
      moreAdminCover(sheet)
        .environmentObject(tabBarVisibility)
        .becoCodexSheetChrome()
    }
  }

  @ViewBuilder
  private func navigationStack(for tab: AppTab) -> some View {
    NavigationStack(path: tabRouter.binding(for: tab)) {
      rootContent(for: tab)
        .navigationDestination(for: AppTabRoute.self) { route in
          route.destination(
            context: context,
            user: user,
            tabRouter: tabRouter,
            hostTab: tab
          )
        }
    }
  }

  @ViewBuilder
  private func rootContent(for tab: AppTab) -> some View {
    if tab == .more {
      MoreHubView(modules: layout.overflow) { module in
        openOverflowModule(module)
      }
    } else {
      tab.makeContentView(
        context: context,
        user: user,
        tabRouter: tabRouter,
        hostTab: tab
      )
    }
  }

  private func openOverflowModule(_ module: AppTab) {
    switch module {
    case .activities:
      moreAdminSheet = .activities
    case .permissions:
      moreAdminSheet = .permissions
    default:
      loadedTabs.insert(module)
      tabRouter.push(.overflowModule(module), on: .more)
    }
  }

  @ViewBuilder
  private func moreAdminCover(_ sheet: MoreAdminSheet) -> some View {
    switch sheet {
    case .activities:
      ActivitiesManagementView(
        repository: context.repository,
        embeddedInCodexSheet: true,
        onDismissSheet: { moreAdminSheet = nil }
      )
    case .permissions:
      PermissionManagementView(
        repository: context.repository,
        userClient: context.userClient,
        authToken: context.authToken,
        embeddedInCodexSheet: true,
        onDismissSheet: { moreAdminSheet = nil }
      )
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
        remoteSessionGeneration: dependencies.remoteSessionGeneration,
        remoteUserId: dependencies.remoteUserId,
        deviceId: dependencies.deviceId,
        onLogout: onLogout
      ),
      user: user
    )
  }
}
