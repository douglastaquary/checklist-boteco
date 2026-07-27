# Checklist Boteco — Swift Packages (umbrella)

Monorepo SPM compatível com **Xcode 14.2+**, **Swift 5.7+** e **iOS 16.0**.

O app iOS referencia **este diretório inteiro** via `ChecklistBoteco.xcodeproj` (um único package local), seguindo o padrão do [Guia de Integração WIMB](https://github.com/douglastaquary/wimb/blob/main/.spdd/INTEGRATION-GUIDE.md).

## Produtos

| Produto | Descrição |
|---------|-----------|
| `Models` | Domínio, permissões, calculadoras |
| `Network` | APIClient, AuthClient, SyncClient |
| `Persistence` | GRDB (SQLite), repositórios, outbox |
| `Env` | AppSession, SyncEngine, BGTask |
| `DesignSystem` | Componentes e feedback global |
| `Auth` | Login, Keychain, Face ID |
| `ChecklistFeature` | Checklist + câmera |
| `WorkClockFeature` | Ponto + geofence |
| `InventoryFeature` | Contagem e auditoria |
| `DashboardFeature` | Resumo local |
| `AdminFeatures` | Atividades e permissões |

## Validação

Este package é **iOS-only** (`platforms: [.iOS(.v16)]`). Não há target macOS suportado.

Não use `swift test` neste diretório: a CLI do SwiftPM compila para o host macOS e ignora o fato de o produto ser validado pelo app iOS, gerando erros falsos de disponibilidade (`ObservableObject`, `Task`, `URLSession.data(for:)`, etc.).

Valide alterações dos packages pelo build do app iOS:

```bash
cd ..
./scripts/ensure-packages-spm-git.sh   # Xcode 14.2 SPM precisa de Packages/.git local
./scripts/build-ios.sh
```

> Após editar código aqui, commitar **no repositório principal** (`ChecklistBoteco/`), junto com `iosApp/` e o restante do projeto — como no [IceCubesApp](https://github.com/Dimillian/IceCubesApp/tree/main/Packages).
>
> O `.git` dentro de `Packages/` é só para o SPM (ignorado pelo monorepo). Use `./scripts/ensure-packages-spm-git.sh` após clone ou quando o Xcode não refletir mudanças.
## Subpastas `*/Package.swift`

Mantidas para desenvolvimento isolado; o **umbrella** em `Package.swift` é a fonte usada pelo Xcode.
