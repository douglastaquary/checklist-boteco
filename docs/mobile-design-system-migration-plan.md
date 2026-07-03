# Plano de migração do design system mobile — Android e iOS

## Objetivo

Uniformizar Android e iOS por meio de um contrato visual único, implementado em Compose no Android e SwiftUI no iOS, e adotar uma linguagem visual inspirada na referência anexada: interface clara, tipografia forte, cabeçalho contextual do usuário, filtros em pills, listas leves agrupadas, ações flutuantes e navegação inferior compacta.

O objetivo não é copiar a tela literalmente. O design deve preservar os fluxos e permissões do Checklist Boteco, funcionar com textos em português, suportar os módulos atuais e respeitar os padrões de acessibilidade de cada plataforma.

## Diagnóstico atual

- O Android usa Compose em `composeApp/src/commonMain`, mas o target configurado atualmente é Android.
- O aplicativo iOS em produção é nativo SwiftUI em `iosApp/`, apoiado pelos Swift Packages em `Packages/`.
- Existem fontes residuais em `composeApp/src/iosMain`, porém elas não representam uma UI Compose iOS ativa.
- O tema atual define apenas esquemas básicos de cores. Não há tokens próprios para espaçamento, formas, elevação, ícones, motion ou dimensões.
- As telas usam diretamente componentes Material 3 e possuem muitos valores locais de cor e dimensão; foram encontrados aproximadamente 124 usos de cores/dimensões hard-coded.
- Checklist, ponto, contagem, dashboard, atividades e permissões usam estruturas de cabeçalho e cards diferentes.
- A navegação inferior pode exibir até seis módulos por perfil. A referência comporta três destinos; uma simples troca visual deixaria a navegação ilegível.
- A primeira etapa deve manter as duas implementações de UI, centralizando tokens, componentes equivalentes e critérios de aceite. A eventual adoção de Compose no iOS é uma migração arquitetural independente.

## Direção visual

### Princípios

1. Conteúdo antes de decoração: fundo claro, pouca elevação e hierarquia por tipografia/espaçamento.
2. Contexto do usuário sempre visível nas telas operacionais personalizadas.
3. Ações primárias previsíveis: FAB para inclusão e ação fixa/inferior para confirmação de lote.
4. Estados claros: pendente, concluído, atrasado, offline, sincronizando e bloqueado devem ter texto/ícone, não apenas cor.
5. Mesma identidade nas duas plataformas, preservando safe areas, gesto de voltar, teclado e controles de sistema.

### Tokens propostos

Os valores devem ser validados em um protótipo antes da migração em massa.

| Grupo | Tokens iniciais |
|---|---|
| Cores neutras | `background #FAFAF8`, `surface #FFFFFF`, `ink #171717`, `muted #6F6F73`, `outline #E2E2E2`, `subtle #F1F1F3` |
| Marca/ação | manter uma única cor de destaque do Checklist Boteco; usar preto/ink para seleção principal como na referência |
| Estados | sucesso, atenção, erro e informação com versões de foreground/background acessíveis |
| Espaçamento | escala 4, 8, 12, 16, 20, 24, 32 e 40 dp |
| Raios | 10 dp para campos, 16 dp para cards, 24–32 dp para pills e navegação |
| Tipografia | display 28–32 sp, título 20–24 sp, item 16–18 sp, metadado 12–14 sp |
| Toques | área mínima de 48 x 48 dp; checkbox visual pode ser menor, mantendo hit target |

Criar:

```text
presentation/designsystem/
├── tokens/
│   ├── BecoColors.kt
│   ├── BecoDimensions.kt
│   ├── BecoShapes.kt
│   ├── BecoTypography.kt
│   └── BecoMotion.kt
├── components/
│   ├── BecoUserHeader.kt
│   ├── BecoSegmentedFilter.kt
│   ├── BecoTaskSection.kt
│   ├── BecoTaskRow.kt
│   ├── BecoBottomNavigation.kt
│   ├── BecoFloatingAction.kt
│   ├── BecoStatusBadge.kt
│   ├── BecoEmptyState.kt
│   └── BecoFeedback.kt
└── BecoTheme.kt
```

## Componentes derivados da referência

### 1. Cabeçalho contextual do usuário

`BecoUserHeader` deve receber um modelo de UI, sem consultar repositório diretamente:

