import SwiftUI

/// Soft fade behind the bottom chat chrome (composer / voice bar).
public struct BecoChatBottomFade: View {
  public init() {}

  public var body: some View {
    LinearGradient(
      colors: [
        Color(.systemGroupedBackground).opacity(0),
        Color(.systemGroupedBackground).opacity(0.72),
        Color(.systemGroupedBackground).opacity(0.96)
      ],
      startPoint: .top,
      endPoint: .bottom
    )
    .ignoresSafeArea()
  }
}

/// Centered secondary caption used for system events in chat timelines.
public struct BecoChatSystemCaption: View {
  private let text: String

  public init(_ text: String) {
    self.text = text
  }

  public var body: some View {
    Text(text)
      .font(.caption)
      .foregroundStyle(.secondary)
      .multilineTextAlignment(.center)
      .frame(maxWidth: .infinity, alignment: .center)
      .padding(.vertical, 4)
  }
}

/// Trailing user bubble shared by Contagem utterances and AI Chat.
public struct BecoChatUserBubble<Content: View>: View {
  private let palette: BecoChatPalette
  private let cornerRadius: CGFloat
  private let content: Content

  public init(
    palette: BecoChatPalette,
    cornerRadius: CGFloat = 18,
    @ViewBuilder content: () -> Content
  ) {
    self.palette = palette
    self.cornerRadius = cornerRadius
    self.content = content()
  }

  public var body: some View {
    content
      .padding(.horizontal, 14)
      .padding(.vertical, 12)
      .background(
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
          .fill(Color.black.opacity(palette.isDark ? 0.55 : 0.82))
      )
      .frame(maxWidth: .infinity, alignment: .trailing)
      .padding(.leading, 48)
  }
}

/// String / AttributedString convenience for user bubbles.
public struct BecoChatUserTextBubble: View {
  private enum Kind {
    case plain(String)
    case attributed(AttributedString)
  }

  private let kind: Kind
  private let palette: BecoChatPalette
  private let cornerRadius: CGFloat

  public init(_ value: String, palette: BecoChatPalette, cornerRadius: CGFloat = 18) {
    self.kind = .plain(value)
    self.palette = palette
    self.cornerRadius = cornerRadius
  }

  public init(_ attributed: AttributedString, palette: BecoChatPalette, cornerRadius: CGFloat = 18) {
    self.kind = .attributed(attributed)
    self.palette = palette
    self.cornerRadius = cornerRadius
  }

  public var body: some View {
    BecoChatUserBubble(palette: palette, cornerRadius: cornerRadius) {
      switch kind {
      case .plain(let value):
        Text(value)
          .font(.body)
          .foregroundColor(.white)
      case .attributed(let attributed):
        Text(attributed)
      }
    }
  }
}

/// Leading assistant bubble with optional sources caption.
public struct BecoChatAssistantBubble<Content: View>: View {
  private let palette: BecoChatPalette
  private let sourcesCaption: String?
  private let cornerRadius: CGFloat
  private let content: Content

  public init(
    palette: BecoChatPalette,
    sourcesCaption: String? = nil,
    cornerRadius: CGFloat = 19,
    @ViewBuilder content: () -> Content
  ) {
    self.palette = palette
    self.sourcesCaption = sourcesCaption
    self.cornerRadius = cornerRadius
    self.content = content()
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      content
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(BecoChatGlassRoundedRectangle(radius: cornerRadius, palette: palette))

      if let sourcesCaption, !sourcesCaption.isEmpty {
        Text(sourcesCaption)
          .font(.caption2)
          .foregroundStyle(palette.mutedForeground)
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(.trailing, 42)
  }
}

/// Text convenience for assistant replies.
public struct BecoChatAssistantTextBubble: View {
  private let value: String
  private let palette: BecoChatPalette
  private let sourcesCaption: String?
  private let cornerRadius: CGFloat

  public init(
    _ value: String,
    palette: BecoChatPalette,
    sourcesCaption: String? = nil,
    cornerRadius: CGFloat = 19
  ) {
    self.value = value
    self.palette = palette
    self.sourcesCaption = sourcesCaption
    self.cornerRadius = cornerRadius
  }

  public var body: some View {
    BecoChatAssistantBubble(
      palette: palette,
      sourcesCaption: sourcesCaption,
      cornerRadius: cornerRadius
    ) {
      Text(value)
        .font(.body)
        .foregroundStyle(palette.foreground)
    }
  }
}
