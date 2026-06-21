# App iOS nativo — Checklist Boteco

Aplicativo **100% SwiftUI nativo** (sem Kotlin Multiplatform/Compose), organizado em Swift Packages em [`Packages/`](../Packages/) e shell em [`iosApp/`](../iosApp/).

Integração SPM segue o padrão do [Guia WIMB](https://github.com/douglastaquary/wimb/blob/main/.spdd/INTEGRATION-GUIDE.md): **um package umbrella** em `Packages/` referenciado pelo Xcode.

## Requisitos

| Ferramenta | Versão |
|------------|--------|
| Xcode | **14.2+** |
| Swift | **5.7+** |
| iOS Deployment Target | **16.0** |

Estado da UI: `ObservableObject` + `@EnvironmentObject` (iOS 16). Sem suporte macOS no app nem nos packages.

## Configuração da API

Edite [`iosApp/Config/Debug.xcconfig`](../iosApp/Config/Debug.xcconfig):

```
CHECKLIST_API_BASE_URL = http://localhost:8181
```

Para dispositivo físico, use o IP da máquina na rede local. Deixe vazio para modo **offline** (admin local `admin@checklistboteco.com` / `admin123`).

## Abrir no Xcode

1. Gere o projeto (se necessário):

```bash
python3 iosApp/generate_xcodeproj.py
```

2. Abra `iosApp/ChecklistBoteco.xcodeproj`.
3. O Xcode resolve **um** package local: `Packages/` (branch `main`, repositório git em [`Packages/`](../Packages/)).
4. Selecione simulador iPhone e **Run** (⌘R).

### Adicionar manualmente (se necessário)

1. **File → Add Package Dependencies... → Add Local...**
2. Selecione a pasta **`Packages`** (não subpastas individuais)
3. Vincule os produtos ao target **ChecklistBoteco**

> Após editar código em `Packages/`, commitar **dentro de `Packages/`** para o Xcode resolver as mudanças.

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

Verifique se o package local `Packages` está vinculado ao target **ChecklistBoteco** e se existe commit na branch `main` dentro de `Packages/`.

### Package não atualiza após editar código

Commit em `Packages/` ou **File → Packages → Reset Package Caches** no Xcode.

## Funcionalidades (paridade Android)

- Login remoto + 2FA + `/api/me`
- Face ID: login salvo (usuário + senha) via Keychain
- Checklist offline com foto e sync push/pull
- Ponto bloqueado fora de 5 m / accuracy > 20 m
- Inventário: rascunho, envio e auditoria diária
- Tabs condicionais por permissão
- Loader global e dialog de erro
- Sync em background (`BGTaskScheduler`)

## Permissões Info.plist

- `NSFaceIDUsageDescription`
- `NSCameraUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `UIBackgroundModes`: `fetch`
- `BGTaskSchedulerPermittedIdentifiers`: `com.checklistboteco.ios.sync`
