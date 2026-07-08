# App bars modernas (Material 3)

## Quando usar

- Telas-raiz de aba que precisam de título centralizado e ações secundárias no overflow.
- Conteúdo rolável com colapso da barra ao rolar (`enterAlwaysScrollBehavior`).

## Componente do projeto

`ModernTopAppBar` em `presentation/components/ModernTopAppBar.kt`:

- `CenterAlignedTopAppBar` com título e subtítulo opcional.
- `rememberModernTopAppBarScrollBehavior()` — conecte ao `Scaffold` via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
- Ações primárias no slot `actions`.
- Ações secundárias via `overflowActions` (menu `MoreVert`).

## Exemplo — Contagem com overflow

```kotlin
val scrollBehavior = rememberModernTopAppBarScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        ModernTopAppBar(
            title = "Contagem",
            subtitle = "Abertura",
            scrollBehavior = scrollBehavior,
            overflowActions = listOf(
                TopAppBarOverflowAction("Gerar auditoria") { viewModel.openAuditSheet() }
            )
        )
    }
) { padding -> /* conteúdo */ }
```

## Regras

1. Raiz de aba — sem `navigationIcon` de voltar (troca de aba é pela `NavigationBar`).
2. Uma barra por `Scaffold` — não empilhar `TopAppBar` em sheet filho se o pai já tiver barra.
3. Rollout incremental — novas telas adotam `ModernTopAppBar`; telas legadas podem manter `TopAppBar` até refatoração.

## Referência

- [App bars (Compose)](https://developer.android.com/develop/ui/compose/components/app-bars?hl=pt-br)
