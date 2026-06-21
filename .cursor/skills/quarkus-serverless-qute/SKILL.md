---
name: quarkus-serverless-qute
description: >-
  Direciona desenvolvimento backend e admin web do ChecklistBoteco no stack
  Quarkus + Qute + vanilla JS + AWS Lambda + DynamoDB (SAM). Use ao implementar
  APIs, painel admin, persistência, deploy ou segurança serverless. Rejeita
  React/Angular, Docker, Kubernetes e bancos relacionais.
---

# ChecklistBoteco — stack Quarkus serverless

Leia este skill **antes** de implementar qualquer feature backend ou admin web.

Referências detalhadas: [reference.md](reference.md) · Exemplos: [examples.md](examples.md)

## Princípios inegociáveis

1. **Um artefato:** API REST, MCP, Qute, CSS e JS no mesmo JAR → `target/function.zip` → Lambda.
2. **Admin web:** Qute + CSS + vanilla JS em `META-INF/resources/`. Sem React, Angular, Vue, npm, Webpack ou SPA separada.
3. **Produção:** somente AWS Lambda + API Gateway HTTP API + DynamoDB via SAM. Sem Docker, Kubernetes, ECS, RDS ou docker-compose.
4. **Desenvolvimento:** `./mvnw quarkus:dev` (porta 8181) + repositórios locais JSON/memória. Sem simular containers.
5. **App Android:** cliente KMP separado; consome `/api/*` via HTTPS (ou HTTP local em `10.0.2.2`).

## Onde colocar código novo

| Tipo | Caminho |
|------|---------|
| Endpoint REST JSON | `backend/src/main/java/com/checklistboteco/backend/**/web/` — `@Path("/api/...")` |
| Página admin Qute | `backend/src/main/resources/templates/` |
| CSS / JS admin | `backend/src/main/resources/META-INF/resources/assets/` |
| Domínio / DTOs | `backend/src/main/java/.../model/` ou `.../domain/` |
| Serviço | `backend/src/main/java/.../application/` |
| Persistência | interface + `Local*Repository` + `Dynamo*Repository` |
| Testes | `backend/src/test/java/` — `@QuarkusTest` + RestAssured |
| Infra AWS | `backend/template.yaml`, `backend/samconfig.toml` |
| Docs deploy | `docs/backend-deploy.md`, `docs/architecture-serverless.md` |

## Checklist por feature

```
- [ ] Endpoint com AdminGuard ou permissão delegada (FeaturePermissions)
- [ ] Repositório Local (@UnlessBuildProfile("prod")) + Dynamo (@IfBuildProfile("prod")) se persistir
- [ ] Seção admin: HTML + JS vanilla (sem framework)
- [ ] Teste @QuarkusTest cobrindo happy path e 401/403
- [ ] Sem Dockerfile, K8s, npm ou banco relacional
- [ ] Docs atualizadas se mudar deploy ou rotas públicas
```

## Padrão API REST

```java
@Path("/api/modulo") @Consumes(APPLICATION_JSON) @Produces(APPLICATION_JSON)
public class ModuloResource {
    @Inject AdminGuard guard;
    @Inject ModuloService service;

    @POST
    public Response criar(@HeaderParam("Authorization") String auth, Request body) {
        return Response.status(CREATED)
            .entity(service.criar(guard.requireXxxAccess(auth), body))
            .build();
    }
}
```

- Erros de negócio: `IllegalArgumentException` → 400 (mapper existente).
- Auth: `Authorization: Bearer <token>` via `AdminGuard`.
- Permissões delegadas: flags em `FeaturePermissions` + métodos `require*Access` no `AdminGuard`.

## Padrão admin web (Qute + vanilla JS)

- Entrada: `AdminResource` em `/` injeta template `admin.html`.
- Assets: `/assets/admin.css`, `/assets/admin.js` (servidos de `META-INF/resources/`).
- API calls: função `api()` em `admin.js` com `fetch`, token em `localStorage` (`checklist-token`).
- Erros: usar `readApiError()` — não inventar parser paralelo.
- Permissões UI: funções `canCreateInventoryCounts()`, `canManageAdministrativeStock()`, etc. espelham backend.
- Formulários complexos: POST JSON via `fetch`. Fluxos simples: HTML form + redirect (Post-Redirect-Get).

## Padrão persistência

```java
// Local — dev/test
@ApplicationScoped
@UnlessBuildProfile("prod")
public class LocalXRepository implements XRepository { ... }

// Dynamo — prod (build profile prod)
@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoXRepository implements XRepository { ... }
```

- Dev: JSON em `backend/.data/*.json` (configurável em `application.properties`).
- Prod: DynamoDB SDK v2 direto (`DefaultCredentialsProvider` + `UrlConnectionHttpClient`).
- **Proibido em código novo:** `scan()` na `@PostConstruct` em prod — preferir get/put/query por pk/sk.
- POJOs usados em Lambda native/reflection: `@RegisterForReflection` quando aplicável.

## Desenvolvimento local

```bash
cd backend
./mvnw quarkus:dev
```

- Admin: http://localhost:8181
- Health: http://localhost:8181/api/health
- Seed: `admin@checklistboteco.com` / `admin123`
- Android emulator: `-PCHECKLIST_API_BASE_URL=http://10.0.2.2:8181`

## Deploy produção (único caminho)

```bash
cd backend
./build-lambda.sh
sam build --template-file template.yaml
sam deploy
```

Ver `backend/samconfig.toml` e `docs/backend-deploy.md`.

## Segurança — estado atual e evolução

**Atual (dev/MVP):** `TokenService` HMAC custom + 2FA de dispositivo; CORS configurável.

**Alvo prod (Fase 4):** Amazon Cognito + JWT authorizer no API Gateway:
- `/api/*` → JWT obrigatório no gateway (401 antes da Lambda).
- Páginas Qute → cookie `id_token` checado no servidor; `auth.js` + rota `/auth/callback`.
- Ver `docs/architecture-serverless.md` e artigo [re:Money security](https://dev.to/vsenger/goodbye-localhost-hello-aws-adding-security-to-remoney-2063).

Não introduzir OAuth/React. Adaptar `admin.js` para Cognito quando a Fase 4 for implementada.

## Anti-patterns — rejeitar em PR

| Anti-pattern | Motivo |
|--------------|--------|
| React / Angular / Vue / Svelte | Stack é Qute + vanilla JS |
| npm / Vite / Webpack | Sem build front-end separado |
| Dockerfile / docker-compose / K8s | Deploy prod é SAM/Lambda only |
| RDS / PostgreSQL / MySQL | DynamoDB only em prod |
| Microserviço ou repo front-end separado | Um JAR, uma Lambda |
| `scan()` DynamoDB na startup em prod | Cold start lento, memória alta |
| Segundo caminho de deploy container | Confunde operação |
| SPA com routing client-side pesado | Admin é multi-view vanilla em um HTML |

## Referências externas (conceito)

- [Quarkus + Qute full-stack](https://dev.to/vsenger/building-a-full-stack-java-app-with-quarkus-no-react-no-angular-no-problem-j1m)
- [Lambda + DynamoDB path to prod](https://dev.to/vsenger/why-quarkus-vanilla-js-aws-lambda-dynamodb-is-the-fastest-path-from-idea-to-production-3nlc)
- [Cognito security](https://dev.to/vsenger/goodbye-localhost-hello-aws-adding-security-to-remoney-2063)

## Roadmap interno

Fases 2–5 documentadas em [docs/architecture-serverless.md](../../docs/architecture-serverless.md).
