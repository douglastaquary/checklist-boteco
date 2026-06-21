import SwiftUI
import Network

public struct GlobalFeedbackOverlay: View {
  @ObservedObject private var feedback = NetworkFeedback.shared
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public var body: some View {
    ZStack {
      if feedback.isLoading {
        Color.black.opacity(0.35).ignoresSafeArea()
        ProgressView()
          .tint(theme.tint)
      }
    }
    .allowsHitTesting(feedback.isLoading)
    .alert(item: Binding(
      get: { feedback.activeAlert },
      set: { if $0 == nil { feedback.dismissError() } }
    )) { alert in
      Alert(
        title: Text("Não foi possível concluir"),
        message: Text(alert.message),
        dismissButton: .default(Text("Entendi")) { feedback.dismissError() }
      )
    }
  }
}

public struct PrimaryButtonStyle: ButtonStyle {
  @EnvironmentObject private var theme: AppTheme

  public init() {}

  public func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .frame(maxWidth: .infinity)
      .padding()
      .background(theme.tint.opacity(configuration.isPressed ? 0.8 : 1))
      .foregroundColor(.white)
      .cornerRadius(12)
  }
}
