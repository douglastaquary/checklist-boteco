package com.checklistboteco.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.checklistboteco.presentation.screen.ActivitiesManagementScreen
import com.checklistboteco.presentation.screen.ChecklistScreen
import com.checklistboteco.presentation.screen.DashboardScreen
import com.checklistboteco.presentation.screen.InventoryCountScreen
import com.checklistboteco.presentation.screen.PermissionManagementScreen
import com.checklistboteco.presentation.screen.WorkClockScreen
import com.checklistboteco.presentation.viewmodel.ActivitiesManagementViewModel
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import com.checklistboteco.presentation.viewmodel.DashboardViewModel
import com.checklistboteco.presentation.viewmodel.InventoryCountViewModel
import com.checklistboteco.presentation.viewmodel.PermissionManagementViewModel
import com.checklistboteco.presentation.viewmodel.WorkClockViewModel

@Composable
fun MainNavGraph(
    context: MainTabContext,
    destinations: List<AppDestination>,
    loadedRoutes: Set<String>,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val startDestination = destinations.firstOrNull()?.route ?: AppDestination.Checklist.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        if (AppDestination.Checklist in destinations) {
            composable(AppDestination.Checklist.route) {
                if (AppDestination.Checklist.route in loadedRoutes) {
                    val viewModel = remember(context.user) {
                        ChecklistViewModel(
                            context.repository,
                            context.user,
                            context.syncCoordinator,
                            scope
                        )
                    }
                    ChecklistScreen(
                        viewModel = viewModel,
                        user = context.user
                    )
                } else {
                    LazyTabPlaceholder()
                }
            }
        }

        if (AppDestination.WorkClock in destinations) {
            composable(AppDestination.WorkClock.route) {
                if (AppDestination.WorkClock.route in loadedRoutes) {
                    val viewModel = remember(context.user, context.authToken, context.remoteUserId) {
                        WorkClockViewModel(
                            repository = context.repository,
                            user = context.user,
                            scope = scope,
                            backendApiClient = context.backendApiClient,
                            authToken = context.authToken,
                            remoteUserId = context.remoteUserId
                        )
                    }
                    WorkClockScreen(
                        viewModel = viewModel,
                        user = context.user
                    )
                } else {
                    LazyTabPlaceholder()
                }
            }
        }

        if (AppDestination.Inventory in destinations) {
            composable(AppDestination.Inventory.route) {
                if (AppDestination.Inventory.route in loadedRoutes) {
                    val viewModel = remember(context.user, context.authToken) {
                        InventoryCountViewModel(
                            context.repository,
                            context.backendApiClient,
                            context.authToken,
                            scope
                        )
                    }
                    InventoryCountScreen(
                        viewModel = viewModel,
                        canCreate = context.user.canCreateInventoryCounts(),
                        canViewInsights = context.user.canViewInventoryInsights(),
                        canManageAdministrativeStock = context.user.canManageAdministrativeStock(),
                        isAdmin = context.user.canManagePermissions()
                    )
                } else {
                    LazyTabPlaceholder()
                }
            }
        }

        if (AppDestination.Dashboard in destinations) {
            composable(AppDestination.Dashboard.route) {
                if (AppDestination.Dashboard.route in loadedRoutes) {
                    val viewModel = remember {
                        DashboardViewModel(context.repository, context.syncCoordinator, scope)
                    }
                    DashboardScreen(viewModel = viewModel)
                } else {
                    LazyTabPlaceholder()
                }
            }
        }

        if (AppDestination.Activities in destinations) {
            composable(AppDestination.Activities.route) {
                if (AppDestination.Activities.route in loadedRoutes) {
                    val viewModel = remember {
                        ActivitiesManagementViewModel(context.repository, context.syncCoordinator, scope)
                    }
                    ActivitiesManagementScreen(viewModel = viewModel)
                } else {
                    LazyTabPlaceholder()
                }
            }
        }

        if (AppDestination.Permissions in destinations) {
            composable(AppDestination.Permissions.route) {
                if (AppDestination.Permissions.route in loadedRoutes) {
                    val viewModel = remember(context.user, context.authToken) {
                        PermissionManagementViewModel(
                            repository = context.repository,
                            currentUser = context.user,
                            backendApiClient = context.backendApiClient,
                            authToken = context.authToken,
                            scope = scope
                        )
                    }
                    PermissionManagementScreen(viewModel = viewModel)
                } else {
                    LazyTabPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun LazyTabPlaceholder() {
    Box(modifier = Modifier.fillMaxSize())
}
