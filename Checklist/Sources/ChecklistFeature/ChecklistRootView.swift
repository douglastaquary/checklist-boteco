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

  private var accessibleAreas: [Area] {
    user.checklistAccessibleAreas
  }

  @State private var selectedArea: Area
  @State private var items: [ActivityWithCompletion] = []
  @State private var selectedFilter: ChecklistViewFilter = .all
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
    let areas = user.checklistAccessibleAreas
    let initialArea = areas.first ?? user.workSector.checklistAreas.first ?? .atendimento
    _selectedArea = State(initialValue: initialArea)
  }

  public var body: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
        BecoSegmentedFilter(
          options: ChecklistViewFilter.allCases.map { filter in
            (filter, filter.label, count(for: filter))
          },
          selected: $selectedFilter
        )
        if accessibleAreas.count > 1 {
          BecoSegmentedFilter(
            options: accessibleAreas.map { ($0, $0.displayName, nil) },
            selected: $selectedArea
          )
        }
        Divider().padding(.vertical, BecoTokens.Spacing.xs)
        Text(sectionTitle).font(.headline)
        if visibleItems.isEmpty {
          VStack(spacing: BecoTokens.Spacing.xs) {
            Text("Nenhuma atividade").font(.headline)
            Text(emptyMessage).font(.subheadline).foregroundStyle(BecoTokens.ColorToken.muted)
          }
          .frame(maxWidth: .infinity)
          .padding(.vertical, BecoTokens.Spacing.xxl)
        } else {
          ForEach(Array(visibleItems.enumerated()), id: \.element.id) { index, item in
            BecoTaskRow(
              title: item.activity.name,
              metadata: "\(item.activity.frequency.displayName) · esforço \(item.activity.effort)",
              completed: item.completion != nil,
              onSelect: { onSelectActivity?(item.activity.id, selectedArea) },
              onComplete: { cameraCapture = CameraCaptureRequest(activityId: item.activity.id) }
            )
            if index < visibleItems.count - 1 { Divider() }
          }
        }
      }
      .padding(.horizontal, BecoTokens.Spacing.md)
      .padding(.bottom, BecoTokens.Spacing.xl)
    }
    .background(BecoTokens.ColorToken.background.ignoresSafeArea())
    .toolbar(.hidden, for: .navigationBar)
    .safeAreaInset(edge: .top, spacing: 0) {
      BecoUserHeader(
        name: user.name,
        role: user.workSector.displayName,
        date: Date.now.formatted(date: .abbreviated, time: .omitted),
        onLogout: onLogout
      )
      .background(BecoTokens.ColorToken.background)
    }
    .task(id: selectedArea) { await reload() }
    .sheet(item: $cameraCapture, onDismiss: { Task { await reload() } }) { request in
      CameraCaptureView { path in
        guard let path else { return }
        Task { await complete(activityId: request.activityId, imagePath: path) }
      }
    }
    .alert(item: $alert) { item in
      Alert(
        title: Text(item.title),
        message: Text(item.message),
        dismissButton: .default(Text("OK"))
      )
    }
  }

  private var sectionTitle: String {
    accessibleAreas.count == 1 ? accessibleAreas[0].displayName : "Atividades"
  }

  private var visibleItems: [ActivityWithCompletion] {
    switch selectedFilter {
    case .all: return items
    case .pending: return items.filter { $0.completion == nil }
    case .completed: return items.filter { $0.completion != nil }
    }
  }

  private func count(for filter: ChecklistViewFilter) -> Int {
    switch filter {
    case .all: return items.count
    case .pending: return items.filter { $0.completion == nil }.count
    case .completed: return items.filter { $0.completion != nil }.count
    }
  }

  private var emptyMessage: String {
    switch selectedFilter {
    case .all: return "Não há atividades para \(selectedArea.displayName)."
    case .pending: return "Todas as atividades visíveis foram concluídas."
    case .completed: return "Nenhuma atividade foi concluída ainda."
    }
  }

  @MainActor
  private func reload() async {
    guard user.canAccessChecklistArea(selectedArea) else {
      items = []
      return
    }
    do {
      items = try repository.activitiesByArea(selectedArea)
    } catch {
      alert = ChecklistAlert(title: "Erro", message: error.localizedDescription)
    }
  }

  @MainActor
  private func complete(activityId: Int64, imagePath: String?) async {
    do {
      try repository.completeActivity(activityId: activityId, userId: user.id, imagePath: imagePath, isLate: false)
      syncController.requestSync()
      await reload()
      if imagePath != nil {
        alert = ChecklistAlert(title: "Checklist", message: "Atividade concluída.")
      }
    } catch {
      alert = ChecklistAlert(title: "Erro", message: error.localizedDescription)
    }
  }
}

private enum ChecklistViewFilter: String, CaseIterable, Hashable {
  case all, pending, completed
  var label: String {
    switch self {
    case .all: return "Todas"
    case .pending: return "Pendentes"
    case .completed: return "Concluídas"
    }
  }
}

private struct CameraCaptureRequest: Identifiable {
  let activityId: Int64
  var id: Int64 { activityId }
}

private struct ChecklistAlert: Identifiable {
  let title: String
  let message: String
  var id: String { "\(title)-\(message)" }
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
          Text(item.activity.frequency.displayName).font(.caption).foregroundColor(.secondary)
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
    if UIImagePickerController.isSourceTypeAvailable(.camera) {
      picker.sourceType = .camera
    } else if UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
      picker.sourceType = .photoLibrary
    }
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
