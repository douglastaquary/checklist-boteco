import SwiftUI
import Network
import DesignSystem

public struct AIChatView: View {
  @Environment(\.colorScheme) private var colorScheme

  private let client: AIChatClient?
  private let token: String?

  @State private var messages: [Message] = []
  @State private var text = ""
  @State private var isSending = false
  @State private var errorMessage: String?
  @State private var usage: AIUsageSummaryDTO?
  @FocusState private var isInputFocused: Bool

  public init(client: AIChatClient?, token: String?) {
    self.client = client
    self.token = token
  }

  private var trimmedText: String {
    text.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private var canSend: Bool {
    !trimmedText.isEmpty && !isSending && usage?.blocked != true
  }

  private var palette: AIChatPalette {
    AIChatPalette(isDark: colorScheme == .dark)
  }

  private var isComposerExpanded: Bool {
    isInputFocused && !text.isEmpty
  }

  public var body: some View {
    ZStack {
      AIChatBackground(palette: palette)

      VStack(spacing: 0) {
        AIChatHeader(palette: palette)
          .padding(.horizontal, 20)
          .padding(.top, 10)

        if let usage {
          AIChatUsageBar(usage: usage, mutedForegroundColor: palette.mutedForeground)
        }

        AIChatMessageScroll(
          messages: messages,
          isSending: isSending,
          palette: palette,
          onSuggestionTap: { text = $0 }
        )

        if let errorMessage {
          Text(errorMessage)
            .font(.footnote)
            .foregroundStyle(.red)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
      }
    }
    .navigationTitle("")
    .safeAreaInset(edge: .bottom) {
      AIChatComposer(
        text: $text,
        isInputFocused: $isInputFocused,
        isSending: isSending,
        canSend: canSend,
        isComposerExpanded: isComposerExpanded,
        isBlocked: usage?.blocked == true,
        palette: palette,
        onSend: { Task { await send() } }
      )
    }
    .task { await loadUsage() }
  }

  @MainActor
  private func send() async {
    guard let client, let token, !token.isEmpty else {
      errorMessage = "Chat indisponível sem conexão com o backend."
      return
    }
    let question = trimmedText
    guard !question.isEmpty, !isSending else { return }
    text = ""
    errorMessage = nil
    messages.append(Message(role: "user", text: question))
    isSending = true
    defer { isSending = false }
    do {
      let context = messages.suffix(4).map { AIChatMessageRequest(role: $0.role, text: $0.text) }
      let response = try await client.send(messages: Array(context), token: token)
      messages.append(
        Message(role: "assistant", text: response.answer, tools: response.consultedTools)
      )
      usage = response.budget
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  @MainActor
  private func loadUsage() async {
    guard let client, let token else { return }
    usage = try? await client.usage(token: token)
  }
}

// MARK: - Subviews (MV)

private struct AIChatPalette {
  let isDark: Bool

  var foreground: Color { isDark ? .white : .black }
  var mutedForeground: Color { isDark ? .white.opacity(0.62) : .secondary }
  var glassStroke: Color { isDark ? .white.opacity(0.14) : .black.opacity(0.08) }
  var glassShadow: Color { isDark ? .black.opacity(0.42) : .black.opacity(0.12) }
}

private struct AIChatBackground: View {
  let palette: AIChatPalette

  var body: some View {
    ZStack {
      LinearGradient(
        colors: palette.isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground), Color(.secondarySystemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )
      Circle()
        .fill(palette.isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.035))
        .frame(width: 240, height: 240)
        .blur(radius: 70)
        .offset(x: -150, y: -240)
      Circle()
        .fill(palette.isDark ? Color.white.opacity(0.05) : Color.white.opacity(0.75))
        .frame(width: 320, height: 320)
        .blur(radius: 80)
        .offset(x: 150, y: 210)
    }
    .ignoresSafeArea()
  }
}

private struct AIChatHeader: View {
  let palette: AIChatPalette

  var body: some View {
    HStack(spacing: 12) {
      Image(systemName: "sparkles")
        .font(.system(size: 18, weight: .semibold))
        .foregroundStyle(palette.foreground)
        .frame(width: 44, height: 44)
        .background(AIChatGlassCapsule(palette: palette))

      VStack(alignment: .leading, spacing: 2) {
        Text("AI do Beco")
          .font(.headline.weight(.semibold))
          .foregroundStyle(palette.foreground)
        Text("ChecklistBoteco · dados do Beco da Praia")
          .font(.caption)
          .foregroundStyle(palette.mutedForeground)
          .lineLimit(1)
      }

      Spacer(minLength: 8)

      HStack(spacing: 14) {
        Image(systemName: "square.and.pencil")
        Image(systemName: "ellipsis")
      }
      .font(.system(size: 17, weight: .semibold))
      .foregroundStyle(palette.foreground)
      .frame(height: 44)
      .padding(.horizontal, 16)
      .background(AIChatGlassCapsule(palette: palette))
    }
  }
}

private struct AIChatUsageBar: View {
  let usage: AIUsageSummaryDTO
  let mutedForegroundColor: Color

  var body: some View {
    let spent = Double(usage.estimatedCostMicros) / 1_000_000
    let limit = Double(usage.monthlyLimitCents) / 100
    HStack {
      Text(String(format: "US$ %.2f de US$ %.2f", spent, limit))
      Spacer()
      Text("\(usage.requests) consultas")
    }
    .font(.caption)
    .foregroundStyle(usage.blocked ? .red : mutedForegroundColor)
    .padding(.horizontal, 20)
    .padding(.vertical, 8)
  }
}

private struct AIChatMessageScroll: View {
  let messages: [Message]
  let isSending: Bool
  let palette: AIChatPalette
  let onSuggestionTap: (String) -> Void

  var body: some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(spacing: 14) {
          if messages.isEmpty {
            AIChatWelcome(palette: palette, onSuggestionTap: onSuggestionTap)
          }
          ForEach(messages) { message in
            AIChatBubble(message: message, palette: palette)
          }
          if isSending {
            AIChatProcessingBubble(isSending: isSending, palette: palette)
          }
        }
        .padding(.horizontal, 18)
        .padding(.top, 18)
        .padding(.bottom, 34)
      }
      .scrollDismissesKeyboard(.interactively)
      .onChange(of: messages.count) { _ in
        if let last = messages.last {
          proxy.scrollTo(last.id, anchor: .bottom)
        }
      }
    }
  }
}

