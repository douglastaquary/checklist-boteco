import SwiftUI
import Network

public struct GlobalFeedbackOverlay: View {
  @ObservedObject private var feedback = NetworkFeedback.shared

  public init() {}

  public var body: some View {
    ZStack {
      if feedback.isLoading {
        Color.black.opacity(0.35).ignoresSafeArea()
        ProgressView()
          .tint(AppColors.primary)
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

public enum AppColors {
  public static let primary = Color(red: 0.45, green: 0.25, blue: 0.10)
}

public struct PrimaryButtonStyle: ButtonStyle {
  public init() {}

  public func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .frame(maxWidth: .infinity)
      .padding()
      .background(AppColors.primary.opacity(configuration.isPressed ? 0.8 : 1))
      .foregroundColor(.white)
      .cornerRadius(12)
  }
}
