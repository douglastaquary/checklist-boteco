# Backend Kotlin e Web Admin

Este backend expõe uma API REST em Kotlin/Ktor para sincronizar dados do app e uma página web administrativa servida em `/`.

## Escopo

- API REST para autenticação, usuários, permissões, atividades, conclusões e sincronização.
- Web admin com dashboard, usuários/permissões e atividades.
- A web admin não exibe marcações de ponto. O módulo de ponto permanece somente no app.
- Persistência em SQLite para MVP, simples, performática e de baixo custo para até 10 pessoas.
- Autenticação por senha + confiança de dispositivo via código de verificação no primeiro login, troca de aparelho ou perda da seed local.

## Execução local

```bash
./gradlew :backend:run
```

Variáveis opcionais:

```bash
PORT=8080
JWT_SECRET=troque-este-segredo
CHECKLIST_BOTECO_DB=backend-data/checklist-boteco.db
```

URLs locais:

- Admin web: `http://localhost:8080`
- Health check: `http://localhost:8080/api/health`

Credenciais seed:

- Email: `admin@checklistboteco.com`
- Senha: `admin123`

## Contratos principais

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@checklistboteco.com",
  "password": "admin123",
  "deviceId": "uuid-do-aparelho",
  "deviceName": "iPhone Douglas"
}
```

No primeiro login de um dispositivo novo, a resposta pede confirmação do aparelho:

```json
{
  "requiresTwoFactor": true,
  "challengeId": "...",
  "deliveryHint": "Código de verificação gerado para confirmação do dispositivo"
}
```

Em desenvolvimento local, o campo `developmentCode` também é retornado para facilitar testes. Em produção, substitua isso por envio via email, SMS ou app autenticador.

Confirmação do aparelho:

```http
POST /api/auth/verify-device
Content-Type: application/json

{
  "challengeId": "...",
  "code": "123456",
  "deviceId": "uuid-do-aparelho",
  "deviceName": "iPhone Douglas"
}
```

Resposta após o dispositivo ser confiado:

```json
{
  "token": "...",
  "user": {
    "id": "...",
    "name": "admin",
    "email": "admin@checklistboteco.com"
  }
}
```

Use o token com:

```http
Authorization: Bearer <token>
```

### Sincronização

Pull:

```http
GET /api/sync/pull?since=0
```

Push:

```http
POST /api/sync/push
Content-Type: application/json

{
  "activities": [],
  "completions": [],
  "workClockEntries": []
}
```

O app deve manter o comportamento offline-first: salvar localmente, marcar como pendente e chamar `push` quando houver conectividade. Depois do `push`, chamar `pull` com o último timestamp sincronizado.

Marcações de ponto enviadas para `/api/sync/push` são filtradas pelo backend: uma marcação só é aceita quando `workClockEntries.userId` é igual ao usuário autenticado no token. Isso impede que um colaborador envie ponto para outro usuário.

## HTTPS

O app deve consumir a API sempre por `https://` em produção. Em deploys gerenciados como Render, Railway e Cloud Run, o TLS fica no edge/proxy da plataforma e o Ktor recebe o tráfego já encaminhado para a porta interna.

Regras:

- Produção: usar somente URL pública `https://...`
- Localhost: `http://localhost:8080` pode ser usado apenas para desenvolvimento
- Não enviar `JWT_SECRET` padrão para produção
- Configurar CORS apenas para domínios reais quando sair do MVP

No build Android, configure a URL pública:

```bash
./gradlew :composeApp:assembleDebug -PCHECKLIST_API_BASE_URL=https://sua-api.exemplo.com
```

Quando essa propriedade fica vazia, o app mantém o login local/offline. Quando ela é preenchida, o login usa a API, confirma dispositivo novo por código e sincroniza marcações de ponto com o token do usuário autenticado.

## Deploy com Docker

Build local:

```bash
docker build -f backend/Dockerfile -t checklist-boteco-backend .
docker run -p 8080:8080 \
  -e JWT_SECRET=troque-este-segredo \
  -e CHECKLIST_BOTECO_DB=/app/data/checklist-boteco.db \
  -v checklist-boteco-data:/app/data \
  checklist-boteco-backend
```

## Deploy em Render

Render informa em sua página de preços que há plano Free para web services e planos pagos por compute; a página free também indica que o workspace Hobby é gratuito e suporta serviços pequenos. Consulte [Render Pricing](https://render.com/pricing) e [Deploy for Free](https://render.com/free).

Passos:

1. Criar um novo **Web Service**.
2. Conectar o repositório.
3. Selecionar deploy via Docker.
4. Dockerfile path: `backend/Dockerfile`.
5. Definir variáveis:
  - `JWT_SECRET`
   - `CHECKLIST_BOTECO_DB=/app/data/checklist-boteco.db`
6. Configurar health check: `/api/health`.
7. Para persistência real, adicionar disco persistente no plano que ofereça suporte ou migrar para Postgres.

Indicação: bom para começar barato e simples. Para produção, prefira plano pago com persistência adequada.

## Deploy em Railway

Railway documenta planos Free, Hobby, Pro e Enterprise. A documentação oficial lista Free a `$0/month` com crédito mensal pequeno e Hobby a `$5/month`, além de cobrança por uso de RAM/CPU/rede/storage. Consulte [Railway Pricing Plans](https://docs.railway.com/reference/pricing/plans) e [Pricing FAQs](https://docs.railway.com/reference/pricing/faqs).

Passos:

1. Criar projeto no Railway.
2. Conectar o repositório.
3. Usar Dockerfile `backend/Dockerfile`.
4. Definir variáveis:
  - `JWT_SECRET`
  - `CHECKLIST_BOTECO_DB=/app/data/checklist-boteco.db`
5. Criar Volume e montar em `/app/data`.
6. Expor porta via variável `PORT` fornecida pelo Railway.
7. Validar `/api/health`.

Indicação: prático para MVP, com atenção ao uso de recursos para evitar cobrança acima do plano.

## Deploy em Google Cloud Run

Cloud Run é serverless para containers e a página oficial menciona cobrança por uso com camada always-free; a documentação mostra deploy de imagens de container. Consulte [Cloud Run Pricing](https://cloud.google.com/run/pricing) e [Deploying container images to Cloud Run](https://docs.cloud.google.com/run/docs/deploying).

Passos:

1. Ativar Cloud Run e Artifact Registry.
2. Buildar e publicar imagem:

```bash
gcloud builds submit --tag REGION-docker.pkg.dev/PROJECT/checklist/backend:latest
```

3. Deploy:

```bash
gcloud run deploy checklist-boteco-backend \
  --image REGION-docker.pkg.dev/PROJECT/checklist/backend:latest \
  --region REGION \
  --allow-unauthenticated \
  --set-env-vars JWT_SECRET=troque-este-segredo
```

4. Validar `/api/health`.

Observação: Cloud Run é ótimo para baixo tráfego, mas o armazenamento local do container não é persistente. Para produção com SQLite, monte um volume compatível quando disponível ou evolua para Cloud SQL/PostgreSQL.

## Próxima evolução recomendada

- Evoluir SQLite para PostgreSQL quando o volume de dados ou necessidade de multi-instância crescer.
- Adicionar refresh token e rotação de segredo.
- Implementar sync incremental no app com cliente HTTP.
- Remover `developmentCode` do retorno do desafio e enviar o código por canal externo.
- Adicionar auditoria de permissões.
- Adicionar migrations do backend.
