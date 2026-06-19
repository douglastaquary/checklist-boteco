# Cursor Project Rules

Copie e cole o conteúdo abaixo no campo de regras do projeto no Cursor.

```text
Este projeto usa o servidor MCP `checklist-boteco-analytics` para consultar dados importados de compras, vendas e auditoria.

Sempre que a solicitação mencionar termos como:
- beco
- Beco da Praia
- vendas
- compras
- abastecimento
- extravio
- perdas
- quantas vendeu
- quanto vendeu
- qual a quantidade vendida

use o MCP antes de responder por inferência.

Regras de uso:
- Para perguntas por produto, use `sales_by_product`.
- Para listagens de vendas, use `sales_list`.
- Para totalizações por produto, categoria ou local, use `sales_aggregate`.
- Para auditoria entre vendido e abastecido, use `sales_audit_stock`.
- Para descobrir cobertura, campos e datasets disponíveis, use `sales_get_schema`, `sales_get_imports`, `purchases_get_schema` e `purchases_get_imports`.

Heurísticas:
- “beco” deve ser interpretado como contexto do estabelecimento do projeto.
- Perguntas como “quantas água com gás vendeu?” devem usar `sales_by_product`.
- Perguntas como “qual a quantidade de produto X vendida no beco no período Y?” devem usar `sales_by_product`.
- Perguntas como “houve extravio de produto X?” devem usar `sales_audit_stock`.
- Nunca responda esse tipo de pergunta sem consultar antes os dados via MCP.
```
