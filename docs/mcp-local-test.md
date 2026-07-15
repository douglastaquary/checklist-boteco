# MCP local para agentes de IA

**Regras MCP canônicas:** [`AGENTS.md`](../AGENTS.md) (seção **Servidor MCP `checklist-boteco-analytics`**).

Este guia cobre setup local e testes. Não duplique heurísticas de linguagem natural aqui — elas vivem em `AGENTS.md`.

## Endpoint local

- URL: `http://127.0.0.1:8080/mcp`
- Header: `Authorization: Bearer local-purchases-token`

## Arquivos prontos no projeto

- Cursor: [.cursor/mcp.json](/Users/douglastaquary/ChecklistBoteco/.cursor/mcp.json)
- Codex: [.codex/mcp.json](/Users/douglastaquary/ChecklistBoteco/.codex/mcp.json)

## Como colar no Cursor

Use o arquivo já pronto abaixo como referência:

- [.cursor/mcp.json](/Users/douglastaquary/ChecklistBoteco/.cursor/mcp.json)

Passos:

1. Abra o projeto no Cursor.
2. Abra as configurações de MCP do Cursor.
3. Cole o conteúdo abaixo ou aponte para o arquivo do projeto:

```json
{
  "mcpServers": {
    "checklist-boteco-analytics": {
      "url": "http://127.0.0.1:8080/mcp",
      "headers": {
        "Authorization": "Bearer local-purchases-token"
      }
    }
  }
}
```

4. Salve a configuração.
5. Reabra o chat/agente no Cursor.
6. Confirme que o servidor `checklist-boteco-analytics` aparece como disponível.

Para orientar o agente a usar o MCP e as regras do projeto, configure as instruções apontando para [`AGENTS.md`](../AGENTS.md) ou o stub [`.cursor/checklist-boteco-agent-instructions.md`](../.cursor/checklist-boteco-agent-instructions.md).

## Como colar no Codex

Use o arquivo já pronto abaixo como referência:

- [.codex/mcp.json](/Users/douglastaquary/ChecklistBoteco/.codex/mcp.json)

Passos:

1. Mantenha o backend local rodando em `127.0.0.1:8080`.
2. Abra o fluxo/local config do Codex que usa MCP.
3. Cole a mesma configuração JSON:

```json
{
  "mcpServers": {
    "checklist-boteco-analytics": {
      "url": "http://127.0.0.1:8080/mcp",
      "headers": {
        "Authorization": "Bearer local-purchases-token"
      }
    }
  }
}
```

4. Salve e reinicie a sessão/chat que vai usar o MCP.
5. Verifique se o servidor MCP aparece disponível antes de testar as perguntas.

Para orientar o agente às regras MCP e roteamento do projeto, use [`AGENTS.md`](../AGENTS.md) ou o stub [`.codex/checklist-boteco-agent-instructions.md`](../.codex/checklist-boteco-agent-instructions.md).

## Ferramentas disponíveis

- `purchases_get_schema`
- `purchases_list`
- `purchases_aggregate`
- `purchases_get_imports`
- `sales_get_schema`
- `sales_list`
- `sales_aggregate`
- `sales_by_product`
- `sales_quantity_by_product_in_period`
- `sales_by_seller`
- `sales_get_imports`
- `sales_audit_stock`
- `inventory_count_sessions`
- `inventory_daily_audit`

## Teste manual por HTTP

```bash
curl -X POST http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer local-purchases-token' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Se estiver tudo certo, a resposta lista as ferramentas MCP de compras, vendas, estoque e ponto.

Ferramentas de **ponto** (histórico de jornada):

- `work_clock_summary` — resumo por colaborador (horas, extras, faltas, descansos)
- `work_clock_entries` — marcações detalhadas de um colaborador
- `work_clock_schedule` — escala 4x3 (dias de trabalho)
- `work_clock_worksite` — coordenadas e raio do Beco da Praia

## Exemplos de teste via chat

No Cursor ou no Codex, depois de conectar o MCP, você pode testar com perguntas como:

- `Quais ferramentas MCP de compras e vendas estão disponíveis?`
- `Quais períodos de compras estão disponíveis no dataset purchases?`
- `Quais períodos de vendas estão disponíveis no dataset sales?`
- `Liste as vendas do período de 2026-06-01 até 2026-06-30.`
- `Agrupe as vendas por produto no período de 2026-06-01 até 2026-06-30.`
- `Rode uma auditoria de vendido x abastecido entre 2026-06-01 e 2026-06-30.`

Exemplos mais objetivos para validar o fluxo:

- `Use o MCP checklist-boteco-analytics e descubra o schema de sales.`
- `Use o MCP checklist-boteco-analytics e liste os imports de sales.`
- `Use o MCP checklist-boteco-analytics e responda quantas unidades de água com gás vendeu em 2026-06-19.`
- `Use o MCP checklist-boteco-analytics e responda qual a quantidade de amstel 600ml vendida no beco entre 2026-06-01 e 2026-06-30.`
- `Use o MCP checklist-boteco-analytics e responda quantas heinekens foram vendidas no beco entre 2026-05-01 e 2026-05-31 e qual o total em reais.`
- `Use o MCP checklist-boteco-analytics e agregue vendas por category entre 2026-06-01 e 2026-06-30.`
- `Use o MCP checklist-boteco-analytics e responda quanto o João Rodrigues vendeu ontem no forró e quanto deu de 10%.`
- `Use o MCP checklist-boteco-analytics e agregue vendas por seller entre 2026-06-01 e 2026-06-30.`
- `Use o MCP checklist-boteco-analytics e faça sales_audit_stock de 2026-06-01 a 2026-06-30.`
- `Use inventory_daily_audit para conferir a contagem de abertura, as vendas e o saldo teórico de 2026-06-20.`
- `Use work_clock_summary de 2026-06-01 a 2026-06-30 e diga quem teve horas extras.`
- `Use work_clock_entries para listar as marcações de ponto de um colaborador em 2026-06-18.`
- `Use work_clock_schedule e informe em quais dias da semana o colaborador trabalha.`

## Como ensinar o agente a reconhecer "beco" e perguntas de produto

Configure as instruções de projeto para ler [`AGENTS.md`](../AGENTS.md). A seção **Servidor MCP** contém:

- contexto fixo do Beco da Praia;
- mapeamento pergunta → ferramenta MCP;
- heurísticas de linguagem natural (`quantas X vendeu`, extravio, ponto, contagem diária).

Atalho para colagem inline: copie essa seção de `AGENTS.md` ou use [docs/ai-agent-instructions.md](ai-agent-instructions.md).

## Observações

- O token MCP é separado do token JWT do login web.
- O acesso via MCP é somente leitura.
- O módulo web de compras e vendas continua restrito a `ADMIN`.
- Para o módulo web, o login continua sendo administrativo; para MCP, basta o token de serviço.
- O chat do iOS não chama `/mcp` por HTTP: ele usa `/api/ai/chat`, que reaproveita o mesmo catálogo e os mesmos serviços de analytics com autenticação JWT. Veja [`ai-chat.md`](ai-chat.md).
