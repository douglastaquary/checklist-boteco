import SwiftUI
import Models
import Network
import Persistence
import DesignSystem

public struct ActivitiesManagementView: View {
  private let repository: ChecklistRepository
  private let embeddedInCodexSheet: Bool
  private let embeddedInParentNavigationStack: Bool
  private let onDismissSheet: (() -> Void)?

  @Environment(\.colorScheme) private var colorScheme
  @State private var path = NavigationPath()
  @State private var activities: [Activity] = []
  @State private var deleteConfirm: ActivityDeleteConfirm?

  public init(
    repository: ChecklistRepository,
    embeddedInCodexSheet: Bool = false,
    embeddedInParentNavigationStack: Bool = false,
    onDismissSheet: (() -> Void)? = nil
  ) {
    self.repository = repository
    self.embeddedInCodexSheet = embeddedInCodexSheet
    self.embeddedInParentNavigationStack = embeddedInParentNavigationStack
    self.onDismissSheet = onDismissSheet
  }

  /// Modal Codex owns its stack; tab/push embed into the parent `NavigationStack`.
  private var ownsNavigationStack: Bool {
    embeddedInCodexSheet || !embeddedInParentNavigationStack
  }

  private var palette: BecoCodexPalette {
    BecoCodexPalette(isDark: colorScheme == .dark)
  }

  public var body: some View {
    Group {
      if ownsNavigationStack {
        NavigationStack(path: $path) {
          activitiesRootWithDestinations
        }
      } else {
        activitiesRootWithDestinations
      }
    }
    .task { reload() }
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

  private var activitiesRootWithDestinations: some View {
    activitiesRoot
      .navigationDestination(for: ActivityAdminRoute.self) { route in
        activityDestination(route)
      }
  }

  private var activitiesRoot: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 20) {
        Text("Atividades")
          .font(.largeTitle.bold())
          .foregroundStyle(palette.foreground)
          .padding(.top, embeddedInCodexSheet ? BecoCodexSheetMetrics.chromeBarHeight : 8)

        Text("Rotinas operacionais cadastradas")
          .font(.subheadline)
          .foregroundStyle(palette.muted)

        BecoCodexGroupedSection(title: "Ações", palette: palette) {
          NavigationLink(value: ActivityAdminRoute.create) {
            BecoCodexRow(
              title: "Nova atividade",
              systemImage: "plus.circle",
              palette: palette,
              action: nil
            )
          }
          .buttonStyle(.plain)
        }

        BecoCodexGroupedSection(title: "Cadastradas", palette: palette) {
          if activities.isEmpty {
            BecoCodexRow(
              title: "Nenhuma atividade cadastrada.",
              showsChevron: false,
              palette: palette
            )
          } else {
            ForEach(Array(activities.enumerated()), id: \.element.id) { index, activity in
              NavigationLink(value: ActivityAdminRoute.edit(activity.id)) {
                BecoCodexRow(
                  title: activity.name,
                  subtitle: activity.area.displayName,
                  systemImage: "checklist",
                  palette: palette,
                  action: nil
                )
              }
              .buttonStyle(.plain)
              .contextMenu {
                Button(role: .destructive) {
                  deleteConfirm = ActivityDeleteConfirm(activity: activity)
                } label: {
                  Label("Excluir", systemImage: "trash")
                }
              }
              if index < activities.count - 1 {
                BecoCodexRowDivider(palette: palette)
              }
            }
          }
        }
      }
      .padding(.horizontal, 20)
      .padding(.bottom, 32)
    }
    .background {
      BecoCodexBackground(palette: palette)
    }
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
    .modifier(CodexSheetCloseIfNeeded(
      enabled: embeddedInCodexSheet,
      palette: palette,
      onDismiss: onDismissSheet
    ))
  }

  @ViewBuilder
  private func activityDestination(_ route: ActivityAdminRoute) -> some View {
    switch route {
    case .create:
      ActivityEditorView(
        title: "Nova atividade",
        initialName: "",
        initialArea: .atendimento,
        palette: palette,
        onSave: { name, area in
          try? repository.insertActivity(
            Activity(id: 0, name: name, area: area, frequency: .diario)
          )
          reload()
        }
      )
    case .edit(let id):
      if let activity = activities.first(where: { $0.id == id }) {
        ActivityEditorView(
          title: "Editar atividade",
          initialName: activity.name,
          initialArea: activity.area,
          palette: palette,
          onSave: { name, area in
            try? repository.updateActivity(
              id: activity.id,
              name: name,
              area: area,
              frequency: activity.frequency
            )
            reload()
          }
        )
      } else {
        BecoCodexDetailChrome(title: "Atividade", palette: palette) {
          Text("Atividade não encontrada.")
            .foregroundStyle(palette.muted)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
      }
    }
  }

  private func reload() {
    activities = (try? repository.allActivities()) ?? []
  }
}

