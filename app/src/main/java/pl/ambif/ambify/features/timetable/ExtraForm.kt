package pl.ambif.ambify.features.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.ExtraActivityRequest
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.MonthMode
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.Reminder
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** The community's own calendar; Google calendars join the list in Stage 6. */
private const val COMMUNITY_CALENDAR = "community"

/**
 * A new extra activity (for one or more students at once) or a change of [activity]. Students offered: everyone
 * when my role may add for others (at once or after approval), else only the student linked with my account.
 */
@Composable
internal fun ExtraFormScreen(
    me: Me,
    students: List<Student>,
    members: List<Member>,
    activity: ExtraActivity?,
    preselected: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val api = LocalAppSession.current.api
    val scope = rememberCoroutineScope()
    val others = me.mode(Action.TIMETABLE_EXTRA_OTHERS)
    val allowed = students.filter { it.memberId == me.id || others != Mode.DENIED }
    var chosen by remember {
        mutableStateOf(
            activity?.let { setOf(it.studentId) }
                ?: setOfNotNull(preselected?.takeIf { id -> allowed.any { it.id == id } } ?: allowed.firstOrNull()?.id),
        )
    }
    var name by rememberSaveable { mutableStateOf(activity?.name.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(activity?.place.orEmpty()) }
    var date by remember { mutableStateOf(activity?.startDate?.let(LocalDate::parse) ?: LocalDate.now()) }
    var start by remember { mutableStateOf(activity?.startTime?.let(LocalTime::parse) ?: LocalTime.of(16, 0)) }
    var end by remember { mutableStateOf(activity?.endTime?.let(LocalTime::parse) ?: LocalTime.of(17, 0)) }
    var rule by remember { mutableStateOf(activity?.recurrence ?: Recurrence(Frequency.WEEKLY, skipSchoolFree = true)) }
    var reminders by remember { mutableStateOf(activity?.reminders.orEmpty()) }
    var addToCalendar by remember { mutableStateOf(activity?.addToCalendar ?: false) }
    var calendars by remember { mutableStateOf(activity?.calendars?.toSet() ?: setOf(COMMUNITY_CALENDAR)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Default recipient of a new reminder: the people of the activity (students with an account), else me.
    val defaultRecipients = students.filter { it.id in chosen }.mapNotNull { it.memberId }.ifEmpty { listOf(me.id) }

    fun save() {
        if (name.isBlank()) return run { notice = "Wpisz nazwę zajęć." }
        if (chosen.isEmpty()) return run { notice = "Wybierz, dla kogo są zajęcia." }
        if (!end.isAfter(start)) return run { notice = "Koniec musi być po początku." }
        val request = ExtraActivityRequest(
            studentIds = chosen.toList(),
            name = name.trim(),
            place = place.trim().ifBlank { null },
            startDate = date.toString(),
            startTime = start.toString(),
            endTime = end.toString(),
            recurrence = rule,
            reminders = reminders,
            addToCalendar = addToCalendar,
            calendars = if (addToCalendar) calendars.toList() else emptyList(),
        )
        saving = true
        scope.launch {
            try {
                if (activity != null) {
                    api.updateExtra(activity.id, request)
                    onSaved("Zapisano zmiany „${request.name}”.")
                } else {
                    val result = api.addExtras(request)
                    onSaved(result.queued?.message ?: "Dodano „${request.name}”.")
                }
            } catch (e: ServerError) {
                notice = e.message
            } finally {
                saving = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle(if (activity == null) "Nowe zajęcia dodatkowe" else "Zmień zajęcia", "", onBack)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }

            LuxuryCard(contentPadding = PaddingValues(14.dp), spacing = 10.dp) {
                LuxurySectionTitle("DLA KOGO")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (activity == null) allowed else students.filter { it.id == activity.studentId }).forEach { student ->
                        val on = student.id in chosen
                        PersonChip(student.name, student.avatar, on) {
                            if (activity == null) chosen = if (on) chosen - student.id else chosen + student.id
                        }
                    }
                }
                Text(
                    when (others) {
                        Mode.ALLOWED -> "Możesz dodawać zajęcia każdemu uczniowi."
                        Mode.APPROVAL -> "Zajęcia dla innych osób zapiszą się po zatwierdzeniu przez Admina lub Ownera."
                        Mode.DENIED -> "Twoja rola dodaje zajęcia tylko sobie (uczeń połączony z Twoim kontem)."
                    },
                    fontSize = 11.sp,
                    color = Luxury.Disabled,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa zajęć") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("Miejsce (opcjonalnie)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            LuxuryCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), spacing = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LuxurySectionTitle("KIEDY", modifier = Modifier.width(52.dp))
                    FieldButton("${DayShort[date.dayOfWeek.value - 1]} ${date.dayMonth()}", Modifier.weight(1f)) { picker = "date" }
                    FieldButton(start.short(), Modifier.width(70.dp)) { picker = "start" }
                    FieldButton(end.short(), Modifier.width(70.dp)) { picker = "end" }
                }
                LuxuryDivider()
                LinkRow("Powtarzanie", describe(rule, date)) { picker = "rule" }
            }

            LuxuryCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), spacing = 4.dp) {
                LuxurySectionTitle("PRZYPOMNIENIA")
                reminders.forEachIndexed { index, reminder ->
                    val member = members.firstOrNull { it.id == reminder.memberId }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { editing = index },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PersonAvatar(member?.name ?: "?", member?.avatar, size = 26.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                (if (reminder.kind == ReminderKind.ALARM) "Budzik" else "Powiadomienie") + " · " + (member?.name ?: "?"),
                                fontSize = 15.sp,
                                color = if (reminder.kind == ReminderKind.ALARM) Substitution else Luxury.Text,
                            )
                            Text(beforeLabel(reminder.minutesBefore) + " przed", fontSize = 12.sp, color = Luxury.TextMuted)
                        }
                        LuxuryOpenMark(size = 36.dp)
                    }
                    LuxuryDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable { editing = -1 },
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("+  Dodaj przypomnienie", color = Luxury.GoldText, fontSize = 15.sp) }
                if (others == Mode.DENIED || me.mode(Action.TIMETABLE_REMIND_OTHERS) == Mode.APPROVAL) {
                    Text("Przypomnienie dla innej osoby może wymagać zatwierdzenia.", fontSize = 11.sp, color = Luxury.Disabled)
                }
            }

            LuxuryCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), spacing = 6.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { addToCalendar = !addToCalendar },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = addToCalendar,
                        onCheckedChange = { addToCalendar = it },
                        colors = CheckboxDefaults.colors(checkedColor = Luxury.GoldButton, checkmarkColor = Luxury.Surface),
                    )
                    Text("Dodaj do kalendarza", fontSize = 15.sp)
                }
                if (addToCalendar) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val on = COMMUNITY_CALENDAR in calendars
                        Chip("${me.familyName ?: "Społeczność"} (AMBiFy)", on) {
                            calendars = if (on) calendars - COMMUNITY_CALENDAR else calendars + COMMUNITY_CALENDAR
                        }
                        Chip("Google — wkrótce", false, enabled = false) {}
                    }
                    Text("Kalendarz pokaże te zajęcia, gdy powstanie moduł Kalendarz (etap 6).", fontSize = 11.sp, color = Luxury.Disabled)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(if (saving) Brushless else Luxury.GoldFill)
                    .clickable(enabled = !saving) { save() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (activity == null && chosen.size > 1) "Zapisz dla ${chosen.size} osób" else "Zapisz",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Luxury.Surface,
                )
            }
        }
    }

    when (picker) {
        "date" -> DateDialog(date, onDismiss = { picker = null }) { date = it }
        "start" -> TimeDialog("Początek", start, onDismiss = { picker = null }) {
            val length = java.time.Duration.between(start, end)
            start = it
            if (!length.isNegative) end = it.plus(length).takeIf { e -> e.isAfter(it) } ?: end
        }
        "end" -> TimeDialog("Koniec", end, onDismiss = { picker = null }) { end = it }
        "rule" -> RecurrenceDialog(rule, date, onDismiss = { picker = null }) { rule = it }
    }
    editing?.let { index ->
        val existing = reminders.getOrNull(index)
        ReminderDialog(
            members = members,
            existing = existing,
            defaultRecipients = defaultRecipients,
            activityName = name.ifBlank { "Zajęcia" },
            onDismiss = { editing = null },
            onDelete = existing?.let { { reminders = reminders - it; editing = null } },
        ) { added ->
            reminders = if (existing != null) reminders.toMutableList().apply { removeAt(index); addAll(index, added) } else reminders + added
            editing = null
        }
    }
}

