package pl.ambif.ambify.core.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Setup : AppDestination("setup")
    data object Login : AppDestination("login")
    data object Join : AppDestination("join")
    data object ResetPassword : AppDestination("reset_password")
    data object Home : AppDestination("home")
    data object Calendar : AppDestination("calendar")
    data object Missions : AppDestination("missions")
    data object Shopping : AppDestination("shopping")
    data object Communication : AppDestination("communication")
    data object Timetable : AppDestination("timetable")
    data object School : AppDestination("school")
    data object Account : AppDestination("account")
}

/** Screens shown before signing in: no module bar there. */
val SignedOutRoutes = setOf(
    AppDestination.Splash.route,
    AppDestination.Setup.route,
    AppDestination.Login.route,
    AppDestination.Join.route,
    AppDestination.ResetPassword.route,
)
