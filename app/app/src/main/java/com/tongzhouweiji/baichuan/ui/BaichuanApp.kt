package com.tongzhouweiji.baichuan.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.tongzhouweiji.baichuan.R
import com.tongzhouweiji.baichuan.ui.screen.agent.AgentEditScreen
import com.tongzhouweiji.baichuan.ui.screen.agentrun.AgentRunsScreen
import com.tongzhouweiji.baichuan.ui.screen.agent.AgentListScreen
import com.tongzhouweiji.baichuan.ui.screen.chat.ChatScreen
import com.tongzhouweiji.baichuan.ui.screen.discover.DiscoverScreen
import com.tongzhouweiji.baichuan.ui.screen.manage.ManageScreen
import com.tongzhouweiji.baichuan.ui.screen.messages.MessagesScreen
import com.tongzhouweiji.baichuan.ui.screen.profile.ProfileScreen
import com.tongzhouweiji.baichuan.ui.screen.error.ErrorEventsScreen
import com.tongzhouweiji.baichuan.ui.screen.memory.MemoryScreen
import com.tongzhouweiji.baichuan.ui.screen.aux.AuxModelScreen
import com.tongzhouweiji.baichuan.ui.screen.backup.BackupScreen
import com.tongzhouweiji.baichuan.ui.screen.mcp.McpScreen
import com.tongzhouweiji.baichuan.ui.screen.plugin.PluginScreen
import com.tongzhouweiji.baichuan.ui.screen.scheduled.ScheduledMessageScreen
import com.tongzhouweiji.baichuan.ui.screen.search.SearchConfigScreen
import com.tongzhouweiji.baichuan.ui.screen.settings.AppearanceScreen
import com.tongzhouweiji.baichuan.ui.screen.settings.ModelConfigScreen
import com.tongzhouweiji.baichuan.ui.screen.settings.ProviderEditScreen

private const val ROUTE_MESSAGES = "messages"
private const val ROUTE_MANAGE = "manage"
private const val ROUTE_DISCOVER = "discover"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_AGENT_LIST = "agent_list"
private const val ROUTE_AGENT_EDIT = "agent_edit"
private const val ROUTE_MODEL_CONFIG = "model_config"
private const val ROUTE_PROVIDER_EDIT = "provider_edit"
private const val ROUTE_APPEARANCE = "appearance"
private const val ROUTE_ERROR_EVENTS = "error_events"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_SCHEDULED = "scheduled"
private const val ROUTE_SEARCH_CONFIG = "search_config"
private const val ROUTE_AUX_MODELS = "aux_models"
private const val ROUTE_BACKUP = "backup"
private const val ROUTE_PLUGINS = "plugins"
private const val ROUTE_MCP = "mcp"
private const val ROUTE_AGENT_RUNS = "agent_runs"

private val mainRoutes = setOf(ROUTE_MESSAGES, ROUTE_MANAGE, ROUTE_DISCOVER, ROUTE_PROFILE)

private data class Destination(
    val route: String,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

private val destinations = listOf(
    Destination(ROUTE_MESSAGES, R.drawable.ic_tab_chat, R.string.tab_messages),
    Destination(ROUTE_MANAGE, R.drawable.ic_tab_manage, R.string.tab_manage),
    Destination(ROUTE_DISCOVER, R.drawable.ic_tab_discover, R.string.tab_discover),
    Destination(ROUTE_PROFILE, R.drawable.ic_tab_profile, R.string.tab_profile),
)

@Composable
fun BaichuanApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_MESSAGES,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_MESSAGES) {
                MessagesScreen(
                    onOpenChat = { agentId -> navController.navigate("$ROUTE_CHAT/$agentId") },
                    onCreateAgent = { navController.navigate("$ROUTE_AGENT_EDIT/0") },
                )
            }
            composable(ROUTE_MANAGE) {
                ManageScreen(
                    onOpenAgents = { navController.navigate(ROUTE_AGENT_LIST) },
                    onOpenModelConfig = { navController.navigate(ROUTE_MODEL_CONFIG) },
                    onOpenPlugins = { navController.navigate(ROUTE_PLUGINS) },
                    onOpenMcp = { navController.navigate(ROUTE_MCP) },
                )
            }
            composable(ROUTE_DISCOVER) { DiscoverScreen() }
            composable(ROUTE_PROFILE) {
                ProfileScreen(
                    onOpenAppearance = { navController.navigate(ROUTE_APPEARANCE) },
                    onOpenErrors = { navController.navigate(ROUTE_ERROR_EVENTS) },
                    onOpenSearch = { navController.navigate(ROUTE_SEARCH_CONFIG) },
                    onOpenAux = { navController.navigate(ROUTE_AUX_MODELS) },
                    onOpenBackup = { navController.navigate(ROUTE_BACKUP) },
                    onOpenAgentRuns = { navController.navigate(ROUTE_AGENT_RUNS) },
                )
            }
            composable(ROUTE_APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_ERROR_EVENTS) {
                ErrorEventsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_SEARCH_CONFIG) {
                SearchConfigScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_AUX_MODELS) {
                AuxModelScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_PLUGINS) {
                PluginScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_MCP) {
                McpScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "scheduled/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.LongType }),
            ) { entry ->
                val agentId = entry.arguments?.getLong("agentId") ?: 0L
                ScheduledMessageScreen(agentId = agentId, onBack = { navController.popBackStack() })
            }
            composable(
                route = "memory/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.LongType }),
            ) { entry ->
                val agentId = entry.arguments?.getLong("agentId") ?: 0L
                MemoryScreen(agentId = agentId, onBack = { navController.popBackStack() })
            }
            composable(
                route = "$ROUTE_CHAT/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.LongType }),
            ) { entry ->
                val agentId = entry.arguments?.getLong("agentId") ?: 0L
                ChatScreen(
                    agentId = agentId,
                    onBack = { navController.popBackStack() },
                    onOpenAgentSettings = { navController.navigate("$ROUTE_AGENT_EDIT/$agentId") },
                )
            }
            composable(ROUTE_AGENT_LIST) {
                AgentListScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { agentId -> navController.navigate("$ROUTE_AGENT_EDIT/$agentId") },
                    onCreate = { navController.navigate("$ROUTE_AGENT_EDIT/0") },
                )
            }
            composable(ROUTE_AGENT_RUNS) {
                AgentRunsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$ROUTE_AGENT_EDIT/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.LongType }),
            ) { entry ->
                val agentId = entry.arguments?.getLong("agentId") ?: 0L
                AgentEditScreen(
                    agentId = agentId,
                    onBack = { navController.popBackStack() },
                    onOpenMemory = { id -> navController.navigate("memory/$id") },
                    onOpenScheduled = { id -> navController.navigate("scheduled/$id") },
                )
            }
            composable(ROUTE_MODEL_CONFIG) {
                ModelConfigScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_PROVIDER_EDIT/$id") },
                    onCreate = { navController.navigate("$ROUTE_PROVIDER_EDIT/0") },
                )
            }
            composable(
                route = "$ROUTE_PROVIDER_EDIT/{providerId}",
                arguments = listOf(navArgument("providerId") { type = NavType.LongType }),
            ) { entry ->
                val providerId = entry.arguments?.getLong("providerId") ?: 0L
                ProviderEditScreen(providerId = providerId, onBack = { navController.popBackStack() })
            }
        }
    }
}