private val Brushless = androidx.compose.ui.graphics.SolidColor(Color(0xFF5A5A60))

/** "w 1. środę": the weekday after "w". */
private val DayAccusative = listOf("poniedziałek", "wtorek", "środę", "czwartek", "piątek", "sobotę", "niedzielę")

internal fun beforeLabel(minutes: Int) = when {
    minutes >= 1440 && minutes % 1440 == 0 -> if (minutes == 1440) "dzień" else "${minutes / 1440} dni"
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} godz."
    else -> "$minutes min"
}

/** "Co tydzień w pn, wt · do 5.01.2027 · pomija dni wolne" — what the rule means, in words. */
internal fun describe(rule: Recurrence, start: LocalDate): String {
    val every = rule.interval.coerceAtLeast(1)
    val base = when (rule.frequency) {
        Frequency.NONE -> return "Jednorazowo, ${start.dayMonth()}.${start.year}"
        Frequency.DAILY -> if (every == 1) "Codziennie" else "Co $every dni"
        Frequency.WEEKLY -> {
            val days = rule.weekdays.ifEmpty { listOf(start.dayOfWeek.value) }.sorted().joinToString(", ") { DayShort[it - 1].lowercase() }
            (if (every == 1) "Co tydzień" else "Co $every tyg.") + " w $days"
        }
        Frequency.MONTHLY -> (if (every == 1) "Co miesiąc" else "Co $every mies.") + when (rule.monthMode) {
            MonthMode.DAY_OF_MONTH -> " ${start.dayOfMonth}. dnia"
            MonthMode.NTH_WEEKDAY -> " w ${(start.dayOfMonth - 1) / 7 + 1}. ${DayAccusative[start.dayOfWeek.value - 1]}"
        }
        Frequency.YEARLY -> if (every == 1) "Co rok ${start.dayMonth()}" else "Co $every lata ${start.dayMonth()}"
    }
    val end = when {
        rule.endDate != null -> LocalDate.parse(rule.endDate).let { " · do ${it.dayMonth()}.${it.year}" }
        rule.count != null -> " · ${rule.count} razy"
        else -> " · bez końca"
    }
    return base + end + if (rule.skipSchoolFree) " · pomija dni wolne" else ""
}

