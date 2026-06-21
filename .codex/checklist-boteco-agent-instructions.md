# Instruções do projeto para o Codex

Quando a conversa mencionar `beco`, `Beco da Praia`, `compras`, `vendas`, `abastecimento`, `extravio`, `quantas vendeu`, `qual quantidade vendeu`, `quanto vendeu`, `ponto`, `jornada`, `horas extras`, `faltas`, `escala`, use o MCP `checklist-boteco-analytics`.

Fluxo recomendado:

1. Descobrir cobertura e schema, se necessário, com `sales_get_schema`, `sales_get_imports`, `purchases_get_schema` e `purchases_get_imports`.
2. Para perguntas por produto, usar `sales_by_product`.
3. Para agrupamentos, usar `sales_aggregate`.
4. Para auditoria de perdas, extravio e venda sem abastecimento, usar `sales_audit_stock`.
5. Para ponto e jornada, usar `work_clock_summary`, `work_clock_entries` e `work_clock_schedule`.

Heurísticas:

- `beco` = `Beco da Praia`.
- Se o usuário não informar local, assuma `Beco da Praia`.
- Se o dado importado não trouxer local explícito, continue consultando o dataset de vendas como fonte do Beco.
- `quantas nome-do-produto vendeu data x?` => `sales_by_product`
- `qual a quantidade do produto x vendida no beco no período y?` => `sales_by_product`
- `houve divergência entre vendido e abastecido de produto x?` => `sales_audit_stock` com `text=produto`

Objetivo:

- Não responder essas perguntas por inferência.
- Sempre consultar os dados importados via MCP antes de responder.
