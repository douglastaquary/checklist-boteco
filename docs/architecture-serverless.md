# Arquitetura serverless — ChecklistBoteco

Contrato arquitetural do backend e admin web. O agente Cursor deve seguir a skill [`.cursor/skills/quarkus-serverless-qute/`](../.cursor/skills/quarkus-serverless-qute/SKILL.md) em todo desenvolvimento backend/web.

## Stack alvo

```
App Android (KMP) ──┐
Admin Qute + JS ────┼──► API Gateway HttpApi ──► Lambda (Quarkus) ──► DynamoDB
MCP client ─────────┘         (JWT Cognito*)              │
Chat IA iOS ─────────┘                 │                   ├──► OpenAI Responses API
                                                    Qute + assets
                                                    no function.zip
```

\* JWT Cognito na Fase 4 — ver abaixo.

## Princípios

| Permitido | Proibido |
|-----------|----------|
| Quarkus 3 + Qute + vanilla JS | React, Angular, Vue, npm, Webpack |
| `./mvnw quarkus:dev` local | Docker / docker-compose para dev |
| SAM + Lambda + DynamoDB prod | Kubernetes, ECS, RDS, segundo deploy container |
| JSON local em `backend/.data/` (dev) | Banco relacional |

O Chat IA usa `/api/ai/*`, exige usuário `ADMIN` e reutiliza internamente o catálogo MCP. Consulte [`ai-chat.md`](ai-chat.md).

Referências conceituais:
- [Quarkus + Qute full-stack](https://dev.to/vsenger/building-a-full-stack-java-app-with-quarkus-no-react-no-angular-no-problem-j1m)
- [Lambda + DynamoDB](https://dev.to/vsenger/why-quarkus-vanilla-js-aws-lambda-dynamodb-is-the-fastest-path-from-idea-to-production-3nlc)
- [Cognito security](https://dev.to/vsenger/goodbye-localhost-hello-aws-adding-security-to-remoney-2063)

---

## Fase 1 — Skill e contrato (concluída)

- Skill de projeto em `.cursor/skills/quarkus-serverless-qute/`
- Este documento como roadmap

---

## Fase 2 — Higiene de deploy

**Objetivo:** um único caminho para produção.

| Item | Status |
|------|--------|
| `backend/build-lambda.sh` | Implementado |
| `backend/samconfig.toml` | Implementado (ajustar secrets antes do deploy) |
| `QUARKUS_PROFILE=prod` no `template.yaml` | Implementado |
| `Dockerfile.deprecated` | JAR runner legado — não usar em prod |
| `docs/backend-deploy.md` | Atualizado |

**Comandos:**

```bash
cd backend
./build-lambda.sh
sam build --template-file template.yaml
sam deploy
```

---

## Fase 3 — Persistência Lambda-ready

**Objetivo:** cold start previsível; perfis consistentes.

| Item | Ação |
|------|------|
| Build profile | Local dev: `@IfBuildProfile("dev")` ou `@UnlessBuildProfile("prod")`; Dynamo: `@IfBuildProfile("prod")` |
| Scan na startup | `DynamoDbStore` e `DynamoInventoryRepository` usam lazy hydration |
| `DynamoAdminStockRepository` | getItem único (sem scan) |
| Repos compras/vendas | Comentários documentando dev/test/prod |

**Padrão de anotação (padronizado):**

```java
@UnlessBuildProfile("prod")  // LocalPurchaseRepository, LocalSalesRepository, etc.
@IfBuildProfile("prod")      // DynamoPurchaseRepository, DynamoDbStore, etc.
```

---

## Fase 4 — Cognito + JWT (produção)

**Objetivo:** API não pública; admin protegido.

### Duas camadas (modelo re:Money)

1. **API Gateway JWT authorizer** — rotas `/api/*` exigem token Cognito válido no header `Authorization`.
2. **Cookie server-side** — páginas Qute checam `id_token` em cookie; redirect para login Cognito se ausente.

### Fluxo browser

```
Browser → Cognito (OAuth) → redirect /auth/callback#id_token=...
auth.js captura fragment → localStorage + cookie → navega para /
admin.js usa token em fetch('/api/...')
```

**Importante:** callback deve ser `/auth/callback`, não `/` — fragment `#id_token` não chega ao servidor.

### Arquivos implementados (base)

| Arquivo | Função |
|---------|--------|
| `assets/auth.js` | Captura `#id_token`, grava cookie + localStorage |
| `templates/auth-callback.html` | Página callback sem auth guard |
| `web/AuthCallbackResource.java` | GET `/auth/callback` |
| `template-cognito.yaml.example` | Overlay SAM Cognito + JWT authorizer |

### Pendente para cutover prod

1. Mesclar `template-cognito.yaml.example` em `template.yaml`
2. `admin.js`: redirect login Cognito quando em prod
3. Mapear claims → permissões
4. App Android: token Cognito
5. Desativar `TokenService` HMAC em prod

### Dev local

Manter auth HMAC + 2FA em `%dev` até migração completa. Flag ou profile separado para não quebrar fluxo local.

---

## Fase 5 — Opcional pós-Cognito

- Java 21 + Lambda SnapStart
- Cognito groups ↔ `FeaturePermissions`
- CORS restrito por ambiente (`CORS_ORIGINS`)
- Native image (avaliar após SnapStart)

---

## Checklist antes de merge (backend/web)

- [ ] Skill `quarkus-serverless-qute` consultada
- [ ] Sem npm / React / Docker novo
- [ ] Local + Dynamo repos se persistir
- [ ] Teste `@QuarkusTest`
- [ ] Docs atualizadas se deploy ou auth mudou
