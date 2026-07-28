import SwiftUI
import UIKit

/// Palette shared by chat-style composers (AI Chat, Contagem).
public struct BecoChatPalette {
  public let isDark: Bool

  public init(isDark: Bool) {
    self.isDark = isDark
  }

  public var foreground: Color { isDark ? .white : .black }
  public var mutedForeground: Color { isDark ? .white.opacity(0.62) : .secondary }
  public var glassStroke: Color { isDark ? .white.opacity(0.14) : .black.opacity(0.08) }
  public var glassShadow: Color { isDark ? .black.opacity(0.42) : .black.opacity(0.12) }
}

public struct BecoChatGlassCapsule: View {
  let palette: BecoChatPalette

  public init(palette: BecoChatPalette) {
    self.palette = palette
  }

  public var body: some View {
    Capsule(style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(Capsule(style: .continuous).stroke(palette.glassStroke, lineWidth: 1))
      .shadow(color: palette.glassShadow, radius: 18, x: 0, y: 10)
  }
}

public struct BecoChatGlassRoundedRectangle: View {
  let radius: CGFloat
  let palette: BecoChatPalette

  public init(radius: CGFloat, palette: BecoChatPalette) {
    self.radius = radius
    self.palette = palette
  }

  public var body: some View {
    RoundedRectangle(cornerRadius: radius, style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(
        RoundedRectangle(cornerRadius: radius, style: .continuous)
          .stroke(palette.glassStroke, lineWidth: 1)
      )
      .shadow(color: palette.glassShadow, radius: 24, x: 0, y: 14)
  }
}

public struct BecoChatComposerIconButton: View {
  let systemName: String
  let accessibilityLabel: String
  let palette: BecoChatPalette
  let action: () -> Void

  public init(
    systemName: String,
    accessibilityLabel: String,
    palette: BecoChatPalette,
    action: @escaping () -> Void = {}
  ) {
    self.systemName = systemName
    self.accessibilityLabel = accessibilityLabel
    self.palette = palette
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      Image(systemName: systemName)
        .font(.system(size: 22, weight: .regular))
        .frame(width: 34, height: 34)
        .foregroundStyle(palette.foreground)
        .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(accessibilityLabel)
  }
}

/// Circular dismiss control shown beside an engaged composer (outside the glass pill).
public struct BecoChatComposerDismissButton: View {
  let palette: BecoChatPalette
  let action: () -> Void

  public init(palette: BecoChatPalette, action: @escaping () -> Void) {
    self.palette = palette
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      Image(systemName: "xmark")
        .font(.system(size: 14, weight: .semibold))
        .foregroundStyle(palette.foreground)
        .frame(width: 36, height: 36)
        .background(
          Circle()
            .fill(palette.isDark ? Color.white.opacity(0.12) : Color.black.opacity(0.08))
        )
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Fechar composer")
  }
}

public struct BecoChatSendButton: View {
  let canSend: Bool
  let palette: BecoChatPalette
  let action: () -> Void

  public init(canSend: Bool, palette: BecoChatPalette, action: @escaping () -> Void) {
    self.canSend = canSend
    self.palette = palette
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      ZStack {
        Circle()
          .fill(canSend ? palette.foreground : palette.foreground.opacity(palette.isDark ? 0.10 : 0.08))
          .frame(width: 34, height: 34)
        Image(systemName: canSend ? "arrow.up" : "square.fill")
          .font(.system(size: canSend ? 18 : 14, weight: .semibold))
          .foregroundStyle(
            canSend
              ? (palette.isDark ? Color.black : Color.white)
              : palette.foreground.opacity(0.68)
          )
      }
    }
    .buttonStyle(.plain)
    .accessibilityLabel(canSend ? "Enviar" : "Aguardando texto")
    .disabled(!canSend)
  }
}

/// Chat-style composer with a stable text field (never relocated while typing).
public struct BecoChatComposer<PlusContent: View, MicContent: View>: View {
  @Binding private var text: String
  @Binding private var isInputFocused: Bool
  private let placeholder: String
  private let canSend: Bool
  private let isSending: Bool
  private let isBlocked: Bool
  private let expandedTrailingLabel: String?
  private let showsDismissButton: Bool
  private let palette: BecoChatPalette
  private let onSend: () -> Void
  private let onDismiss: (() -> Void)?
  private let onInputHeightChange: ((CGFloat) -> Void)?
  private let plusContent: PlusContent
  private let micContent: MicContent

