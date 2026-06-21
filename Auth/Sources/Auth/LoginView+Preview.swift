#if os(iOS) && DEBUG
import SwiftUI
import Env
import Persistence

struct LoginView_Previews: PreviewProvider {
  static var previews: some View {
    LoginView(onLoginSuccess: {}, onRegisterTap: {})
      .environmentObject(previewSession)
  }

  private static var previewSession: AppSession {
    let db = try! AppDatabase.inMemory()
    let repository = ChecklistRepository(dbQueue: db)
    return AppSession(repository: repository, authClient: nil, deviceId: "preview-device")
  }
}
#endif