private struct AIChatWelcome: View {
  let palette: AIChatPalette
  let onSuggestionTap: (String) -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 18) {
      HStack(spacing: 12) {
        Image(systemName: "sparkles")
          .font(.title2.weight(.semibold))
          .foregroundStyle(palette.foreground)
          .frame(width: 42, height: 42)
          .background(Circle().fill(palette.foreground.opacity(palette.isDark ? 0.12 : 0.06)))
        VStack(alignment: .leading, spacing: 3) {
          Text("Como posso ajudar na operação?")
            .font(.headline.weight(.semibold))
            .foregroundStyle(palette.foreground)
          Text("Dados de vendas, compras, estoque e ponto")
            .font(.subheadline)
            .foregroundStyle(palette.mutedForeground)
        }
      }

      VStack(alignment: .leading, spacing: 8) {
        Text("O que você quer entender hoje?")
          .font(.title3.bold())
          .foregroundStyle(palette.foreground)
        Text("Pergunte em linguagem simples. A resposta usa os dados importados e disponíveis no backend do Beco.")
          .font(.body)
          .foregroundStyle(palette.mutedForeground)
      }

      VStack(alignment: .leading, spacing: 10) {
        AIChatSuggestionChip(
          value: "Quanto vendemos este mês?",
          icon: "chart.line.uptrend.xyaxis",
          palette: palette,
          action: { onSuggestionTap("Quanto vendemos este mês?") }
        )
        AIChatSuggestionChip(
          value: "Quantas Heinekens vendemos em março?",
          icon: "magnifyingglass",
          palette: palette,
          action: { onSuggestionTap("Quantas Heinekens vendemos em março?") }
        )
        AIChatSuggestionChip(
          value: "Houve perdas no estoque hoje?",
          icon: "shippingbox",
          palette: palette,
          action: { onSuggestionTap("Houve perdas no estoque hoje?") }
        )
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(20)
    .background(AIChatGlassRoundedRectangle(radius: 28, palette: palette))
  }
}

private struct AIChatSuggestionChip: View {
  let value: String
  let icon: String
  let palette: AIChatPalette
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        Image(systemName: icon)
          .font(.caption.weight(.semibold))
        Text(value)
          .font(.subheadline.weight(.medium))
          .lineLimit(2)
      }
      .foregroundStyle(palette.foreground)
      .padding(.horizontal, 12)
      .padding(.vertical, 9)
      .background(
        Capsule(style: .continuous)
          .fill(palette.foreground.opacity(palette.isDark ? 0.10 : 0.06))
      )
    }
    .buttonStyle(.plain)
  }
}

private struct AIChatBubble: View {
  let message: Message
  let palette: AIChatPalette

