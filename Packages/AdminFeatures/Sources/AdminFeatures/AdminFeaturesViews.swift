import SwiftUI
import Models
import Network
import Persistence
import DesignSystem

public struct ActivitiesManagementView: View {
  private let repository: ChecklistRepository
  @State private var activities: [Activity] = []
  @State private var createSheet: ActivityCreateSheet?
  @State private var editSheet: ActivityEditSheet?
  @State private var deleteConfirm: ActivityDeleteConfirm?

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    List {
      Section {
        BecoPageHeader(title: "Atividades", subtitle: "Rotinas operacionais cadastradas")
          .listRowInsets(EdgeInsets())
          .listRowSeparator(.hidden)
          .themedListRowBackground()
      }
      Section {
        Button("Nova atividade") {
          createSheet = ActivityCreateSheet()
        }
        .themedListRowBackground()
      } header: {
        themedSectionHeader("Ações")
      }
      ActivityListSection(
        activities: activities,
        onEdit: { editSheet = ActivityEditSheet(activity: $0) },
        onDelete: { deleteConfirm = ActivityDeleteConfirm(activity: $0) }
      )
    }
    .themedListStyle()
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .task { reload() }
    .sheet(item: $createSheet) { _ in
      ActivityFormSheet(
        title: "Nova atividade",
        initialName: "",
        initialArea: .atendimento,
        onSave: { name, area in
          try? repository.insertActivity(
            Activity(id: 0, name: name, area: area, frequency: .diario)
          )
          reload()
          createSheet = nil
        },
        onCancel: { createSheet = nil }
      )
    }
    .sheet(item: $editSheet) { sheet in
      ActivityFormSheet(
        title: "Editar atividade",
        initialName: sheet.activity.name,
        initialArea: sheet.activity.area,
        onSave: { name, area in
          try? repository.updateActivity(
            id: sheet.activity.id,
            name: name,
            area: area,
            frequency: sheet.activity.frequency
          )
          reload()
          editSheet = nil
        },
        onCancel: { editSheet = nil }
      )
    }
    .alert(item: $deleteConfirm) { confirm in
      Alert(
        title: Text("Excluir atividade"),
        message: Text("Remover \"\(confirm.activity.name)\"? Esta ação não pode ser desfeita localmente."),
        primaryButton: .destructive(Text("Excluir")) {
          try? repository.deleteActivity(id: confirm.activity.id)
          reload()
        },
        secondaryButton: .cancel()
      )
    }
  }

  private func reload() {
    activities = (try? repository.allActivities()) ?? []
  }
}

private struct ActivityCreateSheet: Identifiable {
  let id = UUID()
}

private struct ActivityEditSheet: Identifiable {
  let activity: Activity
  var id: Int64 { activity.id }
}

private struct ActivityDeleteConfirm: Identifiable {
  let activity: Activity
  var id: Int64 { activity.id }
}

private struct ActivityFormSheet: View {
  let title: String
  let initialName: String
  let initialArea: Area
  let onSave: (String, Area) -> Void
  let onCancel: () -> Void

  @State private var name: String
  @State private var area: Area

  init(
    title: String,
    initialName: String,
    initialArea: Area,
    onSave: @escaping (String, Area) -> Void,
    onCancel: @escaping () -> Void
  ) {
    self.title = title
    self.initialName = initialName
    self.initialArea = initialArea
    self.onSave = onSave
    self.onCancel = onCancel
    _name = State(initialValue: initialName)
    _area = State(initialValue: initialArea)
  }

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
      .navigationTitle(title)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Cancelar", action: onCancel)
        }
        ToolbarItem(placement: .confirmationAction) {
          Button("Salvar") {
            onSave(name.trimmingCharacters(in: .whitespaces), area)
          }
          .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
  }
}

public struct PermissionManagementView: View {
  private let repository: ChecklistRepository
  private let userClient: UserClient?
  private let authToken: String?
  @State private var users: [User] = []
  @State private var editSheet: PermissionEditSheet?
  @State private var loadError: String?

  public init(
    repository: ChecklistRepository,
    userClient: UserClient? = nil,
    authToken: String? = nil
  ) {
    self.repository = repository
    self.userClient = userClient
    self.authToken = authToken
  }

