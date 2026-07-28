import SwiftUI
import UIKit

@MainActor
public final class TabBarVisibilityController: ObservableObject {
  @Published public private(set) var isVisible = true

  private var lastOffset: CGFloat?
  private let threshold: CGFloat = 10

  public init() {}

  public func show(animated: Bool = true) {
    setVisible(true, animated: animated)
  }

  public func hide(animated: Bool = true) {
    setVisible(false, animated: animated)
  }

  public func update(fromScrollOffset offset: CGFloat) {
    defer { lastOffset = offset }
    guard let lastOffset else { return }
    let delta = offset - lastOffset
    if delta < -threshold {
      hide()
    } else if delta > threshold {
      show()
    }
  }

  public func resetScrollTracking() {
    lastOffset = nil
    show(animated: false)
  }

  private func setVisible(_ visible: Bool, animated: Bool) {
    guard isVisible != visible else { return }
    if animated {
      withAnimation(.easeInOut(duration: 0.25)) {
        isVisible = visible
      }
    } else {
      isVisible = visible
    }
  }
}

public enum NativeTabBarAppearance {
  public static func apply() {
    let ink = UIColor(red: 23 / 255, green: 23 / 255, blue: 23 / 255, alpha: 1)
    let muted = UIColor(red: 111 / 255, green: 111 / 255, blue: 115 / 255, alpha: 1)

    let appearance = UITabBarAppearance()
    appearance.configureWithDefaultBackground()

    let item = UITabBarItemAppearance()
    item.normal.iconColor = muted
    item.normal.titleTextAttributes = [.foregroundColor: muted]
    item.selected.iconColor = ink
    item.selected.titleTextAttributes = [.foregroundColor: ink]

    appearance.stackedLayoutAppearance = item
    appearance.inlineLayoutAppearance = item
    appearance.compactInlineLayoutAppearance = item

    let tabBar = UITabBar.appearance()
    tabBar.standardAppearance = appearance
    tabBar.scrollEdgeAppearance = appearance
    tabBar.tintColor = ink
    tabBar.unselectedItemTintColor = muted
    tabBar.isTranslucent = true
  }
}

/// Syncs `TabBarVisibilityController.isVisible` onto the system UITabBar.
public struct TabBarVisibilityBridge: UIViewControllerRepresentable {
  public var isVisible: Bool

  public init(isVisible: Bool) {
    self.isVisible = isVisible
  }

  public func makeUIViewController(context: Context) -> Controller {
    Controller()
  }

  public func updateUIViewController(_ uiViewController: Controller, context: Context) {
    uiViewController.setTabBarHidden(!isVisible, animated: true)
  }

  public final class Controller: UIViewController {
    private var lastHidden: Bool?

    public func setTabBarHidden(_ hidden: Bool, animated: Bool) {
      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        guard let tabBar = self.tabBarController?.tabBar else { return }
        if self.lastHidden == hidden, tabBar.isHidden == hidden { return }
        self.lastHidden = hidden

        let updates = {
          tabBar.alpha = hidden ? 0 : 1
          tabBar.isUserInteractionEnabled = !hidden
        }

        if animated {
          if hidden {
            UIView.animate(withDuration: 0.25, animations: updates) { _ in
              tabBar.isHidden = true
            }
          } else {
            tabBar.isHidden = false
            tabBar.alpha = 0
            UIView.animate(withDuration: 0.25, animations: updates)
          }
        } else {
          tabBar.isHidden = hidden
          tabBar.alpha = hidden ? 0 : 1
          tabBar.isUserInteractionEnabled = !hidden
        }
      }
    }
  }
}

private enum TabBarScrollSpace {
  static let name = "becoTabBarScroll"
}

private struct TabBarScrollOffsetKey: PreferenceKey {
  static var defaultValue: CGFloat = 0
  static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
    value = nextValue()
  }
}

private struct TracksTabBarOnScrollModifier: ViewModifier {
  @EnvironmentObject private var tabBarVisibility: TabBarVisibilityController

  func body(content: Content) -> some View {
    content
      .coordinateSpace(name: TabBarScrollSpace.name)
      .onPreferenceChange(TabBarScrollOffsetKey.self) { offset in
        tabBarVisibility.update(fromScrollOffset: offset)
      }
  }
}

public extension View {
  /// Apply on a `ScrollView`. Pair with `tabBarScrollAnchor()` on the scroll content.
  func tracksTabBarOnScroll() -> some View {
    modifier(TracksTabBarOnScrollModifier())
  }

  /// Apply on the content inside a `ScrollView` that uses `tracksTabBarOnScroll()`.
  func tabBarScrollAnchor() -> some View {
    background(
      GeometryReader { geo in
        Color.clear.preference(
          key: TabBarScrollOffsetKey.self,
          value: geo.frame(in: .named(TabBarScrollSpace.name)).minY
        )
      }
    )
  }
}
