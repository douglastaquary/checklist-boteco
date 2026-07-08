---
name: compose-ui-patterns
description: Padrões Material 3 2026 para UI Jetpack Compose no Checklist Boteco (KMP). Use ao criar ou refatorar telas Android, navegação por abas, NavigationBar, PrimaryTabRow, SecondaryTabRow, Scaffold e TopAppBar em composeApp/.
---

# Compose UI Patterns (Android / KMP)

## Quick start

### Projeto existente

1. Identifique o tipo de navegação: destino principal (`NavigationBar`) vs sub-seção na mesma tela (`SecondaryTabRow` / `PrimaryTabRow`).
2. Leia [`docs/android-compose-navigation.md`](../../docs/android-compose-navigation.md) para o padrão atual do app.
3. Consulte [`references/components-index.md`](references/components-index.md) antes de criar componentes novos.
4. Siga convenções em `composeApp/src/commonMain/` — sem over-engineering, diff mínimo.

### Nova tela em aba existente

1. Adicione rota em `AppDestination` se for destino principal.
2. Registre `composable(route)` em `MainNavGraph.kt`.
3. Não adicione botão voltar na raiz da aba — use `NavigationBar` para trocar destinos.

## Regras Material 3 (2026)

| Cenário | Componente | Não usar |
|---------|------------|----------|
| 3–5 destinos principais do app | `NavigationBar` + Navigation Compose | `PrimaryTabRow` na barra inferior |
| Sub-seções na mesma hierarquia | `SecondaryTabRow` + `Tab` | `FilterChip` em linha para troca de contexto principal |
| Destinos irmãos no topo (raro) | `PrimaryTabRow` + `Tab` | `NavigationBar` com 6+ itens apertados |
| Hierarquia dentro de uma aba | `NavHost` aninhado + seta voltar | Voltar para outra aba via `onBack` |

## Navegação entre abas

Sempre use `navigateToTab()` em [`MainTabNavigation.kt`](../../composeApp/src/commonMain/kotlin/com/checklistboteco/presentation/navigation/MainTabNavigation.kt):

- `saveState = true` / `restoreState = true` — preserva estado ao retornar.
- `launchSingleTop = true` — evita duplicar destinos na pilha.

Detalhes: [`references/navigation-bar.md`](references/navigation-bar.md)

## Guias (tabs)

- **Primárias** (`PrimaryTabRow`): conteúdo relacionado no mesmo nível, abaixo da TopAppBar.
- **Secundárias** (`SecondaryTabRow`): sub-agrupamento dentro de uma área (ex.: áreas do Checklist).

Detalhes: [`references/primary-secondary-tabs.md`](references/primary-secondary-tabs.md)

## Scaffold e TopAppBar

- Uma `Scaffold` por tela-raiz de aba.
- TopAppBar na raiz: título + ações (logout, FAB em tela filha).
- Sem `navigationIcon` de voltar na raiz de aba.
- Telas com conteúdo rolável e overflow: preferir `ModernTopAppBar` + `enterAlwaysScrollBehavior`.

Detalhes: [`references/scaffold-topappbar.md`](references/scaffold-topappbar.md), [`references/app-bars.md`](references/app-bars.md)

## Referências cruzadas

- Skill iOS (paridade de conceitos): [`.cursor/skills/swiftui-ui-patterns/SKILL.md`](../swiftui-ui-patterns/SKILL.md)
- Doc do projeto: [`docs/android-compose-navigation.md`](../../docs/android-compose-navigation.md)
- Google: [Tabs](https://developer.android.com/develop/ui/compose/components/tabs?hl=pt-br), [Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)

## Índice de componentes

Ver [`references/components-index.md`](references/components-index.md).
