import SwiftUI

@MainActor
public final class AppTheme: ObservableObject {
  public static let shared = AppTheme()

  public let tint: Color
  public let primaryBackground: Color
  public let secondaryBackground: Color
  public let rowBackground: Color
  public let sectionHeaderBackground: Color
  public let label: Color
  public let secondaryLabel: Color

  private init() {
    tint = Color(red: 23 / 255, green: 23 / 255, blue: 23 / 255)
    primaryBackground = Color(.systemBackground)
    secondaryBackground = Color(.secondarySystemBackground)
    rowBackground = Color(.systemBackground)
    sectionHeaderBackground = Color(.tertiarySystemFill)
    label = Color.primary
    secondaryLabel = Color.secondary
  }
}

public enum AppColors {
  public static var primary: Color { AppTheme.shared.tint }
}
