package pl.ambif.ambify.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.core.navigation.NoBarRoutes
import pl.ambif.ambify.core.navigation.SignedOutRoutes
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.auth.LoginScreen
import pl.ambif.ambify.features.auth.RegisterScreen
import pl.ambif.ambify.features.auth.ResetPasswordScreen
import pl.ambif.ambify.features.auth.StartScreen
import pl.ambif.ambify.features.account.AccountScreen
import pl.ambif.ambify.features.account.CommunityModulesScreen
import pl.ambif.ambify.features.platform.PlatformScreen
import pl.ambif.ambify.features.calendar.CalendarScreen
import pl.ambif.ambify.features.common.ModulePlaceholderScreen
import pl.ambif.ambify.features.home.HomeScreen
import pl.ambif.ambify.modules.splash.SplashScreen
import pl.ambif.ambify.shared.ModuleId

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val session = LocalAppSession.current
    val sessionState by session.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigationBar =
        currentDestination != null && currentDestination.route !in NoBarRoutes
    var loginNotice by rememberSaveable { mutableStateOf<String?>(null) }

    /** Dashboard, or the start screen for someone who has no community yet. */
    fun openAfterSignIn() {
        val hasCommunity = (session.state.value as? SessionState.SignedIn)?.me?.communityId != null
        navController.navigate(if (hasCommunity) AppDestination.Home.route else AppDestination.Start.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // Signed out anywhere in the app (logout, revoked device, expired session): back to login.
    // Left without a community (removed from the last one): to the start screen.
    LaunchedEffect(sessionState, currentDestination?.route) {
        val route = currentDestination?.route ?: return@LaunchedEffect
        val next = when (val state = sessionState) {
            SessionState.SignedOut -> AppDestination.Login.takeIf { route !in SignedOutRoutes }
            is SessionState.SignedIn -> AppDestination.Start.takeIf { state.me.communityId == null && route !in NoBarRoutes }
        }
        next?.let {
            navController.navigate(it.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Back on the Dashboard (or a sign-in screen) leaves the app without asking: the real app goes to
    // the background like any Android app, the test app closes completely.
    val activity = LocalContext.current as? Activity
    val rootRoutes = setOf(AppDestination.Home.route, AppDestination.Login.route, AppDestination.Start.route)
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
                        if (session.isSignedIn) {
                            session.refreshInBackground()
                            openAfterSignIn()
                        } else {
                            navController.navigate(AppDestination.Login.route) {
                                popUpTo(AppDestination.Splash.route) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(AppDestination.Login.route) {
                LoginScreen(
                    onSignedIn = ::openAfterSignIn,
                    onCreateAccount = { navController.navigate(AppDestination.Register.route) },
                    onForgotPassword = { navController.navigate(AppDestination.ResetPassword.route) },
                    notice = loginNotice,
                )
            }

            composable(AppDestination.Register.route) {
                RegisterScreen(onSignedIn = ::openAfterSignIn, onBack = { navController.popBackStack() })
            }

            composable(AppDestination.Start.route) {
                StartScreen(name = (sessionState as? SessionState.SignedIn)?.me?.name ?: "", onReady = ::openAfterSignIn)
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
                    title = ModuleId.MISSIONS.label,
                    description = "Zadania, punkty i nagrody dla członków organizacji.",
                )
            }

            composable(AppDestination.Lists.route) {
                ModulePlaceholderScreen(
                    title = ModuleId.LISTS.label,
                    description = "Wspólne listy: zakupy, zadania i terminy.",
                )
            }

            composable(AppDestination.Communication.route) {
                ModulePlaceholderScreen(
                    title = ModuleId.COMMUNICATION.label,
                    description = "Wiadomości i dyskusje między członkami organizacji.",
                )
            }

            composable(AppDestination.Timetable.route) {
                ModulePlaceholderScreen(
                    title = ModuleId.TIMETABLE.label,
                    description = "Tygodniowy plan lekcji uczniów.",
                )
            }

            composable(AppDestination.School.route) {
                ModulePlaceholderScreen(
                    title = ModuleId.SCHOOL.label,
                    description = "Oceny, ogłoszenia i sprawy szkolne.",
                )
            }

            composable(AppDestination.Assistant.route) {
                ModulePlaceholderScreen(
                    title = ModuleId.ASSISTANT.label,
                    description = "Rozmowa z AI (Claude, GPT, Gemini) z załącznikami, przez bramkę AI na serwerze.",
                )
            }

            composable(AppDestination.Account.route) {
                AccountScreen(
                    onOpenModules = { navController.navigate(AppDestination.CommunityModules.route) },
                )
            }

            composable(AppDestination.CommunityModules.route) {
                CommunityModulesScreen(onBack = { navController.popBackStack() })
            }

            composable(AppDestination.Platform.route) {
                PlatformScreen()
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
