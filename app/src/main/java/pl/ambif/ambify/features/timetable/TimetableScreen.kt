package pl.ambif.ambify.features.timetable

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.modules.timetable.TimetableCache
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.CodeDialog
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.Recurrences
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.TimetableWeek
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.navigation.SettingsNavIcon
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private enum class TimetableView { DAY, WEEK }

/** What the timetable shows: the plan, the list of extra activities, or the form of one. */
private sealed interface Page {
    data object Plan : Page
    data object Extras : Page
    data class Form(val activity: ExtraActivity?) : Page
    data object Settings : Page
    /** [from]: where "<" goes back to (the plan or the timetable settings): one step. */
    data class Connect(val from: Page) : Page
}

internal val Substitution = Color(0xFFF2C27B)
private val StudentColors = listOf(Color(0xFF8EC3F0), Color(0xFF9AD7A4), Color(0xFFE3A8C0), Color(0xFFF2C27B), Color(0xFFB8A6F0))

/**
 * Plan lekcji: lessons from the school diary (eduVulcan, read by the server, connected by an Owner/Admin in the
 * app) and extra activities, per student, for a day or a week. Who may add activities for whom, and remind whom,
 * follows the community's permission table.
 */
@Composable
fun TimetableScreen(resetKey: Int = 0) {
    val session = LocalAppSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val today = LocalDate.now()
    var monday by rememberSaveable { mutableStateOf(Recurrences.mondayOf(today.coerceSchoolDay()).toString()) }
    var day by rememberSaveable { mutableStateOf(today.coerceSchoolDay().toString()) }
    var studentId by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf(TimetableView.DAY) }
    var page by remember { mutableStateOf<Page>(Page.Plan) }
    var week by remember { mutableStateOf<TimetableWeek?>(null) }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lesson by remember { mutableStateOf<Pair<Student, Lesson>?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                notice = done
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    // Shown from the phone's copy when the server cannot be reached: when that copy was saved.
    var offlineSince by remember { mutableStateOf<Long?>(null) }
    val communityId = me.communityId.orEmpty()

    suspend fun reload() {
        try {
            val fresh = api.timetable(monday)
            week = fresh
            offlineSince = null
            TimetableCache.save(context, communityId, fresh)
        } catch (e: ServerError) {
            val saved = if (e.code == "network") TimetableCache.load(context, communityId, monday) else null
            val shownWeek = week
            if (saved == null && e.code == "network" && shownWeek != null && shownWeek.monday != monday) {
                // A week outside the copy: stay on the week shown rather than show it under another date.
                val back = LocalDate.parse(shownWeek.monday)
                day = back.plusDays(LocalDate.parse(day).dayOfWeek.value - 1L).toString()
                monday = shownWeek.monday
                throw ServerError("network", "Bez połączenia z serwerem — tego tygodnia nie ma w kopii na telefonie.")
            }
            if (saved == null) throw e
            week = saved.week
            offlineSince = saved.savedAt
        }
    }

    LaunchedEffect(monday) { act { reload() } }
    // The offline copy: the other weeks of the range, quietly in the background.
    LaunchedEffect(Unit) {
        TimetableCache.range().filter { it.toString() != monday }.forEach { other ->
            runCatching { TimetableCache.save(context, communityId, api.timetable(other.toString())) }
        }
    }
    LaunchedEffect(Unit) {
        act { members = api.family().members }
        session.refreshReminders()
    }
    LaunchedEffect(resetKey) { if (resetKey > 0) page = Page.Plan }
    BackHandler(enabled = page != Page.Plan) { page = if (page is Page.Form) Page.Extras else Page.Plan }
    val canLink = me.can(Action.TIMETABLE_LINK_VULCAN)

    val current = week
    val students = current?.students.orEmpty()
    val selected = students.firstOrNull { it.id == studentId } ?: students.firstOrNull()

    when (val shown = page) {
        is Page.Connect -> DiaryConnectScreen(members, onBack = { page = shown.from }) {
            page = Page.Plan
            notice = null
            scope.launch { runCatching { reload() } }
        }
        Page.Settings -> TimetableSettingsScreen(
            students = students,
            members = members,
            onBack = { page = Page.Plan },
            onConnect = { page = Page.Connect(from = Page.Settings) },
            onChanged = { scope.launch { runCatching { reload() } } },
        )
        is Page.Form -> ExtraFormScreen(
            me = me,
            students = students,
            members = members,
            activity = shown.activity,
            preselected = selected?.id,
            onBack = { page = Page.Extras },
            onSaved = { message ->
                notice = message
                page = Page.Extras
                scope.launch {
                    runCatching { reload() }
                    session.refreshReminders()
                }
            },
        )
        Page.Extras -> ExtrasScreen(
            week = current,
            me = me,
            notice = notice,
            onBack = { page = Page.Plan },
            onAdd = { page = Page.Form(null) },
            onEdit = { page = Page.Form(it) },
            onDelete = { activity ->
                dialog = AccountDialog.Confirm("Usunąć „${activity.name}”?", "Usuń") {
                    act("Usunięto „${activity.name}”.") {
                        api.deleteExtra(activity.id)
                        reload()
                        session.refreshReminders()
                    }
                }
            },
        )
        Page.Plan -> Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) { ScreenTitle("Plan lekcji", "", onBack = null) }
                    if (current?.diaryConnected == true) ViewSwitch(view) { view = it }
                    if (canLink) {
                        IconButton(onClick = { page = Page.Settings }) {
                            CompositionLocalProvider(LocalContentColor provides Luxury.GoldText) { SettingsNavIcon() }
                        }
                    }
                }
                offlineSince?.let { Text("Bez połączenia z serwerem · plan z ${savedTime(it)}", color = Luxury.TextMuted, fontSize = 13.sp) }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
                if (current == null) return@Column
                if (!current.diaryConnected) {
                    NoTimetable(
                        week = current,
                        canLink = canLink,
                        communityName = me.familyName.orEmpty(),
                        onConnect = { page = Page.Connect(from = Page.Plan) },
                        onExtras = if (students.isEmpty()) null else ({ page = Page.Extras }),
                    )
                    return@Column
                }
                StudentTabs(students, selected?.id) { studentId = it }
                val student = selected ?: return@Column
                WeekSwitch(LocalDate.parse(current.monday), today) { shift ->
                    val next = LocalDate.parse(monday).plusWeeks(shift.toLong())
                    monday = next.toString()
                    day = next.plusDays(LocalDate.parse(day).dayOfWeek.value - 1L).toString()
                }
                if (view == TimetableView.DAY) {
                    DayPicker(LocalDate.parse(current.monday), LocalDate.parse(day), today) { day = it.toString() }
                    DayCard(current, student, LocalDate.parse(day), today) { lesson = student to it }
                } else {
                    WeekGrid(current, student, today, onLesson = { lesson = student to it }, onDay = {
                        day = it.toString()
                        view = TimetableView.DAY
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { page = Page.Extras },
                        border = BorderStroke(1.dp, Luxury.GoldLink),
                        shape = RoundedCornerShape(23.dp),
                        modifier = Modifier.height(46.dp),
                    ) { Text("+  Zajęcia dodatkowe", color = Luxury.GoldText) }
                }
            }
        }
    }

    lesson?.let { (student, picked) -> LessonPopup(student, picked) { lesson = null } }
    when (val open = dialog) {
        is AccountDialog.Code -> CodeDialog(open, onShare = null, onDismiss = { dialog = null })
        is AccountDialog.Confirm -> ConfirmDialog(open, onDismiss = { dialog = null })
        else -> Unit
    }
}

