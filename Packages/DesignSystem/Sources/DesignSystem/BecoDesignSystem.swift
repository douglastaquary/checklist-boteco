import SwiftUI
import Models
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
      .becoInteractivePopGesture()
  }
}

/// Keeps edge-swipe pop working when the system back button is hidden.
public struct BecoInteractivePopGestureEnabler: UIViewControllerRepresentable {
  public init() {}

  public func makeUIViewController(context: Context) -> Controller { Controller() }
  public func updateUIViewController(_ uiViewController: Controller, context: Context) {}

  public final class Controller: UIViewController {
    public override func viewDidAppear(_ animated: Bool) {
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

  func becoInteractivePopGesture() -> some View {
    background(BecoInteractivePopGestureEnabler())
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
  private let compact: Bool

  public init(
    options: [(Option, String, Int?)],
    selected: Binding<Option>,
    compact: Bool = false
  ) {
    self.options = options
    _selected = selected
    self.compact = compact
  }

  public var body: some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: compact ? 6 : BecoTokens.Spacing.xs) {
        ForEach(options, id: \.0) { option, label, count in
          Button { selected = option } label: {
            HStack(spacing: compact ? 4 : BecoTokens.Spacing.xs) {
              Text(label)
              if let count {
                Text("\(count)")
                  .font(compact ? .caption2.weight(.semibold) : .caption.bold())
                  .padding(.horizontal, compact ? 5 : 7)
                  .padding(.vertical, compact ? 1 : 3)
                  .background(
                    selected == option ? Color.white.opacity(0.2) : BecoTokens.ColorToken.outline,
                    in: Capsule()
                  )
              }
            }
            .font(compact ? .caption.weight(.medium) : .subheadline.weight(.semibold))
            .foregroundStyle(selected == option ? Color.white : BecoTokens.ColorToken.ink)
            .padding(.horizontal, compact ? 10 : BecoTokens.Spacing.md)
            .padding(.vertical, compact ? 5 : 0)
            .frame(minHeight: compact ? 28 : 44)
            .background(
              selected == option ? BecoTokens.ColorToken.ink : BecoTokens.ColorToken.subtle,
              in: Capsule()
            )
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
  private let timingStatus: ChecklistTimingStatus?

  public init(title: String, metadata: String, completed: Bool, timingStatus: ChecklistTimingStatus? = nil, onSelect: (() -> Void)? = nil, onComplete: @escaping () -> Void) {
    self.title = title
    self.metadata = metadata
    self.completed = completed
    self.onSelect = onSelect
    self.onComplete = onComplete
    self.timingStatus = timingStatus
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
    .padding(.horizontal, BecoTokens.Spacing.sm)
    .overlay(RoundedRectangle(cornerRadius: 12).stroke(timingColor, lineWidth: timingStatus == nil || timingStatus == .completed ? 0 : 2))
    .accessibilityValue(timingAccessibilityLabel)
    .contentShape(Rectangle())
  }

  private var timingColor: Color {
    switch timingStatus { case .green: return Color(red: 0.18, green: 0.49, blue: 0.20); case .yellow: return Color(red: 0.75, green: 0.52, blue: 0); case .red: return Color(red: 0.78, green: 0.16, blue: 0.16); default: return .clear }
  }

  private var timingAccessibilityLabel: String {
    switch timingStatus { case .green: return "Dentro do prazo"; case .yellow: return "Próxima do limite"; case .red: return "Atrasada"; case .completed: return "Concluída"; case nil: return completed ? "Concluída" : "Pendente" }
  }
}