public struct PermissionManagementView: View {
  private let repository: ChecklistRepository
  private let userClient: UserClient?
  private let authToken: String?
  private let embeddedInCodexSheet: Bool
  private let embeddedInParentNavigationStack: Bool
  private let onDismissSheet: (() -> Void)?

  @Environment(\.colorScheme) private var colorScheme
  @State private var path = NavigationPath()
  @State private var users: [User] = []
  @State private var loadError: String?

  public init(
    repository: ChecklistRepository,
    userClient: UserClient? = nil,
    authToken: String? = nil,
    embeddedInCodexSheet: Bool = false,
    embeddedInParentNavigationStack: Bool = false,
    onDismissSheet: (() -> Void)? = nil
  ) {
    self.repository = repository
    self.userClient = userClient
    self.authToken = authToken
    self.embeddedInCodexSheet = embeddedInCodexSheet
    self.embeddedInParentNavigationStack = embeddedInParentNavigationStack
    self.onDismissSheet = onDismissSheet
  }

  private var ownsNavigationStack: Bool {
    embeddedInCodexSheet || !embeddedInParentNavigationStack
  }

  private var palette: BecoCodexPalette {
    BecoCodexPalette(isDark: colorScheme == .dark)
  }

  public var body: some View {
    Group {
      if ownsNavigationStack {
        NavigationStack(path: $path) {
          permissionsRootWithDestinations
        }
      } else {
        permissionsRootWithDestinations
      }
    }
    .task { await reloadUsers() }
  }

  private var permissionsRootWithDestinations: some View {
    permissionsRoot
      .navigationDestination(for: PermissionAdminRoute.self) { route in
        permissionDestination(route)
      }
  }

