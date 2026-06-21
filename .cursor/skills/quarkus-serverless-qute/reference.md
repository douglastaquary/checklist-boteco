# Referência — ChecklistBoteco serverless

## Mapa do backend

```
backend/
├── pom.xml                          # Quarkus 3, rest-jackson, rest-qute, lambda-http, dynamodb SDK
├── template.yaml                    # SAM: Lambda + HttpApi + 3 tabelas DynamoDB
├── samconfig.toml                   # Config deploy SAM
├── build-lambda.sh                  # package → function.zip
├── Dockerfile.deprecated            # NÃO usar — legado JAR runner
├── src/main/java/com/checklistboteco/backend/
│   ├── web/                         # AdminResource, ApiResource, mappers
│   ├── security/                    # AdminGuard, TokenService, PasswordHasher
│   ├── store/                       # LocalStore, DynamoDbStore (users, sync, activities)
│   ├── inventory/                   # Contagem, estoque admin, auditoria
│   ├── purchases/                   # CSV compras + MCP tools
│   ├── sales/                         # CSV vendas + auditoria stock
│   └── purchases/mcp/               # PurchaseMcpResource POST /mcp
├── src/main/resources/
│   ├── application.properties       # %dev port 8181, CORS, paths .data/
│   ├── templates/admin.html         # Shell admin (views: dashboard, users, inventory…)
│   └── META-INF/resources/assets/   # admin.css, admin.js [, auth.js futuro]
└── src/test/java/                   # @QuarkusTest + RestAssured
```

## Módulos e rotas principais

| Módulo | Prefixo API | Admin UI |
|--------|-------------|----------|
| Auth | `/api/auth/login`, `/api/auth/verify-device` | login em admin.html |
| Users | `/api/users`, `/api/users/{id}/permissions` | aba Equipe |
| Activities | `/api/activities` | aba Atividades |
| Inventory | `/api/inventory/counts`, `/admin-stock/*`, `/audit/daily` | aba Contagens |
| Purchases | `/api/purchases/*` | aba Compras (admin) |
| Sales | `/api/sales/*` | aba Vendas (admin) |
| MCP | `POST /mcp` | — |
| Health | `/api/health` | status no admin |

## Persistência por ambiente

| Módulo | Local (≠ prod) | Prod |
|--------|----------------|------|
| Core (users, sync) | `LocalStore` `@UnlessBuildProfile("prod")` | `DynamoDbStore` `@IfBuildProfile("prod")` → `ChecklistTable` |
| Compras | `LocalPurchaseRepository` `@UnlessBuildProfile("prod")` | `DynamoPurchaseRepository` → `PurchasesTable` |
| Vendas | `LocalSalesRepository` `@UnlessBuildProfile("prod")` | `DynamoSalesRepository` → `SalesTable` |
| Inventário | `LocalInventoryRepository` | `DynamoInventoryRepository` → `ChecklistTable` (kind INVENTORY_*) |
| Estoque admin | `LocalAdminStockRepository` | `DynamoAdminStockRepository` → snapshot `ADMIN_STOCK#SNAPSHOT` |

Arquivos dev em `backend/.data/`:
- `purchases-local.json`, `sales-local.json`, `inventory-local.json`, `inventory-admin-stock.json`

## SAM / Lambda

**Handler:** `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`

**Variáveis de ambiente (template):**
- `JWT_SECRET`, `CHECKLIST_TABLE`, `PURCHASES_TABLE`, `SALES_TABLE`, `PURCHASES_MCP_TOKEN`
- `QUARKUS_PROFILE=prod`

**Tabelas DynamoDB:** `ChecklistTable` (pk), `PurchasesTable` (pk+sk), `SalesTable` (pk+sk) — `PAY_PER_REQUEST`, PITR.

**Roteamento:** HttpApi `/{proxy+}` + `/` → mesma Lambda.

## application.properties — chaves relevantes

```properties
%dev.quarkus.http.port=8181
checklist.dynamodb.table=${CHECKLIST_TABLE:ChecklistTable}
checklist.aws.region=${AWS_REGION:us-east-1}
checklist.jwt.secret=${JWT_SECRET:dev-secret-change-me-32chars-minimum!!}
purchases.local.file=${PURCHASES_LOCAL_FILE:.data/purchases-local.json}
sales.local.file=${SALES_LOCAL_FILE:.data/sales-local.json}
inventory.local.file=${INVENTORY_LOCAL_FILE:.data/inventory-local.json}
inventory.admin-stock.file=${INVENTORY_ADMIN_STOCK_FILE:.data/inventory-admin-stock.json}
quarkus.http.cors.enabled=true
```

## AdminGuard — permissões delegadas

Métodos comuns:
- `requireAdmin(auth)`
- `requireUserManagementAccess(auth, forCreate)`
- `requireInventoryCountAccess(auth)`
- `requireInventoryInsightsAccess(auth)`
- `requireAdministrativeStockAccess(auth)`
- `requireApplyDailyAuditAccess(auth)`

Flags em `Models.FeaturePermissions`: `canRegisterUsers`, `canCreateActivities`, `canEditUsers`, `canCreateInventoryCounts`, `canViewInventoryInsights`, `canManageAdministrativeStock`.

## admin.js — convenções

- `token` / `localStorage.checklist-token`
- `api(path, { method, body })` — JSON + Bearer
- `readApiError(response)` — parse erro API
- `buildUserPermissions(user, key, value)` — PATCH permissões
- `accessibleViews()` — nav por permissão
- `configureNavigation()` — esconde abas não permitidas

## Testes

```bash
cd backend && ./mvnw test
```

Padrão: criar usuário admin → usuário delegado → exercitar endpoint → cleanup DELETE user.

## Cognito (Fase 4 — alvo)

Duas camadas (modelo re:Money):
1. **API Gateway JWT authorizer** em `/api/*`
2. **Cookie `id_token`** para páginas Qute + `/auth/callback` sem guard server-side

Arquivos previstos: `assets/auth.js`, rota callback, parâmetros Cognito no `template.yaml`.

Detalhes: [docs/architecture-serverless.md](../../docs/architecture-serverless.md)
