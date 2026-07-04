import SwiftUI
import UIKit

public enum BecoTokens {
  public enum ColorToken {
    public static let ink = Color(red: 23 / 255, green: 23 / 255, blue: 23 / 255)
    public static let muted = Color(red: 111 / 255, green: 111 / 255, blue: 115 / 255)
    public static let background = Color(red: 250 / 255, green: 250 / 255, blue: 248 / 255)
    public static let surface = Color.white
    public static let subtle = Color(red: 241 / 255, green: 241 / 255, blue: 243 / 255)
    public static let outline = Color(red: 226 / 255, green: 226 / 255, blue: 226 / 255)
    public static let brand = ink
  }

  public enum Spacing {
    public static let xxs: CGFloat = 4
    public static let xs: CGFloat = 8
    public static let sm: CGFloat = 12
    public static let md: CGFloat = 16
    public static let lg: CGFloat = 20
    public static let xl: CGFloat = 24
    public static let xxl: CGFloat = 32
  }
}

public struct BecoUserHeader: View {
  private let name: String
  private let role: String
  private let date: String
  private let initials: String
  private let onLogout: () -> Void

  public init(name: String, role: String, date: String, onLogout: @escaping () -> Void) {
    self.name = name
    self.role = role
    self.date = date
    initials = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    self.onLogout = onLogout
  }

  public var body: some View {
    HStack(spacing: BecoTokens.Spacing.sm) {
      Text(initials.isEmpty ? "CB" : initials)
        .font(.headline)
        .frame(width: 52, height: 52)
        .background(BecoTokens.ColorToken.subtle, in: Circle())
        .accessibilityHidden(true)
      VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
        Text("Olá, \(name)").font(.headline).lineLimit(1)
        Text("\(role) · \(date)").font(.subheadline).foregroundStyle(BecoTokens.ColorToken.muted).lineLimit(1)
      }
      .accessibilityElement(children: .combine)
      .accessibilityLabel("\(name), \(role), \(date)")
      Spacer()
      Menu {
        Button(action: onLogout) { Label("Sair", systemImage: "rectangle.portrait.and.arrow.right") }
      } label: {
        Image(systemName: "ellipsis").frame(width: 44, height: 44)
      }
      .accessibilityLabel("Mais opções")
    }
    .padding(.horizontal, BecoTokens.Spacing.md)
    .padding(.vertical, BecoTokens.Spacing.sm)
  }
}

public struct BecoPageHeader<Actions: View>: View {
  private let title: String
  private let subtitle: String?
  private let actions: Actions

  public init(
    title: String,
    subtitle: String? = nil,
    @ViewBuilder actions: () -> Actions
  ) {
    self.title = title
    self.subtitle = subtitle
    self.actions = actions()
  }

  public var body: some View {
    HStack(alignment: .center, spacing: BecoTokens.Spacing.sm) {
      VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
        Text(title).font(.title2.bold()).foregroundStyle(BecoTokens.ColorToken.ink)
          .accessibilityAddTraits(.isHeader)
        if let subtitle {
          Text(subtitle).font(.subheadline).foregroundStyle(BecoTokens.ColorToken.muted)
        }
      }
      Spacer()
      actions
    }
    .padding(.horizontal, BecoTokens.Spacing.md)
    .padding(.vertical, BecoTokens.Spacing.sm)
    .background(BecoTokens.ColorToken.background)
  }
}

public struct BecoBackButton: View {
  private let action: () -> Void

  public init(action: @escaping () -> Void) {
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      Image(systemName: "chevron.left")
        .font(.system(size: 15, weight: .semibold))
        .foregroundStyle(BecoTokens.ColorToken.ink)
        .frame(width: 40, height: 40)
        .background(.ultraThinMaterial, in: Circle())
        .overlay(Circle().stroke(BecoTokens.ColorToken.outline.opacity(0.7), lineWidth: 1))
        .shadow(color: Color.black.opacity(0.08), radius: 4, y: 1)
        .frame(width: 48, height: 48)
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Voltar")
  }
}

private struct BecoBackButtonModifier: ViewModifier {
  @Environment(\.dismiss) private var dismiss

  func body(content: Content) -> some View {
    content
      .navigationBarBackButtonHidden(true)
      .toolbar {
        ToolbarItem(placement: .navigationBarLeading) {
          BecoBackButton { dismiss() }
        }
      }
      .background(BecoInteractivePopGestureEnabler())
  }
}

private struct BecoInteractivePopGestureEnabler: UIViewControllerRepresentable {
  func makeUIViewController(context: Context) -> Controller { Controller() }
  func updateUIViewController(_ uiViewController: Controller, context: Context) {}

  final class Controller: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
      super.viewDidAppear(animated)
      navigationController?.interactivePopGestureRecognizer?.isEnabled = true
      navigationController?.interactivePopGestureRecognizer?.delegate = nil
    }
  }
}

public extension View {
  func becoBackButton() -> some View {
    modifier(BecoBackButtonModifier())
  }
}

public struct BecoTabBarItem<ID: Hashable>: Identifiable {
  public let id: ID
  public let title: String
  public let systemImage: String

  public init(id: ID, title: String, systemImage: String) {
    self.id = id
    self.title = title
    self.systemImage = systemImage
  }
}