  @State private var inputHeight: CGFloat = BecoChatEditableTextView.minHeight

  public init(
    text: Binding<String>,
    isInputFocused: Binding<Bool>,
    placeholder: String,
    canSend: Bool,
    isSending: Bool,
    isBlocked: Bool = false,
    isComposerExpanded: Bool = false,
    expandedTrailingLabel: String? = nil,
    showsDismissButton: Bool = false,
    palette: BecoChatPalette,
    onSend: @escaping () -> Void,
    onDismiss: (() -> Void)? = nil,
    onInputHeightChange: ((CGFloat) -> Void)? = nil,
    @ViewBuilder plusContent: () -> PlusContent,
    @ViewBuilder micContent: () -> MicContent
  ) {
    _text = text
    _isInputFocused = isInputFocused
    self.placeholder = placeholder
    self.canSend = canSend
    self.isSending = isSending
    self.isBlocked = isBlocked
    self.expandedTrailingLabel = expandedTrailingLabel
    self.showsDismissButton = showsDismissButton
    self.palette = palette
    self.onSend = onSend
    self.onDismiss = onDismiss
    self.onInputHeightChange = onInputHeightChange
    self.plusContent = plusContent()
    self.micContent = micContent()
    _ = isComposerExpanded
  }

  public var body: some View {
    HStack(alignment: .center, spacing: 10) {
      composerPill

      if showsDismissButton {
        BecoChatComposerDismissButton(palette: palette) {
          dismissEditing()
        }
      }
    }
  }

  private var composerPill: some View {
    // Stable stacked layout: text on top, toolbar icons on bottom (never relocates UITextView).
    VStack(alignment: .leading, spacing: 10) {
      textField
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .padding(.top, 4)

      HStack(alignment: .center, spacing: 12) {
        plusContent
          .frame(width: 34, height: 34)

        if let expandedTrailingLabel {
          Text(expandedTrailingLabel)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(palette.mutedForeground)
            .lineLimit(1)
        }

        Spacer(minLength: 8)

        micContent
          .frame(width: 34, height: 34)
        BecoChatSendButton(canSend: canSend, palette: palette, action: onSend)
      }
    }
    .padding(.horizontal, 14)
    .padding(.top, 12)
    .padding(.bottom, 10)
    .background(BecoChatGlassRoundedRectangle(radius: 28, palette: palette))
    .overlay {
      if isSending {
        RoundedRectangle(cornerRadius: 28, style: .continuous)
          .stroke(palette.glassStroke.opacity(1.25), lineWidth: 1)
      }
    }
    .onChange(of: inputHeight) { height in
      onInputHeightChange?(height)
    }
    .onChange(of: text) { value in
      if value.isEmpty {
        inputHeight = BecoChatEditableTextView.minHeight
      }
    }
  }

  private var textField: some View {
    ZStack(alignment: .topLeading) {
      if text.isEmpty {
        Text(placeholder)
          .font(.system(size: 17, weight: .regular))
          .foregroundStyle(palette.mutedForeground)
          .padding(.top, 2)
          .allowsHitTesting(false)
      }

      BecoChatEditableTextView(
        text: $text,
        isFocused: $isInputFocused,
        measuredHeight: $inputHeight,
        foreground: UIColor(palette.foreground),
        isEnabled: !(isSending || isBlocked)
      )
      .frame(height: inputHeight)
    }
  }

  private func dismissEditing() {
    isInputFocused = false
    UIApplication.shared.sendAction(
      #selector(UIResponder.resignFirstResponder),
      to: nil,
      from: nil,
      for: nil
    )
    onDismiss?()
  }
}

/// UITextView-backed input with stable editing (no focus/text reset loops).
struct BecoChatEditableTextView: UIViewRepresentable {
  static let minHeight: CGFloat = 44
  static let maxHeight: CGFloat = 140

  @Binding var text: String
  @Binding var isFocused: Bool
  @Binding var measuredHeight: CGFloat
  let foreground: UIColor
  let isEnabled: Bool

  func makeCoordinator() -> Coordinator {
    Coordinator(self)
  }

