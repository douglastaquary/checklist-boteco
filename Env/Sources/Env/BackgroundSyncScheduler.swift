#if os(iOS)
import BackgroundTasks
import Foundation

public enum BackgroundSyncScheduler {
  public static let taskIdentifier = "com.checklistboteco.ios.sync"

  public static func register(syncController: SyncController) {
    BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
      guard let refreshTask = task as? BGAppRefreshTask else {
        task.setTaskCompleted(success: false)
        return
      }
      handleAppRefresh(task: refreshTask, syncController: syncController)
    }
  }

  public static func schedule() {
    let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
    request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
    try? BGTaskScheduler.shared.submit(request)
  }

  private static func handleAppRefresh(task: BGAppRefreshTask, syncController: SyncController) {
    schedule()
    task.expirationHandler = { task.setTaskCompleted(success: false) }
    Task {
      await syncController.syncOnce()
      task.setTaskCompleted(success: true)
    }
  }
}
#endif