public struct BecoTabBar<ID: Hashable>: View {
  private let items: [BecoTabBarItem<ID>]
  private let selected: ID
  private let hasOverflow: Bool
  private let overflowSelected: Bool
  private let onSelect: (ID) -> Void
  private let onMore: () -> Void

  public init(
    items: [BecoTabBarItem<ID>],
    selected: ID,
    hasOverflow: Bool,
    overflowSelected: Bool,
    onSelect: @escaping (ID) -> Void,
    onMore: @escaping () -> Void
  ) {
    self.items = items
    self.selected = selected
    self.hasOverflow = hasOverflow
    self.overflowSelected = overflowSelected
    self.onSelect = onSelect
    self.onMore = onMore
  }

  public var body: some View {
    HStack(spacing: BecoTokens.Spacing.xs) {
      ForEach(items) { item in
        tabButton(
          title: item.title,
          systemImage: item.systemImage,
          selected: selected == item.id,
          action: { onSelect(item.id) }
        )
      }
      if hasOverflow {
        tabButton(
          title: "Mais",
          systemImage: "ellipsis",
          selected: overflowSelected,
          action: onMore
        )
      }
    }
    .padding(BecoTokens.Spacing.xs)
    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
    .overlay(
      RoundedRectangle(cornerRadius: 32, style: .continuous)
        .stroke(BecoTokens.ColorToken.outline.opacity(0.75), lineWidth: 1)
    )
    .shadow(color: Color.black.opacity(0.12), radius: 12, y: 5)
    .padding(.horizontal, BecoTokens.Spacing.md)
    .padding(.vertical, BecoTokens.Spacing.xs)
  }

  private func tabButton(
    title: String,
    systemImage: String,
    selected: Bool,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      VStack(spacing: BecoTokens.Spacing.xxs) {
        Image(systemName: systemImage).font(.system(size: 20, weight: .semibold))
        Text(title).font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75)
      }
      .foregroundStyle(selected ? Color.white : BecoTokens.ColorToken.muted)
      .frame(maxWidth: .infinity, minHeight: 58)
      .background(
        selected ? BecoTokens.ColorToken.ink : Color.clear,
        in: RoundedRectangle(cornerRadius: 24, style: .continuous)
      )
    }
    .buttonStyle(.plain)
    .accessibilityLabel(title)
    .accessibilityAddTraits(selected ? .isSelected : [])
  }
}

public extension BecoPageHeader where Actions == EmptyView {
  init(title: String, subtitle: String? = nil) {
    self.init(title: title, subtitle: subtitle) { EmptyView() }
  }
}

public struct BecoSegmentedFilter<Option: Hashable>: View {
  private let options: [(Option, String, Int?)]
  @Binding private var selected: Option

  public init(options: [(Option, String, Int?)], selected: Binding<Option>) {
    self.options = options
    _selected = selected
  }

  public var body: some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: BecoTokens.Spacing.xs) {
        ForEach(options, id: \.0) { option, label, count in
          Button { selected = option } label: {
            HStack(spacing: BecoTokens.Spacing.xs) {
              Text(label)
              if let count { Text("\(count)").font(.caption.bold()).padding(.horizontal, 7).padding(.vertical, 3).background(selected == option ? Color.white.opacity(0.2) : BecoTokens.ColorToken.outline, in: Capsule()) }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(selected == option ? Color.white : BecoTokens.ColorToken.ink)
            .padding(.horizontal, BecoTokens.Spacing.md)
            .frame(minHeight: 44)
            .background(selected == option ? BecoTokens.ColorToken.ink : BecoTokens.ColorToken.subtle, in: Capsule())
          }
          .buttonStyle(.plain)
          .accessibilityLabel(count.map { "\(label), \($0) itens" } ?? label)
          .accessibilityValue(selected == option ? "Selecionado" : "Não selecionado")
          .accessibilityAddTraits(selected == option ? .isSelected : [])
        }
      }
    }
  }
}

public struct BecoTaskRow: View {
  private let title: String
  private let metadata: String
  private let completed: Bool
  private let onSelect: (() -> Void)?
  private let onComplete: () -> Void

  public init(title: String, metadata: String, completed: Bool, onSelect: (() -> Void)? = nil, onComplete: @escaping () -> Void) {
    self.title = title
    self.metadata = metadata
    self.completed = completed
    self.onSelect = onSelect
    self.onComplete = onComplete
  }

  public var body: some View {
    HStack(spacing: BecoTokens.Spacing.sm) {
      Button(action: { if !completed { onComplete() } }) {
        Image(systemName: completed ? "checkmark.square.fill" : "square")
          .font(.title2)
          .foregroundStyle(completed ? BecoTokens.ColorToken.brand : BecoTokens.ColorToken.ink)
          .frame(width: 44, height: 44)
      }
      .accessibilityLabel(completed ? "\(title), concluída" : "Concluir \(title)")
      .disabled(completed)
      Button(action: { onSelect?() }) {
        VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
          Text(title).font(.headline).strikethrough(completed).foregroundStyle(completed ? BecoTokens.ColorToken.muted : BecoTokens.ColorToken.ink)
          Text(metadata).font(.subheadline).foregroundStyle(BecoTokens.ColorToken.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
      }
      .buttonStyle(.plain)
      .accessibilityLabel("\(title), \(metadata)")
      .disabled(onSelect == nil)
    }
    .padding(.vertical, BecoTokens.Spacing.xs)
    .contentShape(Rectangle())
  }
}
