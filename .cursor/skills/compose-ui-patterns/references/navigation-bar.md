# NavigationBar + NavHost

## Quando usar

Destinos principais do app (Checklist, Ponto, Dashboard, etc.) em telefone. Máximo recomendado: ~5 itens visíveis confortavelmente; acima disso considerar `NavigationRail` (tablet) ou agrupamento.

## Padrão do projeto

Arquivos:

- [`AppDestination.kt`](../../../composeApp/src/commonMain/kotlin/com/checklistboteco/presentation/navigation/AppDestination.kt)
- [`MainScreen.kt`](../../../composeApp/src/commonMain/kotlin/com/checklistboteco/presentation/screen/MainScreen.kt)
- [`MainNavGraph.kt`](../../../composeApp/src/commonMain/kotlin/com/checklistboteco/presentation/navigation/MainNavGraph.kt)

## Implementação

```kotlin
val navController = rememberNavController()
val navBackStackEntry by navController.currentBackStackEntryAsState()
val currentRoute = navBackStackEntry?.destination?.route

Scaffold(
    bottomBar = {
        NavigationBar {
            destinations.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = { navController.navigateToTab(destination) },
                    icon = { Icon(destination.icon, destination.contentDescription) },
                    label = { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
) { padding ->
    MainNavGraph(..., navController = navController, modifier = Modifier.padding(padding))
}
```

## navigateToTab

```kotlin
fun NavHostController.navigateToTab(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
```

## Lazy load de abas

Marque rotas visitadas em `loadedRoutes` e só compõe ViewModels/telas após a primeira seleção — reduz custo inicial (paridade com iOS `loadedTabs`).

## O que evitar

- `selectedTabIndex` + `when` sem NavHost — perde saveState e back stack.
- Botão voltar na raiz de aba apontando para outra aba.
- Ícones sem `contentDescription`.
