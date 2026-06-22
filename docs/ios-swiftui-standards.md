# Padrões SwiftUI — Checklist Boteco (iOS)

Documento operacional que aplica o skill [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](../.cursor/skills/swiftui-ui-patterns/SKILL.md) ao app iOS deste repositório.

Para setup de Xcode, SPM e API local, veja [`ios-app.md`](ios-app.md).

## Restrições do projeto (fase atual)

| Item | Valor |
|------|-------|
| Deployment target | iOS **16.0** |
| Xcode | **14.2+** |
| Swift | **5.7+** |
| macOS nos packages | **Não** (apenas `.iOS(.v16)`) |
| Persistência | **GRDB** (não SwiftData) |
| Formatação | [`.swiftformat`](../.swiftformat) — indentação 2 espaços |

### Estado e arquitetura de UI

Siga a tabela do skill (seção *State ownership summary*) com fallback iOS 16:

| Cenário | Padrão neste repo |
|---------|-------------------|
| Estado local de uma view | `@State` |
| Filho muta valor do pai | `@Binding` |
| Modelo compartilhado no root (sessão, sync) | `@StateObject` no bootstrap + `@ObservedObject` / `@EnvironmentObject` nos filhos |
| Serviços app-wide | `@EnvironmentObject` ou injeção explícita no init |
| **Proibido neste target** | `@Observable`, `@Environment(Type.self)` (iOS 17+) |
| **Proibido** | ViewModels dedicados por tela |

### Bootstrap do app

Arquivo: [`iosApp/ChecklistBoteco/AppDependencies.swift`](../iosApp/ChecklistBoteco/AppDependencies.swift)

- `AppDependencies.init() throws` — nunca `try!` no launch.
- Ordem: `db` → `repository` → serviços → `bindSyncHandler` após montar o grafo.
- Falha de bootstrap → tela de erro em `ChecklistBotecoApp` (`AppLaunchState`), não crash.

## Workflow antes de editar UI

1. Ler [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](../.cursor/skills/swiftui-ui-patterns/SKILL.md) (regras gerais e anti-patterns).
2. Identificar o componente em [`references/components-index.md`](../.cursor/skills/swiftui-ui-patterns/references/components-index.md).
3. Ler a referência específica (ex.: `form.md`, `tabview.md`, `sheets.md`).
4. Buscar exemplo próximo no repo (`rg "NavigationStack"` em `Packages/`).
5. Implementar view pequena; validar build antes de seguir para a próxima tela.
6. Adicionar `#Preview` quando a tela tiver estados distintos (backlog fase 2).

## Mapeamento skill → código atual

Referências obrigatórias ao refatorar. A coluna *Backlog* indica divergências conhecidas (correção na **fase 2**).

| Área | Referência do skill | Arquivos atuais | Backlog fase 2 |
|------|-------------------|-----------------|----------------|
| App shell / tabs | [`navigationstack.md`](../.cursor/skills/swiftui-ui-patterns/references/navigationstack.md) | [`AppTabRoute.swift`](../iosApp/ChecklistBoteco/AppTabRoute.swift), [`AppDeepLink.swift`](../iosApp/ChecklistBoteco/AppDeepLink.swift) | — |
| Auth / forms | [`form.md`](../.cursor/skills/swiftui-ui-patterns/references/form.md), [`async-state.md`](../.cursor/skills/swiftui-ui-patterns/references/async-state.md) | [`LoginView.swift`](../Packages/Auth/Sources/Auth/LoginView.swift) | Previews 2FA/biometria |
| Sheets / modals | [`sheets.md`](../.cursor/skills/swiftui-ui-patterns/references/sheets.md) | Permissões (Admin), Checklist (câmera), Inventário (rascunho) | — |
| Feedback global | [`overlay.md`](../.cursor/skills/swiftui-ui-patterns/references/overlay.md) | [`DesignSystem.swift`](../Packages/DesignSystem/Sources/DesignSystem/DesignSystem.swift) | Cores de linha via `AppTheme` |
| Listas / features | [`list.md`](../.cursor/skills/swiftui-ui-patterns/references/list.md) | Inventory, WorkClock, AdminFeatures | Extrair subviews; estados loading/error explícitos |
| Previews | [`previews.md`](../.cursor/skills/swiftui-ui-patterns/references/previews.md) | [`LoginView+Preview.swift`](../Packages/Auth/Sources/Auth/LoginView+Preview.swift) | `#Preview` / PreviewProvider por feature |
| Performance | [`performance.md`](../.cursor/skills/swiftui-ui-patterns/references/performance.md) | Várias views com `@ObservedObject session` | — |