```kotlin
data class UserHeaderUiModel(
    val displayName: String,
    val roleLabel: String,
    val dateLabel: String,
    val avatar: AvatarUiModel,
    val syncState: SyncUiState,
    val availableActions: Set<UserHeaderAction>
)
```

Conteúdo recomendado:

- avatar ou iniciais;
- saudação curta e nome;
- cargo/setor e data atual;
- estado de sincronização/offline;
- busca apenas onde houver busca real;
- menu com perfil, sincronizar e sair.

Aplicar o header nas telas cujo conteúdo varia por usuário: Checklist, Ponto e Contagem. Dashboard administrativo, gestão de atividades e permissões devem usar um `BecoPageHeader` mais direto, sem saudação ornamental.

### 2. Filtros em pills

Usar `BecoSegmentedFilter` para alternar conjuntos pequenos e mutuamente exclusivos:

- Checklist: Hoje / Pendentes / Concluídas;
- áreas acessíveis: Atendimento / Cozinha / Estoque / Limpeza;
- Contagem: Rascunho / Enviadas / Auditoria;
- Ponto: Hoje / Semana.

Não usar pills para mais de quatro opções simultâneas. Nesses casos, usar menu, sheet ou chips roláveis.

### 3. Lista agrupada de tarefas

Substituir cards elevados do checklist por `BecoTaskSection` e `BecoTaskRow`:

- agrupar por período do dia, área ou status, conforme o domínio;
- checkbox à esquerda;
- título e metadados no centro;
- horário/status à direita;
- separadores leves;
- estado concluído com contraste reduzido e sem permitir nova mutação indevida;
- manter captura de foto e regras de atraso existentes.

Os períodos “Manhã”, “Tarde” e “Noite” só devem ser usados se houver horário real nas atividades. Caso contrário, agrupar por área ou prioridade para não inventar informação.

### 4. Navegação inferior por perfil

`BecoBottomNavigation` deve ter no máximo quatro destinos visíveis.

Regra proposta:

- funcionário: Checklist, Ponto, Contagem;
- gestor operacional: Checklist, Contagem, Dashboard e Mais;
- administrador: Checklist, Dashboard, Contagem e Mais;
- `Mais` abre um modal bottom sheet com Atividades, Equipe/Permissões, configurações e sair.

`AppDestination.availableFor(user)` continua sendo a fonte de autorização. A navegação apenas distribui destinos autorizados entre barra principal e menu “Mais”; ela nunca concede acesso.

### 5. Ações e feedback

- FAB circular para adicionar atividade/produto/contagem, somente quando autorizado.
- Botão de envio de lote deve permanecer explícito e separado do FAB.
- Loading, erro, offline e sincronização devem usar `BecoFeedback` compartilhado.
- Diálogos destrutivos e confirmação de envio permanecem obrigatórios.

## Arquitetura de implementação

### Estado e modelos de apresentação

- Componentes do design system recebem dados imutáveis e callbacks.
- ViewModels continuam responsáveis por regras, permissões e carregamento.
- Formatação de saudação, cargo, datas e status deve sair dos composables de tela e ir para mappers/testes de apresentação.
- Nenhuma tela deve derivar permissão apenas da aparência; usar os métodos atuais de `User` e `AppDestination`.

### Implementação Android/iOS

Nesta etapa, manter um contrato de design único com duas implementações:

- Compose: `composeApp/src/commonMain/.../presentation/designsystem/`;
- SwiftUI: `Packages/DesignSystem/Sources/DesignSystem/`.

Tokens, nomes dos componentes, estados e critérios de comportamento devem permanecer equivalentes. Diferenças específicas continuam permitidas para:

- safe area e insets do sistema;
- formatação local de data/hora;
- escolha de imagem/avatar;
- feedback háptico;
- comportamento do botão voltar no Android;
- integração com share sheet ou menu nativo, se adotados.

Validar no iOS a Dynamic Island/notch e o indicador de gesto; no Android, edge-to-edge, status bar e navigation bar.

### Migração opcional para Compose Multiplatform no iOS

Compartilhar a implementação da UI, e não apenas o design, exige um projeto separado:

