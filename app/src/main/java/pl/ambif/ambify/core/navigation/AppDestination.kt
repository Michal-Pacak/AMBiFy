package pl.ambif.ambify.core.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Home : AppDestination("home")
    data object Calendar : AppDestination("calendar")
    data object Missions : AppDestination("missions")
    data object Shopping : AppDestination("shopping")
    data object Communication : AppDestination("communication")
    data object Timetable : AppDestination("timetable")
    data object School : AppDestination("school")
    data object Account : AppDestination("account")
}