  private var isUser: Bool { message.role == "user" }

  var body: some View {
    VStack(alignment: isUser ? .trailing : .leading, spacing: 6) {
      Text(message.text)
        .font(.body)
        .foregroundStyle(isUser ? .white : palette.foreground)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
          RoundedRectangle(cornerRadius: 19, style: .continuous)
            .fill(isUser ? Color.black.opacity(0.82) : Color.clear)
            .background {
              if !isUser {
                AIChatGlassRoundedRectangle(radius: 19, palette: palette)
              }
            }
        )

      if !message.tools.isEmpty {
        Text("Fontes: \(message.tools.map(Self.toolLabel).joined(separator: ", "))")
          .font(.caption2)
          .foregroundStyle(palette.mutedForeground)
      }
    }
    .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
    .padding(.leading, isUser ? 54 : 0)
    .padding(.trailing, isUser ? 0 : 42)
    .id(message.id)
  }

  private static func toolLabel(_ value: String) -> String {
    if value.hasPrefix("sales") { return "vendas" }
    if value.hasPrefix("purchases") { return "compras" }
    if value.hasPrefix("inventory") { return "estoque" }
    if value.hasPrefix("work_clock") { return "ponto" }
    return value
  }
}

private struct AIChatProcessingBubble: View {
  let isSending: Bool
  let palette: AIChatPalette

  var body: some View {
    HStack(spacing: 10) {
      ForEach(0..<3, id: \.self) { index in
        Circle()
          .fill(Color.secondary.opacity(0.55))
          .frame(width: 8, height: 8)
          .scaleEffect(isSending ? 1.0 : 0.7)
          .animation(
            .easeInOut(duration: 0.8).repeatForever().delay(Double(index) * 0.16),
            value: isSending
          )
      }
      Text("Processando dados do Beco…")
        .font(.subheadline.weight(.medium))
        .foregroundStyle(palette.mutedForeground)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(14)
    .background(AIChatGlassRoundedRectangle(radius: 18, palette: palette))
    .shimmer(active: true)
  }
}

private struct AIChatComposer: View {
  @Binding var text: String
  var isInputFocused: FocusState<Bool>.Binding
  let isSending: Bool
  let canSend: Bool
  let isComposerExpanded: Bool
  let isBlocked: Bool
  let palette: AIChatPalette
  let onSend: () -> Void

  var body: some View {
    VStack(spacing: 10) {
      if isSending {
        AIChatProcessingPill(palette: palette)
      }
      AIChatComposerInputBar(
        text: $text,
        isInputFocused: isInputFocused,
        isSending: isSending,
        canSend: canSend,
        isComposerExpanded: isComposerExpanded,
        isBlocked: isBlocked,
        palette: palette,
        onSend: onSend
      )
    }
    .padding(.horizontal, 18)
    .padding(.top, 10)
    .padding(.bottom, 8)
    .background(
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
    )
  }
}

private struct AIChatProcessingPill: View {
  let palette: AIChatPalette

  var body: some View {
    HStack(spacing: 8) {
      Image(systemName: "sparkles")
        .font(.caption.weight(.bold))
      Text("Consultando dados do Beco")
      Spacer(minLength: 0)
    }
    .font(.caption.weight(.semibold))
    .foregroundStyle(palette.mutedForeground)
    .padding(.horizontal, 14)
    .padding(.vertical, 7)
    .background(AIChatGlassCapsule(palette: palette))
    .frame(maxWidth: 260)
    .shimmer(active: true)
  }
}

private struct AIChatComposerInputBar: View {
  @Binding var text: String
  var isInputFocused: FocusState<Bool>.Binding
  let isSending: Bool
  let canSend: Bool
  let isComposerExpanded: Bool
  let isBlocked: Bool
  let palette: AIChatPalette
  let onSend: () -> Void

