package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.shared.WidgetType
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val Outline = Color(0xFF3A3A40)
private val Ok = Color(0xFF9FD3A0)
private val Danger = Color(0xFFE79A86)

/**
 * "Widgety": only the modules switched on in the community (and with widgets), each folded. A tap opens its
 * popup: the standard widgets, then "Widgety premium" ("Kup" while the premium extra is missing; locked widgets).
 */
@Composable
internal fun WidgetPicker(
    enabled: List<ModuleId>,
    features: List<Feature>,
    onDashboard: List<DashboardWidget>,
    onBack: () -> Unit,
    onAdd: (WidgetType) -> Unit,
) {
    val modules = enabled.filter { WidgetType.of(it).isNotEmpty() }
    var open by remember { mutableStateOf<ModuleId?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Widgety", "wybierz moduł", onBack)
        if (modules.isEmpty()) {
            Text("Żaden włączony moduł nie ma jeszcze widgetów.", color = Luxury.TextMuted, fontSize = 14.sp)
        } else {
            LuxuryCard(contentPadding = PaddingValues(vertical = 4.dp), spacing = 0.dp) {
                modules.forEachIndexed { index, module ->
                    val types = WidgetType.of(module)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .clickable(role = Role.Button, onClickLabel = "Widgety modułu ${module.label}") { open = module }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(module.label, fontSize = 16.sp, color = Luxury.Text)
                            Text(widgetsWord(types.size), fontSize = 12.sp, color = Luxury.TextMuted)
                        }
                        LuxuryOpenMark(size = 36.dp)
                    }
                    if (index < modules.lastIndex) LuxuryDivider()
                }
            }
        }
        Text(
            "Widać tylko moduły włączone w społeczności.",
            fontSize = 12.sp,
            color = Luxury.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    open?.let { module ->
        ModuleWidgetsPopup(module, features, onDashboard, onDismiss = { open = null }) { type ->
            open = null
            onAdd(type)
        }
    }
}

@Composable
private fun ModuleWidgetsPopup(
    module: ModuleId,
    features: List<Feature>,
    onDashboard: List<DashboardWidget>,
    onDismiss: () -> Unit,
    onAdd: (WidgetType) -> Unit,
) {
    val types = WidgetType.of(module)
    val standard = types.filter { it.premium == null }
    val premium = types.filter { it.premium != null }
    LuxuryPopup(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(module.label, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Zamknij", tint = Luxury.TextMuted) }
        }
        standard.forEachIndexed { index, type ->
            val there = !type.multi && onDashboard.any { it.type == type.key }
            WidgetRow(type, locked = false, there = there, onAdd = { onAdd(type) })
            if (index < standard.lastIndex) LuxuryDivider()
        }
        if (premium.isNotEmpty()) {
            val active = premium.all { it.premium in features }
            Spacer(Modifier.height(4.dp))
            LuxuryDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
                LuxurySectionTitle("Widgety premium", modifier = Modifier.weight(1f))
                if (active) Text("aktywne", fontSize = 12.sp, color = Ok) else FramedButton("Kup") { }
            }
            premium.forEach { type ->
                WidgetRow(type, locked = type.premium !in features, there = false, onAdd = { onAdd(type) })
            }
        }
    }
}

@Composable
private fun WidgetRow(type: WidgetType, locked: Boolean, there: Boolean, onAdd: () -> Unit) {
    // The widget's name in the gold section style, its width in brackets; then the description and a small "Dodaj" on the right.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LuxurySectionTitle(type.label)
            Text(if (type.half) "(pół szerokości)" else "(cała szerokość)", fontSize = 11.sp, color = Luxury.TextMuted)
            if (locked) Text("🔒", fontSize = 14.sp)
        }
        Text(type.description, fontSize = 14.sp, color = if (locked) Luxury.Disabled else Luxury.Text)
        // "Dodaj" (or "Na pulpicie") on the right, under the description.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            when {
                locked -> Unit
                there -> Text("Na pulpicie", fontSize = 12.sp, color = Ok)
                else -> SmallAddButton(onAdd)
            }
        }
    }
}

/** A small outlined "Dodaj" under the widget's description (still a 48 dp touch target). */
@Composable
private fun SmallAddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Luxury.GoldLink, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Plus, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(14.dp))
            Text(" Dodaj", fontSize = 13.sp, color = Luxury.GoldText)
        }
    }
}

