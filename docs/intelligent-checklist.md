# Checklist inteligente

O checklist calcula prazos diários a partir do calendário operacional do Beco da Praia e mantém o mesmo feedback no Android, iOS e painel web.

## Regras

- Verde: atividade pendente com mais de 30 minutos até o prazo.
- Amarelo: atividade pendente nos 30 minutos finais.
- Vermelho: prazo ultrapassado.
- Concluída: registra funcionário, horário, data de serviço e se houve atraso.
- O início recomendado é `prazo - duração estimada` e dispara a notificação local.
- Uma conclusão encerra a ocorrência para todos os responsáveis.
- Sem responsáveis explícitos, a atividade permanece disponível para o setor.

## Calendário inicial

| Dia | Entrada | Almoço | Abertura | Encerramento |
| --- | --- | --- | --- | --- |
| Terça | 15h | 17h | 18h | 00h |
| Sexta | 15h | 17h | 18h | 00h |
| Sábado | 10h | 11h | 12h | 00h |
| Domingo | 10h | 11h | 12h | 00h |

Segunda, quarta e quinta começam desativadas. O admin pode alterar o calendário na Visão geral. O fuso canônico é `America/Fortaleza`.

## API

- `GET /api/checklist/today?date=YYYY-MM-DD`: ocorrências visíveis para o usuário.
- `GET /api/checklist/schedule`: calendário sincronizado com os apps.
- `PUT /api/checklist/schedule`: atualização exclusiva do admin.
- `GET /api/admin/checklist/overview?date=YYYY-MM-DD`: acompanhamento administrativo.

As notificações são locais nesta fase. Os apps atualizam e cancelam lembretes após sincronização; não há FCM ou APNs remoto.
