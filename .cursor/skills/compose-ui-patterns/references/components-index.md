# Índice de componentes Compose — Checklist Boteco

Consulte este índice antes de implementar UI Android.

| Tópico | Referência | Arquivo(s) no projeto |
|--------|------------|----------------------|
| Navegação principal (bottom) | [navigation-bar.md](navigation-bar.md) | `MainScreen.kt`, `AppDestination.kt`, `MainNavGraph.kt` |
| Guias primárias/secundárias | [primary-secondary-tabs.md](primary-secondary-tabs.md) | `ChecklistScreen.kt` |
| Scaffold / TopAppBar | [scaffold-topappbar.md](scaffold-topappbar.md) | `*Screen.kt` em `presentation/screen/` |
| App bar moderna (M3) | [app-bars.md](app-bars.md) | `ModernTopAppBar.kt`, `InventoryCountScreen.kt` |
| Doc de arquitetura | [android-compose-navigation.md](../../../docs/android-compose-navigation.md) | `docs/` |
| Tema Material 3 | — | `presentation/theme/Theme.kt` |
| Estado / ViewModels | — | `presentation/viewmodel/` |

## Checklist antes de abrir PR

- [ ] Destino principal novo → `AppDestination` + `MainNavGraph` + permissão em `availableFor`.
- [ ] Sub-seção na mesma tela → `SecondaryTabRow`, não `FilterChip` solto.
- [ ] Raiz de aba sem botão voltar para outra aba.
- [ ] `navigateToTab` com saveState/restoreState para troca de abas.
- [ ] Skill e doc atualizados se o padrão mudou.

## Referências externas

- [Material 3 — Tabs](https://developer.android.com/develop/ui/compose/components/tabs?hl=pt-br)
- [Material 3 — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)
- [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation)