internal fun Me.can(action: Action) = permissions[action] == Mode.ALLOWED
internal fun Me.mode(action: Action) = permissions[action] ?: Mode.DENIED

/** "1.11, 18:05" (or "dziś, 18:05"): when the offline copy of a week was saved. */
private fun savedTime(at: Long): String {
    val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
    val day = if (time.toLocalDate() == LocalDate.now()) "dziś" else time.toLocalDate().dayMonth()
    return "$day, ${time.hour}:%02d".format(time.minute)
}

/** Saturday and Sunday show the coming Monday (there are no lessons at the weekend). */
private fun LocalDate.coerceSchoolDay(): LocalDate = when (dayOfWeek.value) {
    6 -> plusDays(2)
    7 -> plusDays(1)
    else -> this
}

internal fun Student.color(index: Int): Color =
    avatar?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: StudentColors[index % StudentColors.size]

/** "08:00-08:45" → 8:00 and 8:45; null when Vulcan sent something else. */
internal fun Lesson.span(): Pair<LocalTime, LocalTime>? = runCatching {
    val (from, to) = time.split("-", "–").map { LocalTime.parse(it.trim().padStart(5, '0')) }
    from to to
}.getOrNull()

internal fun LocalTime.short(): String = "$hour:%02d".format(minute)
internal fun String.shortTime(): String = runCatching { LocalTime.parse(this).short() }.getOrDefault(this)

