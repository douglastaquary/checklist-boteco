# Instruções para agentes de IA — Checklist Boteco

Fonte canônica e **agnóstica ao modelo/ferramenta** (Cursor, Codex, Claude Code, etc.). Arquivos em `.cursor/` e `.codex/` são atalhos opcionais que apontam para este documento.

## Contexto do projeto

- Sistema operacional do estabelecimento **Beco da Praia** (`beco` = `Beco da Praia`).
- **Android / Desktop / Compose**: Kotlin Multiplatform em `composeApp/`.
- **iOS**: app nativo SwiftUI em `iosApp/` + Swift Packages em `Packages/`.
- **Backend / admin web**: Quarkus serverless + Qute em `backend/`.
- Persistência local iOS: **GRDB** (SQLite), espelhando SQLDelight do Android.

## Roteamento por domínio

Antes de implementar, leia a skill ou doc indicada para o domínio da tarefa.

| Domínio | Quando usar | Referência obrigatória |
|---------|-------------|----------------------|
| Backend, API, admin web, deploy AWS | Endpoints, Qute, DynamoDB, SAM | [`.cursor/skills/quarkus-serverless-qute/SKILL.md`](.cursor/skills/quarkus-serverless-qute/SKILL.md) |
| Arquitetura serverless | Contratos e decisões | [`docs/architecture-serverless.md`](docs/architecture-serverless.md) |
| **iOS / SwiftUI** | Telas, layout, estado, navegação, sheets | [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](.cursor/skills/swiftui-ui-patterns/SKILL.md) + [`docs/ios-swiftui-standards.md`](docs/ios-swiftui-standards.md) + [`docs/mobile-ui-ux-guidelines.md`](docs/mobile-ui-ux-guidelines.md) |
| Setup iOS (Xcode, SPM, API local) | Build, simulador, troubleshooting | [`docs/ios-app.md`](docs/ios-app.md) |
| Android / KMP / Compose | Telas, navegação, Material 3, abas | [`.cursor/skills/compose-ui-patterns/SKILL.md`](.cursor/skills/compose-ui-patterns/SKILL.md) + [`docs/android-compose-navigation.md`](docs/android-compose-navigation.md) + [`docs/mobile-ui-ux-guidelines.md`](docs/mobile-ui-ux-guidelines.md) |
| Analytics (vendas, compras, ponto, estoque) | Perguntas sobre dados importados | Seção MCP abaixo + [`docs/mcp-local-test.md`](docs/mcp-local-test.md) |

### iOS — regras resumidas

- Deployment: **iOS 16**, Xcode **14.2+**, Swift **5.7+**.
- Estado compartilhado: `ObservableObject` + `@StateObject` / `@ObservedObject` / `@EnvironmentObject` (não `@Observable` neste target).
- Sem ViewModels dedicados; views pequenas + serviços injetados.
- Bootstrap: `AppDependencies` com `init() throws` — proibido `try!` no launch.
- Formatação: [`.swiftformat`](.swiftformat) (2 espaços).
- Validação iOS: usar `xcodebuild` do app. Não usar `swift test` em `Packages/`, pois a CLI compila para macOS host e o projeto não suporta macOS.
- Antes de criar/refatorar UI: ler o skill + entrada em [`references/components-index.md`](.cursor/skills/swiftui-ui-patterns/references/components-index.md).

## Servidor MCP `checklist-boteco-analytics`

Use o MCP **antes de responder por memória** quando a conversa mencionar:

`beco`, `Beco da Praia`, `vendas`, `compras`, `abastecimento`, `extravio`, `perdas`, `quantas vendeu`, `quanto vendeu`, `qual a quantidade vendida`, `vendedor`, `garçom`, `usuário`, `10%`, `gorjeta`, `ponto`, `jornada`, `horas extras`, `faltas`, `escala`, contagem ou auditoria de estoque.

### Contexto fixo

- Este sistema atende somente o estabelecimento **Beco da Praia**.
- Se o usuário não informar o local, assuma **Beco da Praia**.
- Se o dataset importado não tiver coluna de local explícita, trate a pergunta como referente ao dataset do Beco no projeto.

### Ferramentas por tipo de pergunta

| Pergunta | Tool MCP |
|----------|----------|
| Vendas por produto / “quantas X vendeu?” | `sales_by_product` ou `sales_quantity_by_product_in_period` |
| Vendas por usuário/vendedor/garçom e 10% | `sales_by_seller` ou `sales_aggregate` com `groupBy=seller` |
| Listagem de vendas | `sales_list` |
| Totalizações (produto, categoria, local) | `sales_aggregate` |
| Auditoria vendido × abastecido / extravio | `sales_audit_stock` |
| Schema e cobertura de vendas | `sales_get_schema`, `sales_get_imports` |
| Compras (listagem, agregação, schema) | `purchases_list`, `purchases_aggregate`, `purchases_get_schema`, `purchases_get_imports` |
| Contagem / saldo teórico diário | `inventory_daily_audit`, `inventory_count_sessions` |
| Resumo de ponto (horas, extras, faltas) | `work_clock_summary` |
| Datas e quantidade de faltas | `work_clock_absences` |
| Marcações detalhadas | `work_clock_entries` |
| Escala 4x3 | `work_clock_schedule` |
| Local do ponto (coordenadas) | `work_clock_worksite` |

### Heurísticas de linguagem natural

- `quantas cervejas vendeu em 10/06/2026?` → `sales_by_product`
- `qual a quantidade de produto X no beco no período Y?` → `sales_by_product` com período
- `quanto o João Rodrigues vendeu ontem no forró?` → `sales_by_seller` com período resolvido e local Beco da Praia
- `quanto deu de 10% por garçom?` → `sales_aggregate` com `groupBy=seller`
- `houve extravio de água com gás?` → `sales_audit_stock` com filtro textual
- `quais dias João faltou em junho?` → `work_clock_absences` com período
- `quantas faltas cada colaborador teve este mês?` → `work_clock_absences`

### Configuração local do MCP

- Endpoint: `http://127.0.0.1:8080/mcp`
- Header: `Authorization: Bearer local-purchases-token`
- Arquivos prontos: [`.cursor/mcp.json`](.cursor/mcp.json), [`.codex/mcp.json`](.codex/mcp.json)
- Guia completo: [`docs/mcp-local-test.md`](docs/mcp-local-test.md)

O token MCP é **separado** do JWT do login web. Acesso somente leitura.

## Credenciais de desenvolvimento

- **Admin**: `admin@checklistboteco.com` / `admin123`
- Backend local (dev): `http://localhost:8181` (Quarkus)

## Commits e PRs

- Siga o estilo de commit existente no repositório.
- iOS: ver checklist de PR em [`docs/ios-swiftui-standards.md`](docs/ios-swiftui-standards.md).
- Não commite artefatos de build (`iosApp/.build/`, `Packages/.build/`, `DerivedData/`).
