import SwiftUI

public struct ThemedFormStyle: ViewModifier {
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public func body(content: Content) -> some View {
    content
      .scrollContentBackground(.hidden)
      .background(theme.secondaryBackground)
  }
}

public struct ThemedListStyle: ViewModifier {
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public func body(content: Content) -> some View {
    content
      .scrollContentBackground(.hidden)
      .background(theme.secondaryBackground)
  }
}

extension View {
  public func themedFormStyle() -> some View {
    modifier(ThemedFormStyle())
  }

  public func themedListStyle() -> some View {
    modifier(ThemedListStyle())
  }
}
