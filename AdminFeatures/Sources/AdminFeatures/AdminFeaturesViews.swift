import SwiftUI
import Models
import Persistence
import DesignSystem

public struct ActivitiesManagementView: View {
  private let repository: ChecklistRepository
  @State private var name = ""
  @State private var area: Area = .atendimento
  @State private var activities: [Activity] = []

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    Form {
      ActivityCreateSection(name: $name, area: $area) {
        try? repository.insertActivity(
          Activity(id: 0, name: name, area: area, frequency: .daily)
        )
        name = ""
        activities = (try? repository.allActivities()) ?? []
      }
      ActivityListSection(activities: activities)
    }
    .themedFormStyle()
    .navigationTitle("Atividades")
    .task { activities = (try? repository.allActivities()) ?? [] }
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
          Text(user.email).font(.caption).foregroundStyle(.secondary)
        }
        PermissionTogglesSection(permissions: $permissions)
        Section {
          Button("Salvar permissões") {
            try? repository.updateUserPermissions(userId: user.id, permissions: permissions)
            onSaved()
          }
          .buttonStyle(PrimaryButtonStyle())
        }
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

private struct ActivityCreateSection: View {
  @Binding var name: String
  @Binding var area: Area
  let onAdd: () -> Void

  var body: some View {
    Section("Nova atividade") {
      TextField("Nome da atividade", text: $name)
      Picker("Área", selection: $area) {
        ForEach(Area.allCases, id: \.self) { Text($0.displayName).tag($0) }
      }
      Button("Adicionar", action: onAdd)
    }
  }
}

private struct ActivityListSection: View {
  let activities: [Activity]

  var body: some View {
    Section("Cadastradas") {
      ForEach(activities) { activity in
        Text("\(activity.name) — \(activity.area.displayName)")
      }
    }
  }
}

private struct PermissionTogglesSection: View {
  @Binding var permissions: FeaturePermissions

  var body: some View {
    Section("Permissões") {
      Toggle("Cadastrar usuários", isOn: $permissions.canRegisterUsers)
      Toggle("Criar atividades", isOn: $permissions.canCreateActivities)
      Toggle("Editar usuários", isOn: $permissions.canEditUsers)
      Toggle("Contagem de inventário", isOn: $permissions.canCreateInventoryCounts)
      Toggle("Insights de inventário", isOn: $permissions.canViewInventoryInsights)
      Toggle("Estoque administrativo", isOn: $permissions.canManageAdministrativeStock)
    }
  }
}
