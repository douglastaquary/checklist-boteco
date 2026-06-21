import SwiftUI
import Models
import Persistence

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
    .navigationTitle("Atividades")
    .task { activities = (try? repository.allActivities()) ?? [] }
  }
}

public struct PermissionManagementView: View {
  private let repository: ChecklistRepository
  @State private var users: [User] = []
  @State private var selectedUserId: Int64?
  @State private var permissions = FeaturePermissions.default

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    Form {
      Picker("Usuário", selection: $selectedUserId) {
        Text("Selecione").tag(Optional<Int64>.none)
        ForEach(users, id: \.id) { user in
          Text(user.name).tag(Optional(user.id))
        }
      }
      .onChange(of: selectedUserId) { newValue in
        if let user = users.first(where: { $0.id == newValue }) {
          permissions = user.featurePermissions
        }
      }
      PermissionTogglesSection(permissions: $permissions)
      Section {
        Button("Salvar permissões") {
          if let selectedUserId {
            try? repository.updateUserPermissions(userId: selectedUserId, permissions: permissions)
            users = (try? repository.allUsers()) ?? []
          }
        }
      }
    }
    .navigationTitle("Permissões")
    .task { users = (try? repository.allUsers()) ?? [] }
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
