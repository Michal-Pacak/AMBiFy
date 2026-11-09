package pl.ambif.ambify.server.timetable

import io.ktor.http.HttpStatusCode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.Membership
import pl.ambif.ambify.server.accounts.Secrets
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.accounts.Users
import pl.ambif.ambify.server.accounts.Validate
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.Avatar
import pl.ambif.ambify.shared.AvatarShape
import pl.ambif.ambify.shared.Avatars
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.ExtraActivityRequest
import pl.ambif.ambify.shared.ExtraSaveResult
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.DiaryConnection
import pl.ambif.ambify.shared.DiaryKind
import pl.ambif.ambify.shared.DiaryPupil
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.LessonChange
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.Occurrence
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.Recurrences
import pl.ambif.ambify.shared.Reminder
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.TimetableWeek
import pl.ambif.ambify.server.timetable.diary.DiaryException
import pl.ambif.ambify.server.timetable.diary.DiaryPupilData
import pl.ambif.ambify.server.timetable.diary.EduVulcan
import pl.ambif.ambify.server.timetable.diary.SchoolDiary
import pl.ambif.ambify.server.timetable.diary.Vault
import org.jetbrains.exposed.v1.core.neq
import org.slf4j.LoggerFactory

/** What waits for approval when someone may add an activity (or set reminders) only after approval. */
@Serializable
private data class QueuedActivities(val request: ExtraActivityRequest)

/**
 * The timetable: lessons from the school diary (eduVulcan, read by the server itself), the community's students and
 * their extra activities with recurrence and reminders. What a role may do comes from the permission table
 * (Action.TIMETABLE_*). The diary's keys are kept encrypted by [vault].
 */
