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
            if isSending { ProgressView("Consultando dados do Beco…").padding() }
          }
          .padding(16)
        }
        .onChange(of: messages.count) { _ in if let last=messages.last { proxy.scrollTo(last.id,anchor:.bottom) } }
      }
      if let errorMessage { Text(errorMessage).font(.footnote).foregroundStyle(.red).padding(.horizontal) }
      composer
    }
    .background(Color(.systemGroupedBackground))
    .navigationTitle("")
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
    HStack(alignment: .bottom, spacing: 10) {
      TextField("Pergunte sobre o Beco…", text: $text, axis: .vertical).lineLimit(1...4).textFieldStyle(.roundedBorder)
      Button { Task { await send() } } label: { Image(systemName: "arrow.up").font(.headline).frame(width: 42,height:42).background(Color.black).foregroundStyle(.white).clipShape(Circle()) }
        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending || usage?.blocked == true)
    }.padding().background(.ultraThinMaterial)
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
