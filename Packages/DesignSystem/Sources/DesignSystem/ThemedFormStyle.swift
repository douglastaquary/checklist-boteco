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

public struct ThemedListRowBackground: ViewModifier {
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public func body(content: Content) -> some View {
    content.listRowBackground(theme.rowBackground)
  }
}

extension View {
  public func themedFormStyle() -> some View {
    modifier(ThemedFormStyle())
  }

  public func themedListStyle() -> some View {
    modifier(ThemedListStyle())
  }

  public func themedListRowBackground() -> some View {
    modifier(ThemedListRowBackground())
  }

  @ViewBuilder
  public func themedSectionHeader(_ title: String) -> some View {
    ThemedSectionHeader(title: title)
  }
}

public struct ThemedSectionHeader: View {
  @EnvironmentObject private var theme: AppTheme
  private let title: String

  public init(title: String) {
    self.title = title
  }

  public var body: some View {
    Text(title.uppercased())
      .font(.caption)
      .fontWeight(.semibold)
      .foregroundColor(theme.secondaryLabel)
      .padding(.horizontal, 4)
      .padding(.vertical, 6)
      .frame(maxWidth: .infinity, alignment: .leading)
      .background(theme.sectionHeaderBackground)
  }
}