@Composable
internal fun PersonChip(name: String, avatar: pl.ambif.ambify.shared.Avatar?, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .border(1.dp, if (on) Luxury.GoldLink else Color(0xFF3A3A40), RoundedCornerShape(21.dp))
            .then(if (on) Modifier.background(Luxury.GoldBase.copy(alpha = 0.14f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonAvatar(name, avatar, size = 26.dp)
        Text(name, fontSize = 15.sp, color = if (on) Luxury.GoldLight else Luxury.TextMuted)
    }
}

@Composable
internal fun Chip(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(17.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            color = when {
                on -> Luxury.Surface
                enabled -> Luxury.TextMuted
                else -> Luxury.Disabled
            },
        )
    }
}

@Composable
private fun FieldButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C20))
            .border(1.dp, Color(0xFF5A5A60), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 14.sp) }
}

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(value, fontSize = 12.sp, color = Luxury.TextMuted)
        }
        LuxuryOpenMark(size = 36.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, time: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** How often: quick choices like a phone alarm, or own rule like Outlook (every N units, days, end). */
@Composable
private fun RecurrenceDialog(initial: Recurrence, start: LocalDate, onDismiss: () -> Unit, onDone: (Recurrence) -> Unit) {
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val weekday = start.dayOfWeek.value
    val presets = listOf(
        "Nie powtarza się" to Recurrence(Frequency.NONE),
        "Codziennie" to Recurrence(Frequency.DAILY),
        "Co tydzień w ${DayShort[weekday - 1].lowercase()}" to Recurrence(Frequency.WEEKLY, weekdays = listOf(weekday)),
        "Co miesiąc ${start.dayOfMonth}. dnia" to Recurrence(Frequency.MONTHLY),
    ).map { (label, preset) -> label to preset.copy(skipSchoolFree = rule.skipSchoolFree) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Powtarzanie", style = MaterialTheme.typography.titleLarge)
        Text(describe(rule, start), fontSize = 13.sp, color = Luxury.GoldText)
        LuxurySectionTitle("SZYBKI WYBÓR")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { (label, preset) -> Chip(label, rule == preset) { rule = preset } }
        }
        LuxuryDivider()
        LuxurySectionTitle("WŁASNE")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Co", fontSize = 15.sp)
            Stepper(rule.interval, 1..99) { rule = rule.copy(interval = it, frequency = if (rule.frequency == Frequency.NONE) Frequency.WEEKLY else rule.frequency) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Frequency.DAILY to "dzień", Frequency.WEEKLY to "tydz.", Frequency.MONTHLY to "mies.", Frequency.YEARLY to "rok").forEach { (frequency, label) ->
                Chip(label, rule.frequency == frequency) { rule = rule.copy(frequency = frequency) }
            }
        }
        if (rule.frequency == Frequency.WEEKLY) {
            val days = rule.weekdays.ifEmpty { listOf(weekday) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayShort.forEachIndexed { index, label ->
                    val day = index + 1
                    val on = day in days
                    Box(modifier = Modifier.weight(1f)) {
                        Chip(label, on) {
                            val next = if (on) days - day else days + day
                            if (next.isNotEmpty()) rule = rule.copy(weekdays = next.sorted())
                        }
                    }
                }
            }
        }
        if (rule.frequency == Frequency.MONTHLY) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("dnia ${start.dayOfMonth}.", rule.monthMode == MonthMode.DAY_OF_MONTH) { rule = rule.copy(monthMode = MonthMode.DAY_OF_MONTH) }
                Chip("w ${(start.dayOfMonth - 1) / 7 + 1}. ${DayAccusative[weekday - 1]}", rule.monthMode == MonthMode.NTH_WEEKDAY) {
                    rule = rule.copy(monthMode = MonthMode.NTH_WEEKDAY)
                }
            }
        }
        if (rule.frequency != Frequency.NONE) {
            LuxuryDivider()
            LuxurySectionTitle("KOŃCZY SIĘ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Nigdy", rule.endDate == null && rule.count == null) { rule = rule.copy(endDate = null, count = null) }
                Chip(rule.endDate?.let { LocalDate.parse(it).let { d -> "Do ${d.dayMonth()}.${d.year}" } } ?: "Do dnia…", rule.endDate != null) { picking = true }
                Chip("Po ${rule.count ?: 10} razach", rule.count != null) { rule = rule.copy(endDate = null, count = rule.count ?: 10) }
            }
            rule.count?.let { count -> Stepper(count, 1..500) { rule = rule.copy(count = it) } }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { rule = rule.copy(skipSchoolFree = !rule.skipSchoolFree) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = rule.skipSchoolFree,
                onCheckedChange = { rule = rule.copy(skipSchoolFree = it) },
                colors = CheckboxDefaults.colors(checkedColor = Luxury.GoldButton, checkmarkColor = Luxury.Surface),
            )
            Text("Pomijaj ferie i dni wolne (dni bez lekcji w Vulcanie)", fontSize = 14.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
            TextButton(onClick = { onDone(rule); onDismiss() }) { Text("Gotowe") }
        }
    }
    if (picking) {
        DateDialog(rule.endDate?.let(LocalDate::parse) ?: start.plusMonths(3), onDismiss = { picking = false }) {
            rule = rule.copy(endDate = it.toString(), count = null)
        }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0xFF5A5A60), RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, color = if (enabled) Luxury.Text else Luxury.Disabled) }
}

