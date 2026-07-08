package com.checklistboteco.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.sync.SyncCoordinator
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.designsystem.UserHeaderUiModel
import com.checklistboteco.presentation.designsystem.components.BecoBottomNavigation
import com.checklistboteco.presentation.designsystem.components.BecoUserHeader
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.presentation.navigation.AppDestination
import com.checklistboteco.presentation.navigation.MainNavGraph
import com.checklistboteco.presentation.navigation.MainTabContext
import com.checklistboteco.presentation.navigation.navigateToTab
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    user: User,
    repository: ChecklistRepository,
    syncCoordinator: SyncCoordinator?,
    backendApiClient: BackendApiClient?,
    authToken: String?,
    remoteUserId: String?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationLayout = remember(user) { AppDestination.layoutFor(user) }
    val destinations = remember(navigationLayout) { navigationLayout.primary + navigationLayout.overflow }
    val startRoute = destinations.firstOrNull()?.route ?: AppDestination.Checklist.route
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var loadedRoutes by remember { mutableStateOf(setOf(startRoute)) }
    var showMore by remember { mutableStateOf(false) }
    val dateLabel = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }
    val userHeader = remember(user, dateLabel) { UserHeaderUiModel.from(user, dateLabel) }
    val showsUserHeader = currentRoute in setOf(
        AppDestination.Checklist.route,
        AppDestination.WorkClock.route,
        AppDestination.Inventory.route
    )

    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            loadedRoutes = loadedRoutes + route
        }
    }

    LaunchedEffect(destinations, currentRoute) {
        if (currentRoute != null && destinations.none { it.route == currentRoute }) {
            navController.navigateToTab(destinations.first())
            loadedRoutes = loadedRoutes + destinations.first().route
        }
    }

    val tabContext = remember(user, repository, syncCoordinator, backendApiClient, authToken, remoteUserId, onLogout) {
        MainTabContext(
            user = user,
            repository = repository,
            syncCoordinator = syncCoordinator,
            backendApiClient = backendApiClient,
            authToken = authToken,
            remoteUserId = remoteUserId,
            onLogout = onLogout
        )
    }

    Scaffold(
        topBar = {
            if (showsUserHeader) {
                BecoUserHeader(
                    model = userHeader,
                    onLogout = onLogout,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.statusBars.only(WindowInsetsSides.Top)
                    )
                )
            }
        },
        bottomBar = {
            if (destinations.size > 1) {
                BecoBottomNavigation(
                    destinations = navigationLayout.primary,
                    selectedRoute = currentRoute,
                    hasOverflow = navigationLayout.overflow.isNotEmpty(),
                    onDestinationSelected = { destination -> loadedRoutes = loadedRoutes + destination.route; navController.navigateToTab(destination) },
                    onMoreSelected = { showMore = true }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        modifier = modifier
    ) { padding ->
        val contentModifier = Modifier
            .padding(padding)
            .then(
                if (showsUserHeader) {
                    Modifier
                } else {
                    Modifier
                        .windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
                        .padding(top = BecoSpacing.xs)
                }
            )
        MainNavGraph(
            context = tabContext,
            destinations = destinations,
            loadedRoutes = loadedRoutes,
            navController = navController,
            modifier = contentModifier
        )
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Text("Mais módulos", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            navigationLayout.overflow.forEach { destination ->
                ListItem(
                    headlineContent = { Text(destination.title) },
                    leadingContent = { Icon(destination.icon, destination.contentDescription) },
                    modifier = Modifier.clickable {
                        showMore = false
                        loadedRoutes = loadedRoutes + destination.route
                        navController.navigateToTab(destination)
                    }
                )
            }
        }
    }
}
