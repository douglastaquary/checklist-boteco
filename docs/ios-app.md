# App iOS nativo — Checklist Boteco

Aplicativo **100% SwiftUI nativo** (sem Kotlin Multiplatform/Compose), organizado em Swift Packages em [`Packages/`](../Packages/) e shell em [`iosApp/`](../iosApp/).

Integração SPM usa um package umbrella em `Packages/`, mantido como submódulo na branch dedicada `ios-packages`. O Xcode resolve uma revisão publicada e reproduzível desse package.

## Requisitos

| Ferramenta | Versão |
|------------|--------|
| Xcode | **14.2+** |
| Swift | **5.7+** |
| iOS Deployment Target | **16.0** |

Estado da UI: `ObservableObject` + `@EnvironmentObject` (iOS 16). Sem suporte macOS no app nem nos packages.

## Padrões SwiftUI e agentes de IA

Antes de criar ou refatorar telas:

1. [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](../.cursor/skills/swiftui-ui-patterns/SKILL.md) — regras gerais e anti-patterns
2. [`docs/ios-swiftui-standards.md`](ios-swiftui-standards.md) — restrições iOS 16, checklist de PR e backlog de refatoração
3. [`references/components-index.md`](../.cursor/skills/swiftui-ui-patterns/references/components-index.md) — referência por componente (TabView, Form, Sheets, …)

Overlay específico Checklist Boteco: [`.cursor/skills/swiftui-ui-patterns/ios-development.md`](../.cursor/skills/swiftui-ui-patterns/ios-development.md).

Instruções gerais do repositório (MCP, roteamento backend/iOS): [`AGENTS.md`](../AGENTS.md).

Formatação: [`.swiftformat`](../.swiftformat) (2 espaços).

## Configuração da API

Edite [`iosApp/Config/Debug.xcconfig`](../iosApp/Config/Debug.xcconfig):

```xcconfig
CHECKLIST_API_BASE_URL = http:/$()/localhost:8181
```

> Em arquivos `.xcconfig`, `//` inicia comentário. Use `http:/$()/…` para URLs com `//`.

Para dispositivo físico, use o IP da máquina na rede local. Deixe vazio para modo **offline** (admin local `admin@checklistboteco.com` / `admin123`).

Com API configurada: **sem seed local** — dados vêm do pull pós-login. Detalhes e checklist de teste: [docs/mobile-api-sync.md](mobile-api-sync.md).

## Abrir no Xcode

1. Inicialize o submódulo após clonar ou trocar de branch:

```bash
git submodule update --init --recursive
```

2. Gere o projeto (se necessário):

```bash
python3 iosApp/generate_xcodeproj.py
```

3. Abra `iosApp/ChecklistBoteco.xcodeproj`.
4. O Xcode resolve **um** package source-control local em `Packages/`, fixado pelo `Package.resolved`.
5. Selecione simulador iPhone e **Run** (⌘R).

### Atualizar código em `Packages/`

As alterações do submódulo precisam ser publicadas antes de atualizar o ponteiro no projeto principal:

```bash
cd Packages
git switch ios-packages
git add -A
git commit -m "Descreva a alteração dos packages"
git push origin ios-packages
cd ..
git add Packages iosApp/ChecklistBoteco.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
```

O commit do repositório principal registra o novo gitlink. Não edite a revisão manualmente sem publicar o commit do submódulo.

## Estrutura de pacotes (umbrella)

| Produto | Responsabilidade |
|---------|------------------|
| `Models` | Domínio, permissões, WorkClockCalculator, validadores |
| `Network` | APIClient, AuthClient, SyncClient, ErrorMapper |
| `Persistence` | GRDB (SQLite), ChecklistRepository, outbox |
| `Env` | AppSession, SyncEngine, BGTask sync |
| `DesignSystem` | Cores, overlay de loading, alertas |
| `Auth` | Login, registro, Keychain + Face ID |
| `ChecklistFeature` | Checklist por área + câmera |
| `WorkClockFeature` | Ponto + CoreLocation geofence 5 m |
| `InventoryFeature` | Contagem, envio e auditoria diária |
| `DashboardFeature` | Resumo local |
| `AdminFeatures` | Atividades e permissões delegadas |
| `AIChatFeature` | Chat administrativo sobre vendas, compras, estoque e ponto |

Ver [`Packages/README.md`](../Packages/README.md).

## Persistência: GRDB vs SwiftData

**Decisão atual: GRDB (SQLite)** — espelha SQLDelight do Android e suporta outbox de sync em transação SQL.

| Critério | GRDB | SwiftData |
|----------|------|-----------|
| iOS mínimo | 16 | **17+** |
| Xcode | 14.2+ | 15+ |
| Paridade Android / outbox | Sim | Refactor grande |
| Offline-first sync | Transações SQL nativas | `ModelContext` sem SQL direto |

SwiftData só faria sentido após subir deployment target para iOS 17+ e aceitar reimplementar schema + migrations + testes.

## Testes unitários

Via umbrella (recomendado):

```bash
cd Packages && swift test
```

Ou por módulo:

```bash
cd Packages/Models && swift test
```

Build do app:

```bash
xcodebuild -project iosApp/ChecklistBoteco.xcodeproj -scheme ChecklistBoteco \
  -destination 'platform=iOS Simulator,name=iPhone 14' build
```

## Troubleshooting

### `No such module 'Models'`

Verifique se o package local `Packages` está vinculado ao target **ChecklistBoteco** e se a pasta existe na raiz do repositório.

### Package não atualiza após editar código

1. Confirme `git submodule status` sem prefixo `-` ou `+`.
2. Confirme que o commit de `Packages/` foi enviado para `origin/ios-packages`.
3. Execute **File → Packages → Reset Package Caches** no Xcode.

## Funcionalidades (paridade Android)

- Login remoto + 2FA + `/api/me`
- Face ID: login salvo (usuário + senha) via Keychain
- Checklist offline com foto e sync push/pull
- Ponto bloqueado fora de 5 m / accuracy > 20 m
- Inventário: rascunho, envio e auditoria diária
- Tabs condicionais por permissão
- Loader global e dialog de erro
- Sync em background (`BGTaskScheduler`)
- Chat IA somente para administradores, com orçamento e fontes consultadas

## Permissões Info.plist

- `NSFaceIDUsageDescription`
- `NSCameraUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `UIBackgroundModes`: `fetch`
- `BGTaskSchedulerPermittedIdentifiers`: `com.checklistboteco.ios.sync`

## Backlog de refatoração UI

Melhorias de padrões SwiftUI planejadas (sem escopo na fase 1 de docs) estão em [`ios-swiftui-standards.md` — Fase 2](ios-swiftui-standards.md#fase-2--refatoração-ui-backlog).
