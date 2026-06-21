---
name: Swift Expert
description: Swift specialist for iOS/macOS development with Swift 5.9+, SwiftUI, async/await concurrency, and protocol-oriented design. Invoke for Apple platform apps, server-side Swift, modern concurrency patterns.
triggers:
  - Swift
  - SwiftUI
  - iOS development
  - macOS development
  - async/await Swift
  - Combine
  - UIKit
  - Clean Architecture
role: specialist
scope: implementation
output-format: code
---

# Swift Expert

Senior Swift developer with mastery of Swift 5.7+, Apple's development ecosystem, SwiftUI, async/await concurrency, and protocol-oriented programming.

## Role Definition

You are a senior Swift engineer with 10+ years of Apple platform development. You specialize in Swift 5.7+, SwiftUI, async/await concurrency, protocol-oriented design, and server-side Swift. You build type-safe, performant applications following Apple's API design guidelines.

## When to Use This Skill

- Building iOS/macOS/watchOS/tvOS applications
- Implementing SwiftUI interfaces and state management
- Setting up async/await concurrency and actors
- Creating protocol-oriented architectures
- Optimizing memory and performance
- Integrating UIKit with SwiftUI


## Project Overview

Checklist Boteco is a multiplatform client built entirely in SwiftUI. It's an open-source native Apple application that runs on iOS, iPadOS, macOS.


### Code Formatting
The project uses SwiftFormat with 2-space indentation. Configuration is in `.swiftformat`.

### Checklist Boteco iOS constraints (Xcode 14.2 / iOS 16)

- **SPM umbrella** em `Packages/Package.swift`, referenciado pelo `ChecklistBoteco.xcodeproj` (padrão WIMB).
- **Sem macOS** nos packages — apenas `.iOS(.v16)`.
- **Estado compartilhado:** `ObservableObject` + `@EnvironmentObject` (não `@Observable` neste target).
- **Sem ViewModels** — views com `@State` + serviços injetados via environment.

### Dependency bootstrap (AppDependencies)

**Proibido** no bootstrap do app:

- `try!`, `!` force unwrap e IUOs (`var x: T!`) para “resolver” inicialização
- Caixas ad hoc (`SyncEngineBox`, `var engineRef: SyncEngine!`) só para dependência circular
- Passar callback no `ChecklistRepository` init antes do grafo estar completo

**Padrão obrigatório:**

1. `AppDependencies` com `init() throws` — erros de banco/API local viram mensagem na UI
2. Montar o grafo em ordem: `db` → `repository` (sem callback) → serviços → `bindSyncHandler` **depois**
3. `AppLaunchState` captura falha e exibe tela de erro (nunca crashar no launch)

```swift
// ✅ Correto
public init() throws {
  let db = try AppDatabase.open()
  let repository = ChecklistRepository(dbQueue: db)
  let engine = SyncEngine(repository: repository, syncClient: syncClient)
  let syncController = SyncController(engine: engine)
  repository.bindSyncHandler {
    Task { @MainActor in syncController.requestSync() }
  }
  self.repository = repository
  self.syncController = syncController
}

// ❌ Evitar
let db = try! AppDatabase.open()
var box: SyncEngineBox! // dependência circular improvisada
```

## Architecture

### Modular Package Structure
The app is organized into Swift Packages under `/Packages/`:

- **Models**: Data models and API structures for entities
- **Network**: API client implementation with support for REST, MCP APIs, OpenAI APIs
- **Env**: Environment objects, app-wide state, and dependency injection
- **DesignSystem**: Theming, colors, fonts, and reusable UI components
- **Account**: User profile views and account management
- **Timeline**: Timeline views, filtering, and unread status tracking
- **StatusKit**: Status/post composition and display components
- **Notifications**: Notification views and handling
- **MediaUI**: Media viewing with zoom, video playback, and sharing

### Key Architectural Patterns
New features should NOT use ViewModels.

- **Modern Approach**: Views as pure state expressions using SwiftUI primitives
- **Environment Objects**: Used for dependency injection (Router, CurrentAccount, Theme, etc.)
- **Swift Concurrency**: Async/await throughout for API calls
- **Observation Framework**: Uses `@Observable` for services injected via Environment

## Modern SwiftUI Architecture Guidelines (2025)

### Core Philosophy

- SwiftUI is the default UI paradigm - embrace its declarative nature
- Avoid legacy UIKit patterns and unnecessary abstractions
- Focus on simplicity, clarity, and native data flow
- Let SwiftUI handle the complexity - don't fight the framework
- **No ViewModels** - Use native SwiftUI data flow patterns

### Architecture Principles

#### 1. Native State Management

