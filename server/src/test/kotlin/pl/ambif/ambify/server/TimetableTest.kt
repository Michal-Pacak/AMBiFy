package pl.ambif.ambify.server

import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pl.ambif.ambify.server.accounts.AccessTokens
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.timetable.TimetableService
import pl.ambif.ambify.server.timetable.diary.DiaryException
import pl.ambif.ambify.server.timetable.diary.DiaryLesson
import pl.ambif.ambify.server.timetable.diary.DiaryLink
import pl.ambif.ambify.server.timetable.diary.DiaryPupilData
import pl.ambif.ambify.server.timetable.diary.SchoolDiary
import pl.ambif.ambify.server.timetable.diary.Vault
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.DiaryKind
import pl.ambif.ambify.shared.LessonChange
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ExtraActivityRequest
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.MonthMode
import pl.ambif.ambify.shared.Permissions
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.Recurrences
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.Reminder
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.RoleCreate
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class TimetableTest {
    private val tokens = testTokens()
    private val monday = LocalDate.of(2026, 11, 2)

    private fun AccountService.session(issued: Tokens): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString())
    }

    /**
     * A diary in memory: a grant is the list of pupil keys a parent account sees. [lessons] per pupil key;
     * [failing] makes every call fail (the diary does not answer).
     */
    private class FakeDiary : SchoolDiary {
        val pupils = mutableMapOf<String, DiaryPupilData>()
        val lessons = mutableMapOf<String, List<DiaryLesson>>()
        val disconnected = mutableListOf<String>()
        var failing = false

        fun pupil(key: String, firstName: String, surname: String, clazz: String) {
            pupils[key] = DiaryPupilData(key, firstName, surname, clazz, "SP 1")
        }

        override fun connect(grant: List<String>): DiaryLink {
            if (failing) throw DiaryException("Brak połączenia z eduVulcan.")
            val credential = grant.joinToString(",")
            return DiaryLink(credential, pupils(credential))
        }

        override fun pupils(credential: String): List<DiaryPupilData> {
            if (failing) throw DiaryException("Brak połączenia z eduVulcan.")
            return credential.split(",").mapNotNull { pupils[it] }
        }

        override fun lessons(credential: String, pupilKey: String, from: LocalDate, to: LocalDate) =
            lessons[pupilKey].orEmpty().filter { !it.date.isBefore(from) && !it.date.isAfter(to) }

        override fun disconnect(credential: String) {
            disconnected += credential
        }
    }

    private class World(
        val accounts: AccountService,
        val timetable: TimetableService,
        val diary: FakeDiary,
        val owner: UserSession,
        val user: UserSession,
    )

    /** Owner Michał (Platform Admin, timetable granted) and User Bartosz; eduVulcan has Bartosz's and Filip's lessons. */
    private fun world(connect: Boolean = true): World {
        val database = Db.open(Files.createTempDirectory("ambify-timetable").resolve("t.db").toString())
        val accounts = AccountService(database, tokens)
        val diary = FakeDiary()
        val timetable = TimetableService(database, accounts, Vault.random(), mapOf(DiaryKind.VULCAN to diary), today = { monday })
        val owner = accounts.session(accounts.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24")))
        accounts.setGrant(owner, accounts.me(owner).communityId!!, "TIMETABLE", true)
        accounts.register(RegisterRequest(accounts.createInvitation(owner).code, "Bartosz Pacak", "bartosz@pacak.waw.pl", "haslo1234", "X"))
        val user = accounts.session(accounts.login(LoginRequest("bartosz@pacak.waw.pl", "haslo1234", "X")))
        diary.pupil("u1|7", "Bartosz", "Pacak", "6b")
        diary.pupil("u1|8", "Filip", "Pacak", "2a")
        val lessons = (0..4).map { day -> DiaryLesson(monday.plusDays(day.toLong()), 1, "Matematyka", "21", "[nauczyciel]", "08:00-08:45") }
        // Wednesday 4.11 is a day off for Bartosz: no lessons that day.
        diary.lessons["u1|7"] = lessons.filter { it.date != monday.plusDays(2) }
        diary.lessons["u1|8"] = lessons
        if (connect) timetable.connectDiary(owner, DiaryKind.VULCAN, listOf("u1|7", "u1|8"))
        return World(accounts, timetable, diary, owner, user)
    }

    private fun request(vararg studentIds: String, reminders: List<Reminder> = emptyList()) = ExtraActivityRequest(
        studentIds.toList(), "Basen", null, monday.toString(), "16:00", "17:00",
        Recurrence(Frequency.WEEKLY, weekdays = listOf(1, 3), skipSchoolFree = true), reminders,
    )

    @Test
    fun recurrenceRules() {
        val start = LocalDate.of(2026, 11, 4) // a Wednesday
        val weekly2 = Recurrences.occurrences(start, Recurrence(Frequency.WEEKLY, 2, listOf(3, 5)), start, start.plusDays(20))
        assertEquals(listOf("2026-11-04", "2026-11-06", "2026-11-18", "2026-11-20"), weekly2.map { it.toString() })

        val nthWednesday = Recurrences.occurrences(start, Recurrence(Frequency.MONTHLY, monthMode = MonthMode.NTH_WEEKDAY), start, LocalDate.of(2027, 1, 31))
        assertEquals(listOf("2026-11-04", "2026-12-02", "2027-01-06"), nthWednesday.map { it.toString() })

        val threeTimes = Recurrences.occurrences(start, Recurrence(Frequency.DAILY, count = 3), start, start.plusDays(30))
        assertEquals(3, threeTimes.size)
        val untilFriday = Recurrences.occurrences(start, Recurrence(Frequency.DAILY, endDate = "2026-11-06"), start, start.plusDays(30))
        assertEquals(3, untilFriday.size)
    }

    @Test
    fun diaryLessonsShowAndDaysOffAreSkipped() {
        val w = world()
        val bartosz = w.timetable.week(w.owner, monday).students.first { it.name == "Bartosz" }
        w.timetable.addActivities(w.owner, request(bartosz.id))

        val week = w.timetable.week(w.owner, monday.plusDays(3))
        assertEquals(listOf("Bartosz", "Filip"), week.students.map { it.name })
        assertEquals(4, week.lessons.getValue(bartosz.id).size)
        // Monday and Wednesday every week, but Wednesday is a day off in Vulcan.
        assertEquals(listOf(monday.toString()), week.occurrences.map { it.date })
    }

    @Test
    fun userAddsForThemselvesButOthersOnlyAsTheTableSays() {
        val w = world()
        val students = w.timetable.week(w.owner, monday).students
        val bartosz = students.first { it.name == "Bartosz" }
        val filip = students.first { it.name == "Filip" }
        val bartoszId = w.accounts.me(w.user).id
        w.timetable.linkStudent(w.owner, bartosz.id, bartoszId)

        val own = w.timetable.addActivities(w.user, request(bartosz.id))
        assertEquals(1, own.activities.size)
        // For someone else: not allowed for the lowest role by default.
        assertEquals("forbidden", assertFailsWith<ApiException> { w.timetable.addActivities(w.user, request(filip.id)) }.code)

        // A reminder for another member: only after approval; the Owner approves and it is saved.
        val ownerId = w.accounts.me(w.owner).id
        val queued = w.timetable.addActivities(w.user, request(bartosz.id, reminders = listOf(Reminder(ownerId, ReminderKind.ALARM, 45))))
        assertNotNull(queued.queued)
        val pending = w.accounts.requests(w.owner).single()
        assertEquals(Action.TIMETABLE_REMIND_OTHERS, pending.action)
        assertTrue(w.accounts.requests(w.user).isEmpty())
        w.accounts.decide(w.owner, pending.id, approve = true)
        assertEquals(2, w.timetable.week(w.owner, monday).activities.size)
    }

    @Test
    fun ownerChangesTheTableAndRoles() {
        val w = world()
        val table = w.accounts.permissionTable(w.owner)
        val user = table.roles.single { it.name == "User" }
        val filip = w.timetable.week(w.owner, monday).students.first { it.name == "Filip" }

        w.accounts.setPermission(w.owner, user.id, Action.TIMETABLE_EXTRA_OTHERS, Mode.ALLOWED)
        assertEquals(1, w.timetable.addActivities(w.user, request(filip.id)).activities.size)
        assertEquals("forbidden", assertFailsWith<ApiException> { w.accounts.setPermission(w.user, user.id, Action.INVITE, Mode.ALLOWED) }.code)
        assertEquals("no_approval", assertFailsWith<ApiException> { w.accounts.setPermission(w.owner, user.id, Action.INVITE, Mode.APPROVAL) }.code)

        // A role between Admin and User starts "in between".
        val roles = w.accounts.createRole(w.owner, RoleCreate("Trener", table.roles.single { it.name == "Admin" }.id))
        assertEquals(listOf("Owner", "Admin", "Trener", "User"), roles.map { it.name })
        val trener = roles.single { it.name == "Trener" }
        assertEquals(Mode.APPROVAL, Permissions.effective(Action.CALENDAR_EDIT_OTHERS, trener, roles, emptyMap()))
        assertEquals(Mode.ALLOWED, Permissions.effective(Action.CALENDAR_ADD_OTHERS, trener, roles, emptyMap()))
        assertEquals(listOf("Owner", "Trener", "Admin", "User"), w.accounts.moveRole(w.owner, trener.id, up = true).map { it.name })
        assertEquals("role_in_use", assertFailsWith<ApiException> { w.accounts.deleteRole(w.owner, user.id) }.code)
        assertEquals(listOf("Owner", "Admin", "User"), w.accounts.deleteRole(w.owner, trener.id).map { it.name })
    }

    @Test
    fun connectingTheDiary() {
        val w = world(connect = false)
        val empty = w.timetable.week(w.user, monday)
        assertEquals(false, empty.diaryConnected)
        assertEquals(listOf("Michał Pacak"), empty.diaryLinkers)
        // The lowest role may not connect it by default.
        assertEquals("forbidden", assertFailsWith<ApiException> { w.timetable.connectDiary(w.user, DiaryKind.VULCAN, listOf("u1|7")) }.code)

        val connection = w.timetable.connectDiary(w.owner, DiaryKind.VULCAN, listOf("u1|7", "u1|8"))
        assertEquals(listOf("Bartosz", "Filip"), connection.pupils.map { it.name })
        assertEquals("Michał Pacak", connection.addedByName)
        assertNotNull(connection.lastSyncAt)
        val userWeek = w.timetable.week(w.user, monday)
        assertTrue(userWeek.diaryConnected)
        // The lowest role sees only its own plan; Bartosz is not linked yet, so no students show for the User.
        assertTrue(userWeek.students.isEmpty())
        val week = w.timetable.week(w.owner, monday)
        assertEquals(5, week.lessons.getValue(connection.pupils.last().studentId).size)
    }

    @Test
    fun aRoleSeesOnlyItsOwnPlanUntilAllowedToSeeAll() {
        val w = world()
        val bartosz = w.timetable.week(w.owner, monday).students.first { it.name == "Bartosz" }
        w.timetable.linkStudent(w.owner, bartosz.id, w.accounts.me(w.user).id)

        // The lowest role (User) sees only the student linked to their account.
        assertEquals(listOf("Bartosz"), w.timetable.week(w.user, monday).students.map { it.name })

        // Allowed to see all: every student shows.
        val user = w.accounts.permissionTable(w.owner).roles.single { it.name == "User" }
        w.accounts.setPermission(w.owner, user.id, Action.TIMETABLE_VIEW_ALL, Mode.ALLOWED)
        assertEquals(listOf("Bartosz", "Filip"), w.timetable.week(w.user, monday).students.map { it.name })

        // Denied both "own" and "all": no access to the timetable at all.
        w.accounts.setPermission(w.owner, user.id, Action.TIMETABLE_VIEW_ALL, Mode.DENIED)
        w.accounts.setPermission(w.owner, user.id, Action.TIMETABLE_VIEW_SELF, Mode.DENIED)
        assertEquals("forbidden", assertFailsWith<ApiException> { w.timetable.week(w.user, monday) }.code)
    }

    @Test
    fun theSamePupilStaysTheSameStudent() {
        val w = world()
        val bartosz = w.timetable.week(w.owner, monday).students.first { it.name == "Bartosz" }
        w.timetable.linkStudent(w.owner, bartosz.id, w.accounts.me(w.user).id)
        w.timetable.addActivities(w.owner, request(bartosz.id))

        // A second parent account sees Bartosz too and a pupil of the same first name from another school.
        w.diary.pupil("u2|3", "Filip", "Nowak", "1a")
        val second = w.timetable.connectDiary(w.owner, DiaryKind.VULCAN, listOf("u1|7", "u2|3"))
        assertEquals(listOf("Bartosz", "Filip N."), second.pupils.map { it.name })
        assertEquals(bartosz.id, second.pupils.first().studentId)
        assertEquals(2, w.timetable.diaryConnections(w.owner).size)
        val week = w.timetable.week(w.owner, monday)
        assertEquals(listOf("Bartosz", "Filip", "Filip N."), week.students.map { it.name })
        assertEquals(w.accounts.me(w.user).id, week.students.first().memberId)
        assertEquals(1, week.activities.size)
    }

    @Test
    fun connectingAgainReplacesTheOldConnection() {
        val w = world()
        val old = w.timetable.diaryConnections(w.owner).single()
        w.timetable.connectDiary(w.owner, DiaryKind.VULCAN, listOf("u1|7", "u1|8"))
        val now = w.timetable.diaryConnections(w.owner).single()
        assertTrue(old.id != now.id)
        assertEquals(listOf("u1|7,u1|8"), w.diary.disconnected)
    }

    @Test
    fun updatesReplaceLessonsAndKeepErrors() {
        val w = world()
        val connection = w.timetable.diaryConnections(w.owner).single()
        val filip = connection.pupils.last().studentId
        w.diary.lessons["u1|8"] = listOf(
            DiaryLesson(monday, 1, "Matematyka", "21", "Anna Nowak", "08:00-08:45", LessonChange(ChangeKind.SUBSTITUTION, "Nieobecność nauczyciela")),
        )
        w.timetable.syncDiary(w.owner, connection.id)
        val lessons = w.timetable.week(w.owner, monday).lessons.getValue(filip)
        assertEquals(1, lessons.size)
        assertEquals(ChangeKind.SUBSTITUTION, lessons.single().change?.kind)

        w.diary.failing = true
        val failed = w.timetable.syncDiary(w.owner, connection.id)
        assertEquals("Brak połączenia z eduVulcan.", failed.lastError)
        assertEquals(1, w.timetable.week(w.owner, monday).lessons.getValue(filip).size)
        assertEquals("diary", assertFailsWith<ApiException> { w.timetable.connectDiary(w.owner, DiaryKind.VULCAN, listOf("u1|7")) }.code)

        w.diary.failing = false
        w.timetable.disconnectDiary(w.owner, connection.id)
        assertTrue(w.timetable.diaryConnections(w.owner).isEmpty())
        // The students and their lessons stay.
        assertEquals(1, w.timetable.week(w.owner, monday).lessons.getValue(filip).size)
    }
}
