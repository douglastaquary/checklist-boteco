import SwiftUI
import Env

/// Executa `syncOnce` ao abrir a tela, garantindo dados atualizados do backend antes da UI local.
struct SyncRefreshingContainer<Content: View>: View {
  let syncController: SyncController
  @ViewBuilder let content: () -> Content

  var body: some View {
    content()
      .task {
        await syncController.syncOnce()
      }
  }
}