- configurar targets `iosArm64` e `iosSimulatorArm64` no módulo Compose;
- criar o entry point Compose para o aplicativo iOS;
- adaptar navegação, câmera, persistência GRDB/SQLDelight e integrações nativas;
- migrar telas incrementalmente sem interromper o app SwiftUI atual;
- validar tamanho do binário, tempo de inicialização e comportamento nativo antes da substituição.

Essa conversão não é pré-requisito para uniformizar o produto visualmente.

## Fases de execução

### Fase 0 — Baseline e inventário

- Capturar screenshots atuais de todas as telas em Android e iOS.
- Listar estados por tela: carregando, vazio, com dados, erro, offline, sem permissão e teclado aberto.
- Definir dispositivos de referência: Android compacto e grande; iPhone SE/compacto e iPhone com Dynamic Island.
- Registrar métricas básicas de acessibilidade e número de componentes hard-coded.

**Saída:** catálogo visual atual e matriz de estados.

### Fase 1 — Fundação do design system

- Criar tokens de cor, tipografia, forma, spacing e motion.
- Refatorar `ChecklistBotecoTheme` para `BecoTheme`, mantendo alias temporário para não quebrar todas as telas.
- Adicionar preview/catalog screen dos componentes compartilhados.
- Remover cores diretas dos primeiros componentes migrados.
- Definir modo escuro. Se não for entregue nesta etapa, bloquear o app em tema claro de forma explícita até existir contraste validado no dark mode.

**Critério de aceite:** tokens centralizados, componentes renderizando em Android/iOS e contraste WCAG AA para textos essenciais.

### Fase 2 — Shell, header e navegação

- Criar `BecoAppShell`, `BecoUserHeader`, `BecoPageHeader` e `BecoBottomNavigation`.
- Atualizar `MainScreen` para distribuir destinos autorizados entre barra e “Mais”.
- Tratar safe areas sem padding duplicado entre shell e telas.
- Exibir nome, setor/cargo, data e status de sync no header operacional.
- Preservar restauração de rota e comportamento de deep link/back stack.

**Critério de aceite:** nenhum perfil vê módulo sem permissão; nenhum perfil recebe mais de quatro destinos visíveis; logout e sincronização continuam acessíveis.

### Fase 3 — Piloto no Checklist

- Migrar `ChecklistScreen` para a lista leve da referência.
- Criar filtros por status e área.
- Definir agrupamento por área inicialmente; usar período do dia somente após o domínio possuir horário planejado.
- Migrar conclusão, foto, atraso, vazio, offline e loading.
- Executar teste de usabilidade com funcionário em aparelho real.

**Critério de aceite:** concluir uma atividade exige no máximo o mesmo número de toques do fluxo atual e todas as regras existentes permanecem cobertas.

### Fase 4 — Ponto e Contagem

- Aplicar header contextual e pills no Ponto.
- Migrar Contagem para Rascunho / Enviadas / Auditoria.
- Reutilizar linhas leves para itens de estoque, preservando edição somente antes do envio.
- Manter confirmação do lote e dados de usuário/data/hora.
- Usar badges para saldo, divergência, alerta e offline.

**Critério de aceite:** rascunho local, envio, falha de rede e auditoria continuam funcionais nas duas plataformas.

### Fase 5 — Administração e formulários

- Migrar Dashboard, Atividades, Permissões, Login e Cadastro.
- Substituir cores locais do dashboard por tokens sem perder semântica de alerta.
- Padronizar campos, sheets, dialogs, botões destrutivos e estados vazios.
- Usar `BecoPageHeader` nas telas administrativas.

**Critério de aceite:** nenhuma tela de produção usa cores de layout hard-coded fora do design system.

### Fase 6 — Consolidação e remoção do legado

- Remover componentes e aliases temporários.
- Executar busca estática por cores, spacing e shapes fora do design system.
- Atualizar documentação e screenshots.
- Comparar APK/IPA, tempo de primeira composição e regressões de recomposição.
- Liberar por feature flag ou rollout interno antes de produção.

## Estratégia de testes

### Unitários

- mapper de `UserHeaderUiModel` por perfil;
- distribuição de destinos entre barra e menu “Mais”;
- labels de saudação/data por locale;
- agrupamento e ordenação de atividades;
- estados de pills e contadores;
- regras de visibilidade por permissão.

### UI e screenshots

- screenshot tests dos componentes e telas principais em largura compacta e expandida;
- Android e iOS em light mode; dark mode somente quando oficialmente suportado;
- fontes em 100%, 130% e 200%;
- nomes longos, textos em português e listas vazias/grandes;
- teclado aberto, orientação e safe areas.