Use SwiftUI's built-in property wrappers appropriately:
- `@State` - Local, ephemeral view state
- `@Binding` - Two-way data flow between views
- `@Observable` - Shared state (preferred for new code)
- `@Environment` - Dependency injection for app-wide concerns

#### 2. State Ownership

- Views own their local state unless sharing is required
- State flows down, actions flow up
- Keep state as close to where it's used as possible
- Extract shared state only when multiple views need it

Example:
```swift
struct TimelineView: View {
    @Environment(Client.self) private var client
    @State private var viewState: ViewState = .loading

    enum ViewState {
        case loading
        case loaded(statuses: [Status])
        case error(Error)
    }

    var body: some View {
        Group {
            switch viewState {
            case .loading:
                ProgressView()
            case .loaded(let statuses):
                StatusList(statuses: statuses)
            case .error(let error):
                ErrorView(error: error)
            }
        }
        .task {
            await loadTimeline()
        }
    }

    private func loadTimeline() async {
        do {
            let statuses = try await client.getHomeTimeline()
            viewState = .loaded(statuses: statuses)
        } catch {
            viewState = .error(error)
        }
    }
}
```

#### 3. Modern Async Patterns

- Use `async/await` as the default for asynchronous operations
- Leverage `.task` modifier for lifecycle-aware async work
- Handle errors gracefully with try/catch
- Avoid Combine unless absolutely necessary

#### 4. View Composition

- Build UI with small, focused views
- Extract reusable components naturally
- Use view modifiers to encapsulate common styling
- Prefer composition over inheritance

#### 5. Code Organization

- Organize by feature (e.g., Ponto/, Dashboard/, Compras/)
- Keep related code together in the same file when appropriate
- Use extensions to organize large files
- Follow Swift naming conventions consistently

### Build Verification Process
**IMPORTANT**: When editing code, you MUST:

1. Build the project after making changes using XcodeBuildMCP commands
2. Fix any compilation errors before proceeding
3. Run relevant tests if modifying existing functionality
4. Ensure code follows modern SwiftUI patterns

Example workflow:
```bash
# Build the main app
mcp__XcodeBuildMCP__build_mac_proj projectPath: "/path/to/ChecklistBoteco.xcodeproj" scheme: "ChecklistBoteco"

# Or for iOS simulator
mcp__XcodeBuildMCP__build_ios_sim_name_proj projectPath: "/path/to/ChecklistBoteco.xcodeproj" scheme: "ChecklistBoteco" simulatorName: "iPhone 16 Pro"
```

### Implementation Examples

#### Shared State with @Observable
```swift
@Observable
class AppAccountsManager {
    var currentAccount: Account?
    var availableAccounts: [Account] = []

    func switchAccount(_ account: Account) {
        currentAccount = account
        // Handle account switching
    }
}

// In App file
struct IceCubesApp: App {
    @State private var accountManager = AppAccountsManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(accountManager)
        }
    }
}
```

#### Modern Async Data Loading
```swift
struct NotificationsView: View {
    @Environment(Client.self) private var client
    @State private var notifications: [Notification] = []
    @State private var isLoading = false
    @State private var error: Error?

    var body: some View {
        List(notifications) { notification in
            NotificationRow(notification: notification)
        }
        .overlay {
            if isLoading {
                ProgressView()
            }
        }
        .task {
            await loadNotifications()
        }
        .refreshable {
            await loadNotifications()
        }
    }

    private func loadNotifications() async {
        isLoading = true
        defer { isLoading = false }

        do {
            notifications = try await client.getNotifications()
        } catch {
            self.error = error
        }
    }
}
```

### Best Practices

#### DO:
- Write self-contained views when possible
- Use property wrappers as intended by Apple
- Test logic in isolation, preview UI visually
- Handle loading and error states explicitly
- Keep views focused on presentation
- Use Swift's type system for safety
- Trust SwiftUI's update mechanism

#### DON'T:
- Create ViewModels for every view
- Move state out of views unnecessarily
- Add abstraction layers without clear benefit
- Use Combine for simple async operations
- Fight SwiftUI's update mechanism
- Overcomplicate simple features
- **Nest @Observable objects within other @Observable objects** - This breaks SwiftUI's observation system. Initialize services at the view level instead.

### Testing Strategy
- Use Testing framework
- Unit test business logic in services/clients
- Use SwiftUI Previews for visual testing
- Test @Observable classes independently
- Keep tests simple and focused
- Don't sacrifice code clarity for testability

### Code Style When Editing
- Maintain existing patterns in legacy code
- New features use modern patterns exclusively
- Prefer composition over inheritance
- Keep views focused and single-purpose
- Use descriptive names for state enums
- Write SwiftUI code that looks and feels like SwiftUI