package pl.ambif.ambify.server.timetable

import org.jetbrains.exposed.v1.core.Table

// Mirrors of the timetable tables created by V005__roles_timetable.sql and V006__school_diary.sql (the SQL files are the source of truth).

object Students : Table("students") {
    val id = text("id")
    val organizationId = text("organization_id")
    val name = text("name")
    val clazz = text("clazz")
    val memberId = text("member_id").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Lessons : Table("lessons") {
    val studentId = text("student_id")
    val date = text("date")
    val position = integer("position")
    val subject = text("subject")
    val room = text("room")
    val teacher = text("teacher")
    val time = text("time")
    val changeKind = text("change_kind").nullable()
    val changeNote = text("change_note")
    override val primaryKey = PrimaryKey(studentId, date, position)
}

object ExtraActivities : Table("extra_activities") {
    val id = text("id")
    val organizationId = text("organization_id")
    val studentId = text("student_id")
    val name = text("name")
    val place = text("place").nullable()
    val startDate = text("start_date")
    val startTime = text("start_time")
    val endTime = text("end_time")
    val recurrence = text("recurrence")
    val addToCalendar = bool("add_to_calendar")
    val calendars = text("calendars")
    val createdBy = text("created_by")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ActivityReminders : Table("activity_reminders") {
    val activityId = text("activity_id")
    val memberId = text("member_id")
    val kind = text("kind")
    val minutesBefore = integer("minutes_before")
    override val primaryKey = PrimaryKey(activityId, memberId, kind, minutesBefore)
}

object DiaryConnections : Table("diary_connections") {
    val id = text("id")
    val organizationId = text("organization_id")
    val kind = text("kind")
    val addedBy = text("added_by")
    val credential = text("credential")
    val createdAt = long("created_at")
    val lastSyncAt = long("last_sync_at").nullable()
    val lastError = text("last_error").nullable()
    override val primaryKey = PrimaryKey(id)
}

object DiaryPupils : Table("diary_pupils") {
    val connectionId = text("connection_id")
    val pupilKey = text("pupil_key")
    val studentId = text("student_id")
    val school = text("school")
    override val primaryKey = PrimaryKey(connectionId, pupilKey)
}