## Anti-patterns (do skill — aplicar sempre)

- Views gigantes misturando layout, rede, roteamento e formatação.
- Vários booleans para sheets/alerts mutuamente exclusivos — preferir enum + `.sheet(item:)`.
- Chamadas de rede no `body` — usar `.task` / `.task(id:)` ou serviço injetado.
- `@EnvironmentObject` para dependências locais de feature sem motivo.
- `AnyView` para contornar tipos — preferir composição.

## Checklist de PR (iOS)

Antes de abrir PR com mudanças SwiftUI:

- [ ] Li a referência do componente em `references/`.
- [ ] Ownership de state está na camada correta (`@State` local vs `@EnvironmentObject` compartilhado).
- [ ] Async work usa `.task` ou `.task(id:)` com estados loading/error visíveis quando aplicável.
- [ ] Sheets/modals usam enum identificável quando representam um modelo selecionado.
- [ ] Nenhum `try!` / force unwrap novo no bootstrap ou fluxos críticos.
- [ ] `xcodebuild` compila para simulador iOS 16 sem erros.
- [ ] Sem artefatos `.build/` ou `DerivedData/` no commit.
- [ ] Formatação consistente com `.swiftformat`.

## Fase 2 — Refatoração UI (backlog)

Itens **já implementados** (2026-06):