internal val DayShort = listOf("Pn", "Wt", "Śr", "Czw", "Pt", "So", "Nd")
internal val DayLong = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
private val MonthGenitive = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
internal fun LocalDate.dayMonth() = "$dayOfMonth.$monthValue"

@Composable
private fun ViewSwitch(view: TimetableView, onPick: (TimetableView) -> Unit) {
    Row(
        modifier = Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).border(1.dp, Color(0xFF5A5A60), RoundedCornerShape(19.dp)).padding(3.dp),
    ) {
        TimetableView.entries.forEach { option ->
            val on = option == view
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier)
                    .clickable { onPick(option) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (option == TimetableView.DAY) "Dzień" else "Tydzień",
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) Luxury.Surface else Color(0xFFC9C9CF),
                )
            }
        }
    }
}

@Composable
private fun StudentTabs(students: List<Student>, selected: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        students.forEach { student ->
            val on = student.id == selected
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, if (on) Luxury.GoldLink else Color(0xFF3A3A40), RoundedCornerShape(22.dp))
                    .then(if (on) Modifier.background(Luxury.GoldBase.copy(alpha = 0.14f)) else Modifier)
                    .clickable { onPick(student.id) }
                    .padding(start = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonAvatar(student.name, student.avatar, size = 30.dp)
                Text(student.name, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) Luxury.GoldLight else Color(0xFFC9C9CF))
            }
        }
    }
}

@Composable
private fun WeekSwitch(monday: LocalDate, today: LocalDate, onShift: (Int) -> Unit) {
    val friday = monday.plusDays(4)
    val label = buildString {
        append(if (monday.month == friday.month) "${monday.dayOfMonth}–${friday.dayOfMonth}" else "${monday.dayOfMonth} ${MonthGenitive[monday.monthValue - 1]} – ${friday.dayOfMonth}")
        append(" ${MonthGenitive[friday.monthValue - 1]}")
        if (Recurrences.mondayOf(today) == monday) append(" · ten tydzień")
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(AppIcons.Back, contentDescription = "Poprzedni tydzień", tint = Luxury.GoldText) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = { onShift(1) }) { Icon(AppIcons.Forward, contentDescription = "Następny tydzień", tint = Luxury.GoldText) }
    }
}

@Composable
private fun DayPicker(monday: LocalDate, day: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..4).forEach { offset ->
            val date = monday.plusDays(offset.toLong())
            val on = date == day
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (on) Modifier.background(Luxury.GoldFill)
                        else Modifier.background(Color(0xFF1C1C20)).border(1.dp, if (date == today) Luxury.GoldDeep else Color(0xFF3A3A40), RoundedCornerShape(14.dp)),
                    )
                    .clickable { onPick(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (on) Luxury.Surface else Color(0xFFC9C9CF)
                Text(DayShort[offset], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                Text(date.dayMonth(), fontSize = 12.sp, color = color)
            }
        }
    }
}

/** Activities of [student] on [date], in order of their start. */
internal fun TimetableWeek.extrasOn(student: Student, date: LocalDate): List<ExtraActivity> {
    val byId = activities.associateBy { it.id }
    return occurrences.filter { it.date == date.toString() }
        .mapNotNull { byId[it.activityId] }
        .filter { it.studentId == student.id }
        .sortedBy { it.startTime }
}

