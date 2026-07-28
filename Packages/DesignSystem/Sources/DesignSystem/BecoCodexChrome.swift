import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Layout metrics for Codex-style page sheets (matches ChatGPT/Codex mobile chrome).
public enum BecoCodexSheetMetrics {
  /// Large continuous top corners on the page sheet.
  public static let cornerRadius: CGFloat = 38
  /// Equal inset from the sheet edge to the circle (top == leading/trailing).
  public static let chromeEdgeInset: CGFloat = 12
  public static let circleButtonSize: CGFloat = 36

  public static var chromeBarHeight: CGFloat {
    chromeEdgeInset * 2 + circleButtonSize
  }
}

/// Codex-style palette for modal admin sheets (follows app light/dark).
public struct BecoCodexPalette {
  public let isDark: Bool

  public init(isDark: Bool = false) {
    self.isDark = isDark
  }

  public var backgroundTop: Color {
    isDark ? Color.black : Color(.systemBackground)
  }

  public var backgroundMid: Color {
    isDark ? Color(red: 0.03, green: 0.03, blue: 0.04) : Color(.systemGroupedBackground)
  }

  public var foreground: Color { isDark ? .white : .black }
  public var muted: Color { isDark ? Color.white.opacity(0.62) : .secondary }
  public var card: Color {
    isDark ? Color.white.opacity(0.08) : Color(.secondarySystemGroupedBackground)
  }
  public var controlFill: Color {
    isDark ? Color.white.opacity(0.12) : Color.black.opacity(0.08)
  }
  public var dividerOpacity: Double { isDark ? 0.35 : 1 }
}

/// Full-bleed gradient background used by Codex-style modals.
public struct BecoCodexBackground: View {
  let palette: BecoCodexPalette

  public init(palette: BecoCodexPalette = BecoCodexPalette()) {
    self.palette = palette
  }

  public var body: some View {
    LinearGradient(
      colors: [palette.backgroundTop, palette.backgroundMid, palette.backgroundTop],
      startPoint: .top,
      endPoint: .bottom
    )
    .ignoresSafeArea()
  }
}

/// Circular chrome button (X dismiss / back) matching Codex mobile.
public struct BecoCodexCircleButton: View {
  let systemName: String
  let accessibilityLabel: String
  let palette: BecoCodexPalette
  let action: () -> Void

  public init(
    systemName: String,
    accessibilityLabel: String,
    palette: BecoCodexPalette = BecoCodexPalette(),
    action: @escaping () -> Void
  ) {
    self.systemName = systemName
    self.accessibilityLabel = accessibilityLabel
    self.palette = palette
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      Image(systemName: systemName)
        .font(.system(size: 14, weight: .semibold))
        .foregroundStyle(palette.foreground)
        .frame(
          width: BecoCodexSheetMetrics.circleButtonSize,
          height: BecoCodexSheetMetrics.circleButtonSize
        )
        .background(Circle().fill(palette.controlFill))
    }
    .buttonStyle(.plain)
    .accessibilityLabel(accessibilityLabel)
  }
}

/// Floating close control inset equally from the sheet’s top-trailing corner.
public struct BecoCodexCloseOverlay: ViewModifier {
  let palette: BecoCodexPalette
  let action: () -> Void

  public func body(content: Content) -> some View {
    content
      .overlay {
        BecoCodexCircleButton(
          systemName: "xmark",
          accessibilityLabel: "Fechar",
          palette: palette,
          action: action
        )
        .padding(BecoCodexSheetMetrics.chromeEdgeInset)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
        .ignoresSafeArea(edges: .top)
      }
  }
}

public extension View {
  func becoCodexCloseOverlay(
    palette: BecoCodexPalette,
    action: @escaping () -> Void
  ) -> some View {
    modifier(BecoCodexCloseOverlay(palette: palette, action: action))
  }
}

/// Thin host for Codex-style admin modules (navigation owned by content).
public struct BecoCodexModuleSheet<Content: View>: View {
  let content: Content

  public init(
    title: String = "",
    palette: BecoCodexPalette = BecoCodexPalette(),
    onDismiss: @escaping () -> Void = {},
    @ViewBuilder content: () -> Content
  ) {
    self.content = content()
    _ = title
    _ = palette
    _ = onDismiss
  }

  public var body: some View {
    content
  }
}

#if canImport(UIKit)
/// Applies `UISheetPresentationController.preferredCornerRadius` (iOS 16+, Xcode 14.2-safe).
public struct BecoSheetPreferredCornerRadius: UIViewControllerRepresentable {
  let radius: CGFloat

  public init(_ radius: CGFloat) {
    self.radius = radius
  }

  public func makeUIViewController(context: Context) -> Controller {
    Controller(radius: radius)
  }

  public func updateUIViewController(_ uiViewController: Controller, context: Context) {
    uiViewController.radius = radius
    uiViewController.applyPreferredCornerRadius()
  }

  public final class Controller: UIViewController {
    var radius: CGFloat

