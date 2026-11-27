package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import pl.ambif.ambify.shared.WidgetSlot
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import pl.ambif.ambify.features.timetable.Substitution
import pl.ambif.ambify.features.timetable.cancelled
import pl.ambif.ambify.features.timetable.dayMonth
import pl.ambif.ambify.features.timetable.extrasOn
import pl.ambif.ambify.features.timetable.short
import pl.ambif.ambify.features.timetable.shortTime
import pl.ambif.ambify.features.timetable.span
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.TimetableWeek
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.ui.components.AvatarZoom
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** The timetable weeks the Dashboard has (this one and the next), and when they were saved if from the phone's copy. */
data class TimetableData(val weeks: List<TimetableWeek>, val offlineSince: Long? = null) {
    val students: List<Student> get() = weeks.flatMap { it.students }.distinctBy { it.id }

    fun days(studentId: String): Map<LocalDate, List<Lesson>> =
        weeks.flatMap { it.lessons[studentId].orEmpty() }.groupBy { LocalDate.parse(it.date) }

    fun extras(student: Student, date: LocalDate): List<ExtraActivity> =
        weeks.firstOrNull { LocalDate.parse(it.monday).let { m -> !date.isBefore(m) && date.isBefore(m.plusDays(7)) } }
            ?.extrasOn(student, date).orEmpty()
}

private val Cancelled: androidx.compose.ui.graphics.Color get() = Luxury.Error
private val ChipBorder: androidx.compose.ui.graphics.Color get() = Luxury.Outline

/**
 * "Lekcje": the coming lessons of one person and, at the bottom, always a time: the end of lessons today, or after
 * lessons the start on the next school day. Several people = a switch of people (the choice is remembered on this
 * phone). Only its controls answer a touch (person, the gear = settings, the gold mark = Plan lekcji, "+ N kolejne" =
 * the whole day); elsewhere the card only floats. [interactive] false: a picture of it (dragged, preview).
 */
@Composable
internal fun LessonsWidget(
    widget: DashboardWidget,
    data: TimetableData?,
    now: LocalDateTime,
    store: DashboardStore?,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = data?.students.orEmpty()
    val people = widget.people.mapNotNull { id -> all.firstOrNull { it.id == id } }.ifEmpty { all }
    var picked by remember(widget.id) { mutableStateOf(store?.person(widget.id)) }
    val student = people.firstOrNull { it.id == picked } ?: people.firstOrNull()
    var expanded by remember(widget.id) { mutableStateOf(false) }
    val day = student?.let { LessonsDays.of(data!!.days(it.id), now) }

    LuxuryCard(modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 10.dp), spacing = 0.dp) {
        val single = student?.takeIf { people.size == 1 }
        val name = listOfNotNull(single?.name?.substringBefore(' '), day?.dayTag).joinToString(" · ").ifEmpty { null }
        WidgetHeader(
            module = "Lekcje", name = name, interactive = interactive, onSettings = onSettings, onOpen = onOpen, openLabel = "Otwórz Plan lekcji",
            leading = single?.let { s -> { PersonAvatar(s.name, s.avatar, size = 22.dp) } },
        )
        if (people.size > 1 && widget.slot != WidgetSlot.FULL) {
            // B-001: at half width the chips did not fit; "‹ child ›" like the places in Pogoda, and a swipe.
            PersonArrows(people, student?.id, interactive) { id ->
                picked = id
                expanded = false
                store?.savePerson(widget.id, id)
            }
            Spacer(Modifier.height(4.dp))
        } else if (people.size > 1) {
            PersonSwitch(people, student?.id, interactive) { id ->
                picked = id
                expanded = false
                store?.savePerson(widget.id, id)
            }
            Spacer(Modifier.height(6.dp))
        }
        val inner = Modifier.padding(end = 10.dp)
        when {
            data == null -> Muted("Wczytywanie planu…", inner)
            student == null -> Muted("Brak planu lekcji. Dziennik podłącza się w module Plan lekcji.", inner)
            day == null -> Muted("Brak lekcji w najbliższych dniach.", inner)
            else -> Column(inner) {
                val count = LessonsDays.count(widget.height)
                val shown = if (expanded) day.lessons else day.lessons.drop(day.firstShown).take(count)
                val time = now.toLocalTime()
                shown.forEach { lesson ->
                    val span = lesson.span()
                    val running = day.today && span != null && !time.isBefore(span.first) && time.isBefore(span.second) && !lesson.cancelled
                    val past = day.today && span != null && !time.isBefore(span.second)
                    LessonLine(lesson, running, past)
                }
                val more = day.lessons.size - (day.firstShown + shown.size)
                when {
                    expanded -> MoreLink("Zwiń", interactive) { expanded = false }
                    more > 0 -> MoreLink(LessonsDays.moreLabel(more), interactive) { expanded = true }
                }
                val extras = if (widget.height == WidgetHeight.SMALL && !expanded) emptyList() else data.extras(student, day.date)
                extras.take(if (expanded) extras.size else 2).forEachIndexed { index, extra ->
                    ExtraLine(extra, dashed = index == 0)
                }
                Spacer(Modifier.height(8.dp))
                LuxuryDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.footerLabel, fontSize = 10.5.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
                    Text(day.footerTime, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldLight)
                }
            }
        }
        data?.offlineSince?.let { since ->
            Muted("Bez połączenia z serwerem · plan z ${savedAt(since)}", inner.padding(top = 6.dp), size = 11)
        }
    }
}

