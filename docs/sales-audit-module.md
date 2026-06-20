# Módulo de auditoria de vendas x abastecimento

## Objetivo

Criar um fluxo administrativo para importar relatórios CSV de vendas, normalizar os dados, armazená-los no DynamoDB e cruzá-los com o módulo de compras para identificar:

- extravio;
- consumo sem registro;
- roubo;
- perdas;
- venda sem abastecimento correspondente;
- abastecimento sem saída registrada no período.

## Escopo do MVP

- acesso somente para usuários `ADMIN`;
- importação CSV com prévia e mapeamento;
- campos obrigatórios de vendas: `data`, `produto`, `local` e `quantidade`;
- colunas adicionais preservadas como atributos dinâmicos;
- persistência em tabela DynamoDB separada;
- consulta web com filtros por período, categoria, local, texto e valor;
- auditoria `vendido x abastecido`;
- exposição somente leitura via MCP para agentes como Codex e Cursor.

## Campos normalizados de vendas

| Campo | Obrigatório | Exemplos |
|---|---:|---|
| `saleDate` | Sim | `data`, `data_venda`, `emissao` |
| `description` | Sim | `produto`, `item`, `mercadoria` |
| `location` | Sim | `local`, `loja`, `pdv` |
| `quantity` | Sim | `quantidade`, `qtd`, `qtde` |
| `category` | Não | `categoria`, `grupo` |
| `totalInCents` | Não | `total`, `valor`, `receita` |
| `documentNumber` | Não | `cupom`, `pedido`, `documento` |
| `unit` | Não | `unidade`, `un` |
| `unitPriceInCents` | Não | `valor_unitario`, `preco_unitario` |

## Endpoints REST

- `POST /api/sales/imports/preview`
- `POST /api/sales/imports/{id}/commit`
- `GET /api/sales/imports`
- `GET /api/sales/schema`
- `POST /api/sales/query`
- `POST /api/sales/aggregate`
- `POST /api/sales/audit/stock`

## Regras da auditoria

O backend cruza compras e vendas pelo produto normalizado e local.

- `CRITICO`: existe venda, mas não há abastecimento correspondente;
- `ALERTA`: quantidade vendida é maior que a quantidade abastecida;
- `ATENCAO`: houve abastecimento, mas não houve saída registrada;
- `OK`: quantidades compatíveis no período.

## MCP local para testes

Endpoint:

- `POST http://localhost:8080/mcp`

Header obrigatório:

```text
Authorization: Bearer local-purchases-token
```

Ferramentas disponíveis:

- `sales_get_schema`
- `sales_list`
- `sales_aggregate`
- `sales_get_imports`
- `sales_audit_stock`
- além das ferramentas já existentes de compras

Exemplo de configuração:

```json
{
  "mcpServers": {
    "checklist-boteco-analytics": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer local-purchases-token"
      }
    }
  }
}
```

## DynamoDB e AWS

O template SAM cria automaticamente:

- `PurchasesTable`
- `SalesTable`

Ambas usam `PAY_PER_REQUEST`, sem VPC, sem subnets e sem security groups.
