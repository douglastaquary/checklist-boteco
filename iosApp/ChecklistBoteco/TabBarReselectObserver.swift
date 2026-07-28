import SwiftUI
import UIKit

/// Fires when the user taps an already-selected UITabBar item (SwiftUI `onChange` does not).
struct TabBarReselectObserver: UIViewControllerRepresentable {
  var onReselectIndex: (Int) -> Void

  func makeUIViewController(context: Context) -> Controller {
    Controller(onReselectIndex: onReselectIndex)
  }

  func updateUIViewController(_ uiViewController: Controller, context: Context) {
    uiViewController.onReselectIndex = onReselectIndex
  }

  final class Controller: UIViewController, UITabBarControllerDelegate {
    var onReselectIndex: (Int) -> Void
    private weak var observedTabBarController: UITabBarController?

    init(onReselectIndex: @escaping (Int) -> Void) {
      self.onReselectIndex = onReselectIndex
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidAppear(_ animated: Bool) {
      super.viewDidAppear(animated)
      attach()
    }

    override func didMove(toParent parent: UIViewController?) {
      super.didMove(toParent: parent)
      attach()
    }

    private func attach() {
      guard let tabBarController = findTabBarController() else { return }
      if observedTabBarController !== tabBarController {
        tabBarController.delegate = self
        observedTabBarController = tabBarController
      }
    }

    private func findTabBarController() -> UITabBarController? {
      var current: UIViewController? = parent ?? self
      while let candidate = current {
        if let tab = candidate as? UITabBarController { return tab }
        current = candidate.parent
      }
      return nil
    }

    func tabBarController(
      _ tabBarController: UITabBarController,
      shouldSelect viewController: UIViewController
    ) -> Bool {
      if tabBarController.selectedViewController === viewController {
        onReselectIndex(tabBarController.selectedIndex)
      }
      return true
    }
  }
}
