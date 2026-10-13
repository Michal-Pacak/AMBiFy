package pl.ambif.ambify.features.calendar.domain

import java.time.Instant

enum class OrganizationCalendarSetupMethod {
    SELECT_FROM_GOOGLE_ACCOUNT,
    ENTER_CALENDAR_ID,
}

data class OrganizationCalendarConnection(
    val organizationId: String,
    val calendarId: String,
    val connectedByUserId: String,
    val setupMethod: OrganizationCalendarSetupMethod,
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val startsAt: Instant,
    val endsAt: Instant,
)

data class PersonalCalendarConnection(
    val userId: String,
    val calendarId: String,
    val accountEmail: String,
    val managedByUserId: String? = null,
)

data class EventPersonalCalendarSync(
    val eventId: String,
    val userId: String,
    val enabled: Boolean,
)