class TimetableService(
    private val database: Database,
    private val accounts: AccountService,
    private val vault: Vault = Vault.random(),
    private val diaries: Map<DiaryKind, SchoolDiary> = mapOf(DiaryKind.VULCAN to EduVulcan()),
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val log = LoggerFactory.getLogger(TimetableService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        val runApproved: (String, String, String) -> Unit = { organizationId, requesterId, payload ->
            insertActivities(organizationId, requesterId, json.decodeFromString<QueuedActivities>(payload).request)
        }
        accounts.approvalHandlers[Action.TIMETABLE_EXTRA_OTHERS] = runApproved
        accounts.approvalHandlers[Action.TIMETABLE_REMIND_OTHERS] = runApproved
    }

    // --- the school diary ---

    /**
     * Connects a parent account of the diary with its one-time grant (eduVulcan: the tokens from /api/ap), takes its
     * pupils into the community and reads their lessons at once. A pupil already in the community (from another
     * parent account, or a student of that first name) stays the same student, with their member link and activities.
     * An older connection seeing only these pupils (connecting again after an error) is replaced.
     */
    fun connectDiary(session: UserSession, kind: DiaryKind, grant: List<String>): DiaryConnection {
        val membership = transaction(database) { linker(session) }
        val diary = diaries.getValue(kind)
        val link = try {
            diary.connect(grant)
        } catch (e: DiaryException) {
            throw ApiException(HttpStatusCode.BadGateway, "diary", e.message.orEmpty())
        }
        val connectionId = Secrets.id()
        val replaced = transaction(database) {
            val organizationId = membership.organizationId
            DiaryConnections.insert {
                it[id] = connectionId
                it[DiaryConnections.organizationId] = organizationId
                it[DiaryConnections.kind] = kind.name
                it[addedBy] = membership.user[Users.id]
                it[credential] = vault.seal(link.credential)
                it[createdAt] = clock()
            }
            link.pupils.forEach { addPupil(organizationId, connectionId, it) }
            val keys = link.pupils.map { it.key }.toSet()
            DiaryConnections.selectAll()
                .where { (DiaryConnections.organizationId eq organizationId) and (DiaryConnections.id neq connectionId) }
                .filter { row -> pupilKeys(row[DiaryConnections.id]).let { it.isNotEmpty() && keys.containsAll(it) } }
                .map { row -> row[DiaryConnections.credential].also { DiaryConnections.deleteWhere { id eq row[DiaryConnections.id] } } }
        }
        replaced.forEach { runCatching { diary.disconnect(vault.open(it)) } }
        sync(connectionId)
        return transaction(database) { connection(connectionId) }
    }

    fun diaryConnections(session: UserSession): List<DiaryConnection> = transaction(database) {
        val membership = linker(session)
        DiaryConnections.selectAll().where { DiaryConnections.organizationId eq membership.organizationId }
            .orderBy(DiaryConnections.createdAt).map { connection(it[DiaryConnections.id]) }
    }

    /** "Odśwież teraz": the connection's lessons read again at once. */
    fun syncDiary(session: UserSession, connectionId: String): DiaryConnection {
        transaction(database) { ownConnection(linker(session), connectionId) }
        sync(connectionId)
        return transaction(database) { connection(connectionId) }
    }

    /** Forgets the connection (the diary is told, if it answers); its students and their lessons stay. */
    fun disconnectDiary(session: UserSession, connectionId: String) {
        val row = transaction(database) {
            ownConnection(linker(session), connectionId).also { DiaryConnections.deleteWhere { id eq connectionId } }
        }
        runCatching { diaries.getValue(DiaryKind.valueOf(row[DiaryConnections.kind])).disconnect(vault.open(row[DiaryConnections.credential])) }
    }

    /** The daily update of every connection (18:00). */
    fun syncAllDiaries() {
        val ids = transaction(database) { DiaryConnections.selectAll().map { it[DiaryConnections.id] } }
        ids.forEach { runCatching { sync(it) }.onFailure { e -> log.warn("Diary update of {} failed", it, e) } }
    }

    /**
     * Reads the pupils (a new one appears, e.g. after a new school year) and their lessons from a week ago to four
     * weeks ahead; the lessons in that range are replaced. A failure is kept as the connection's last error.
     */
    private fun sync(connectionId: String) {
        val row = transaction(database) { DiaryConnections.selectAll().where { DiaryConnections.id eq connectionId }.singleOrNull() } ?: return
        val diary = diaries.getValue(DiaryKind.valueOf(row[DiaryConnections.kind]))
        val from = Recurrences.mondayOf(today()).minusWeeks(1)
        val to = Recurrences.mondayOf(today()).plusWeeks(5).minusDays(1)
        try {
            val credential = vault.open(row[DiaryConnections.credential])
            val pupils = diary.pupils(credential)
            val lessons = pupils.associate { it.key to diary.lessons(credential, it.key, from, to) }
            transaction(database) {
                val organizationId = row[DiaryConnections.organizationId]
                val known = DiaryPupils.selectAll().where { DiaryPupils.connectionId eq connectionId }
                    .associate { it[DiaryPupils.pupilKey] to it[DiaryPupils.studentId] }
                for (pupil in pupils) {
                    val studentId = known[pupil.key] ?: addPupil(organizationId, connectionId, pupil)
                    if (pupil.clazz.isNotBlank()) Students.update({ Students.id eq studentId }) { it[clazz] = pupil.clazz }
                    Lessons.deleteWhere { (Lessons.studentId eq studentId) and (date.between(from.toString(), to.toString())) }
                    lessons[pupil.key].orEmpty().forEach { lesson ->
                        Lessons.insert {
                            it[Lessons.studentId] = studentId
                            it[date] = lesson.date.toString()
                            it[position] = lesson.position
                            it[subject] = lesson.subject
                            it[room] = lesson.room
                            it[teacher] = lesson.teacher
                            it[time] = lesson.time
                            it[changeKind] = lesson.change?.kind?.name
                            it[changeNote] = lesson.change?.note.orEmpty()
                        }
                    }
                }
                DiaryConnections.update({ DiaryConnections.id eq connectionId }) {
                    it[lastSyncAt] = clock()
                    it[lastError] = null
                }
            }
        } catch (e: DiaryException) {
            transaction(database) { DiaryConnections.update({ DiaryConnections.id eq connectionId }) { it[lastError] = e.message } }
        }
    }

    /** The student a pupil of the diary is: the same pupil seen by another account, a free student of that first name, or a new one. */
    private fun addPupil(organizationId: String, connectionId: String, pupil: DiaryPupilData): String {
        val community = DiaryPupils.join(DiaryConnections, org.jetbrains.exposed.v1.core.JoinType.INNER, DiaryPupils.connectionId, DiaryConnections.id)
            .selectAll().where { DiaryConnections.organizationId eq organizationId }.toList()
        val studentId = community.firstOrNull { it[DiaryPupils.pupilKey] == pupil.key }?.get(DiaryPupils.studentId) ?: run {
            val taken = community.map { it[DiaryPupils.studentId] }.toSet()
            val sameName = Students.selectAll().where { (Students.organizationId eq organizationId) and (Students.name eq pupil.firstName) }.singleOrNull()
            when {
                sameName == null -> upsertStudent(organizationId, pupil.firstName, pupil.clazz)
                sameName[Students.id] !in taken -> sameName[Students.id]
                else -> upsertStudent(organizationId, "${pupil.firstName} ${pupil.surname.take(1)}.".trim(), pupil.clazz)
            }
        }
        DiaryPupils.deleteWhere { (DiaryPupils.connectionId eq connectionId) and (pupilKey eq pupil.key) }
        DiaryPupils.insert {
            it[DiaryPupils.connectionId] = connectionId
            it[pupilKey] = pupil.key
            it[DiaryPupils.studentId] = studentId
            it[school] = pupil.school
        }
        return studentId
    }

    private fun pupilKeys(connectionId: String): Set<String> =
        DiaryPupils.selectAll().where { DiaryPupils.connectionId eq connectionId }.map { it[DiaryPupils.pupilKey] }.toSet()

    private fun connection(connectionId: String): DiaryConnection {
        val row = DiaryConnections.selectAll().where { DiaryConnections.id eq connectionId }.single()
        val addedBy = Users.selectAll().where { Users.id eq row[DiaryConnections.addedBy] }.singleOrNull()?.get(Users.name).orEmpty()
        val pupils = DiaryPupils.join(Students, org.jetbrains.exposed.v1.core.JoinType.INNER, DiaryPupils.studentId, Students.id).selectAll()
            .where { DiaryPupils.connectionId eq connectionId }.orderBy(Students.createdAt)
            .map { DiaryPupil(it[Students.id], it[Students.name], it[Students.clazz], it[DiaryPupils.school]) }
        return DiaryConnection(connectionId, DiaryKind.valueOf(row[DiaryConnections.kind]), addedBy, pupils, row[DiaryConnections.lastSyncAt], row[DiaryConnections.lastError])
    }

    private fun ownConnection(membership: Membership, connectionId: String): ResultRow =
        DiaryConnections.selectAll()
            .where { (DiaryConnections.id eq connectionId) and (DiaryConnections.organizationId eq membership.organizationId) }
            .singleOrNull() ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono.")

    private fun linker(session: UserSession): Membership {
        accounts.requireModule(session, ModuleId.TIMETABLE)
        return accounts.requireAction(session, Action.TIMETABLE_LINK_VULCAN)
    }

    /** The week of [day] (Monday to Sunday): every student's lessons, the activities and their dates in that week. */
    fun week(session: UserSession, day: LocalDate): TimetableWeek = transaction(database) {
        val membership = viewer(session)
        val monday = Recurrences.mondayOf(day)
        val sunday = monday.plusDays(6)
        val me = membership.user[Users.id]
        // "View all" shows every student; otherwise only students linked to the caller's own account.
        val students = students(membership.organizationId).let { all -> if (seesAll(membership)) all else all.filter { it.memberId == me } }
        val lessons = students.associate { student ->
            student.id to Lessons.selectAll()
                .where { (Lessons.studentId eq student.id) and (Lessons.date.between(monday.toString(), sunday.toString())) }
                .orderBy(Lessons.date).orderBy(Lessons.position)
                .map {
                    val change = it[Lessons.changeKind]?.let { kind -> LessonChange(ChangeKind.valueOf(kind), it[Lessons.changeNote]) }
                    Lesson(it[Lessons.date], it[Lessons.position], it[Lessons.subject], it[Lessons.room], it[Lessons.teacher], it[Lessons.time], change)
                }
        }
        val visible = students.map { it.id }.toSet()
        val activities = activities(membership.organizationId).filter { it.studentId in visible }
        val occurrences = activities.flatMap { activity ->
            val schoolDays = lessons[activity.studentId].orEmpty().map { it.date }.toSet()
            // A weekday without lessons in a week that has lessons on other days: a holiday or a day off.
            val schoolFree = { date: LocalDate ->
                schoolDays.isNotEmpty() && date.dayOfWeek <= DayOfWeek.FRIDAY && date.toString() !in schoolDays
            }
            Recurrences.occurrences(LocalDate.parse(activity.startDate), activity.recurrence, monday, sunday, schoolFree)
                .map { Occurrence(activity.id, it.toString()) }
        }
        val connected = DiaryConnections.selectAll().where { DiaryConnections.organizationId eq membership.organizationId }.count() > 0
        val linkers = if (connected) emptyList() else accounts.membersAllowed(membership.organizationId, Action.TIMETABLE_LINK_VULCAN)
        TimetableWeek(monday.toString(), students, lessons, activities, occurrences, connected, linkers)
    }

    /**
     * Adds an activity for each chosen student. For oneself (a student linked to the caller's account) the role needs
     * "for oneself"; for others "for other people"; reminders for other members need "reminders for others".
     * All allowed: saved at once. Some only after approval: the whole request waits. Any not allowed: refused.
     */
    fun addActivities(session: UserSession, request: ExtraActivityRequest): ExtraSaveResult = transaction(database) {
        val membership = viewer(session)
        validate(request)
        val students = students(membership.organizationId).associateBy { it.id }
        if (request.studentIds.isEmpty() || request.studentIds.any { it !in students }) {
            throw ApiException(HttpStatusCode.BadRequest, "no_student", "Wybierz, dla kogo są zajęcia.")
        }
        val me = membership.user[Users.id]
        val needed = buildList {
            if (request.studentIds.any { students[it]?.memberId == me }) add(Action.TIMETABLE_EXTRA_SELF)
            if (request.studentIds.any { students[it]?.memberId != me }) add(Action.TIMETABLE_EXTRA_OTHERS)
            if (request.reminders.any { it.memberId != me }) add(Action.TIMETABLE_REMIND_OTHERS)
        }
        val modes = needed.associateWith { accounts.can(membership, it) }
        when {
            modes.values.any { it == Mode.DENIED } -> throw ApiException(
                HttpStatusCode.Forbidden, "forbidden",
                "Twoja rola nie może dodawać tych zajęć (" + modes.filterValues { it == Mode.DENIED }.keys.joinToString { it.label.lowercase() + " " + it.scope } + ").",
            )
            modes.values.any { it == Mode.APPROVAL } -> {
                val names = request.studentIds.mapNotNull { students[it]?.name }.joinToString()
                val summary = "${request.name} dla: $names, ${describe(request.recurrence, request.startDate)}, ${request.startTime}–${request.endTime}"
                ExtraSaveResult(queued = accounts.queueRequest(membership, modes.filterValues { it == Mode.APPROVAL }.keys.first(), json.encodeToString(QueuedActivities(request)), summary))
            }
            else -> ExtraSaveResult(activities = insertActivities(membership.organizationId, me, request))
        }
    }

    /** Changes one activity (its student stays); needs the same rights as adding it. */
    fun updateActivity(session: UserSession, activityId: String, request: ExtraActivityRequest): ExtraActivity = transaction(database) {
        val membership = viewer(session)
        validate(request)
        val row = activityRow(membership.organizationId, activityId)
        requireEdit(membership, row)
        ExtraActivities.update({ ExtraActivities.id eq activityId }) { it.fill(request) }
        saveReminders(activityId, request.reminders)
        activities(membership.organizationId).single { it.id == activityId }
    }

    fun deleteActivity(session: UserSession, activityId: String) {
        transaction(database) {
            val membership = viewer(session)
            requireEdit(membership, activityRow(membership.organizationId, activityId))
            ExtraActivities.deleteWhere { id eq activityId }
        }
    }

    /** Links a student from the diary with a member's account (null: unlinks). */
    fun linkStudent(session: UserSession, studentId: String, memberId: String?): List<Student> = transaction(database) {
        val membership = accounts.requireAction(session, Action.TIMETABLE_LINK_VULCAN)
        if (Students.selectAll().where { (Students.id eq studentId) and (Students.organizationId eq membership.organizationId) }.count() == 0L) {
            throw ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono.")
        }
        if (memberId != null && accounts.membersOf(membership.organizationId).none { it[Users.id] == memberId }) {
            throw ApiException(HttpStatusCode.BadRequest, "not_member", "Ta osoba nie należy do społeczności.")
        }
        Students.update({ Students.id eq studentId }) { it[Students.memberId] = memberId }
        students(membership.organizationId)
    }

    /** AMBiFy TEST: the timetable granted to the test Owner's community; the lessons come from connecting eduVulcan. */
    fun seedDemo(owner: UserSession) {
        // The demo Owner is the Platform Admin; the timetable is premium, so the demo community gets it granted.
        accounts.setGrant(owner, accounts.me(owner).communityId!!, ModuleId.TIMETABLE.name, true)
    }

    // --- helpers; all run inside a transaction ---

    /** Has access to the timetable: the module and at least "view own" (else nothing to show). */
    private fun viewer(session: UserSession): Membership {
        accounts.requireModule(session, ModuleId.TIMETABLE)
        val membership = accounts.membership(session)
        val canView = accounts.can(membership, Action.TIMETABLE_VIEW_ALL) == Mode.ALLOWED ||
            accounts.can(membership, Action.TIMETABLE_VIEW_SELF) == Mode.ALLOWED
        if (!canView) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Twoja rola nie może przeglądać planu.")
        return membership
    }

    /** Whether the caller sees every student's plan (else only students linked to their own account). */
    private fun seesAll(membership: Membership): Boolean =
        accounts.can(membership, Action.TIMETABLE_VIEW_ALL) == Mode.ALLOWED

    private fun upsertStudent(organizationId: String, name: String, clazz: String): String {
        val existing = Students.selectAll().where { (Students.organizationId eq organizationId) and (Students.name eq name) }.singleOrNull()
        if (existing != null) {
            if (clazz.isNotBlank()) Students.update({ Students.id eq existing[Students.id] }) { it[Students.clazz] = clazz }
            return existing[Students.id]
        }
        val studentId = Secrets.id()
        Students.insert {
            it[id] = studentId
            it[Students.organizationId] = organizationId
            it[Students.name] = name
            it[Students.clazz] = clazz
            it[createdAt] = clock()
        }
        return studentId
    }

    /** Students of a community; a linked student wears the member's icon, the others a circle in a color from the name. */
    private fun students(organizationId: String): List<Student> {
        val members = accounts.membersOf(organizationId).associateBy { it[Users.id] }
        return Students.selectAll().where { Students.organizationId eq organizationId }.orderBy(Students.createdAt).map { row ->
            val member = row[Students.memberId]?.let(members::get)
            val avatar = member?.let { with(accounts) { it.memberAvatar(it[Users.name]) } }
                ?: Avatar(Avatars.initials(row[Students.name]), Avatars.PALETTE[Math.floorMod(row[Students.name].hashCode(), Avatars.PALETTE.size)], AvatarShape.CIRCLE)
            Student(row[Students.id], row[Students.name], row[Students.clazz], member?.get(Users.id), avatar)
        }
    }

    private fun activities(organizationId: String): List<ExtraActivity> {
        val names = accounts.membersOf(organizationId).associate { it[Users.id] to it[Users.name] }
        return ExtraActivities.selectAll().where { ExtraActivities.organizationId eq organizationId }.orderBy(ExtraActivities.createdAt).map { row ->
            val activityId = row[ExtraActivities.id]
            ExtraActivity(
                id = activityId,
                studentId = row[ExtraActivities.studentId],
                name = row[ExtraActivities.name],
                place = row[ExtraActivities.place],
                startDate = row[ExtraActivities.startDate],
                startTime = row[ExtraActivities.startTime],
                endTime = row[ExtraActivities.endTime],
                recurrence = json.decodeFromString(row[ExtraActivities.recurrence]),
                reminders = ActivityReminders.selectAll().where { ActivityReminders.activityId eq activityId }.map {
                    Reminder(it[ActivityReminders.memberId], ReminderKind.valueOf(it[ActivityReminders.kind]), it[ActivityReminders.minutesBefore])
                },
                addToCalendar = row[ExtraActivities.addToCalendar],
                calendars = json.decodeFromString(row[ExtraActivities.calendars]),
                createdByName = names[row[ExtraActivities.createdBy]].orEmpty(),
            )
        }
    }

    private fun insertActivities(organizationId: String, createdBy: String, request: ExtraActivityRequest): List<ExtraActivity> {
        val ids = request.studentIds.map { studentId ->
            val activityId = Secrets.id()
            ExtraActivities.insert {
                it[id] = activityId
                it[ExtraActivities.organizationId] = organizationId
                it[ExtraActivities.studentId] = studentId
                it[ExtraActivities.createdBy] = createdBy
                it[createdAt] = clock()
                it.fill(request)
            }
            saveReminders(activityId, request.reminders)
            activityId
        }
        return activities(organizationId).filter { it.id in ids }
    }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.fill(request: ExtraActivityRequest) {
        this[ExtraActivities.name] = request.name.trim()
        this[ExtraActivities.place] = request.place?.trim()?.ifEmpty { null }
        this[ExtraActivities.startDate] = request.startDate
        this[ExtraActivities.startTime] = request.startTime
        this[ExtraActivities.endTime] = request.endTime
        this[ExtraActivities.recurrence] = json.encodeToString(request.recurrence)
        this[ExtraActivities.addToCalendar] = request.addToCalendar
        this[ExtraActivities.calendars] = json.encodeToString(request.calendars)
    }

    private fun saveReminders(activityId: String, reminders: List<Reminder>) {
        ActivityReminders.deleteWhere { ActivityReminders.activityId eq activityId }
        reminders.distinct().forEach { reminder ->
            ActivityReminders.insert {
                it[ActivityReminders.activityId] = activityId
                it[memberId] = reminder.memberId
                it[kind] = reminder.kind.name
                it[minutesBefore] = reminder.minutesBefore
            }
        }
    }

    private fun activityRow(organizationId: String, activityId: String): ResultRow =
        ExtraActivities.selectAll().where { (ExtraActivities.id eq activityId) and (ExtraActivities.organizationId eq organizationId) }
            .singleOrNull() ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono.")

    /** One's own activity (for a student linked to oneself) needs "for oneself"; anyone else's "for other people". */
    private fun requireEdit(membership: Membership, row: ResultRow) {
        val me = membership.user[Users.id]
        val own = Students.selectAll().where { Students.id eq row[ExtraActivities.studentId] }.single()[Students.memberId] == me
        val action = if (own) Action.TIMETABLE_EXTRA_SELF else Action.TIMETABLE_EXTRA_OTHERS
        if (accounts.can(membership, action) != Mode.ALLOWED) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Nie możesz zmieniać tych zajęć.")
        }
    }

    private fun validate(request: ExtraActivityRequest) {
        Validate.name(request.name, "Nazwa zajęć")
        val start = runCatching { LocalTime.parse(request.startTime) }.getOrNull()
        val end = runCatching { LocalTime.parse(request.endTime) }.getOrNull()
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException(HttpStatusCode.BadRequest, "bad_time", "Podaj godziny od–do, np. 16:00–17:00.")
        }
        if (runCatching { LocalDate.parse(request.startDate) }.isFailure || request.recurrence.endDate?.let { runCatching { LocalDate.parse(it) }.isFailure } == true) {
            throw ApiException(HttpStatusCode.BadRequest, "bad_date", "Nieprawidłowa data.")
        }
        if (request.recurrence.interval !in 1..52 || request.reminders.any { it.minutesBefore !in 0..(7 * 24 * 60) }) {
            throw ApiException(HttpStatusCode.BadRequest, "bad_rule", "Nieprawidłowe powtarzanie albo przypomnienie.")
        }
    }

    private fun describe(rule: Recurrence, startDate: String): String = when (rule.frequency) {
        Frequency.NONE -> "jednorazowo $startDate"
        Frequency.DAILY -> if (rule.interval == 1) "codziennie" else "co ${rule.interval} dni"
        Frequency.WEEKLY -> (if (rule.interval == 1) "co tydzień" else "co ${rule.interval} tygodnie") +
            rule.weekdays.joinToString(prefix = " (", postfix = ")") { listOf("Pn", "Wt", "Śr", "Czw", "Pt", "So", "Nd")[it - 1] }
        Frequency.MONTHLY -> if (rule.interval == 1) "co miesiąc" else "co ${rule.interval} miesiące"
        Frequency.YEARLY -> "co roku"
    }
}
