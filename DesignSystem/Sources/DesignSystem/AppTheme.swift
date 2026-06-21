import SwiftUI

@MainActor
public final class AppTheme: ObservableObject {
  public static let shared = AppTheme()

  public let tint: Color
  public let primaryBackground: Color
  public let secondaryBackground: Color
  public let rowBackground: Color
  public let label: Color
  public let secondaryLabel: Color

  private init() {
    tint = Color(red: 0.45, green: 0.25, blue: 0.10)
    primaryBackground = Color(.systemBackground)
    secondaryBackground = Color(.secondarySystemBackground)
    rowBackground = Color(.systemBackground)
    label = Color.primary
    secondaryLabel = Color.secondary
  }
}

public enum AppColors {
  public static var primary: Color { AppTheme.shared.tint }
}
