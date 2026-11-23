package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import pl.ambif.ambify.features.timetable.Substitution
import pl.ambif.ambify.features.timetable.cancelled
import pl.ambif.ambify.features.timetable.dayMonth
import pl.ambif.ambify.features.timetable.short
import pl.ambif.ambify.features.timetable.shortTime
import pl.ambif.ambify.features.timetable.span
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.shared.WidgetSlot
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val CancelledColor = Color(0xFFFF8A80)
private val ChoiceOutline = Color(0xFF3A3A40)

private val dayNames = mapOf(
    DayOfWeek.MONDAY to "pn.", DayOfWeek.TUESDAY to "wt.", DayOfWeek.WEDNESDAY to "śr.", DayOfWeek.THURSDAY to "czw.",
    DayOfWeek.FRIDAY to "pt.", DayOfWeek.SATURDAY to "sob.", DayOfWeek.SUNDAY to "niedz.",
)

/** "DZIŚ", "JUTRO · WT.", "CZW. 27.11". */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "DZIŚ"
    today.plusDays(1) -> "JUTRO · ${dayNames[date.dayOfWeek]!!.uppercase()}"
    else -> "${dayNames[date.dayOfWeek]!!.uppercase()} ${date.dayMonth()}"
}

private fun TimetableData.chosen(widget: DashboardWidget): List<Student> =
    widget.people.mapNotNull { id -> students.firstOrNull { it.id == id } }.ifEmpty { students }

/** Every child's extra activities on [date], by start. */
private fun TimetableData.extrasOn(people: List<Student>, date: LocalDate): List<Pair<Student, ExtraActivity>> =
    people.flatMap { student -> extras(student, date).map { student to it } }.sortedBy { it.second.startTime }

/** The days (today and later, in the weeks the Dashboard has) with any extra activity. */
private fun TimetableData.extraDays(people: List<Student>, today: LocalDate): List<Pair<LocalDate, List<Pair<Student, ExtraActivity>>>> =
    (0L..13L).map { today.plusDays(it) }.map { it to extrasOn(people, it) }.filter { it.second.isNotEmpty() }

/**
 * "Zajęcia dodatkowe" (board 15 A): the chosen children's activities today and the next days with any (Small:
 * one day, Medium: two, Large: three). Nothing today: "Dziś bez zajęć · najbliższe: …".
 */