@Composable
private fun DayCard(week: TimetableWeek, student: Student, date: LocalDate, today: LocalDate, onLesson: (Lesson) -> Unit) {
    val lessons = week.lessons[student.id].orEmpty().filter { it.date == date.toString() }.sortedBy { it.position }
    val now = LocalTime.now()
    LuxuryCard(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp), spacing = 2.dp) {
        val spans = lessons.mapNotNull { it.span() }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
            LuxurySectionTitle(
                "${DayLong[date.dayOfWeek.value - 1].uppercase()} · ${lessons.size} ${lessonsWord(lessons.size)}",
                modifier = Modifier.weight(1f),
            )
            if (spans.isNotEmpty()) Text("${spans.first().first.short()}–${spans.last().second.short()}", fontSize = 12.sp, color = Luxury.TextMuted)
        }
        if (lessons.isEmpty()) {
            Text("Brak lekcji w tym dniu.", style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        lessons.forEachIndexed { index, lesson ->
            val span = lesson.span()
            val past = date.isBefore(today) || (date == today && span != null && now.isAfter(span.second))
            val running = date == today && span != null && !now.isBefore(span.first) && !now.isAfter(span.second) && !lesson.cancelled
            if (running) RunningLesson(lesson, span!!, now) { onLesson(lesson) }
            else LessonRow(lesson, past, divider = index < lessons.lastIndex) { onLesson(lesson) }
        }
        LuxurySectionTitle("PO LEKCJACH", modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
        val extras = week.extrasOn(student, date)
        val color = student.color(week.students.indexOf(student))
        if (extras.isEmpty()) {
            ExtraRow("Brak zajęć dodatkowych", "dodasz je przyciskiem poniżej", Luxury.Disabled, Color.White.copy(alpha = 0.04f), marker = false)
        }
        extras.forEach { activity ->
            val reminders = if (activity.reminders.isEmpty()) "" else " · przypomnienia: ${activity.reminders.size}"
            ExtraRow(activity.name, "${activity.startTime.shortTime()}–${activity.endTime.shortTime()} · dodał(a): ${activity.createdByName}$reminders", color, color.copy(alpha = 0.12f))
        }
    }
}

private fun lessonsWord(count: Int) = when {
    count == 1 -> "LEKCJA"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "LEKCJE"
    else -> "LEKCJI"
}

@Composable
private fun LessonRow(lesson: Lesson, past: Boolean, divider: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${lesson.position}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Luxury.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.subject,
                    fontSize = 15.sp,
                    color = if (past || lesson.cancelled) Luxury.Disabled else Luxury.Text,
                    textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
                )
                Text(lessonDetails(lesson), fontSize = 12.sp, color = Luxury.TextMuted)
            }
            val change = lesson.change
            when {
                change != null -> Text(changeLabel(change.kind), fontSize = 11.sp, color = changeColor(change.kind))
                past -> Text("odbyła się", fontSize = 11.sp, color = Luxury.TextMuted)
            }
        }
        if (divider) LuxuryDivider()
    }
}

private fun lessonDetails(lesson: Lesson): String {
    val span = lesson.span()
    val time = span?.let { "${it.first.short()}–${it.second.short()}" } ?: lesson.time
    return if (lesson.room.isBlank()) time else "$time · sala ${lesson.room}"
}

@Composable
private fun RunningLesson(lesson: Lesson, span: Pair<LocalTime, LocalTime>, now: LocalTime, onClick: () -> Unit) {
    val total = (span.second.toSecondOfDay() - span.first.toSecondOfDay()).coerceAtLeast(1)
    val done = (now.toSecondOfDay() - span.first.toSecondOfDay()).toFloat() / total
    val left = (span.second.toSecondOfDay() - now.toSecondOfDay()) / 60
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Color(0xFF2C2818))
            .border(1.5.dp, Luxury.GoldBase, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${lesson.position}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldLight, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.subject, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(lessonDetails(lesson), fontSize = 12.sp, color = Color(0xFFC9C9CF))
            }
            Text(
                "TERAZ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Luxury.Surface,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Luxury.GoldBase).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        LinearProgressIndicator(
            progress = { done.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = Luxury.GoldBase,
            trackColor = Color.White.copy(alpha = 0.12f),
            drawStopIndicator = {},
        )
        Text("jeszcze $left min", fontSize = 11.sp, color = Luxury.GoldText)
    }
}

@Composable
private fun ExtraRow(name: String, details: String, color: Color, tint: Color, marker: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(tint).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The "+" marks an extra activity; the empty row has none, so only the space stays (the texts line up).
        Text(if (marker) "+" else "", fontSize = 18.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp)
            Text(details, fontSize = 12.sp, color = Luxury.TextMuted)
        }
    }
}

