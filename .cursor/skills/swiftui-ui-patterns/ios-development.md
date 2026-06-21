---
name: checklist-boteco-ios
description: Overlay Checklist Boteco para desenvolvimento iOS nativo (iOS 16, Xcode 14.2, GRDB, SPM). Use junto com swiftui-ui-patterns ao editar iosApp/ ou Packages/.
---

# Checklist Boteco — iOS development overlay

Este arquivo complementa [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](SKILL.md). **Em caso de conflito**, prevalecem:

1. [`docs/ios-swiftui-standards.md`](../../../docs/ios-swiftui-standards.md)
2. [`SKILL.md`](SKILL.md) (skill SwiftUI)

## Quando usar

- Editar `iosApp/` ou `Packages/*` (SwiftUI, GRDB, sync, auth).
- Adicionar telas, tabs, forms, sheets ou componentes de layout iOS.

## Constraints Checklist Boteco

| Item | Valor |
|------|-------|
| iOS deployment | **16.0** |
| Xcode | **14.2+** |
| Swift | **5.7+** |
| UI state | `ObservableObject` + `@StateObject` / `@ObservedObject` / `@EnvironmentObject` |
| ViewModels | **Não** — views + serviços injetados |
| Persistência | **GRDB** em `Packages/Persistence/` |
| Packages | Monorepo em [`Packages/`](../../../Packages/) — umbrella `Package.swift` |

**Não usar** `@Observable`, `@Environment(Type.self)` iOS 17+ ou SwiftData neste target.

## Estrutura

```
iosApp/ChecklistBoteco/     # Shell Xcode (App, MainTabView, AppDependencies)
Packages/                   # Swift Packages (Auth, Checklist, Network, Env, …)
docs/ios-app.md             # Setup Xcode, API, build
docs/ios-swiftui-standards.md  # Padrões SwiftUI + checklist PR + backlog
```

## Bootstrap (obrigatório)

[`iosApp/ChecklistBoteco/AppDependencies.swift`](../../../iosApp/ChecklistBoteco/AppDependencies.swift):

```swift
// ✅ Correto
public init() throws {
  let db = try AppDatabase.open()
  let repository = ChecklistRepository(dbQueue: db)
  // … montar grafo …
  repository.bindSyncHandler { Task { @MainActor in syncController.requestSync() } }
}

// ❌ Proibido no launch
let db = try! AppDatabase.open()
```

Falha de bootstrap → `AppLaunchState` exibe erro; nunca crashar no `init` do app.

## Workflow UI

1. Ler [`SKILL.md`](SKILL.md) e [`references/components-index.md`](references/components-index.md).
2. Aplicar [`docs/ios-swiftui-standards.md`](../../../docs/ios-swiftui-standards.md).
3. Formatar com [`.swiftformat`](../../../.swiftformat).

## API local (Debug)

[`iosApp/Config/Debug.xcconfig`](../../../iosApp/Config/Debug.xcconfig):

```xcconfig
CHECKLIST_API_BASE_URL = http:/$()/localhost:8181
```

Em `.xcconfig`, `//` inicia comentário — use `http:/$()/…` para URLs com barras duplas.

## Testes

```bash
cd Packages/Models && swift test   # por módulo
xcodebuild -project iosApp/ChecklistBoteco.xcodeproj -scheme ChecklistBoteco \
  -destination 'platform=iOS Simulator,name=iPhone 14' build
```

## Referência legada

Stub em [`.cursor/skills/ios/ios-development.md`](../ios/ios-development.md) aponta para este arquivo.
