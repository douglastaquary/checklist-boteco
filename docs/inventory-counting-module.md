# Módulo de abastecimento e contagem

O módulo registra a contagem de mercadorias feita antes da abertura do Beco da Praia e cruza os itens com as vendas importadas no módulo de vendas.

## Fluxo

1. O funcionário monta uma lista local com produto, quantidade, categoria, volume/unidade, valor de venda, custo opcional e conservação.
2. Antes do envio, o sistema pede uma confirmação explícita.
3. Todo o lote é enviado de uma vez. O backend grava `countedAt`, `submittedAt`, ID/nome do usuário autenticado e local.
4. Depois do envio não existe edição. Somente um administrador pode apagar a sessão completa.
5. A auditoria diária calcula `saldo teórico = contagem de abertura - vendas do dia`.

No Android, o rascunho permanece no banco SQLDelight até o backend confirmar o envio. Falhas de rede não apagam a lista.

## Permissões

- `canCreateInventoryCounts`: cria e envia contagens.
- `canViewInventoryInsights`: consulta histórico, dashboard e auditoria.
- Administradores recebem as duas permissões e são os únicos que podem apagar contagens enviadas.

## API REST

- `POST /api/inventory/counts`: envia uma sessão completa.
- `GET /api/inventory/counts?from=AAAA-MM-DD&to=AAAA-MM-DD`: lista sessões.
- `DELETE /api/inventory/counts/{id}`: exclusão administrativa.
- `POST /api/inventory/audit/daily`: cruza uma data com as vendas.

## MCP

- `inventory_count_sessions`: lista sessões imutáveis em um período.
- `inventory_daily_audit`: retorna contagem, vendido e saldo teórico por produto no dia.

Exemplo: “No Beco, qual foi o saldo teórico de Heineken após as vendas de 20/06/2026?” O agente chama `inventory_daily_audit` com `date=2026-06-20` e `text=heineken`.
