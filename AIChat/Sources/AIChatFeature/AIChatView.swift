import SwiftUI
import Network
import DesignSystem

public struct AIChatView: View {
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

  public var body: some View {
    VStack(spacing: 0) {
      BecoPageHeader(title: "Chat IA", subtitle: "Pergunte sobre o Beco da Praia")
        .padding(.horizontal, 20)
        .padding(.top, 8)
      if let usage { usageBar(usage) }
      ScrollViewReader { proxy in
        ScrollView {
          LazyVStack(spacing: 12) {
            if messages.isEmpty { welcome }
            ForEach(messages) { bubble($0) }
            if isSending { processingBubble }
          }
          .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: messages.count) { _ in if let last=messages.last { proxy.scrollTo(last.id,anchor:.bottom) } }
      }
      if let errorMessage { Text(errorMessage).font(.footnote).foregroundStyle(.red).padding(.horizontal) }
    }
    .background(Color(.systemGroupedBackground))
    .navigationTitle("")
    .safeAreaInset(edge: .bottom) { composer }
    .task { await loadUsage() }
  }

  private var welcome: some View {
    VStack(alignment: .leading, spacing: 12) {
      Image(systemName: "sparkles").font(.largeTitle)
      Text("O que você quer entender hoje?").font(.title3.bold())
      Text("Consulte vendas, compras, gastos, estoque, perdas e ponto usando os dados importados.").foregroundStyle(.secondary)
      suggestion("Quanto vendemos este mês?")
      suggestion("Quais foram os maiores gastos?")
      suggestion("Houve perdas no estoque hoje?")
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(18).background(.white).clipShape(RoundedRectangle(cornerRadius: 20))
  }

  private func suggestion(_ value: String) -> some View {
    Button(value) { text=value }.buttonStyle(.bordered).tint(.black)
  }

  private func bubble(_ message: Message) -> some View {
    VStack(alignment: message.role == "user" ? .trailing : .leading, spacing: 5) {
      Text(message.text).padding(12).background(message.role == "user" ? Color.black : Color.white).foregroundStyle(message.role == "user" ? .white : .primary).clipShape(RoundedRectangle(cornerRadius: 16))
      if !message.tools.isEmpty { Text("Fontes: \(message.tools.map(toolLabel).joined(separator: ", "))").font(.caption2).foregroundStyle(.secondary) }
    }.frame(maxWidth: .infinity, alignment: message.role == "user" ? .trailing : .leading).id(message.id)
  }

  private var composer: some View {
    VStack(spacing: 8) {
      if isSending {
        HStack(spacing: 8) {
          Circle().frame(width: 6, height: 6)
          Text("Consultando vendas, estoque e ponto")
          Spacer(minLength: 0)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(.white.opacity(0.72))
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(
          Capsule()
            .fill(Color.black.opacity(0.86))
            .overlay(Capsule().stroke(.white.opacity(0.1), lineWidth: 1))
        )
        .frame(maxWidth: 300)
        .shimmer(active: true)
      }

      HStack(alignment: .bottom, spacing: 12) {
        Button {} label: {
          Image(systemName: "plus")
            .font(.system(size: 24, weight: .regular))
            .frame(width: 32, height: 44)
            .foregroundStyle(.white)
        }
        .accessibilityLabel("Adicionar contexto")
        .disabled(true)

        ZStack(alignment: .leading) {
          if text.isEmpty {
            Text("Pergunte ao Codex")
              .font(.system(size: 20, weight: .regular))
              .foregroundStyle(.white.opacity(0.58))
          }
          TextField("", text: $text, axis: .vertical)
            .focused($isInputFocused)
            .lineLimit(1...4)
            .font(.system(size: 20, weight: .regular))
            .foregroundStyle(.white)
            .tint(.white)
            .submitLabel(.send)
            .onSubmit { Task { await send() } }
            .disabled(isSending || usage?.blocked == true)
        }

        Button { Task { await send() } } label: {
          Image(systemName: text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "mic" : "arrow.up")
            .font(.system(size: 24, weight: .semibold))
            .frame(width: 38, height: 44)
            .foregroundStyle(.white)
        }
        .accessibilityLabel("Enviar pergunta")
        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending || usage?.blocked == true)
      }
      .padding(.horizontal, 18)
      .padding(.vertical, 12)
      .background(
        Capsule()
          .fill(Color.black.opacity(0.9))
          .overlay(Capsule().stroke(.white.opacity(0.1), lineWidth: 1))
          .shadow(color: .black.opacity(0.2), radius: 24, x: 0, y: 14)
      )
      .overlay {
        if isSending {
          Capsule()
            .stroke(.white.opacity(0.14), lineWidth: 1)
            .shimmer(active: true)
        }
      }
    }
    .padding(.horizontal, 18)
    .padding(.top, 10)
    .padding(.bottom, 8)
    .background(.ultraThinMaterial)
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
        .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(14)
    .background(Color.white)
    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    .shimmer(active: true)
  }

  private func usageBar(_ value: AIUsageSummaryDTO) -> some View {
    let spent=Double(value.estimatedCostMicros)/1_000_000
    let limit=Double(value.monthlyLimitCents)/100
    return HStack { Text(String(format: "US$ %.2f de US$ %.2f",spent,limit)); Spacer(); Text("\(value.requests) consultas") }
      .font(.caption).foregroundStyle(value.blocked ? .red : .secondary).padding(.horizontal,20).padding(.vertical,8)
  }

  @MainActor private func send() async {
    guard let client, let token, !token.isEmpty else { errorMessage="Chat indisponível sem conexão com o backend."; return }
    let question=text.trimmingCharacters(in:.whitespacesAndNewlines); text=""; errorMessage=nil
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