  public var body: some View {
    List {
      Section {
        BecoPageHeader(title: "Equipe", subtitle: "Usuários e permissões de acesso")
          .listRowInsets(EdgeInsets())
          .listRowSeparator(.hidden)
          .themedListRowBackground()
      }
      if let loadError {
        Section {
          Text(loadError)
            .foregroundColor(.secondary)
            .themedListRowBackground()
        }
      }
      Section {
        ForEach(users, id: \.id) { user in
          Button {
            editSheet = PermissionEditSheet(user: user)
          } label: {
            HStack {
              Text(user.name)
              Spacer()
              Text(user.permissionLevel.rawValue)
                .font(.caption)
                .foregroundColor(.secondary)
            }
          }
          .buttonStyle(.plain)
          .themedListRowBackground()
        }
      } header: {
        themedSectionHeader("Usuários")
      }
    }
    .themedListStyle()
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .task { await reloadUsers() }
    .sheet(item: $editSheet) { sheet in
      PermissionEditorSheet(
        user: sheet.user,
        repository: repository,
        userClient: userClient,
        authToken: authToken,
        onSaved: {
          Task { await reloadUsers() }
          editSheet = nil
        },
        onCancel: { editSheet = nil }
      )
    }
  }

  private func reloadUsers() async {
    if let userClient, let authToken, !authToken.isEmpty {
      do {
        let remoteUsers = try await userClient.listUsers(token: authToken)
        try repository.upsertRemoteUsers(remoteUsers)
        users = (try? repository.allUsers()) ?? []
        loadError = nil
        return
      } catch {
        loadError = error.localizedDescription
      }
    }
    users = (try? repository.allUsers()) ?? []
  }
}

private struct PermissionEditSheet: Identifiable {
  let user: User
  var id: Int64 { user.id }
}

private struct PermissionEditorSheet: View {
  let user: User
  let repository: ChecklistRepository
  let userClient: UserClient?
  let authToken: String?
  let onSaved: () -> Void
  let onCancel: () -> Void

  @State private var permissions: FeaturePermissions
  @State private var saveError: String?

  init(
    user: User,
    repository: ChecklistRepository,
    userClient: UserClient? = nil,
    authToken: String? = nil,
    onSaved: @escaping () -> Void,
    onCancel: @escaping () -> Void
  ) {
    self.user = user
    self.repository = repository
    self.userClient = userClient
    self.authToken = authToken
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
          Text(user.email).font(.caption).foregroundColor(.secondary)
            .themedListRowBackground()
        } header: {
          themedSectionHeader("Usuário")
        }
        PermissionTogglesSection(permissions: $permissions)
        if let saveError {
          Section {
            Text(saveError).foregroundColor(.red)
              .themedListRowBackground()
          }
        }
        Section {
          BecoButton("Salvar permissões") {
            Task { await savePermissions() }
          }
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

  private func savePermissions() async {
    if let userClient,
       let authToken,
       !authToken.isEmpty,
       let remoteId = user.remoteId,
       !remoteId.isEmpty {
      do {
        let updated = try await userClient.updatePermissions(
          token: authToken,
          userId: remoteId,
          permissions: permissions
        )
        try repository.updateUserPermissions(userId: user.id, permissions: updated.featurePermissions)
        saveError = nil
        onSaved()
        return
      } catch {
        saveError = error.localizedDescription
        return
      }
    }
    try? repository.updateUserPermissions(userId: user.id, permissions: permissions)
    onSaved()
  }
}

private struct ActivityListSection: View {
  let activities: [Activity]
  let onEdit: (Activity) -> Void
  let onDelete: (Activity) -> Void

  var body: some View {
    Section {
      if activities.isEmpty {
        Text("Nenhuma atividade cadastrada.")
          .foregroundColor(.secondary)
          .themedListRowBackground()
      } else {
        ForEach(activities) { activity in
          Button {
            onEdit(activity)
          } label: {
            HStack {
              VStack(alignment: .leading, spacing: 4) {
                Text(activity.name).font(.headline)
                Text(activity.area.displayName)
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
              Spacer()
              Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.secondary)
            }
          }
          .buttonStyle(.plain)
          .themedListRowBackground()
          .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
              onDelete(activity)
            } label: {
              Label("Excluir", systemImage: "trash")
            }
          }
        }
      }
    } header: {
      themedSectionHeader("Cadastradas")
    }
  }
}

private struct PermissionTogglesSection: View {
  @Binding var permissions: FeaturePermissions

  var body: some View {
    Section {
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
      Toggle("Importar compras", isOn: $permissions.canImportPurchases)
        .themedListRowBackground()
    } header: {
      themedSectionHeader("Permissões")
    }
  }
}
