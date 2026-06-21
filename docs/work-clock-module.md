# Módulo de ponto (work clock)

Registro de jornada com geofence GPS, sincronização com DynamoDB e painel admin para escala 4x3, resumo semanal e exportação contábil.

## Regras de negócio

| Métrica | Regra |
|---------|--------|
| Jornada diária esperada | 8 h |
| Meta semanal (escala 4x3) | 40 h |
| Horas extras | Apenas acima de 40 h/semana |
| Descanso padrão | 1 h (almoço + descanso) |
| Descanso em turno longo | 2 h quando jornada diária ≥ 12 h |
| Geofence | Marcação só dentro de **5 m** do Beco da Praia |
| Precisão GPS | Accuracy ≤ 20 m |

Horas trabalhadas contam intervalos entre `ENTRADA`/`ALMOCO_FIM`/`DESCANSO_FIM` e paradas (`ALMOCO_INICIO`, `DESCANSO_INICIO`, `SAIDA`).

Faltas na escala: dia configurado como trabalho **sem** `ENTRADA`, ou dia passado com `ENTRADA` mas sem `SAIDA`.

## Coordenadas do local

Configuráveis no backend (`application.properties`):

```properties
worksite.latitude=-23.85491
worksite.longitude=-46.13872
worksite.radiusMeters=5
worksite.name=Beco da Praia
```

Endpoint público autenticado: `GET /api/work-clock/worksite`

## GPS no Android (sem Google Maps API)

A validação de distância usa **GPS nativo** (Fused Location Provider) + fórmula Haversine. **Não é necessária** chave do Maps SDK.

1. Permissões em `AndroidManifest.xml`: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
2. Dependência: `play-services-location`
3. `LocationProvider` (expect/actual) observa coordenadas; o botão de ponto só habilita com distância ≤ 5 m e accuracy ≤ 20 m

### Mapa visual (opcional)

Se quiser mapa na tela (não obrigatório para geofence):

1. Google Cloud Console → ativar **Maps SDK for Android**
2. Criar API key restrita ao package `com.checklistboteco` + SHA-1 debug/release
3. `local.properties`: `MAPS_API_KEY=...`
4. `AndroidManifest` meta-data `com.google.android.geo.API_KEY`

## Sincronização app → backend

- SQLDelight: colunas `syncStatus` (`PENDING`/`SYNCED`) e `remoteId`
- Push via `POST /api/sync/push` com `workClockEntries`
- `deviceId` correto via `DeviceIdentity.getOrCreateDeviceId()`
- Retry automático no `init` do `WorkClockViewModel` e após falha de rede
- Backend rejeita entradas com `distanceFromWorkMeters > worksite.radiusMeters`

## API admin (`Authorization: Bearer`, perfil ADMIN)

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/api/work-clock/summary?from&to&userId` | Resumo por colaborador |
| GET | `/api/work-clock/entries?userId&from&to` | Marcações detalhadas |
| GET/PUT | `/api/work-clock/schedule/{userId}` | Escala 4x3 (dias da semana) |
| GET | `/api/work-clock/export.csv?month&year` | CSV mensal |
| GET | `/api/work-clock/export.pdf?month&year` | PDF mensal (OpenPDF) |
| GET | `/api/work-clock/worksite` | Coordenadas do local |

## Admin web

Aba **Ponto** (somente admin): filtros de período, tabela resumo, configuração de escala por usuário, export CSV/PDF e modal de marcações.

## MCP (somente leitura)

O histórico de ponto é exposto no mesmo servidor MCP `checklist-boteco-analytics` (`POST /mcp`, token `PURCHASES_MCP_TOKEN`).

| Tool | Descrição |
|------|-----------|
| `work_clock_summary` | Resumo por colaborador (`from`, `to`, `userId` opcional) |
| `work_clock_entries` | Marcações detalhadas (`userId`, `from`, `to`) |
| `work_clock_schedule` | Escala 4x3 do colaborador (`userId`) |
| `work_clock_worksite` | Coordenadas e raio do Beco da Praia |

Exemplo:

```bash
curl -X POST http://127.0.0.1:8181/mcp \
  -H 'Authorization: Bearer local-purchases-token' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"work_clock_summary","arguments":{"from":"2026-06-01","to":"2026-06-30"}}}'
```

Guia completo: [docs/mcp-local-test.md](mcp-local-test.md)

## Desenvolvimento local

```bash
cd backend && ./mvnw quarkus:dev
# http://localhost:8181/admin
```

App Android apontando para o host:

```properties
CHECKLIST_API_BASE_URL=http://10.0.2.2:8181
```
