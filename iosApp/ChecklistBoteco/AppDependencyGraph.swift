import SwiftUI
import Env
import Network

extension View {
  /// Instala dependências globais e tarefas de lifecycle do app shell.
  func withAppDependencyGraph(
    session: AppSession,
    syncController: SyncController
  ) -> some View {
    modifier(AppDependencyGraphModifier(session: session, syncController: syncController))
  }
}

private struct AppDependencyGraphModifier: ViewModifier {
  let session: AppSession
  let syncController: SyncController

  func body(content: Content) -> some View {
    content
      .environmentObject(session)
      .environmentObject(NetworkFeedback.shared)
      .task(id: session.authToken) {
        guard session.authToken != nil else { return }
        await syncController.syncOnce()
      }
      .onAppear {
        #if os(iOS)
        BackgroundSyncScheduler.register(syncController: syncController)
        BackgroundSyncScheduler.schedule()
        #endif
      }
  }
}