@Composable
private fun WeekGrid(week: TimetableWeek, student: Student, today: LocalDate, onLesson: (Lesson) -> Unit, onDay: (LocalDate) -> Unit) {
    val monday = LocalDate.parse(week.monday)
    val lessons = week.lessons[student.id].orEmpty()
    val byDay = lessons.groupBy { it.date }
    val positions = lessons.map { it.position }.distinct().sorted()
    val now = LocalTime.now()
    val color = student.color(week.students.indexOf(student))
    LuxuryCard(contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp), spacing = 5.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Spacer(Modifier.width(30.dp))
            (0..4).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                val on = date == today
                Text(
                    DayShort[offset],
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (on) Luxury.Surface else Luxury.GoldText,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier)
                        .clickable { onDay(date) }
                        .padding(vertical = 4.dp),
                )
            }
        }
        if (positions.isEmpty()) Text("Brak lekcji w tym tygodniu.", style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted)
        positions.forEach { position ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                val start = lessons.firstOrNull { it.position == position }?.span()?.first
                Column(modifier = Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$position", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Luxury.TextMuted)
                    start?.let { Text(it.short(), fontSize = 9.sp, color = Luxury.TextMuted) }
                }
                (0..4).forEach { offset ->
                    val date = monday.plusDays(offset.toLong())
                    val lesson = byDay[date.toString()]?.firstOrNull { it.position == position }
                    Box(modifier = Modifier.weight(1f).height(44.dp)) {
                        if (lesson != null) {
                            val span = lesson.span()
                            val running = date == today && span != null && !now.isBefore(span.first) && !now.isAfter(span.second) && !lesson.cancelled
                            val past = date.isBefore(today) || (date == today && span != null && now.isAfter(span.second))
                            WeekCell(lesson, running, past) { onLesson(lesson) }
                        }
                    }
                }
            }
        }
        LuxuryDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("+", fontSize = 16.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(30.dp).padding(top = 10.dp))
            (0..4).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.extrasOn(student, date).forEach { activity ->
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.14f)).clickable { onDay(date) }.padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(activity.name.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(activity.startTime.shortTime(), fontSize = 9.sp, color = Color(0xFFC9C9CF))
                        }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
        Legend("trwa teraz", Modifier.background(Color(0xFF2C2818)).border(1.5.dp, Luxury.GoldBase, RoundedCornerShape(3.dp)))
        Legend("zajęcia dodatkowe", Modifier.background(color.copy(alpha = 0.3f)))
    }
}

@Composable
private fun WeekCell(lesson: Lesson, running: Boolean, past: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (running) Color(0xFF2C2818) else Color(0xFF26262B))
            .then(if (running) Modifier.border(1.5.dp, Luxury.GoldBase, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            abbreviation(lesson.subject),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                running -> Luxury.GoldLight
                past || lesson.cancelled -> Luxury.Disabled
                else -> Luxury.Text
            },
            textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
        )
        val change = lesson.change
        when {
            running -> Text("teraz", fontSize = 9.sp, color = Luxury.GoldText)
            change != null -> Text(changeShort(change.kind), fontSize = 9.sp, color = changeColor(change.kind), maxLines = 1)
            else -> Text(lesson.room, fontSize = 9.sp, color = Luxury.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Język angielski" → ANG, "Wychowanie fizyczne" → WF, "Matematyka" → MAT. */
internal fun abbreviation(subject: String): String {
    val words = subject.split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.first().lowercase() == "język" && words.size > 1 -> words[1].take(3)
        words.size >= 2 && words.all { it.first().isLetter() } && words.first().length > 4 && words[1].length > 4 -> words.take(2).joinToString("") { it.take(1) }
        else -> words.first().take(3)
    }.uppercase()
}

@Composable
private fun Legend(label: String, swatch: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).then(swatch))
        Text(label, fontSize = 11.sp, color = Luxury.TextMuted)
    }
}

@Composable
private fun LessonPopup(student: Student, lesson: Lesson, onDismiss: () -> Unit) {
    val date = LocalDate.parse(lesson.date)
    LuxuryPopup(onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Luxury.GoldFill), contentAlignment = Alignment.Center) {
                Text("${lesson.position}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Luxury.Surface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleLarge)
                Text("${DayLong[date.dayOfWeek.value - 1]} ${date.dayMonth()} · ${lessonDetails(lesson).substringBefore(" · ")}", fontSize = 13.sp, color = Luxury.GoldText)
            }
        }
        lesson.change?.let { change ->
            val color = changeColor(change.kind)
            Text(
                listOf(changeTitle(change.kind), change.note).filter { it.isNotBlank() }.joinToString(" — "),
                fontSize = 13.sp,
                color = color,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        PopupRow("Sala") { Text(lesson.room.ifBlank { "—" }, fontSize = 15.sp) }
        LuxuryDivider()
        PopupRow("Nauczyciel") { Text(lesson.teacher.ifBlank { "—" }, fontSize = 15.sp) }
        LuxuryDivider()
        PopupRow("Uczeń") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonAvatar(student.name, student.avatar, size = 26.dp)
                Text(student.name, fontSize = 15.sp)
            }
        }
        Text(
            "Plan i zastępstwa z dziennika eduVulcan, odświeżane codziennie o 18:00. Sprawdziany i zadania — moduł Szkoła.",
            fontSize = 11.sp,
            color = Luxury.Disabled,
        )
    }
}