  private var permissionsRoot: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 20) {
        Text(embeddedInCodexSheet ? "Permissão" : "Equipe")
          .font(.largeTitle.bold())
          .foregroundStyle(palette.foreground)
          .padding(.top, embeddedInCodexSheet ? BecoCodexSheetMetrics.chromeBarHeight : 8)

        Text("Usuários e permissões de acesso")
          .font(.subheadline)
          .foregroundStyle(palette.muted)

        if let loadError {
          BecoCodexGroupedSection(title: "Aviso", palette: palette) {
            BecoCodexRow(
              title: loadError,
              systemImage: "exclamationmark.triangle",
              showsChevron: false,
              palette: palette
            )
          }
        }

        BecoCodexGroupedSection(title: "Usuários", palette: palette) {
          if users.isEmpty {
            BecoCodexRow(
              title: "Nenhum usuário encontrado.",
              showsChevron: false,
              palette: palette
            )
          } else {
            ForEach(Array(users.enumerated()), id: \.element.id) { index, user in
              NavigationLink(value: PermissionAdminRoute.edit(user.id)) {
                BecoCodexRow(
                  title: user.name,
                  subtitle: user.email,
                  systemImage: "person",
                  trailing: user.permissionLevel.rawValue,
                  palette: palette,
                  action: nil
                )
              }
              .buttonStyle(.plain)
              if index < users.count - 1 {
                BecoCodexRowDivider(palette: palette)
              }
            }
          }
        }
      }
      .padding(.horizontal, 20)
      .padding(.bottom, 32)
    }
    .background {
      BecoCodexBackground(palette: palette)
    }
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
    .modifier(CodexSheetCloseIfNeeded(
      enabled: embeddedInCodexSheet,
      palette: palette,
      onDismiss: onDismissSheet
    ))
  }

  @ViewBuilder
  private func permissionDestination(_ route: PermissionAdminRoute) -> some View {
    switch route {
    case .edit(let userId):
      if let user = users.first(where: { $0.id == userId }) {
        PermissionEditorView(
          user: user,
          repository: repository,
          userClient: userClient,
          authToken: authToken,
          palette: palette,
          onSaved: {
            Task { await reloadUsers() }
          }
        )
      } else {
        BecoCodexDetailChrome(title: "Permissões", palette: palette) {
          Text("Usuário não encontrado.")
            .foregroundStyle(palette.muted)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
      }
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

// MARK: - Routes / editors

private enum ActivityAdminRoute: Hashable {
  case create
  case edit(Int64)
}

private enum PermissionAdminRoute: Hashable {
  case edit(Int64)
}

private struct ActivityDeleteConfirm: Identifiable {
  let activity: Activity
  var id: Int64 { activity.id }
}

private struct ActivityEditorView: View {
  let title: String
  let initialName: String
  let initialArea: Area
  let palette: BecoCodexPalette
  let onSave: (String, Area) -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var name: String
  @State private var area: Area

  init(
    title: String,
    initialName: String,
    initialArea: Area,
    palette: BecoCodexPalette,
    onSave: @escaping (String, Area) -> Void
  ) {
    self.title = title
    self.initialName = initialName
    self.initialArea = initialArea
    self.palette = palette
    self.onSave = onSave
    _name = State(initialValue: initialName)
    _area = State(initialValue: initialArea)
  }

  var body: some View {
    BecoCodexDetailChrome(title: title, palette: palette) {
      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          BecoCodexGroupedSection(title: "Dados", palette: palette) {
            VStack(alignment: .leading, spacing: 8) {
              Text("Nome")
                .font(.caption)
                .foregroundStyle(palette.muted)
              TextField("Nome da atividade", text: $name)
                .foregroundStyle(palette.foreground)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            BecoCodexRowDivider(palette: palette, leadingInset: 16)

            VStack(alignment: .leading, spacing: 8) {
              Text("Área")
                .font(.caption)
                .foregroundStyle(palette.muted)
              Picker("Área", selection: $area) {
                ForEach(Area.allCases, id: \.self) { Text($0.displayName).tag($0) }
              }
              .pickerStyle(.menu)
              .tint(palette.foreground)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
          }

          Button {
            onSave(name.trimmingCharacters(in: .whitespaces), area)
            dismiss()
          } label: {
            Text("Salvar")
              .font(.body.weight(.semibold))
              .frame(maxWidth: .infinity)
              .padding(.vertical, 14)
              .foregroundStyle(palette.isDark ? Color.black : Color.white)
              .background(palette.foreground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
          }
          .buttonStyle(.plain)
          .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
          .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.45 : 1)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 32)
      }
    }
  }
}

private struct PermissionEditorView: View {
  let user: User
  let repository: ChecklistRepository
  let userClient: UserClient?
  let authToken: String?
  let palette: BecoCodexPalette
  let onSaved: () -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var permissions: FeaturePermissions
  @State private var saveError: String?
  @State private var isSaving = false

  init(
    user: User,
    repository: ChecklistRepository,
    userClient: UserClient? = nil,
    authToken: String? = nil,
    palette: BecoCodexPalette,
    onSaved: @escaping () -> Void
  ) {
    self.user = user
    self.repository = repository
    self.userClient = userClient
    self.authToken = authToken
    self.palette = palette
    self.onSaved = onSaved
    _permissions = State(initialValue: user.featurePermissions)
  }

  var body: some View {
    BecoCodexDetailChrome(title: "Permissões", palette: palette) {
      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          BecoCodexGroupedSection(title: "Usuário", palette: palette) {
            BecoCodexRow(
              title: user.name,
              subtitle: user.email,
              systemImage: "person.crop.circle",
              showsChevron: false,
              palette: palette
            )
          }

          BecoCodexGroupedSection(title: "Permissões", palette: palette) {
            Group {
              permissionToggle("Cadastrar usuários", binding: $permissions.canRegisterUsers)
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Criar atividades", binding: $permissions.canCreateActivities)
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Editar usuários", binding: $permissions.canEditUsers)
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Contagem de inventário", binding: $permissions.canCreateInventoryCounts)
            }
            Group {
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Insights de inventário", binding: $permissions.canViewInventoryInsights)
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Estoque administrativo", binding: $permissions.canManageAdministrativeStock)
              BecoCodexRowDivider(palette: palette, leadingInset: 16)
              permissionToggle("Importar compras", binding: $permissions.canImportPurchases)
            }
          }

          if let saveError {
            Text(saveError)
              .font(.footnote)
              .foregroundStyle(.red)
          }

          Button {
            Task { await savePermissions() }
          } label: {
            HStack {
              if isSaving {
                ProgressView()
                  .tint(palette.isDark ? Color.black : Color.white)
              }
              Text("Salvar permissões")
                .font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(palette.isDark ? Color.black : Color.white)
            .background(palette.foreground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
          }
          .buttonStyle(.plain)
          .disabled(isSaving)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 32)
      }
    }
  }

  private func permissionToggle(_ title: String, binding: Binding<Bool>) -> some View {
    Toggle(isOn: binding) {
      Text(title)
        .foregroundStyle(palette.foreground)
    }
    .tint(.blue)
    .padding(.horizontal, 16)
    .padding(.vertical, 12)
  }

  private func savePermissions() async {
    isSaving = true
    defer { isSaving = false }
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
        dismiss()
        return
      } catch {
        saveError = error.localizedDescription
        return
      }
    }
    try? repository.updateUserPermissions(userId: user.id, permissions: permissions)
    onSaved()
    dismiss()
  }
}

private struct CodexSheetCloseIfNeeded: ViewModifier {
  let enabled: Bool
  let palette: BecoCodexPalette
  let onDismiss: (() -> Void)?

  func body(content: Content) -> some View {
    if enabled, let onDismiss {
      content.becoCodexCloseOverlay(palette: palette, action: onDismiss)
    } else {
      content
    }
  }
}