/** Half width: one child at a time between arrows; first name over surname, so "Antoni Szarkowski" fits too. */
@Composable
private fun PersonArrows(people: List<Student>, selected: String?, interactive: Boolean, onPick: (String) -> Unit) {
    val index = people.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val person = people[index]
    fun step(by: Int) = onPick(people[(index + by + people.size) % people.size].id)
    var drag by remember { mutableStateOf(0f) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 10.dp)
            .pointerInput(index, interactive) {
                if (!interactive) return@pointerInput
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag > 40f) step(-1) else if (drag < -40f) step(1)
                    drag = 0f
                }) { change, dx -> change.consume(); drag += dx }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { step(-1) }, enabled = interactive) {
            Icon(AppIcons.Back, contentDescription = "Poprzednie dziecko", tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
        }
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Lekcje: ${person.name}, ${index + 1} z ${people.size}" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PersonAvatar(person.name, person.avatar, size = 24.dp, zoom = AvatarZoom.HOLD)
            val parts = person.name.trim().split(' ', limit = 2)
            parts.forEach { part ->
                Text(part, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        IconButton(onClick = { step(1) }, enabled = interactive) {
            Icon(AppIcons.Forward, contentDescription = "Następne dziecko: ${people[(index + 1) % people.size].name}", tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PersonSwitch(people: List<Student>, selected: String?, interactive: Boolean, onPick: (String) -> Unit) {
    val names = people.size <= 2
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        people.forEach { person ->
            val on = person.id == selected
            val shape = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (on) Luxury.GoldBase.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) Luxury.GoldBase else ChipBorder, shape)
                    .clickable(enabled = interactive, role = Role.Tab) { onPick(person.id) }
                    .semantics {
                        this.selected = on
                        contentDescription = "Lekcje: ${person.name}"
                    }
                    .padding(start = 6.dp, end = if (names) 14.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonAvatar(person.name, person.avatar, size = 30.dp, zoom = AvatarZoom.HOLD)
                if (names) {
                    Text(person.name.substringBefore(' '), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) Luxury.GoldLight else Luxury.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun LessonLine(lesson: Lesson, running: Boolean, past: Boolean) {
    val span = lesson.span()
    val color = when {
        running -> Luxury.GoldLight
        past || lesson.cancelled -> Luxury.Disabled.copy(alpha = 0.7f)
        else -> Luxury.Text
    }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(span?.first?.short() ?: lesson.time, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (running) Luxury.GoldLight else Luxury.TextMuted, modifier = Modifier.width(48.dp))
        Text(
            lesson.subject,
            fontSize = WidgetText.Item,
            color = color,
            fontWeight = if (running) FontWeight.Medium else FontWeight.Normal,
            textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val change = lesson.change?.kind
        val (tag, tagColor) = when {
            change == ChangeKind.CANCELLED -> "odwołana" to Cancelled
            change == ChangeKind.SUBSTITUTION -> "zastępstwo" to Substitution
            change == ChangeKind.CHANGED -> "zmiana" to Substitution
            running -> (if (lesson.room.isBlank()) "teraz" else "teraz · s. ${lesson.room}") to Luxury.GoldLight
            else -> "" to Color.Transparent
        }
        if (tag.isNotEmpty()) Text(tag, fontSize = 11.sp, color = tagColor, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ExtraLine(extra: ExtraActivity, dashed: Boolean) {
    val line = Luxury.GoldBase.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (dashed) 4.dp else 0.dp)
            .drawBehind {
                if (dashed) drawLine(line, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            .heightIn(min = 28.dp)
            .padding(top = if (dashed) 2.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(extra.startTime.shortTime(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Luxury.TextMuted, modifier = Modifier.width(48.dp))
        Text(extra.name, fontSize = 14.sp, color = Luxury.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("dodatkowe", fontSize = 11.sp, color = Luxury.TextMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MoreLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = Luxury.GoldText,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(start = 48.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    )
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 13) {
    Text(text, fontSize = size.sp, color = Luxury.TextMuted, modifier = modifier.padding(vertical = 6.dp))
}

/** "dziś, 18:05" or "14.11, 18:05". */
private fun savedAt(at: Long): String {
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val day = if (time.toLocalDate() == LocalDate.now()) "dziś" else time.toLocalDate().dayMonth()
    return "$day, ${time.hour}:%02d".format(time.minute)
}
