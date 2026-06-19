# MCP local para Codex e Cursor

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

Se quiser orientar o agente do Cursor a usar automaticamente o MCP do projeto para perguntas de vendas e compras, use também este texto como instrução de projeto:

- [.cursor/checklist-boteco-agent-instructions.md](/Users/douglastaquary/ChecklistBoteco/.cursor/checklist-boteco-agent-instructions.md)

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

Se quiser orientar o agente do Codex a usar automaticamente o MCP do projeto para perguntas como `quantas vendeu`, `quanto vendeu` e menções a `beco`, use também este texto como instrução do projeto:

- [.codex/checklist-boteco-agent-instructions.md](/Users/douglastaquary/ChecklistBoteco/.codex/checklist-boteco-agent-instructions.md)

## Ferramentas disponíveis

- `purchases_get_schema`
- `purchases_list`
- `purchases_aggregate`
- `purchases_get_imports`
- `sales_get_schema`
- `sales_list`
- `sales_aggregate`
- `sales_by_product`
- `sales_get_imports`
- `sales_audit_stock`

## Teste manual por HTTP

```bash
curl -X POST http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer local-purchases-token' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Se estiver tudo certo, a resposta lista as ferramentas MCP de compras e vendas.

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
- `Use o MCP checklist-boteco-analytics e agregue vendas por category entre 2026-06-01 e 2026-06-30.`
- `Use o MCP checklist-boteco-analytics e faça sales_audit_stock de 2026-06-01 a 2026-06-30.`

## Como ensinar o agente a reconhecer "beco" e perguntas de produto

Use uma instrução de projeto com estas regras:

- sempre que o usuário mencionar `beco`, tratar como contexto do estabelecimento Beco da Praia;
- sempre que perguntar `quantas X vendeu`, `quanto vendeu de X`, `qual a quantidade de X vendida`, usar o MCP `checklist-boteco-analytics`;
- para perguntas por produto, usar primeiro `sales_by_product`;
- para perguntas de divergência, extravio ou perdas, usar `sales_audit_stock`;
- se o CSV importado não tiver um campo de local explícito, ainda assim responder a pergunta como referente ao dataset do Beco importado no projeto.

## Observações

- O token MCP é separado do token JWT do login web.
- O acesso via MCP é somente leitura.
- O módulo web de compras e vendas continua restrito a `ADMIN`.
- Para o módulo web, o login continua sendo administrativo; para MCP, basta o token de serviço.
