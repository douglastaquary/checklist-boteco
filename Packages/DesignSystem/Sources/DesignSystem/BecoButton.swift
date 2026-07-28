import SwiftUI

/// Primary/secondary action button aligned to Apple HIG (minimum 44pt height).
public struct BecoButton: View {
  public enum Variant {
    case primary
    case secondary
  }

  private let title: String
  private let variant: Variant
  private let isLoading: Bool
  private let expandsHorizontally: Bool
  private let action: () -> Void

  @EnvironmentObject private var theme: AppTheme
  @Environment(\.isEnabled) private var isEnabled

  public init(
    _ title: String,
    variant: Variant = .primary,
    isLoading: Bool = false,
    expandsHorizontally: Bool = true,
    action: @escaping () -> Void
  ) {
    self.title = title
    self.variant = variant
    self.isLoading = isLoading
    self.expandsHorizontally = expandsHorizontally
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      HStack(spacing: BecoTokens.Spacing.xs) {
        if isLoading {
          ProgressView()
            .progressViewStyle(.circular)
            .tint(foregroundColor)
        }
        Text(title)
          .font(.body.weight(.semibold))
          .lineLimit(1)
          .minimumScaleFactor(0.85)
      }
      .foregroundStyle(foregroundColor)
      .frame(maxWidth: expandsHorizontally ? .infinity : nil)
      .frame(minHeight: 44)
      .padding(.horizontal, BecoTokens.Spacing.md)
      .background(backgroundColor, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
      .opacity(isEnabled && !isLoading ? 1 : 0.45)
    }
    .buttonStyle(.plain)
    .disabled(isLoading)
    .accessibilityLabel(title)
  }

  private var foregroundColor: Color {
    switch variant {
    case .primary: return .white
    case .secondary: return theme.label
    }
  }

  private var backgroundColor: Color {
    switch variant {
    case .primary: return theme.tint
    case .secondary: return theme.secondaryBackground
    }
  }
}

/// Legacy style kept as a thin HIG-aligned wrapper for any remaining `Button { }.buttonStyle` call sites.
public struct PrimaryButtonStyle: ButtonStyle {
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .font(.body.weight(.semibold))
      .foregroundStyle(Color.white)
      .frame(maxWidth: .infinity)
      .frame(minHeight: 44)
      .padding(.horizontal, BecoTokens.Spacing.md)
      .background(
        theme.tint.opacity(configuration.isPressed ? 0.8 : 1),
        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
      )
  }
}
