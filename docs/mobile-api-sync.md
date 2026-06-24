# Sincronização mobile com API (offline-first)

O app iOS e Android usam SQLite local como cache e a API Quarkus como fonte de verdade quando `CHECKLIST_API_BASE_URL` está configurada.

## Configuração

| Plataforma | Variável | Exemplo dev |
|------------|----------|-------------|
| iOS | `CHECKLIST_API_BASE_URL` em `Debug.xcconfig` | `http://localhost:8181` |
| Android | `CHECKLIST_API_BASE_URL` / `BackendEnvironment` | `http://10.0.2.2:8181` |

Sem URL configurada, o app mantém o **seed offline** (admin/colaborador + atividades locais).

## Política de seed (opção A)

- **Com API:** não roda seed; na primeira execução remove artefatos `seed-*` (usuários demo e atividades locais).
- **Sem API:** seed atual para desenvolvimento offline.

## Fluxo de login e sessão

1. Login remoto (`POST /api/auth/login` + 2FA se necessário).
2. `GET /api/me` confirma perfil e permissões.
3. Token e `remoteUserId` persistem em `SyncMetadata`.
4. **Cold start:** restaura sessão via metadata + `GET /api/me` (sem limpar token no boot).
5. **Pull inicial paginado** após login (`GET /api/sync/pull` até `hasMore = false`).
6. Push de outbox local (`POST /api/sync/push`).

## Endpoints integrados

| Recurso | Endpoints |
|---------|-----------|
| Checklist | `/api/sync/pull`, `/api/sync/push` |
| Usuários | `GET/POST /api/users`, `PATCH .../permissions` |
| Ponto | `GET /api/work-clock/worksite` (cache local + geofence) |
| Dashboard admin | `GET /api/admin/dashboard` (opcional, fallback local) |
| Inventário | `GET /api/inventory/counts`, `GET .../admin-stock/balances` + POST existentes |

## Checklist de teste manual

1. Backend Quarkus em `:8181` com usuários reais (não seed local).
2. Login admin → checklist vazio até pull → atividades do servidor.
3. Colaborador completa tarefa → push → admin vê no backend/admin web.
4. Admin cria/edita atividade → sync → colaborador vê após pull.
5. Permissões alteradas no app → refletidas em `GET /api/users`.
6. Ponto usa raio do `worksite` da API.
7. Sem `CHECKLIST_API_BASE_URL` → seed + login offline continua funcionando.
8. Fechar e reabrir app com API → sessão restaurada sem novo login.

## Limitações conhecidas

- Fotos de conclusão usam path local; upload de imagem é fase futura.
- DB com seed antigo antes da migração: purge one-shot na primeira execução com API.