    init(radius: CGFloat) {
      self.radius = radius
      super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    public override func viewDidLoad() {
      super.viewDidLoad()
      view.isUserInteractionEnabled = false
      view.backgroundColor = .clear
    }

    public override func didMove(toParent parent: UIViewController?) {
      super.didMove(toParent: parent)
      applyPreferredCornerRadius()
    }

    public override func viewDidLayoutSubviews() {
      super.viewDidLayoutSubviews()
      applyPreferredCornerRadius()
    }

    public override func viewDidAppear(_ animated: Bool) {
      super.viewDidAppear(animated)
      applyPreferredCornerRadius()
    }

    func applyPreferredCornerRadius() {
      var current: UIViewController? = self
      while let vc = current {
        if let sheet = vc.sheetPresentationController {
          sheet.preferredCornerRadius = radius
          return
        }
        current = vc.parent
      }
    }
  }
}
#endif

/// Page-sheet presentation matching Codex mobile (top gap + rounded card, not full-screen).
public struct BecoCodexSheetChrome: ViewModifier {
  public init() {}

  public func body(content: Content) -> some View {
    content
      .presentationDetents([.large])
      .presentationDragIndicator(.hidden)
      #if canImport(UIKit)
      .background(BecoSheetPreferredCornerRadius(BecoCodexSheetMetrics.cornerRadius))
      #endif
  }
}

public extension View {
  /// Applies Codex-style page sheet chrome (does not fill the entire screen at the top).
  func becoCodexSheetChrome() -> some View {
    modifier(BecoCodexSheetChrome())
  }
}

/// Grouped card container with optional section header.
public struct BecoCodexGroupedSection<Content: View>: View {
  let title: String?
  let palette: BecoCodexPalette
  let content: Content

  public init(
    title: String? = nil,
    palette: BecoCodexPalette = BecoCodexPalette(),
    @ViewBuilder content: () -> Content
  ) {
    self.title = title
    self.palette = palette
    self.content = content()
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      if let title {
        Text(title)
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(palette.muted)
          .padding(.horizontal, 4)
      }
      VStack(spacing: 0) {
        content
      }
      .background(palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
  }
}

/// Single row inside a Codex grouped card.
public struct BecoCodexRow: View {
  let title: String
  let subtitle: String?
  let systemImage: String?
  let trailing: String?
  let showsChevron: Bool
  let palette: BecoCodexPalette
  let action: (() -> Void)?

  public init(
    title: String,
    subtitle: String? = nil,
    systemImage: String? = nil,
    trailing: String? = nil,
    showsChevron: Bool = true,
    palette: BecoCodexPalette = BecoCodexPalette(),
    action: (() -> Void)? = nil
  ) {
    self.title = title
    self.subtitle = subtitle
    self.systemImage = systemImage
    self.trailing = trailing
    self.showsChevron = showsChevron
    self.palette = palette
    self.action = action
  }

  public var body: some View {
    let row = HStack(spacing: 14) {
      if let systemImage {
        Image(systemName: systemImage)
          .font(.body.weight(.semibold))
          .foregroundStyle(palette.foreground)
          .frame(width: 28)
      }
      VStack(alignment: .leading, spacing: 2) {
        Text(title)
          .font(.body)
          .foregroundStyle(palette.foreground)
          .multilineTextAlignment(.leading)
        if let subtitle {
          Text(subtitle)
            .font(.caption)
            .foregroundStyle(palette.muted)
        }
      }
      Spacer(minLength: 8)
      if let trailing {
        Text(trailing)
          .font(.subheadline)
          .foregroundStyle(palette.muted)
      }
      if showsChevron {
        Image(systemName: "chevron.right")
          .font(.caption.weight(.semibold))
          .foregroundStyle(palette.muted)
      }
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 14)
    .contentShape(Rectangle())

    if let action {
      Button(action: action) {
        row
      }
      .buttonStyle(.plain)
    } else {
      row
    }
  }
}

public struct BecoCodexRowDivider: View {
  let palette: BecoCodexPalette
  let leadingInset: CGFloat

  public init(palette: BecoCodexPalette = BecoCodexPalette(), leadingInset: CGFloat = 58) {
    self.palette = palette
    self.leadingInset = leadingInset
  }

  public var body: some View {
    Divider()
      .padding(.leading, leadingInset)
      .opacity(palette.dividerOpacity)
  }
}

/// Detail screen chrome with circular back inset like Codex mobile.
public struct BecoCodexDetailChrome<Content: View>: View {
  let title: String
  let palette: BecoCodexPalette
  let content: Content

  @Environment(\.dismiss) private var dismiss

  public init(
    title: String,
    palette: BecoCodexPalette = BecoCodexPalette(),
    @ViewBuilder content: () -> Content
  ) {
    self.title = title
    self.palette = palette
    self.content = content()
  }

  public var body: some View {
    ZStack(alignment: .top) {
      BecoCodexBackground(palette: palette)
      VStack(spacing: 0) {
        Color.clear
          .frame(height: BecoCodexSheetMetrics.chromeBarHeight)
        content
      }
      BecoCodexDetailTopBar(
        title: title,
        palette: palette,
        onBack: { dismiss() }
      )
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
      .ignoresSafeArea(edges: .top)
    }
    .navigationBarBackButtonHidden(true)
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
  }
}

private struct BecoCodexDetailTopBar: View {
  let title: String
  let palette: BecoCodexPalette
  let onBack: () -> Void

  var body: some View {
    ZStack {
      Text(title)
        .font(.headline.weight(.semibold))
        .foregroundStyle(palette.foreground)
        .lineLimit(1)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, BecoCodexSheetMetrics.chromeBarHeight)

      HStack(spacing: 0) {
        BecoCodexCircleButton(
          systemName: "chevron.left",
          accessibilityLabel: "Voltar",
          palette: palette,
          action: onBack
        )
        Spacer(minLength: 0)
      }
    }
    .padding(BecoCodexSheetMetrics.chromeEdgeInset)
  }
}
