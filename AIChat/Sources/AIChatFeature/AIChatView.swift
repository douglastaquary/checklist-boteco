import SwiftUI
import Network
import DesignSystem

public struct AIChatView: View {
  private let client: AIChatClient?
  private let token: String?
  @Environment(\.colorScheme) private var colorScheme
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

  private var isDark: Bool {
    colorScheme == .dark
  }

  private var foregroundColor: Color {
    isDark ? .white : .black
  }

  private var mutedForegroundColor: Color {
    isDark ? .white.opacity(0.62) : .secondary
  }

  private var glassStrokeColor: Color {
    isDark ? .white.opacity(0.14) : .black.opacity(0.08)
  }

  private var glassShadowColor: Color {
    isDark ? .black.opacity(0.42) : .black.opacity(0.12)
  }

  private var isComposerExpanded: Bool {
    isInputFocused && !text.isEmpty
  }

  public var body: some View {
    ZStack {
      screenBackground

      VStack(spacing: 0) {
        chatHeader
          .padding(.horizontal, 20)
          .padding(.top, 10)

        if let usage { usageBar(usage) }

        ScrollViewReader { proxy in
          ScrollView {
            LazyVStack(spacing: 14) {
              if messages.isEmpty { welcome }
              ForEach(messages) { bubble($0) }
              if isSending { processingBubble }
            }
            .padding(.horizontal, 18)
            .padding(.top, 18)
            .padding(.bottom, 34)
          }
          .scrollDismissesKeyboard(.interactively)
          .onChange(of: messages.count) { _ in if let last=messages.last { proxy.scrollTo(last.id,anchor:.bottom) } }
        }

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
    .safeAreaInset(edge: .bottom) { composer }
    .task { await loadUsage() }
  }

  private var screenBackground: some View {
    ZStack {
      LinearGradient(
        colors: isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground), Color(.secondarySystemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )

      Circle()
        .fill((isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.035)))
        .frame(width: 240, height: 240)
        .blur(radius: 70)
        .offset(x: -150, y: -240)

      Circle()
        .fill((isDark ? Color.white.opacity(0.05) : Color.white.opacity(0.75)))
        .frame(width: 320, height: 320)
        .blur(radius: 80)
        .offset(x: 150, y: 210)
    }
    .ignoresSafeArea()
  }

  private var chatHeader: some View {
    HStack(spacing: 12) {
      Image(systemName: "sparkles")
        .font(.system(size: 18, weight: .semibold))
        .foregroundStyle(foregroundColor)
        .frame(width: 44, height: 44)
        .background(glassCapsule)

      VStack(alignment: .leading, spacing: 2) {
        Text("AI do Beco")
          .font(.headline.weight(.semibold))
          .foregroundStyle(foregroundColor)
        Text("ChecklistBoteco · dados do Beco da Praia")
          .font(.caption)
          .foregroundStyle(mutedForegroundColor)
          .lineLimit(1)
      }

      Spacer(minLength: 8)

      HStack(spacing: 14) {
        Image(systemName: "square.and.pencil")
        Image(systemName: "ellipsis")
      }
      .font(.system(size: 17, weight: .semibold))
      .foregroundStyle(foregroundColor)
      .frame(height: 44)
      .padding(.horizontal, 16)
      .background(glassCapsule)
    }
  }

  private var welcome: some View {
    VStack(alignment: .leading, spacing: 18) {
      HStack(spacing: 12) {
        Image(systemName: "sparkles")
          .font(.title2.weight(.semibold))
          .foregroundStyle(foregroundColor)
          .frame(width: 42, height: 42)
          .background(Circle().fill(foregroundColor.opacity(isDark ? 0.12 : 0.06)))
        VStack(alignment: .leading, spacing: 3) {
          Text("Como posso ajudar na operação?")
            .font(.headline.weight(.semibold))
            .foregroundStyle(foregroundColor)
          Text("Dados de vendas, compras, estoque e ponto")
            .font(.subheadline)
            .foregroundStyle(mutedForegroundColor)
        }
      }

      VStack(alignment: .leading, spacing: 8) {
        Text("O que você quer entender hoje?")
          .font(.title3.bold())
          .foregroundStyle(foregroundColor)
        Text("Pergunte em linguagem simples. A resposta usa os dados importados e disponíveis no backend do Beco.")
          .font(.body)
          .foregroundStyle(mutedForegroundColor)
      }

      VStack(alignment: .leading, spacing: 10) {
        suggestion("Quanto vendemos este mês?", icon: "chart.line.uptrend.xyaxis")
        suggestion("Quantas Heinekens vendemos em março?", icon: "magnifyingglass")
        suggestion("Houve perdas no estoque hoje?", icon: "shippingbox")
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(20)
    .background(glassRoundedRectangle(radius: 28))
  }

  private func suggestion(_ value: String, icon: String) -> some View {
    Button { text=value } label: {
      HStack(spacing: 8) {
        Image(systemName: icon)
          .font(.caption.weight(.semibold))
        Text(value)
          .font(.subheadline.weight(.medium))
          .lineLimit(2)
      }
      .foregroundStyle(foregroundColor)
      .padding(.horizontal, 12)
      .padding(.vertical, 9)
      .background(Capsule(style: .continuous).fill(foregroundColor.opacity(isDark ? 0.10 : 0.06)))
    }
    .buttonStyle(.plain)
  }

  private func bubble(_ message: Message) -> some View {
    let isUser = message.role == "user"
    return VStack(alignment: isUser ? .trailing : .leading, spacing: 6) {
      Text(message.text)
        .font(.body)
        .foregroundStyle(isUser ? .white : foregroundColor)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
          RoundedRectangle(cornerRadius: 19, style: .continuous)
            .fill(isUser ? Color.black.opacity(0.82) : Color.clear)
            .background {
              if !isUser { glassRoundedRectangle(radius: 19) }
            }
        )

      if !message.tools.isEmpty {
        Text("Fontes: \(message.tools.map(toolLabel).joined(separator: ", "))")
          .font(.caption2)
          .foregroundStyle(mutedForegroundColor)
      }
    }
    .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
    .padding(.leading, isUser ? 54 : 0)
    .padding(.trailing, isUser ? 0 : 42)
    .id(message.id)
  }

  private var composer: some View {
    VStack(spacing: 10) {
      if isSending {
        processingPill
      }

      composerInputBar
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

  private var processingPill: some View {
    HStack(spacing: 8) {
      Image(systemName: "sparkles")
        .font(.caption.weight(.bold))
      Text("Consultando dados do Beco")
      Spacer(minLength: 0)
    }
    .font(.caption.weight(.semibold))
    .foregroundStyle(mutedForegroundColor)
    .padding(.horizontal, 14)
    .padding(.vertical, 7)
    .background(glassCapsule)
    .frame(maxWidth: 260)
    .shimmer(active: true)
  }

  private var composerInputBar: some View {
    VStack(spacing: isComposerExpanded ? 8 : 0) {
      if isComposerExpanded {
        textInput
          .padding(.horizontal, 18)
          .padding(.top, 14)
      }

      HStack(alignment: .center, spacing: 10) {
        composerIconButton(systemName: "plus", accessibilityLabel: "Adicionar contexto")
          .disabled(true)
          .opacity(0.75)

        if !isComposerExpanded {
          textInput
        }

        Spacer(minLength: isComposerExpanded ? 8 : 0)

        if isComposerExpanded {
          Text("Beco · médio")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(mutedForegroundColor)
        }

        composerIconButton(systemName: "mic", accessibilityLabel: "Entrada por voz")
          .disabled(true)

        sendButton
      }
      .padding(.leading, 16)
      .padding(.trailing, 8)
      .padding(.vertical, 6)
    }
    .background(glassRoundedRectangle(radius: isComposerExpanded ? 26 : 32))
    .overlay {
      if isSending {
        RoundedRectangle(cornerRadius: isComposerExpanded ? 26 : 32, style: .continuous)
          .stroke(glassStrokeColor.opacity(1.25), lineWidth: 1)
          .shimmer(active: true)
      }
    }
  }

  private var textInput: some View {
    ZStack(alignment: .leading) {
      if text.isEmpty {
        Text("Pergunte a AI do Beco")
          .font(.system(size: 17, weight: .regular))
          .foregroundStyle(mutedForegroundColor)
          .lineLimit(1)
      }

      TextField("", text: $text, axis: .vertical)
        .focused($isInputFocused)
        .lineLimit(1...5)
        .font(.system(size: 17, weight: .regular))
        .foregroundStyle(foregroundColor)
        .tint(.blue)
        .submitLabel(.send)
        .onSubmit { Task { await send() } }
        .disabled(isSending || usage?.blocked == true)
    }
    .padding(.vertical, isComposerExpanded ? 3 : 7)
  }

  private var sendButton: some View {
    Button { Task { await send() } } label: {
      ZStack {
        Circle()
          .fill(canSend ? foregroundColor : foregroundColor.opacity(isDark ? 0.10 : 0.08))
          .frame(width: 34, height: 34)
        Image(systemName: canSend ? "arrow.up" : "square.fill")
          .font(.system(size: canSend ? 18 : 14, weight: .semibold))
          .foregroundStyle(canSend ? (isDark ? Color.black : Color.white) : foregroundColor.opacity(0.68))
      }
    }
    .accessibilityLabel(canSend ? "Enviar pergunta" : "Aguardando pergunta")
    .disabled(!canSend)
  }

  private func composerIconButton(systemName: String, accessibilityLabel: String) -> some View {
    Button {} label: {
      Image(systemName: systemName)
        .font(.system(size: 22, weight: .regular))
        .frame(width: 30, height: 34)
        .foregroundStyle(foregroundColor)
    }
    .accessibilityLabel(accessibilityLabel)
  }

  private var processingBubble: some View {
    HStack(spacing: 10) {
      ForEach(0..<3, id: \.self) { index in
        Circle()
          .fill(Color.secondary.opacity(0.55))
          .frame(width: 8, height: 8)
          .scaleEffect(isSending ? 1.0 : 0.7)
          .animation(.easeInOut(duration: 0.8).repeatForever().delay(Double(index) * 0.16), value: isSending)
      }
      Text("Processando dados do Beco…")
        .font(.subheadline.weight(.medium))
        .foregroundStyle(mutedForegroundColor)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(14)
    .background(glassRoundedRectangle(radius: 18))
    .shimmer(active: true)
  }

  private func usageBar(_ value: AIUsageSummaryDTO) -> some View {
    let spent=Double(value.estimatedCostMicros)/1_000_000
    let limit=Double(value.monthlyLimitCents)/100
    return HStack { Text(String(format: "US$ %.2f de US$ %.2f",spent,limit)); Spacer(); Text("\(value.requests) consultas") }
      .font(.caption).foregroundStyle(value.blocked ? .red : mutedForegroundColor).padding(.horizontal,20).padding(.vertical,8)
  }

  private var glassCapsule: some View {
    Capsule(style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(Capsule(style: .continuous).stroke(glassStrokeColor, lineWidth: 1))
      .shadow(color: glassShadowColor, radius: 18, x: 0, y: 10)
  }

  private func glassRoundedRectangle(radius: CGFloat) -> some View {
    RoundedRectangle(cornerRadius: radius, style: .continuous)
      .fill(.ultraThinMaterial)
      .overlay(
        RoundedRectangle(cornerRadius: radius, style: .continuous)
          .stroke(glassStrokeColor, lineWidth: 1)
      )
      .shadow(color: glassShadowColor, radius: 24, x: 0, y: 14)
  }

  @MainActor private func send() async {
    guard let client, let token, !token.isEmpty else { errorMessage="Chat indisponível sem conexão com o backend."; return }
    let question=trimmedText
    guard !question.isEmpty, !isSending else { return }
    text=""; errorMessage=nil
    messages.append(Message(role:"user",text:question)); isSending=true; defer { isSending=false }
    do {
      let context=messages.suffix(4).map { AIChatMessageRequest(role:$0.role,text:$0.text) }
      let response=try await client.send(messages:Array(context),token:token)
      messages.append(Message(role:"assistant",text:response.answer,tools:response.consultedTools)); usage=response.budget
    } catch { errorMessage=error.localizedDescription }
  }
  @MainActor private func loadUsage() async { guard let client,let token else{return}; usage=try? await client.usage(token:token) }
  private func toolLabel(_ value:String)->String { if value.hasPrefix("sales") { return "vendas" }; if value.hasPrefix("purchases") { return "compras" }; if value.hasPrefix("inventory") { return "estoque" }; if value.hasPrefix("work_clock") { return "ponto" }; return value }
}

private struct Message: Identifiable {
  let id=UUID(); let role:String; let text:String; var tools:[String]=[]
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
        withAnimation(.linear(duration: 1.25).repeatForever(autoreverses: false)) { phase = 0.9 }
      }
  }
}

private extension View {
  func shimmer(active: Bool) -> some View {
    modifier(ShimmerModifier(active: active))
  }
}
