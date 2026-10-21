package com.example.chess.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chess.ui.screens.AnalysisScreen
import com.example.chess.ui.screens.AnalyticsScreen
import com.example.chess.ui.screens.DashboardScreen
import com.example.chess.ui.screens.EndgameScreen
import com.example.chess.ui.screens.GameDetailScreen
import com.example.chess.ui.screens.LoginScreen
import com.example.chess.ui.screens.MistakesScreen
import com.example.chess.ui.screens.OpeningsScreen
import com.example.chess.ui.screens.PlayScreen
import com.example.chess.ui.screens.PlannerScreen
import com.example.chess.ui.screens.PuzzleScreen
import com.example.chess.ui.screens.SettingsScreen
import com.example.chess.ui.screens.TrainHomeScreen

object Routes {
    const val Login = "login"
    const val Dashboard = "dashboard"
    const val Analytics = "analytics"
    const val Train = "train"
    const val Play = "play"
    const val Profile = "profile"
    const val Analysis = "analysis?fen={fen}&gameId={gameId}"
    const val Mistakes = "mistakes"
    const val Puzzles = "puzzles"
    const val Openings = "openings"
    const val Endgames = "endgames"
    const val Planner = "planner"
    const val Game = "game/{id}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Dashboard, "Home", Icons.Outlined.Home),
    Tab(Routes.Analytics, "Games", Icons.Outlined.Insights),
    Tab(Routes.Train, "Train", Icons.Outlined.School),
    Tab(Routes.Play, "Play", Icons.Outlined.SportsEsports),
    Tab(Routes.Profile, "You", Icons.Outlined.Person),
)

@Composable
fun ChessOsRoot(signedIn: Boolean) {
    val nav = rememberNavController()
    val start = if (signedIn) Routes.Dashboard else Routes.Login
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in tabs.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable(Routes.Login) {
                LoginScreen(onSignedIn = {
                    nav.navigate(Routes.Dashboard) { popUpTo(Routes.Login) { inclusive = true } }
                })
            }
            composable(Routes.Dashboard) {
                DashboardScreen(
                    onAnalyze = { nav.navigate("analysis?fen=&gameId=") },
                    onMistakes = { nav.navigate(Routes.Mistakes) },
                    onGame = { nav.navigate("game/$it") },
                )
            }
            composable(Routes.Analytics) {
                AnalyticsScreen(
                    onGame = { nav.navigate("game/$it") },
                    onAnalyzeFen = { nav.navigate("analysis?fen=${android.net.Uri.encode(it)}&gameId=") },
                )
            }
            composable(Routes.Train) {
                TrainHomeScreen(
                    onPuzzles = { nav.navigate(Routes.Puzzles) },
                    onMistakes = { nav.navigate(Routes.Mistakes) },
                    onOpenings = { nav.navigate(Routes.Openings) },
                    onEndgames = { nav.navigate(Routes.Endgames) },
                    onPlanner = { nav.navigate(Routes.Planner) },
                )
            }
            composable(Routes.Play) { PlayScreen() }
            composable(Routes.Profile) { SettingsScreen(onSignedOut = { nav.navigate(Routes.Login) { popUpTo(0) } }) }
            composable(
                Routes.Analysis,
                arguments = listOf(
                    navArgument("fen") { type = NavType.StringType; defaultValue = "" },
                    navArgument("gameId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                AnalysisScreen(
                    initialFen = it.arguments?.getString("fen").orEmpty(),
                    gameId = it.arguments?.getString("gameId").orEmpty(),
                )
            }
            composable(Routes.Mistakes) { MistakesScreen(onOpenFen = { nav.navigate("analysis?fen=${android.net.Uri.encode(it)}&gameId=") }) }
            composable(Routes.Puzzles) { PuzzleScreen() }
            composable(Routes.Openings) { OpeningsScreen() }
            composable(Routes.Endgames) { EndgameScreen() }
            composable(Routes.Planner) { PlannerScreen() }
            composable(Routes.Game, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                GameDetailScreen(
                    gameId = it.arguments?.getString("id").orEmpty(),
                    onAnalyzeBoard = { fen -> nav.navigate("analysis?fen=${android.net.Uri.encode(fen)}&gameId=${it.arguments?.getString("id")}") },
                )
            }
        }
    }
}