@Composable
private fun ColumnScope.PopupRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = 12.sp, color = Luxury.TextMuted, modifier = Modifier.width(86.dp))
        value()
    }
}

internal val Lesson.cancelled get() = change?.kind == ChangeKind.CANCELLED

private fun changeLabel(kind: ChangeKind) = when (kind) {
    ChangeKind.CANCELLED -> "odwołana"
    ChangeKind.SUBSTITUTION -> "zastępstwo"
    ChangeKind.CHANGED -> "zmiana"
}

private fun changeShort(kind: ChangeKind) = when (kind) {
    ChangeKind.CANCELLED -> "odwoł."
    ChangeKind.SUBSTITUTION -> "zast."
    ChangeKind.CHANGED -> "zmiana"
}

private fun changeTitle(kind: ChangeKind) = when (kind) {
    ChangeKind.CANCELLED -> "Lekcja odwołana"
    ChangeKind.SUBSTITUTION -> "Zastępstwo"
    ChangeKind.CHANGED -> "Zmiana w planie"
}

private fun changeColor(kind: ChangeKind) = if (kind == ChangeKind.CANCELLED) Color(0xFFFF8A80) else Substitution

/** All extra activities of the community, by student, with Edytuj / Usuń. */
@Composable
private fun ExtrasScreen(
    week: TimetableWeek?,
    me: Me,
    notice: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ExtraActivity) -> Unit,
    onDelete: (ExtraActivity) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTitle("Zajęcia dodatkowe", "kto może dodawać — wg uprawnień społeczności", onBack)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
            val current = week ?: return@Column
            current.students.forEachIndexed { index, student ->
                val activities = current.activities.filter { it.studentId == student.id }.sortedBy { it.name }
                val color = student.color(index)
                LuxuryCard(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), spacing = 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(student.name, student.avatar, size = 28.dp)
                        LuxurySectionTitle(student.name.uppercase())
                    }
                    if (activities.isEmpty()) Text("Brak zajęć dodatkowych.", fontSize = 14.sp, color = Luxury.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                    activities.forEachIndexed { i, activity ->
                        if (i > 0) LuxuryDivider()
                        ExtraItem(activity, color, onEdit = { onEdit(activity) }, onDelete = { onDelete(activity) })
                    }
                }
            }
            val canAdd = me.mode(Action.TIMETABLE_EXTRA_OTHERS) != Mode.DENIED ||
                (me.mode(Action.TIMETABLE_EXTRA_SELF) != Mode.DENIED && current.students.any { it.memberId == me.id })
            if (canAdd) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Luxury.GoldFill).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Text("+  Dodaj zajęcia dodatkowe", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Luxury.Surface) }
            } else {
                Text(
                    "Twoja rola nie może dodawać zajęć. Jeśli plan ucznia ma być Twój, poproś Ownera lub Admina o połączenie go z Twoim kontem.",
                    fontSize = 13.sp,
                    color = Luxury.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ExtraItem(activity: ExtraActivity, color: Color, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val ended = activity.recurrence.endDate?.let { LocalDate.parse(it).isBefore(LocalDate.now()) } == true
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(if (ended) Color(0xFF5A5A60) else color))
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(activity.name, fontSize = 16.sp, color = if (ended) Luxury.Disabled else Luxury.Text)
            Text(
                "${describe(activity.recurrence, LocalDate.parse(activity.startDate))} · ${activity.startTime.shortTime()}–${activity.endTime.shortTime()}",
                fontSize = 12.sp,
                color = Color(0xFFC9C9CF),
            )
            val details = listOfNotNull(
                activity.place,
                if (activity.addToCalendar) "w kalendarzu" else null,
                if (activity.reminders.isNotEmpty()) "przypomnienia: ${activity.reminders.size}" else null,
                if (ended) "zakończone" else null,
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "), fontSize = 12.sp, color = Luxury.TextMuted)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(AppIcons.More, contentDescription = "Opcje: ${activity.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edytuj") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Usuń") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
