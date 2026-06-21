import SwiftUI
import Models

/// Mantém `NavigationPath` independente por tab (deep links e reset futuro).
@MainActor
final class TabRouter: ObservableObject {
  @Published private var paths: [AppTab: NavigationPath] = [:]

  func binding(for tab: AppTab) -> Binding<NavigationPath> {
    Binding(
      get: { self.paths[tab] ?? NavigationPath() },
      set: { self.paths[tab] = $0 }
    )
  }

  func push(_ route: AppTabRoute, on tab: AppTab) {
    var path = paths[tab] ?? NavigationPath()
    path.append(route)
    paths[tab] = path
  }

  func reset(_ tab: AppTab) {
    paths[tab] = NavigationPath()
  }

  func resetAll() {
    paths.removeAll()
  }
}
