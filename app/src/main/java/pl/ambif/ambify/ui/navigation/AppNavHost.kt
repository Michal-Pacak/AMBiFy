package pl.ambif.ambify.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.core.navigation.SignedOutRoutes
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.auth.JoinScreen
import pl.ambif.ambify.features.auth.LoginScreen
import pl.ambif.ambify.features.auth.ResetPasswordScreen
import pl.ambif.ambify.features.auth.SetupScreen
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
    val session = LocalAppSession.current
    val sessionState by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigationBar =
        currentDestination != null && currentDestination.route !in SignedOutRoutes
    var loginNotice by rememberSaveable { mutableStateOf<String?>(null) }

    fun openAfterSignIn() {
        navController.navigate(AppDestination.Home.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // Signed out anywhere in the app (logout, revoked device, expired session): back to login.
    LaunchedEffect(sessionState, currentDestination?.route) {
        val route = currentDestination?.route
        if (sessionState is SessionState.SignedOut && route != null && route !in SignedOutRoutes) {
            navController.navigate(AppDestination.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Back on the Dashboard (or a sign-in screen) leaves the app without asking: the real app goes to
    // the background like any Android app, the test app closes completely.
    val activity = LocalContext.current as? Activity
    val rootRoutes = setOf(AppDestination.Home.route, AppDestination.Login.route, AppDestination.Setup.route)
    BackHandler(enabled = currentDestination?.route in rootRoutes) {
        if (BuildConfig.TEST_LOGIN) activity?.finishAndRemoveTask() else activity?.moveTaskToBack(true)
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
                        scope.launch {
                            val next = when {
                                session.isSignedIn -> {
                                    session.refreshInBackground()
                                    AppDestination.Home
                                }
                                runCatching { session.api.authStatus().familyExists }.getOrDefault(true) -> AppDestination.Login
                                else -> AppDestination.Setup
                            }
                            navController.navigate(next.route) {
                                popUpTo(AppDestination.Splash.route) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(AppDestination.Setup.route) {
                SetupScreen(onSignedIn = ::openAfterSignIn)
            }

            composable(AppDestination.Login.route) {
                LoginScreen(
                    onSignedIn = ::openAfterSignIn,
                    onJoin = { navController.navigate(AppDestination.Join.route) },
                    onForgotPassword = { navController.navigate(AppDestination.ResetPassword.route) },
                    notice = loginNotice,
                )
            }

            composable(AppDestination.Join.route) {
                JoinScreen(onSignedIn = ::openAfterSignIn, onBack = { navController.popBackStack() })
            }

            composable(AppDestination.ResetPassword.route) {
                ResetPasswordScreen(
                    onDone = {
                        loginNotice = "Hasło zmienione. Zaloguj się nowym hasłem."
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppDestination.Home.route) {
                val me = (sessionState as? SessionState.SignedIn)?.me
                HomeScreen(organizationName = me?.familyName ?: "", userName = me?.name ?: "")
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
                    description = "Tygodniowy plan lekcji uczniów.",
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
