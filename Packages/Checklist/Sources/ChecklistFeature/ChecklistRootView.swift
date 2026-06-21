import SwiftUI
import Models
import Persistence
import Env
import DesignSystem
public struct ChecklistRootView: View {
  @EnvironmentObject private var session: AppSession

  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let onLogout: () -> Void

  @State private var selectedArea: Area = .atendimento
  @State private var items: [ActivityWithCompletion] = []
  @State private var showCamera = false
  @State private var pendingActivityId: Int64?
  @State private var errorMessage: String?

  public init(repository: ChecklistRepository, syncController: SyncController, onLogout: @escaping () -> Void) {
    self.repository = repository
    self.syncController = syncController
    self.onLogout = onLogout
  }

  public var body: some View {
    NavigationStack {
      VStack {
        Picker("Área", selection: $selectedArea) {
          ForEach(Area.allCases.filter { session.currentUser?.canAccessArea($0) == true }, id: \.self) { area in
            Text(area.displayName).tag(area)
          }
        }
        .pickerStyle(.segmented)
        .padding()

        List(items) { item in
          HStack {
            VStack(alignment: .leading) {
              Text(item.activity.name).font(.headline)
              Text(item.activity.frequency.displayName).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Toggle(
              "",
              isOn: Binding(
                get: { item.completion != nil },
                set: { enabled in
                  if enabled, item.completion == nil {
                    pendingActivityId = item.activity.id
                    showCamera = true
                  }
                }
              )
            )
            .disabled(item.completion != nil)
          }
        }
      }
      .navigationTitle("Checklist")
      .toolbar {
        ToolbarItem(placement: .navigationBarTrailing) {
          Button("Sair", action: onLogout)
        }
      }
      .task(id: selectedArea) { await reload() }
      .sheet(isPresented: $showCamera) {
        CameraCaptureView { path in
          Task { await complete(activityId: pendingActivityId, imagePath: path) }
        }
      }
      .alert("Erro", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
        Button("OK", role: .cancel) {}
      } message: {
        Text(errorMessage ?? "")
      }
    }
  }

  private func reload() async {
    do {
      items = try repository.activitiesByArea(selectedArea)
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  private func complete(activityId: Int64?, imagePath: String?) async {
    guard let activityId, let userId = session.currentUser?.id else { return }
    do {
      try repository.completeActivity(activityId: activityId, userId: userId, imagePath: imagePath, isLate: false)
      syncController.requestSync()
      await reload()
    } catch {
      errorMessage = error.localizedDescription
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