private fun widgetsWord(count: Int) = when {
    count == 1 -> "1 widget"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count widgety"
    else -> "$count widgetów"
}

/**
 * Settings of a "Lekcje" widget: whose lessons (several = a switch in the widget), the height, a preview; for a
 * widget already on the Dashboard also moving it without dragging and removing it. Opened by "Dodaj" in the picker
 * and by the gear on the widget — the same window.
 */
@Composable
internal fun LessonsSettings(
    widget: DashboardWidget,
    isNew: Boolean,
    data: TimetableData?,
    now: LocalDateTime,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (DashboardWidget) -> Unit,
    onDelete: () -> Unit,
) {
    val students = data?.students.orEmpty()
    var people by remember(widget.id) {
        mutableStateOf(widget.people.filter { id -> students.any { it.id == id } }.ifEmpty { students.map { it.id } })
    }
    var height by remember(widget.id) { mutableStateOf(widget.height) }
    val draft = widget.copy(people = people, height = height)

    LuxuryPopup(onDismiss = onDismiss) {
        Column {
            Text(if (isNew) "Dodaj: „Lekcje”" else "Ustawienia: „Lekcje”", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Plan lekcji", fontSize = 13.sp, color = Luxury.TextMuted)
        }
        LuxurySectionTitle("Czyje lekcje · można kilka")
        if (data == null) Text("Wczytywanie…", fontSize = 13.sp, color = Luxury.TextMuted)
        else if (students.isEmpty()) Text("Nikt w społeczności nie ma jeszcze planu lekcji (dziennik podłącza się w module Plan lekcji).", fontSize = 13.sp, color = Luxury.TextMuted)
        students.forEach { student ->
            val on = student.id in people
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(if (on) Luxury.GoldBase.copy(alpha = 0.10f) else Color.Transparent)
                    .border(1.dp, if (on) Luxury.GoldBase else Outline, shape)
                    .clickable(role = Role.Checkbox) {
                        // At least one person stays chosen.
                        people = if (on) (people - student.id).ifEmpty { people } else people + student.id
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonAvatar(student.name, student.avatar, size = 30.dp)
                Text(student.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).border(1.6.dp, Luxury.GoldBase, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.GoldLight, modifier = Modifier.size(16.dp)) }
            }
        }
        if (students.size > 1) {
            Text(
                if (people.size > 1) "Kilka osób: w widgecie będzie przełącznik." else "Jedna osoba: bez przełącznika, imię w nagłówku.",
                fontSize = 12.sp,
                color = Luxury.TextMuted,
            )
        }

        LuxurySectionTitle("Wysokość", modifier = Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, Outline, RoundedCornerShape(24.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            WidgetHeight.entries.forEach { option ->
                val on = option == height
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier)
                        .clickable(role = Role.RadioButton) { height = option },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) Luxury.Surface else Luxury.TextMuted)
                }
            }
        }
        Text(
            when (height) {
                WidgetHeight.SMALL -> "Tylko bieżąca lekcja i godzina końca."
                WidgetHeight.MEDIUM -> "Bieżąca i 2 kolejne lekcje."
                WidgetHeight.LARGE -> "Do 5 lekcji, prawie cały dzień."
            },
            fontSize = 12.sp,
            color = Luxury.TextMuted,
        )

        if (!isNew) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                LuxurySectionTitle("Przesuń", modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                    Icon(AppIcons.Up, contentDescription = "Przesuń w górę", tint = if (canMoveUp) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
                IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                    Icon(AppIcons.ExpandMore, contentDescription = "Przesuń w dół", tint = if (canMoveDown) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
            }
        }

        LuxurySectionTitle("Podgląd", modifier = Modifier.padding(top = 4.dp))
        LessonsWidget(draft, data, now, store = null, interactive = false, onSettings = {}, onOpen = {})

        Spacer(Modifier.height(4.dp))
        if (isNew) {
            GoldButton("Dodaj na pulpit", enabled = people.isNotEmpty()) { onSave(draft) }
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        } else {
            GoldButton("Zapisz") { onSave(draft) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Danger.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Text("Usuń z pulpitu", color = Danger, fontSize = 14.sp) }
        }
        Spacer(Modifier.width(1.dp))
    }
}
