import SwiftUI
import Models
import Persistence
import Env
import DesignSystem

public struct ChecklistRootView: View {
  private let user: User
  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let onLogout: () -> Void
  private let onSelectActivity: ((Int64, Area) -> Void)?

  @State private var selectedArea: Area = .atendimento
  @State private var items: [ActivityWithCompletion] = []
  @State private var cameraCapture: CameraCaptureRequest?
  @State private var alert: ChecklistAlert?

  public init(
    user: User,
    repository: ChecklistRepository,
    syncController: SyncController,
    onLogout: @escaping () -> Void,
    onSelectActivity: ((Int64, Area) -> Void)? = nil
  ) {
    self.user = user
    self.repository = repository
    self.syncController = syncController
    self.onLogout = onLogout
    self.onSelectActivity = onSelectActivity
  }

  public var body: some View {
    VStack {
      Picker("Área", selection: $selectedArea) {
        ForEach(Area.allCases.filter { user.canAccessArea($0) }, id: \.self) { area in
          Text(area.displayName).tag(area)
        }
      }
      .pickerStyle(.segmented)
      .padding()

      List(items) { item in
        ActivityChecklistRow(
          item: item,
          onSelect: { onSelectActivity?(item.activity.id, selectedArea) },
          onMarkComplete: {
            cameraCapture = CameraCaptureRequest(activityId: item.activity.id)
          }
        )
        .themedListRowBackground()
      }
      .themedListStyle()
    }
    .navigationTitle("Checklist")
    .toolbar {
      ToolbarItem(placement: .navigationBarTrailing) {
        Button("Sair", action: onLogout)
      }
    }
    .task(id: selectedArea) { await reload() }
    .sheet(item: $cameraCapture) { request in
      CameraCaptureView { path in
        Task { await complete(activityId: request.activityId, imagePath: path) }
      }
    }
    .alert(item: $alert) { item in
      Alert(
        title: Text("Erro"),
        message: Text(item.message),
        dismissButton: .cancel(Text("OK"))
      )
    }
  }

  @MainActor
  private func reload() async {
    do {
      items = try repository.activitiesByArea(selectedArea)
    } catch {
      alert = ChecklistAlert(message: error.localizedDescription)
    }
  }

  @MainActor
  private func complete(activityId: Int64, imagePath: String?) async {
    do {
      try repository.completeActivity(activityId: activityId, userId: user.id, imagePath: imagePath, isLate: false)
      syncController.requestSync()
      await reload()
    } catch {
      alert = ChecklistAlert(message: error.localizedDescription)
    }
  }
}

private struct CameraCaptureRequest: Identifiable {
  let activityId: Int64
  var id: Int64 { activityId }
}

private struct ChecklistAlert: Identifiable {
  let message: String
  var id: String { message }
}

private struct ActivityChecklistRow: View {
  let item: ActivityWithCompletion
  let onSelect: (() -> Void)?
  let onMarkComplete: () -> Void

  var body: some View {
    HStack {
      Button(action: { onSelect?() }) {
        VStack(alignment: .leading) {
          Text(item.activity.name).font(.headline)
          Text(item.activity.frequency.displayName).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
      }
      .buttonStyle(.plain)
      .disabled(onSelect == nil)
      Toggle(
        "",
        isOn: Binding(
          get: { item.completion != nil },
          set: { enabled in
            if enabled, item.completion == nil {
              onMarkComplete()
            }
          }
        )
      )
      .disabled(item.completion != nil)
    }
  }
}

struct CameraCaptureView: UIViewControllerRepresentable {
  let onCapture: (String?) -> Void

  func makeUIViewController(context: Context) -> UIImagePickerController {
    let picker = UIImagePickerController()
    picker.sourceType = .camera
    picker.delegate = context.coordinator
    return picker
  }

  func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

  func makeCoordinator() -> Coordinator { Coordinator(onCapture: onCapture) }

  final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    let onCapture: (String?) -> Void
    init(onCapture: @escaping (String?) -> Void) { self.onCapture = onCapture }

    func imagePickerController(
      _ picker: UIImagePickerController,
      didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
      picker.dismiss(animated: true)
      guard let image = info[.originalImage] as? UIImage,
            let data = image.jpegData(compressionQuality: 0.85)
      else {
        onCapture(nil)
        return
      }
      let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("completion-\(UUID().uuidString).jpg")
      try? data.write(to: url)
      onCapture(url.path)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
      picker.dismiss(animated: true)
      onCapture(nil)
    }
  }
}

import UIKit
