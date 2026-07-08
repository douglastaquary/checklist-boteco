# Chat de IA do Beco da Praia

O módulo **Chat IA** permite que administradores consultem, em linguagem natural, os dados operacionais já expostos pelo MCP `checklist-boteco-analytics`.

## Arquitetura e segurança

```text
iOS (JWT do administrador) → /api/ai/chat → OpenAI Responses API
                                      └── ferramentas internas de analytics
```

- A chave da OpenAI existe somente no backend.
- O iOS não recebe a chave nem o token MCP.
- MCP e Chat IA reutilizam o mesmo catálogo de ferramentas somente leitura.
- Perguntas e respostas não são persistidas. São guardadas apenas métricas: usuário, horário, modelo, tokens, custo, latência e ferramentas consultadas.
- O contexto enviado é limitado às quatro últimas mensagens da sessão atual.

## Configuração local

Defina `OPENAI_API_KEY` no ambiente antes de iniciar o Quarkus. Um arquivo local `backend/.env.local` pode ser usado pelo shell e está ignorado pelo Git. O nome `backend/env.local` também está ignorado para evitar vazamento acidental, mas o padrão recomendado é `backend/.env.local`. Não inclua chaves em `application.properties`, commits ou documentação.

```bash
cd backend
set -a
source .env.local
set +a
./mvnw quarkus:dev
```

### Passo a passo para teste end-to-end local

1. Crie o arquivo local de ambiente:

   ```bash
   cd /Users/douglastaquary/ChecklistBoteco/backend
   touch .env.local
   chmod 600 .env.local
   ```

2. Edite `backend/.env.local` e adicione a chave sem aspas:

   ```bash
   OPENAI_API_KEY=sk-...
   ```

3. Suba o backend carregando o arquivo:

   ```bash
   cd /Users/douglastaquary/ChecklistBoteco/backend
   set -a
   source .env.local
   set +a
   ./mvnw quarkus:dev
   ```

4. Valide que o chat deixou de retornar `503`:

   ```bash
   TOKEN=$(curl -sS -H 'Content-Type: application/json' \
     -d '{"email":"admin@checklistboteco.com","password":"admin123","deviceId":"ai-local-test","deviceName":"AI Local Test"}' \
     http://127.0.0.1:8181/api/auth/login)
   ```

   Se o login retornar `requiresTwoFactor=true`, finalize pelo endpoint `/api/auth/verify-device` usando o `developmentCode` da resposta de login.

5. Faça a pergunta pelo app iOS ou via API:

   ```bash
   curl -sS -H "Authorization: Bearer <JWT_ADMIN>" \
     -H 'Content-Type: application/json' \
     -d '{"messages":[{"role":"user","text":"Quantas Heinekens vendemos em março de 2026?"}]}' \
     http://127.0.0.1:8181/api/ai/chat
   ```

6. Resultado esperado com a base local atual:

   - `378` Heinekens vendidas em março de 2026.
   - Total aproximado: `R$ 6.477,55`.
   - Fontes consultadas devem incluir `sales_quantity_by_product_in_period` ou outra ferramenta `sales_*`.

Se `/api/ai/chat` retornar `503`, a variável `OPENAI_API_KEY` não foi carregada pelo processo do Quarkus. Se retornar `401`, o JWT do administrador expirou ou não foi informado. Se retornar dados zerados, valide primeiro o MCP direto na seção “Teste local com dados de vendas”.

Em produção, crie um segredo no AWS Secrets Manager com a chave JSON `OPENAI_API_KEY` e informe seu ARN no parâmetro SAM `OpenAiApiKeySecretArn`. O template resolve o segredo durante o deploy; o valor não fica no repositório.

Configurações opcionais:

| Variável | Padrão | Finalidade |
|---|---:|---|
| `OPENAI_MODEL` | `gpt-5-mini` | Modelo de respostas e function calling |
| `AI_MONTHLY_LIMIT_CENTS` | `500` | Limite mensal em centavos de dólar |
| `AI_MAX_OUTPUT_TOKENS` | `700` | Máximo de tokens por resposta |
| `OPENAI_TIMEOUT_SECONDS` | `15` | Timeout da chamada externa |

## Consumo e custos

O endpoint `GET /api/ai/usage` informa tokens de entrada, tokens em cache, saída, custo estimado e orçamento. Quando o custo mensal alcança o limite, `/api/ai/chat` retorna HTTP `429`.

O custo é uma estimativa baseada nas tarifas configuradas. Atualize as variáveis `AI_*_MICRODOLLARS_PER_MILLION` quando o preço do modelo mudar e compare periodicamente com o painel da OpenAI.

Para reduzir consumo, o servidor:

- envia somente ferramentas relacionadas à pergunta;
- prefere agregações a listagens completas;
- limita contexto e tamanho da resposta;
- usa um prefixo estável para favorecer prompt caching;
- faz no máximo uma rodada de execução das ferramentas e uma resposta final.

## Exemplos

- “Quanto vendemos em abril no Beco?”
- “Quantas Heinekens foram vendidas em fevereiro de 2026?”
- “Quantas Heinekens vendemos em março de 2026?”
- “Quais foram os maiores gastos com mercadorias este mês?”
- “Houve perda ou extravio no estoque hoje?”
- “Quem fez horas extras nesta semana?”

Se não houver dados ou período suficiente, o chat informa o que falta em vez de estimar números.

## Teste local com dados de vendas

Para testar uma pergunta de vendas pelo chat, três coisas precisam existir ao mesmo tempo:

1. Backend Quarkus rodando com `OPENAI_API_KEY` configurada.
2. Login no app iOS com usuário administrador.
3. Base de vendas carregada no store local/DynamoDB usado pelo backend.

O teste MCP direto não usa OpenAI e serve para validar se os dados ainda existem:

```bash
curl -sS -H 'Authorization: Bearer local-purchases-token' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"sales_quantity_by_product_in_period","arguments":{"product":"heineken","from":"2026-03-01","to":"2026-03-31"}}}' \
  http://127.0.0.1:8181/mcp
```

Com a base local `backend/.data/sales-local.json` importada de `Relatorio-Venda-beco-V2.csv`, a consulta “quantas Heinekens vendemos em março de 2026?” deve retornar `378` unidades e aproximadamente `R$ 6.477,55`. Se esse arquivo for removido, o backend apontar para outro store, ou a base DynamoDB estiver vazia, será necessário importar novamente o CSV antes do teste pelo chat.