### Integração/regressão

- login e carregamento das permissões;
- concluir checklist com/sem foto;
- ponto com permissões de localização;
- rascunho e envio de contagem offline/online;
- atualização de permissões e reconstrução imediata dos destinos;
- back stack, restauração após processo morto e logout.

### Acessibilidade

- TalkBack e VoiceOver;
- ordem de foco coerente;
- rótulo de ícones e checkboxes;
- hit target mínimo de 48 dp;
- contraste AA;
- não depender somente de cor para status.

## Sequência recomendada de arquivos

1. `presentation/theme/Theme.kt` e novos tokens em `presentation/designsystem/tokens`.
2. Novos componentes em `presentation/designsystem/components`.
3. `presentation/navigation/AppDestination.kt` para barra/“Mais”.
4. `presentation/screen/MainScreen.kt` para o shell compartilhado.
5. `ChecklistScreen.kt` como piloto.
6. `WorkClockScreen.kt` e telas de inventário.
7. Dashboard, Atividades, Permissões, Login e Cadastro.
8. Testes em `commonTest`, testes de screenshot e validações específicas Android/iOS.

## Definition of Done

- Android e iOS usam tokens equivalentes e componentes com o mesmo contrato visual.
- Header contextual mostra dados reais do usuário e aparece apenas onde agrega contexto.
- Navegação respeita permissões e limita a barra a quatro itens.
- Checklist e Contagem usam padrões de lista consistentes.
- Nenhuma funcionalidade offline, sincronização ou permissão regrediu.
- Todas as telas têm estados loading, vazio, erro e offline definidos.
- TalkBack/VoiceOver e fontes ampliadas foram validados.
- Não existem cores de produto hard-coded nas telas migradas.
- Testes unitários, integração e screenshots passam nas duas plataformas.

## Estimativa por etapa

| Etapa | Esforço estimado |
|---|---:|
| Baseline e especificação | 1–2 dias |
| Tokens, tema e catálogo | 2–3 dias |
| Shell, header e navegação | 3–4 dias |
| Checklist piloto | 3–4 dias |
| Ponto e Contagem | 4–6 dias |
| Administração, login e formulários | 5–7 dias |
| QA, acessibilidade e consolidação | 4–6 dias |

Estimativa total: 22–32 dias úteis para uma pessoa, reduzível com execução paralela de componentes, telas e QA após a fundação do design system.

## Estado da implementação

Concluído neste incremento:

- tokens, tema e componentes-base equivalentes em Compose e SwiftUI;
- cabeçalho contextual do usuário no piloto de Checklist;
- filtros por status e área;
- lista leve de atividades com estados vazios;
- navegação Compose limitada a quatro destinos, com excedentes no menu `Mais`;
- testes unitários de filtros, header e distribuição da navegação;
- compilação Android e build do aplicativo iOS para simulador.
- migração visual de Ponto e Contagem em Compose e SwiftUI;
- header contextual aplicado às três telas operacionais: Checklist, Ponto e Contagem;
- Contagem com seletor em pills, lista leve, estado vazio e ações de lote preservadas;
- Ponto com localização, resumo e CTA de registro reorganizados na nova hierarquia visual.
- `BecoPageHeader` criado para telas administrativas, sem saudação ornamental;
- Dashboard, Atividades, Equipe/Permissões e Login migrados para a linguagem visual comum;
- listas administrativas simplificadas e estados vazios padronizados;
- login com identidade do Beco da Praia e hierarquia equivalente nas duas plataformas.
- semântica de acessibilidade aplicada a headers, filtros, tarefas e estados vazios;
- builds Android e iOS validados após a migração e atualização do submódulo.

Próximo incremento:

- executar revisão manual com TalkBack/VoiceOver e registrar screenshots em aparelhos de referência.

## Decisões adotadas no piloto

1. Cor de destaque: roxo existente (`#5E35B1`).
2. Tema: claro nesta etapa; dark mode depende de validação de contraste posterior.
3. Avatar: iniciais do usuário.
4. Checklist: agrupamento e filtro por área, sem inventar horários inexistentes.
5. Navegação: até quatro destinos autorizados; quando houver excedente, três destinos e `Mais`.
