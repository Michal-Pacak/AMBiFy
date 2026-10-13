package pl.ambif.ambify.core.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Home : AppDestination("home")
    data object Calendar : AppDestination("calendar")
    data object Account : AppDestination("account")
}
