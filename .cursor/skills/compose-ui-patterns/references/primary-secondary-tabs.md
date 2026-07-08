# PrimaryTabRow e SecondaryTabRow

Referência Google: [Tabs (Compose)](https://developer.android.com/develop/ui/compose/components/tabs?hl=pt-br)

## Quando usar cada uma

| Componente | Posição | Uso |
|------------|---------|-----|
| `PrimaryTabRow` | Abaixo da TopAppBar | Destinos irmãos no mesmo nível (ex.: Músicas / Álbum / Playlist) |
| `SecondaryTabRow` | Dentro da área de conteúdo | Sub-agrupamento hierárquico (ex.: áreas do Checklist) |
| `NavigationBar` | Parte inferior | Destinos principais do app — **não** substituir por PrimaryTabRow |

## SecondaryTabRow — Checklist (áreas)

```kotlin
val selectedIndex = accessibleAreas.indexOf(state.selectedArea).coerceAtLeast(0)

SecondaryTabRow(selectedTabIndex = selectedIndex) {
    accessibleAreas.forEachIndexed { index, area ->
        Tab(
            selected = selectedIndex == index,
            onClick = { viewModel.selectArea(area) },
            text = { Text(area.displayName) }
        )
    }
}
```

Arquivo: [`ChecklistScreen.kt`](../../../composeApp/src/commonMain/kotlin/com/checklistboteco/presentation/screen/ChecklistScreen.kt)

## PrimaryTabRow — template para features futuras

```kotlin
var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
    sections.forEachIndexed { index, section ->
        Tab(
            selected = selectedTabIndex == index,
            onClick = { selectedTabIndex = index },
            text = {
                Text(
                    text = section.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}
```

## Parâmetros principais do `Tab`

- `selected` — destaque visual.
- `onClick` — troca de conteúdo ou navegação.
- `text` / `icon` — pelo menos um; ícones comuns em guias primárias.
- `enabled` — desabilitar guia quando aplicável.

## O que evitar

- `FilterChip` em linha para troca de contexto principal da tela — preferir `SecondaryTabRow`.
- Mais de um nível de guias na mesma tela sem necessidade clara.
- `PrimaryTabRow` com 6+ destinos principais do app — usar `NavigationBar`.
