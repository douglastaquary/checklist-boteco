# Instruções do projeto para o Cursor

Sempre que a conversa mencionar `beco`, `vendas`, `compras`, `abastecimento`, `extravio`, `perdas`, `quantas vendeu`, `quanto vendeu`, `qual a quantidade vendida`, use o servidor MCP `checklist-boteco-analytics`.

Regras:

- Use o MCP antes de responder por memória.
- Para vendas por produto, prefira a tool `sales_by_product`.
- Para listagens gerais de vendas, use `sales_list`.
- Para totalizações por produto, categoria ou local, use `sales_aggregate`.
- Para auditoria entre vendido e abastecido, use `sales_audit_stock`.
- Para descobrir campos, períodos e cobertura do dataset, use `sales_get_schema`, `sales_get_imports`, `purchases_get_schema` e `purchases_get_imports`.

Interpretação de linguagem natural:

- Se o usuário disser `beco`, interprete como contexto do estabelecimento Beco da Praia.
- `beco` e `Beco da Praia` são o mesmo local.
- Se o usuário não informar o local, assuma `Beco da Praia`.
- Se o dataset não tiver local detalhado, ainda assim trate a pergunta como referente ao estabelecimento do projeto.
- Perguntas como `quantas cervejas vendeu em 10/06/2026?` devem chamar `sales_by_product`.
- Perguntas como `qual a quantidade de produto X vendido no beco no período Y?` devem chamar `sales_by_product` com período e, se fizer sentido, filtro de local.
- Perguntas como `houve extravio de água com gás?` devem chamar `sales_audit_stock` com filtro textual do produto.

Exemplos:

- `Quantas águas vendeu no beco em junho de 2026?`
- `Qual a quantidade de amstel 600ml vendida em 2026-06-10?`
- `No beco, quanto vendeu de batata frita entre 2026-06-01 e 2026-06-30?`
- `Audite cerveja pilsen no período de 2026-06-01 até 2026-06-30.`