- `withAppDependencyGraph()` — [`AppDependencyGraph.swift`](../iosApp/ChecklistBoteco/AppDependencyGraph.swift)
- `AppTab.makeContentView()` + `label` — [`AppTabContent.swift`](../iosApp/ChecklistBoteco/AppTabContent.swift)
- Auth gate unificado (`AuthScreen`) — [`ChecklistBotecoApp.swift`](../iosApp/ChecklistBoteco/ChecklistBotecoApp.swift)
- `LoginPhase` + `@FocusState` + `.task` — [`LoginView.swift`](../Packages/Auth/Sources/Auth/LoginView.swift)
- Câmera `.sheet(item:)` + `ActivityChecklistRow` — [`ChecklistRootView.swift`](../Packages/Checklist/Sources/ChecklistFeature/ChecklistRootView.swift)
- Overlay enum-driven (`FeedbackAlert`) — [`NetworkFeedback.swift`](../Packages/Network/Sources/Network/NetworkFeedback.swift)
- Preview Login — [`LoginView+Preview.swift`](../Packages/Auth/Sources/Auth/LoginView+Preview.swift)
- `TabRouter` + `NavigationStack` por tab — [`TabRouter.swift`](../iosApp/ChecklistBoteco/TabRouter.swift), [`MainTabView.swift`](../iosApp/ChecklistBoteco/MainTabView.swift)
- `AppTheme` (tokens semânticos iOS 16) — [`AppTheme.swift`](../Packages/DesignSystem/Sources/DesignSystem/AppTheme.swift)
- Checklist/WorkClock sem `@EnvironmentObject session` — injeção explícita de `User` / ids
- Subviews Inventory e Admin + previews Checklist/Admin
- `AppTabRoute` + `TabRouter.push` — [`AppTabRoute.swift`](../iosApp/ChecklistBoteco/AppTabRoute.swift)
- `WorkClockDayEntriesView` (navegação tipada a partir do Ponto)
- `ThemedFormStyle` / `ThemedListStyle` — [`ThemedFormStyle.swift`](../Packages/DesignSystem/Sources/DesignSystem/ThemedFormStyle.swift)
- Permissões via `.sheet(item:)` — [`AdminFeaturesViews.swift`](../Packages/AdminFeatures/Sources/AdminFeatures/AdminFeaturesViews.swift)
- `MainTabView` recebe `User` injetado (sem `@ObservedObject session`)
- Previews WorkClock, Inventory, MainTab
- Rotas `dashboardAreaDetail` e `checklistActivityDetail` + telas de detalhe
- `themedListRowBackground()` + token `rowBackground` em `AppTheme`
- Atividades: criar via `.sheet(item: ActivityCreateSheet)`
- Previews Login (2FA, biometria) e MainTab (admin vs colaborador)
- `TabRouter` injetado só em `makeContentView` (sem `@EnvironmentObject` global)
- Ponto: feedback GPS, registro local sem token, localização simulada no simulador — [`WorkClockRootView.swift`](../Packages/WorkClock/Sources/WorkClockFeature/WorkClockRootView.swift)
- Login offline por email + fallback local quando API rejeita — [`AppSession.swift`](../Packages/Env/Sources/Env/AppSession.swift)
- Usuário seed `colaborador@checklistboteco.com` / `colab123` para testar aba Ponto
- Ponto: simulador usa sempre coordenada do estabelecimento; device exige GPS real ≤ 5 m; alert modal + botão em `safeAreaInset`
- Checklist: sem segmented com 1 área; filtro multi-área via menu na toolbar; lista única com section header — [`ChecklistRootView.swift`](../Packages/Checklist/Sources/ChecklistFeature/ChecklistRootView.swift)
- Checklist: áreas por setor — cozinha vê `COZINHA`; demais setores veem `ATENDIMENTO` (`WorkSector.checklistAreas` em [`UserModels.swift`](../Packages/Models/Sources/Models/UserModels.swift))
- Simulador: conclusão de checklist usa galeria quando câmera indisponível; cancelar reverte toggle via `reload` no `onDismiss`
- Android emulador: [`CameraCapture.android.kt`](../composeApp/src/androidMain/kotlin/com/checklistboteco/platform/CameraCapture.android.kt) usa galeria quando emulador ou câmera indisponível/falha
- Inventário: rota `inventoryAuditDetail` + `InventoryAuditDetailView`; tap na auditoria diária — [`InventoryRootView.swift`](../Packages/Inventory/Sources/InventoryFeature/InventoryRootView.swift)
- Deep links `checklistboteco://` — [`AppDeepLink.swift`](../iosApp/ChecklistBoteco/AppDeepLink.swift), URL scheme em [`Info.plist`](../iosApp/ChecklistBoteco/Info.plist)
- `themedSectionHeader` + token `sectionHeaderBackground` — [`ThemedFormStyle.swift`](../Packages/DesignSystem/Sources/DesignSystem/ThemedFormStyle.swift)
- Atividades: editar/excluir com sheet + alert de confirmação — [`AdminFeaturesViews.swift`](../Packages/AdminFeatures/Sources/AdminFeatures/AdminFeaturesViews.swift)
- `MainTabView`: lazy load de tabs (`loadedTabs`) + `onOpenURL` para deep links
- Previews: estados vazios (Inventory, Admin atividades) — `*+Preview.swift` nos packages
- Previews de erro de rede (Login `debugLocalError`, Inventory `networkError` banner)
- Inventário: erros de sync inline via banner (sem depender só do overlay global)
- `AppDependencyGraph`: `session` injetado sem `@ObservedObject` no modifier (menos re-render global)
- `RootView`: observa `session` localmente; `MainTabContext` sem referência a `AppSession`
- Inventário: criar/editar rascunho via `.sheet(item:)` — [`InventoryDraftFormSheet.swift`](../Packages/Inventory/Sources/InventoryFeature/InventoryDraftFormSheet.swift), `updateInventoryDraft` no repositório
- Performance shell: `AppDependenciesHolder` e `SyncController` sem `ObservableObject` desnecessário; `RootView` observa só `AppSession`; sync lifecycle via `let` no `AppDependencyGraphModifier`

**Backlog fase 2 (iOS UI): concluído** — próximas melhorias entram como demanda de produto ou subida de deployment target (iOS 17+).

## Precedência em caso de conflito

1. [`docs/ios-swiftui-standards.md`](ios-swiftui-standards.md) (este arquivo)
2. [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](../.cursor/skills/swiftui-ui-patterns/SKILL.md)
3. [`.cursor/skills/swiftui-ui-patterns/ios-development.md`](../.cursor/skills/swiftui-ui-patterns/ios-development.md) (overlay Checklist Boteco)

Se o skill genérico recomendar `@Observable` ou APIs iOS 17+, **ignore** até o deployment target subir.