  var body: some View {
    VStack(spacing: isComposerExpanded ? 8 : 0) {
      if isComposerExpanded {
        AIChatTextInput(
          text: $text,
          isInputFocused: isInputFocused,
          isSending: isSending,
          isBlocked: isBlocked,
          isComposerExpanded: isComposerExpanded,
          palette: palette,
          onSubmit: onSend
        )
        .padding(.horizontal, 18)
        .padding(.top, 14)
      }

      HStack(alignment: .center, spacing: 10) {
        AIChatComposerIconButton(
          systemName: "plus",
          accessibilityLabel: "Adicionar contexto",
          palette: palette
        )
        .disabled(true)
        .opacity(0.75)

        if !isComposerExpanded {
          AIChatTextInput(
            text: $text,
            isInputFocused: isInputFocused,
            isSending: isSending,
            isBlocked: isBlocked,
            isComposerExpanded: isComposerExpanded,
            palette: palette,
            onSubmit: onSend
          )
        }

        Spacer(minLength: isComposerExpanded ? 8 : 0)

        if isComposerExpanded {
          Text("Beco · médio")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(palette.mutedForeground)
        }

        AIChatComposerIconButton(
          systemName: "mic",
          accessibilityLabel: "Entrada por voz",
          palette: palette
        )
        .disabled(true)

        AIChatSendButton(canSend: canSend, palette: palette, action: onSend)
      }
      .padding(.leading, 16)
      .padding(.trailing, 8)
      .padding(.vertical, 6)
    }
    .background(AIChatGlassRoundedRectangle(radius: isComposerExpanded ? 26 : 32, palette: palette))
    .overlay {
      if isSending {
        RoundedRectangle(cornerRadius: isComposerExpanded ? 26 : 32, style: .continuous)
          .stroke(palette.glassStroke.opacity(1.25), lineWidth: 1)
          .shimmer(active: true)
      }
    }
  }
}

private struct AIChatTextInput: View {
  @Binding var text: String
  var isInputFocused: FocusState<Bool>.Binding
  let isSending: Bool
  let isBlocked: Bool
  let isComposerExpanded: Bool
  let palette: AIChatPalette
  let onSubmit: () -> Void

  var body: some View {
    ZStack(alignment: .leading) {
      if text.isEmpty {
        Text("Pergunte a AI do Beco")
          .font(.system(size: 17, weight: .regular))
          .foregroundStyle(palette.mutedForeground)
          .lineLimit(1)
      }

      TextField("", text: $text, axis: .vertical)
        .focused(isInputFocused)
        .lineLimit(1...5)
        .font(.system(size: 17, weight: .regular))
        .foregroundStyle(palette.foreground)
        .tint(.blue)
        .submitLabel(.send)
        .onSubmit(onSubmit)
        .disabled(isSending || isBlocked)
    }
    .padding(.vertical, isComposerExpanded ? 3 : 7)
  }
}

private struct AIChatSendButton: View {
  let canSend: Bool
  let palette: AIChatPalette
  let action: () -> Void

  var body: some View {
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
    .accessibilityLabel(canSend ? "Enviar pergunta" : "Aguardando pergunta")
    .disabled(!canSend)
  }
}

private struct AIChatComposerIconButton: View {
  let systemName: String
  let accessibilityLabel: String
  let palette: AIChatPalette

  var body: some View {
    Button {} label: {
      Image(systemName: systemName)
        .font(.system(size: 22, weight: .regular))
        .frame(width: 30, height: 34)
        .foregroundStyle(palette.foreground)
    }
    .accessibilityLabel(accessibilityLabel)
  }
}

private struct AIChatGlassCapsule: View {
  let palette: AIChatPalette

  var body: some View {
    Capsule(style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(Capsule(style: .continuous).stroke(palette.glassStroke, lineWidth: 1))
      .shadow(color: palette.glassShadow, radius: 18, x: 0, y: 10)
  }
}

private struct AIChatGlassRoundedRectangle: View {
  let radius: CGFloat
  let palette: AIChatPalette

  var body: some View {
    RoundedRectangle(cornerRadius: radius, style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(
        RoundedRectangle(cornerRadius: radius, style: .continuous)
          .stroke(palette.glassStroke, lineWidth: 1)
      )
      .shadow(color: palette.glassShadow, radius: 24, x: 0, y: 14)
  }
}

private struct Message: Identifiable {
  let id = UUID()
  let role: String
  let text: String
  var tools: [String] = []
}

private struct ShimmerModifier: ViewModifier {
  let active: Bool
  @State private var phase: CGFloat = -0.8

  func body(content: Content) -> some View {
    content
      .overlay {
        if active {
          GeometryReader { proxy in
            let width = proxy.size.width
            LinearGradient(
              colors: [.clear, .white.opacity(0.28), .clear],
              startPoint: .top,
              endPoint: .bottom
            )
            .rotationEffect(.degrees(18))
            .offset(x: width * phase)
            .blendMode(.screen)
          }
          .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
          .allowsHitTesting(false)
        }
      }
      .onAppear {
        guard active else { return }
        withAnimation(.linear(duration: 1.25).repeatForever(autoreverses: false)) {
          phase = 0.9
        }
      }
  }
}

private extension View {
  func shimmer(active: Bool) -> some View {
    modifier(ShimmerModifier(active: active))
  }
}