@Composable
internal fun ExtrasWidget(
    widget: DashboardWidget,
    data: TimetableData?,
    now: LocalDateTime,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val half = widget.slot != WidgetSlot.FULL
    val today = now.toLocalDate()
    LuxuryCard(modifier = modifier, contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 10.dp), spacing = 0.dp) {
        WidgetHeader(module = if (half) "Zajęcia" else "Zajęcia dodatkowe", name = null, interactive = interactive, onSettings = onSettings, onOpen = onOpen, openLabel = "Otwórz Plan lekcji")
        val inner = Modifier.padding(end = 8.dp)
        if (data == null) {
            Note("Wczytywanie planu…", inner)
            return@LuxuryCard
        }
        val people = data.chosen(widget)
        val days = data.extraDays(people, today)
        // At half width the height does nothing (as in "Pogoda"): the nearest day, up to 3 activities.
        val shownDays = if (half) 1 else when (widget.height) {
            WidgetHeight.SMALL -> 1
            WidgetHeight.MEDIUM -> 2
            WidgetHeight.LARGE -> 3
        }
        Column(inner, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (days.firstOrNull()?.first != today) {
                Text("Dziś bez zajęć", fontSize = 14.sp)
                days.firstOrNull()?.let { (date, list) ->
                    val (student, extra) = list.first()
                    Text(
                        "najbliższe: ${dayLabel(date, today).lowercase()} ${extra.startTime.shortTime()} · ${extra.name} (${student.name.substringBefore(' ')})",
                        fontSize = 12.sp, color = Luxury.TextMuted,
                    )
                } ?: Text("Brak zajęć w najbliższych dwóch tygodniach.", fontSize = 12.sp, color = Luxury.TextMuted)
                if (half || widget.height == WidgetHeight.SMALL) return@Column
            }
            days.take(shownDays).forEachIndexed { index, (date, list) ->
                if (index > 0 || date != today) Box(Modifier.padding(vertical = 4.dp)) { LuxuryDivider() }
                Text(dayLabel(date, today), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.TextMuted)
                list.take(if (half) 3 else 5).forEach { (student, extra) ->
                    val running = date == today && runCatching {
                        val t = now.toLocalTime()
                        !t.isBefore(LocalTime.parse(extra.startTime)) && t.isBefore(LocalTime.parse(extra.endTime))
                    }.getOrDefault(false)
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (half) extra.startTime.shortTime() else "${extra.startTime.shortTime()}–${extra.endTime.shortTime()}",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (running) Luxury.GoldLight else Luxury.TextMuted,
                            modifier = Modifier.width(if (half) 40.dp else 84.dp),
                        )
                        if (people.size > 1) PersonAvatar(student.name, student.avatar, size = 22.dp)
                        // The place under the name, not beside it (S24, 23.11: "Piłka n…" squeezed by the place).
                        Column(Modifier.weight(1f)) {
                            Text(extra.name, fontSize = WidgetText.Item, color = if (running) Luxury.GoldLight else Luxury.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!half) extra.place?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = Luxury.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Jutro w szkole" (board 15 B): one child's next school day after today — start, end, number of lessons, the
 * changes and the activities after school. Small: one line.
 */
@Composable
internal fun TomorrowWidget(
    widget: DashboardWidget,
    data: TimetableData?,
    now: LocalDateTime,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val student = data?.chosen(widget)?.firstOrNull()
    val days = student?.let { data.days(it.id) }.orEmpty()
    val date = days.keys.filter { it.isAfter(today) && days[it].orEmpty().any { l -> !l.cancelled } }.minOrNull()
    val lessons = date?.let { days[it] }.orEmpty().sortedBy { it.position }
    val held = lessons.filter { !it.cancelled }
    val half = widget.slot != WidgetSlot.FULL
    val name = listOfNotNull(student?.name?.substringBefore(' '), date?.let { dayLabel(it, today).lowercase().replaceFirstChar(Char::uppercase) }).joinToString(" · ").ifEmpty { null }
    LuxuryCard(modifier = modifier, contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 10.dp), spacing = 0.dp) {
        WidgetHeader(
            module = when {
                half -> "Szkoła"
                date == today.plusDays(1) -> "Jutro w szkole"
                else -> "Najbliższy dzień w szkole"
            },
            name = name, interactive = interactive, onSettings = onSettings, onOpen = onOpen, openLabel = "Otwórz Plan lekcji",
            leading = student?.let { s -> { PersonAvatar(s.name, s.avatar, size = 22.dp) } },
        )
        val inner = Modifier.padding(end = 8.dp)
        when {
            data == null -> Note("Wczytywanie planu…", inner)
            student == null -> Note("Brak planu lekcji. Dziennik podłącza się w module Plan lekcji.", inner)
            date == null -> Note("Brak lekcji w najbliższych dniach.", inner)
            else -> Column(inner, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val start = held.firstNotNullOfOrNull { it.span()?.first }
                val end = held.lastOrNull()?.span()?.second
                val changes = lessons.filter { it.change != null }
                val extras = data.extras(student, date)
                if (widget.height == WidgetHeight.SMALL) {
                    Text(
                        listOfNotNull(
                            if (start != null && end != null) "${start.short()} – ${end.short()}" else null,
                            "${held.size} ${lessonsWord(held.size)}",
                        ).joinToString(" · ") + if (changes.isNotEmpty()) " · " else "",
                        fontSize = 14.sp,
                    )
                    if (changes.isNotEmpty()) Text("${changes.size} ${if (changes.size == 1) "zmiana" else "zmiany"}", fontSize = 13.sp, color = Luxury.GoldText)
                    return@Column
                }
                // At half width the three tiles don't fit side by side (S24, 23.11: "8:5/0"), so they are rows.
                if (half) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Luxury.GoldBase.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        TileRow("Start", start?.short() ?: "–")
                        TileRow("Koniec", end?.short() ?: "–")
                        TileRow("Lekcji", held.size.toString())
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("START", start?.short() ?: "–", Modifier.weight(1f))
                    Tile("KONIEC", end?.short() ?: "–", Modifier.weight(1f))
                    Tile("LEKCJI", held.size.toString(), Modifier.weight(1f))
                }
                if (changes.isNotEmpty()) {
                    Text("ZMIANY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.TextMuted, modifier = Modifier.padding(top = 4.dp))
                    val shown = if (widget.height == WidgetHeight.LARGE) 6 else 3
                    changes.take(shown).forEach { ChangeLine(it, half) }
                    if (changes.size > shown) Text("+ ${changes.size - shown} więcej · Duża pokazuje do 6", fontSize = 12.sp, color = Luxury.TextMuted)
                }
                if (extras.isNotEmpty()) {
                    Box(Modifier.padding(vertical = 4.dp)) { LuxuryDivider() }
                    Text("PO SZKOLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.TextMuted)
                    extras.take(3).forEach { extra ->
                        Row(modifier = Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (half) extra.startTime.shortTime() else "${extra.startTime.shortTime()}–${extra.endTime.shortTime()}",
                                fontSize = 13.sp, color = Luxury.TextMuted, maxLines = 1, softWrap = false, modifier = Modifier.width(if (half) 48.dp else 84.dp),
                            )
                            Text(extra.name, fontSize = WidgetText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun lessonsWord(n: Int) = when {
    n == 1 -> "lekcja"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "lekcje"
    else -> "lekcji"
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Luxury.GoldBase.copy(alpha = 0.08f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.TextMuted)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldLight)
    }
}

@Composable
private fun TileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.TextMuted, maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldLight, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ChangeLine(lesson: Lesson, half: Boolean = false) {
    val kind = lesson.change?.kind
    if (half) {
        // Narrow: the hour and what happened on the first line, the subject under it (whole words, no squeezing).
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lesson.span()?.first?.short() ?: lesson.time, fontSize = 12.sp, color = Luxury.TextMuted, maxLines = 1, softWrap = false)
                Text(
                    when (kind) {
                        ChangeKind.CANCELLED -> "  odwołana"
                        ChangeKind.SUBSTITUTION -> "  zastępstwo"
                        else -> "  zmiana"
                    },
                    fontSize = 11.sp, color = if (kind == ChangeKind.CANCELLED) CancelledColor else Substitution, maxLines = 1,
                )
            }
            Text(
                lesson.subject, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (kind == ChangeKind.CANCELLED) TextDecoration.LineThrough else null,
                color = if (kind == ChangeKind.CANCELLED) Luxury.TextMuted else Luxury.Text,
            )
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(lesson.span()?.first?.short() ?: lesson.time, fontSize = 13.sp, color = Luxury.TextMuted, modifier = Modifier.width(48.dp))
        Text(
            lesson.subject, fontSize = WidgetText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (kind == ChangeKind.CANCELLED) TextDecoration.LineThrough else null,
            color = if (kind == ChangeKind.CANCELLED) Luxury.TextMuted else Luxury.Text,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            when (kind) {
                ChangeKind.CANCELLED -> "odwołana"
                ChangeKind.SUBSTITUTION -> "zastępstwo"
                else -> "zmiana"
            },
            fontSize = 11.sp, color = if (kind == ChangeKind.CANCELLED) CancelledColor else Substitution,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = Luxury.TextMuted, modifier = modifier.padding(vertical = 6.dp))
}

// --- settings ---

/** Whose activities (several) / whose next day (one), then the shared width, height and buttons. */
@Composable
internal fun TimetableWidgetSettings(
    widget: DashboardWidget,
    tomorrow: Boolean,
    data: TimetableData?,
    now: LocalDateTime,
    frame: SettingsFrame,
    onSave: (DashboardWidget) -> Unit,
) {
    val students = data?.students.orEmpty()
    var people by remember(widget.id, students.size) {
        mutableStateOf(widget.people.filter { id -> students.any { it.id == id } }.ifEmpty { if (tomorrow) students.take(1).map { it.id } else students.map { it.id } })
    }
    var height by remember(widget.id) { mutableStateOf(widget.height) }
    var half by remember(widget.id) { mutableStateOf(widget.slot != WidgetSlot.FULL) }
    val draft = widget.copy(people = people, height = height, slot = slotFor(widget, half))
    WidgetSettingsWindow(
        frame = frame, title = if (tomorrow) "Jutro w szkole" else "Zajęcia dodatkowe", module = "Plan lekcji",
        half = half, onHalf = { half = it }, height = height, onHeight = { height = it },
        heightNote = {
            if (tomorrow) when (it) {
                WidgetHeight.SMALL -> "Jedna linia: od–do, ile lekcji, ile zmian."
                WidgetHeight.MEDIUM -> "Start, koniec, liczba lekcji, do 3 zmian i zajęcia po szkole."
                WidgetHeight.LARGE -> "Jak Średnia, do 6 zmian."
            } else when (it) {
                WidgetHeight.SMALL -> "Tylko najbliższy dzień z zajęciami."
                WidgetHeight.MEDIUM -> "Dwa najbliższe dni z zajęciami."
                WidgetHeight.LARGE -> "Trzy najbliższe dni z zajęciami."
            }
        },
        ready = people.isNotEmpty(),
        heightFixed = if (half && !tomorrow) "Przy pół szerokości wysokość się nie zmienia: najbliższy dzień, do 3 zajęć." else null,
        onSave = { onSave(draft) },
        preview = {
            if (tomorrow) TomorrowWidget(draft, data, now, interactive = false, onSettings = {}, onOpen = {})
            else ExtrasWidget(draft, data, now, interactive = false, onSettings = {}, onOpen = {})
        },
    ) {
        LuxurySectionTitle(if (tomorrow) "Czyj dzień · jedna osoba" else "Czyje zajęcia · można kilka")
        if (data == null) Text("Wczytywanie…", fontSize = 13.sp, color = Luxury.TextMuted)
        else if (students.isEmpty()) Text("Nikt w społeczności nie ma jeszcze planu lekcji.", fontSize = 13.sp, color = Luxury.TextMuted)
        students.forEach { student ->
            val on = student.id in people
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
                    .background(if (on) Luxury.GoldBase.copy(alpha = 0.10f) else Color.Transparent)
                    .border(1.dp, if (on) Luxury.GoldBase else ChoiceOutline, shape)
                    .clickable(role = if (tomorrow) Role.RadioButton else Role.Checkbox) {
                        people = when {
                            tomorrow -> listOf(student.id)
                            on -> (people - student.id).ifEmpty { people }
                            else -> people + student.id
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonAvatar(student.name, student.avatar, size = 30.dp)
                Text(student.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp))
            }
        }
    }
}