  func makeUIView(context: Context) -> GrowingChatTextView {
    let view = GrowingChatTextView()
    view.delegate = context.coordinator
    context.coordinator.attach(view)
    view.minHeight = Self.minHeight
    view.maxHeight = Self.maxHeight
    view.heightHandler = { [weak coordinator = context.coordinator] height in
      coordinator?.applyMeasuredHeight(height)
    }
    view.backgroundColor = .clear
    view.textContainerInset = UIEdgeInsets(top: 2, left: 0, bottom: 2, right: 0)
    view.textContainer.lineFragmentPadding = 0
    view.font = .systemFont(ofSize: 17)
    view.textColor = foreground
    view.tintColor = .systemBlue
    view.isScrollEnabled = false
    view.isEditable = true
    view.isSelectable = true
    view.allowsEditingTextAttributes = false
    view.keyboardType = .default
    view.returnKeyType = .default
    view.autocorrectionType = .yes
    view.autocapitalizationType = .sentences
    view.smartInsertDeleteType = .yes
    view.smartQuotesType = .yes
    view.smartDashesType = .yes
    view.spellCheckingType = .yes
    view.dataDetectorTypes = []
    view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    return view
  }

  func updateUIView(_ uiView: GrowingChatTextView, context: Context) {
    let coordinator = context.coordinator
    coordinator.parent = self
    coordinator.attach(uiView)

    // Never rewrite text from SwiftUI while the field is first responder.
    if !uiView.isFirstResponder, uiView.text != text {
      uiView.text = text
      uiView.recalculateHeight()
    }

    if uiView.textColor != foreground {
      uiView.textColor = foreground
    }
    uiView.isEditable = isEnabled
    uiView.isUserInteractionEnabled = isEnabled

    if isFocused, !uiView.isFirstResponder {
      DispatchQueue.main.async {
        if self.isFocused, !uiView.isFirstResponder {
          uiView.becomeFirstResponder()
        }
      }
    } else if !isFocused, uiView.isFirstResponder {
      DispatchQueue.main.async {
        if !self.isFocused, uiView.isFirstResponder {
          uiView.resignFirstResponder()
        }
      }
    }
  }

  final class Coordinator: NSObject, UITextViewDelegate {
    var parent: BecoChatEditableTextView
    private weak var textView: GrowingChatTextView?
    private var lastAppliedHeight: CGFloat = -1

    init(_ parent: BecoChatEditableTextView) {
      self.parent = parent
    }

    func attach(_ view: GrowingChatTextView) {
      textView = view
      view.heightHandler = { [weak self] height in
        self?.applyMeasuredHeight(height)
      }
    }

    func applyMeasuredHeight(_ height: CGFloat) {
      guard abs(lastAppliedHeight - height) > 0.5 else { return }
      lastAppliedHeight = height
      guard abs(parent.measuredHeight - height) > 0.5 else { return }
      DispatchQueue.main.async {
        if abs(self.parent.measuredHeight - height) > 0.5 {
          self.parent.measuredHeight = height
        }
      }
    }

    func textViewDidChange(_ textView: UITextView) {
      let newText = textView.text ?? ""
      if parent.text != newText {
        parent.text = newText
      }
      (textView as? GrowingChatTextView)?.recalculateHeight()
    }

    func textViewShouldBeginEditing(_ textView: UITextView) -> Bool {
      parent.isEnabled
    }

    func textViewDidBeginEditing(_ textView: UITextView) {
      if !parent.isFocused {
        parent.isFocused = true
      }
    }

    func textViewDidEndEditing(_ textView: UITextView) {
      if parent.isFocused {
        parent.isFocused = false
      }
    }

    func textView(
      _ textView: UITextView,
      shouldChangeTextIn range: NSRange,
      replacementText text: String
    ) -> Bool {
      true
    }
  }
}

final class GrowingChatTextView: UITextView {
  var minHeight: CGFloat = 44
  var maxHeight: CGFloat = 140
  var heightHandler: ((CGFloat) -> Void)?
  private var isRecalculating = false

  override func layoutSubviews() {
    super.layoutSubviews()
    recalculateHeight()
  }

  func recalculateHeight() {
    guard !isRecalculating else { return }
    isRecalculating = true
    defer { isRecalculating = false }

    let width = bounds.width > 1 ? bounds.width : 280
    let fitting = sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
    let clamped = min(max(ceil(fitting.height), minHeight), maxHeight)
    let needsScroll = fitting.height > maxHeight + 0.5
    if isScrollEnabled != needsScroll {
      isScrollEnabled = needsScroll
    }
    heightHandler?(clamped)
  }
}
