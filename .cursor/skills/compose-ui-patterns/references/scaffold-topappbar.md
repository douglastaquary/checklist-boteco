# Scaffold e TopAppBar

## Hierarquia

```
MainScreen (Scaffold + NavigationBar)
  └── MainNavGraph / NavHost
        └── Tela-raiz da aba (Scaffold + TopAppBar)
              └── Conteúdo / SecondaryTabRow
              └── [futuro] NavHost aninhado para detalhes
```

## Regras

1. **MainScreen** — apenas `bottomBar` (`NavigationBar`); sem TopAppBar global.
2. **Tela-raiz de aba** — `TopAppBar` com título; ações à direita (logout, exportar).
3. **Raiz de aba** — sem `navigationIcon` de voltar; troca de aba é pela barra inferior.
4. **Tela de detalhe** (dentro da aba) — `navigationIcon` com `ArrowBack` + `navController.popBackStack()` ou callback do ViewModel.

## Exemplo — raiz de aba (Checklist)

```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Checklist - ${user.name}") },
            actions = {
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sair")
                }
            }
        )
    }
) { padding -> /* conteúdo */ }
```

## Exemplo — detalhe hierárquico (Permissões)

```kotlin
TopAppBar(
    title = { Text(user.name) },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
        }
    }
)
```

## Padding

Use `Modifier.padding(padding)` do `Scaffold` pai apenas uma vez por nível — evite double-padding entre `MainScreen` e telas filhas.
