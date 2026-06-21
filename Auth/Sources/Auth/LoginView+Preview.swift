#if os(iOS) && DEBUG
import SwiftUI
import Env
import Persistence
import DesignSystem

struct LoginView_Previews: PreviewProvider {
  static var previews: some View {
    Group {
      LoginView(onLoginSuccess: {}, onRegisterTap: {})
        .previewDisplayName("Credenciais")
      LoginView(
        onLoginSuccess: {},
        onRegisterTap: {},
        debugPhase: .biometricUnlock,
        debugUsername: "admin@checklistboteco.com"
      )
      .previewDisplayName("Biometria")
      LoginView(
        onLoginSuccess: {},
        onRegisterTap: {},
        debugPhase: .twoFactor,
        debugTwoFactorHint: "Código de desenvolvimento: 123456",
        debugUsername: "admin@checklistboteco.com"
      )
      .previewDisplayName("2FA")
    }
    .environmentObject(previewSession)
    .environmentObject(AppTheme.shared)
  }

  private static var previewSession: AppSession {
    let db = try! AppDatabase.inMemory()
    let repository = ChecklistRepository(dbQueue: db)
    return AppSession(repository: repository, authClient: nil, deviceId: "preview-device")
  }
}
#endif
