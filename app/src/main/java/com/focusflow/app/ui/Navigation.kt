package com.focusflow.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusflow.app.ui.backlog.BacklogScreen
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.goals.GoalDetailScreen
import com.focusflow.app.ui.goals.GoalsScreen
import com.focusflow.app.ui.habits.HabitsScreen
import com.focusflow.app.ui.points.PointsScreen
import com.focusflow.app.ui.tasks.TasksScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Tasks("tasks", "Tasks", Icons.Filled.CalendarMonth),
    Habits("habits", "Habits", Icons.Filled.Repeat),
    Points("points", "Self-Efficacy", Icons.Filled.Insights),
    Goals("goals", "Goals", Icons.Filled.TrackChanges),
}

private const val BACKLOG = "backlog"

/** Bottom tabs, like the iOS TabView. */
@Composable
fun FocusFlowNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    Scaffold(
        // Each screen's top bar handles the status bar itself.
        contentWindowInsets = NoInsets,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Tasks.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Tasks.route) { TasksScreen(onOpenBacklog = { nav.navigate(BACKLOG) }) }
            composable(Tab.Habits.route) { HabitsScreen() }
            composable(Tab.Points.route) { PointsScreen() }
            composable(Tab.Goals.route) { GoalsScreen(onOpenGoal = { nav.navigate("goal/$it") }) }
            composable("goal/{goalId}") { e ->
                GoalDetailScreen(goalId = e.arguments?.getString("goalId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(BACKLOG) { BacklogScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
