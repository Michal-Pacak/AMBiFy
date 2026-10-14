package pl.ambif.ambify.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.features.account.AccountScreen
import pl.ambif.ambify.features.calendar.CalendarScreen
import pl.ambif.ambify.features.common.ModulePlaceholderScreen
import pl.ambif.ambify.features.home.HomeScreen
import pl.ambif.ambify.modules.splash.SplashScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigationBar =
        currentDestination != null && currentDestination.route != AppDestination.Splash.route

    // Back on the Dashboard asks before leaving the app (other screens go back to the Dashboard).
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = currentDestination?.route == AppDestination.Home.route) {
        confirmExit = true
    }
    if (confirmExit) {
        val activity = LocalContext.current as? Activity
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Zamknąć AMBiFy?") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("Zamknij") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Anuluj") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavigationBar) {
                ModuleBar(
                    currentDestination = currentDestination,
                    onDestinationSelected = { route ->
                        navController.navigateToBottomDestination(route)
                    },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Splash.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(AppDestination.Splash.route) {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(AppDestination.Home.route) {
                            popUpTo(AppDestination.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(AppDestination.Home.route) {
                HomeScreen()
            }

            composable(AppDestination.Calendar.route) {
                CalendarScreen()
            }

            composable(AppDestination.Missions.route) {
                ModulePlaceholderScreen(
                    title = "Misje",
                    description = "Zadania, punkty i nagrody dla członków organizacji.",
                )
            }

            composable(AppDestination.Shopping.route) {
                ModulePlaceholderScreen(
                    title = "Zakupy",
                    description = "Wspólne listy zakupów całej organizacji.",
                )
            }

            composable(AppDestination.Communication.route) {
                ModulePlaceholderScreen(
                    title = "Czat",
                    description = "Wiadomości i dyskusje między członkami organizacji.",
                )
            }

            composable(AppDestination.Timetable.route) {
                ModulePlaceholderScreen(
                    title = "Plan lekcji",
                    description = "Tygodniowy plan lekcji dzieci.",
                )
            }

            composable(AppDestination.School.route) {
                ModulePlaceholderScreen(
                    title = "Szkoła",
                    description = "Oceny, ogłoszenia i sprawy szkolne.",
                )
            }

            composable(AppDestination.Account.route) {
                AccountScreen()
            }
        }
    }
}

private fun NavHostController.navigateToBottomDestination(route: String) {
    navigate(route) {
        popUpTo(AppDestination.Home.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
