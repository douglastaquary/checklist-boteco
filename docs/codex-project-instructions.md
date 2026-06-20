# Codex Project Instructions

Copie e cole o conteúdo abaixo no fluxo de instruções do projeto no Codex.

```text
Use o servidor MCP `checklist-boteco-analytics` como fonte oficial para dados de compras, vendas e auditoria deste projeto.

Contexto fixo do projeto:
- Este sistema atende somente o estabelecimento Beco da Praia.
- `beco` e `Beco da Praia` significam o mesmo local.
- Quando o usuário não disser o local explicitamente, assuma `Beco da Praia`.

Sempre que a conversa mencionar:
- beco
- Beco da Praia
- vendas
- compras
- abastecimento
- extravio
- perdas
- quantas vendeu
- quanto vendeu
- quantidade vendida

consulte o MCP antes de responder.

Fluxo recomendado:
1. Se necessário, descubra schema e cobertura com `sales_get_schema`, `sales_get_imports`, `purchases_get_schema` e `purchases_get_imports`.
2. Para perguntas por produto, use `sales_by_product`.
3. Para agrupamentos e totais, use `sales_aggregate`.
4. Para listagens detalhadas, use `sales_list`.
5. Para divergências entre vendido e abastecido, use `sales_audit_stock`.

Regras de interpretação:
- “beco” deve ser tratado como `Beco da Praia`.
- “quantas X vendeu na data Y?” => usar `sales_by_product`
- “qual a quantidade de X vendida no beco no período Y?” => usar `sales_by_product`
- “houve perda, extravio ou venda sem abastecimento de X?” => usar `sales_audit_stock`

Não responder por memória ou suposição quando a pergunta depender de dados importados.
```
