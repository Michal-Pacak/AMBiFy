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
    /** Ustawienia on the module bar, with its sections. */
    data object Settings : AppDestination("settings")
    data object SettingsAccount : AppDestination("settings/account")
    data object SettingsCommunities : AppDestination("settings/communities")
    data object SettingsApp : AppDestination("settings/app")
    /** Screens of one community, opened from its popup in Ustawienia → Społeczności. */
    data object CommunityModules : AppDestination("community/{id}/modules") {
        fun of(communityId: String) = "community/$communityId/modules"
    }
    data object CommunityPeople : AppDestination("community/{id}/people/{section}") {
        fun of(communityId: String, section: String) = "community/$communityId/people/$section"
    }
    data object CommunityPermissions : AppDestination("community/{id}/permissions") {
        fun of(communityId: String) = "community/$communityId/permissions"
    }
    data object CommunityRequests : AppDestination("community/{id}/requests") {
        fun of(communityId: String) = "community/$communityId/requests"
    }
    /** 0.7.7: Ustawienia → Wygląd (the account's theme, own themes). */
    data object SettingsLook : AppDestination("settings/look")
    data object CommunityLook : AppDestination("community/{id}/look") {
        fun of(communityId: String) = "community/$communityId/look"
    }
    /** The theme editor: [id] "new" for a new theme; [community] "-" for my own (Premium konta). */
    data object ThemeEditor : AppDestination("theme-editor/{community}/{id}") {
        fun of(communityId: String?, themeId: String?) = "theme-editor/${communityId ?: "-"}/${themeId ?: "new"}"
    }
    data object Platform : AppDestination("platform")
    /** 0.7.9: bug reports — "Zgłoś błąd", my reports, one of them (in Ustawienia → Aplikacja). */
    data object ReportBug : AppDestination("settings/report")
    data object MyReports : AppDestination("settings/reports")
    data object MyReport : AppDestination("settings/reports/{id}") {
        fun of(reportId: String) = "settings/reports/$reportId"
    }
    /** The Platform Admin's panel of all reports. */
    data object PlatformReports : AppDestination("platform/reports")
    data object PlatformReport : AppDestination("platform/reports/{id}") {
        fun of(reportId: String) = "platform/reports/$reportId"
    }
    /** 0.7.8: the 16-day forecast of a Pogoda widget (a part of the Dashboard). */
    data object Forecast : AppDestination("forecast/{widget}") {
        fun of(widgetId: String) = "forecast/$widgetId"
    }
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

/** 0.7.9: someone without a circle may report a bug and see their reports too (no module bar for them there). */
val NoCircleRoutes = setOf(AppDestination.Start.route, AppDestination.ReportBug.route, AppDestination.MyReports.route, AppDestination.MyReport.route)
