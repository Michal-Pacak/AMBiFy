package pl.ambif.ambify.core.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Register : AppDestination("register")
    data object Login : AppDestination("login")
    data object ResetPassword : AppDestination("reset_password")
    /** Signed in, but not in any community yet: found one or join with a code. */
    data object Start : AppDestination("start")
    data object Home : AppDestination("home")
    data object Calendar : AppDestination("calendar")
    data object Missions : AppDestination("missions")
    data object Lists : AppDestination("lists")
    data object Communication : AppDestination("communication")
    data object Timetable : AppDestination("timetable")
    data object School : AppDestination("school")
    data object Assistant : AppDestination("assistant")
    data object Account : AppDestination("account")
    data object CommunityModules : AppDestination("community_modules")
    data object Platform : AppDestination("platform")
}

/** Screens shown before signing in. */
val SignedOutRoutes = setOf(
    AppDestination.Splash.route,
    AppDestination.Register.route,
    AppDestination.Login.route,
    AppDestination.ResetPassword.route,
)

/** No module bar there. */
val NoBarRoutes = SignedOutRoutes + AppDestination.Start.route
