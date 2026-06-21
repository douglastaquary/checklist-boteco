import SwiftUI
import Models
import Persistence
import DesignSystem

public struct ActivitiesManagementView: View {
  private let repository: ChecklistRepository
  @State private var activities: [Activity] = []
  @State private var createSheet: ActivityCreateSheet?

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    List {
      Section {
        Button("Nova atividade") {
          createSheet = ActivityCreateSheet()
        }
        .themedListRowBackground()
      }
      ActivityListSection(activities: activities)
    }
    .themedListStyle()
    .navigationTitle("Atividades")
    .task { reload() }
    .sheet(item: $createSheet) { _ in
      ActivityCreateSheetView(repository: repository) {
        reload()
        createSheet = nil
      } onCancel: {
        createSheet = nil
      }
    }
  }

  private func reload() {
    activities = (try? repository.allActivities()) ?? []
  }
}

private struct ActivityCreateSheet: Identifiable {
  let id = UUID()
}

private struct ActivityCreateSheetView: View {
  let repository: ChecklistRepository
  let onSaved: () -> Void
  let onCancel: () -> Void

  @State private var name = ""
  @State private var area: Area = .atendimento

  var body: some View {
    NavigationStack {
      Form {
        TextField("Nome da atividade", text: $name)
          .themedListRowBackground()
        Picker("Área", selection: $area) {
          ForEach(Area.allCases, id: \.self) { Text($0.displayName).tag($0) }
        }
        .themedListRowBackground()
      }
      .themedFormStyle()
      .navigationTitle("Nova atividade")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Cancelar", action: onCancel)
        }
        ToolbarItem(placement: .confirmationAction) {
          Button("Salvar") {
            try? repository.insertActivity(
              Activity(id: 0, name: name, area: area, frequency: .daily)
            )
            onSaved()
          }
          .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
  }
}

public struct PermissionManagementView: View {
  private let repository: ChecklistRepository
  @State private var users: [User] = []
  @State private var editSheet: PermissionEditSheet?

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    List {
      Section("Usuários") {
        ForEach(users, id: \.id) { user in
          Button {
            editSheet = PermissionEditSheet(user: user)
          } label: {
            HStack {
              Text(user.name)
              Spacer()
              Text(user.permissionLevel.rawValue)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
          }
          .buttonStyle(.plain)
          .themedListRowBackground()
        }
      }
    }
    .themedListStyle()
    .navigationTitle("Permissões")
    .task { users = (try? repository.allUsers()) ?? [] }
    .sheet(item: $editSheet) { sheet in
      PermissionEditorSheet(
        user: sheet.user,
        repository: repository,
        onSaved: {
          users = (try? repository.allUsers()) ?? []
          editSheet = nil
        },
        onCancel: { editSheet = nil }
      )
    }
  }
}

private struct PermissionEditSheet: Identifiable {
  let user: User
  var id: Int64 { user.id }
}

private struct PermissionEditorSheet: View {
  let user: User
  let repository: ChecklistRepository
  let onSaved: () -> Void
  let onCancel: () -> Void

  @State private var permissions: FeaturePermissions

  init(
    user: User,
    repository: ChecklistRepository,
    onSaved: @escaping () -> Void,
    onCancel: @escaping () -> Void
  ) {
    self.user = user
    self.repository = repository
    self.onSaved = onSaved
    self.onCancel = onCancel
    _permissions = State(initialValue: user.featurePermissions)
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          Text(user.name).font(.headline)
            .themedListRowBackground()
          Text(user.email).font(.caption).foregroundStyle(.secondary)
            .themedListRowBackground()
        }
        PermissionTogglesSection(permissions: $permissions)
        Section {
          Button("Salvar permissões") {
            try? repository.updateUserPermissions(userId: user.id, permissions: permissions)
            onSaved()
          }
          .buttonStyle(PrimaryButtonStyle())
        }
        .themedListRowBackground()
      }
      .themedFormStyle()
      .navigationTitle("Editar permissões")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Cancelar", action: onCancel)
        }
      }
    }
  }
}

private struct ActivityListSection: View {
  let activities: [Activity]

  var body: some View {
    Section("Cadastradas") {
      ForEach(activities) { activity in
        Text("\(activity.name) — \(activity.area.displayName)")
          .themedListRowBackground()
      }
    }
  }
}

private struct PermissionTogglesSection: View {
  @Binding var permissions: FeaturePermissions

  var body: some View {
    Section("Permissões") {
      Toggle("Cadastrar usuários", isOn: $permissions.canRegisterUsers)
        .themedListRowBackground()
      Toggle("Criar atividades", isOn: $permissions.canCreateActivities)
        .themedListRowBackground()
      Toggle("Editar usuários", isOn: $permissions.canEditUsers)
        .themedListRowBackground()
      Toggle("Contagem de inventário", isOn: $permissions.canCreateInventoryCounts)
        .themedListRowBackground()
      Toggle("Insights de inventário", isOn: $permissions.canViewInventoryInsights)
        .themedListRowBackground()
      Toggle("Estoque administrativo", isOn: $permissions.canManageAdministrativeStock)
        .themedListRowBackground()
    }
  }
}
