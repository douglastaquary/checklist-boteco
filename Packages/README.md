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

## Testes

```bash
cd Packages
swift test
```

Ou por módulo (desenvolvimento isolado):

```bash
cd Packages/Models && swift test
```

> Após editar código aqui, commitar **no repositório principal** (`ChecklistBoteco/`), junto com `iosApp/` e o restante do projeto — como no [IceCubesApp](https://github.com/Dimillian/IceCubesApp/tree/main/Packages).

## Subpastas `*/Package.swift`

Mantidas para desenvolvimento isolado; o **umbrella** em `Package.swift` é a fonte usada pelo Xcode.