/**
 * A reminder: for whom (one or more members; each gets it on their own phone), how (notification or alarm) and
 * when. Changing an existing one edits that person's reminder.
 */
@Composable
private fun ReminderDialog(
    members: List<Member>,
    existing: Reminder?,
    defaultRecipients: List<String>,
    activityName: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onDone: (List<Reminder>) -> Unit,
) {
    var who by remember { mutableStateOf(existing?.let { setOf(it.memberId) } ?: defaultRecipients.toSet()) }
    var kind by remember { mutableStateOf(existing?.kind ?: ReminderKind.NOTIFICATION) }
    var minutes by remember { mutableStateOf(existing?.minutesBefore ?: 15) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Przypomnienie", style = MaterialTheme.typography.titleLarge)
        LuxurySectionTitle("DLA KOGO")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { member ->
                val on = member.id in who
                PersonChip(member.name, member.avatar, on) {
                    who = if (existing != null) setOf(member.id) else if (on) who - member.id else who + member.id
                }
            }
        }
        Text("Domyślnie osoba z zajęć. Każdy wybrany dostaje przypomnienie na swoim telefonie.", fontSize = 11.sp, color = Luxury.Disabled)
        LuxurySectionTitle("JAK")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ReminderKind.NOTIFICATION to "Powiadomienie", ReminderKind.ALARM to "Budzik").forEach { (option, label) ->
                val on = kind == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (on) Luxury.GoldLink else Color(0xFF3A3A40), RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.background(Luxury.GoldBase.copy(alpha = 0.14f)) else Modifier)
                        .clickable { kind = option },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.sp, color = if (on) Luxury.GoldLight else Luxury.TextMuted) }
            }
        }
        Text(
            if (kind == ReminderKind.ALARM) "Dzwoni na pełnym ekranie, także przy wyciszonym telefonie — z drzemką 5 min."
            else "Zwykłe powiadomienie: „$activityName za ${beforeLabel(minutes)}”.",
            fontSize = 11.sp,
            color = Luxury.Disabled,
        )
        LuxurySectionTitle("KIEDY")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 15, 30, 45, 60, 120, 1440).forEach { option ->
                Chip(if (option == 1440) "dzień wcześniej" else beforeLabel(option), minutes == option) { minutes = option }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            onDelete?.let { delete ->
                TextButton(onClick = delete) { Text("Usuń", color = Luxury.TextMuted) }
                Box(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onDismiss) { Text("Anuluj") }
            TextButton(onClick = { if (who.isNotEmpty()) onDone(who.map { Reminder(it, kind, minutes) }) }, enabled = who.isNotEmpty()) {
                Text(if (existing == null) "Dodaj" else "Zapisz")
            }
        }
    }
}
