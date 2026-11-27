package pl.ambif.ambify.features.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.WeatherPlace
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.shared.WidgetSlot
import pl.ambif.ambify.ui.components.FieldError
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val Rain: androidx.compose.ui.graphics.Color get() = Luxury.Info
private val Warn: androidx.compose.ui.graphics.Color get() = Luxury.Danger
private val Dot: androidx.compose.ui.graphics.Color get() = Luxury.Outline
private val FieldOutline: androidx.compose.ui.graphics.Color get() = Luxury.Outline

/** "opt|nohours" in [DashboardWidget.people]: the widget shows no hours and warnings. */
private const val NO_HOURS = "opt|nohours"

private fun DashboardWidget.showsHours() = NO_HOURS !in people && height != WidgetHeight.SMALL

/**
 * "Pogoda" (board 13): one page per place, swiped (and in a whole-width widget also "‹ ›"), dots under it. The
 * last seen place is remembered on this phone. Small: now, min / max, rain; Medium/Large: also the next hours and a
 * warning; Large also tomorrow.
 */
@Composable
internal fun WeatherWidget(
    widget: DashboardWidget,
    store: DashboardStore?,
    interactive: Boolean,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val source = remember { WeatherSource(context) }
    val places = WeatherPlace.of(widget)
    val half = widget.slot != WidgetSlot.FULL
    val start = store?.person(widget.id)?.toIntOrNull()?.coerceIn(0, (places.size - 1).coerceAtLeast(0)) ?: 0
    val pager = rememberPagerState(initialPage = start) { places.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, widget.id) {
        snapshotFlow { pager.currentPage }.collect { store?.savePerson(widget.id, it.toString()) }
    }
    val forecasts = remember { mutableStateListOf<Pair<WeatherPlace, Forecast?>>() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(places) {
        forecasts.clear()
        forecasts.addAll(places.map { it to source.cached(it) })
        while (true) {
            places.forEachIndexed { i, place -> source.forecast(place)?.let { if (i < forecasts.size) forecasts[i] = place to it } }
            now = LocalDateTime.now()
            delay(WeatherSource.FRESH_MS)
        }
    }

    LuxuryCard(modifier = modifier, contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 10.dp), spacing = 0.dp) {
        val page = pager.currentPage
        val place = places.getOrNull(page)
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!half && places.size > 1 && interactive) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((page - 1 + places.size) % places.size) } }) {
                    Icon(AppIcons.Back, contentDescription = "Poprzednie miejsce", tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
                }
            }
            // "POGODA", and the place smaller under it (Michał, 23.11); the counter never wraps.
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                horizontalAlignment = if (!half && places.size > 1) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                LuxurySectionTitle("Pogoda")
                if (place != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(place.name, fontSize = 13.sp, color = Luxury.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (!half && places.size > 1) Text(" · ${page + 1}/${places.size}", fontSize = 12.sp, color = Luxury.TextMuted, maxLines = 1, softWrap = false)
                }
            }
            if (!half && places.size > 1 && interactive) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((page + 1) % places.size) } }) {
                    Icon(AppIcons.Forward, contentDescription = "Następne miejsce: ${places[(page + 1) % places.size].name}", tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onSettings, enabled = interactive) {
                Icon(AppIcons.Gear, contentDescription = "Ustawienia widgetu Pogoda", tint = Luxury.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        if (places.isEmpty()) {
            Text("Dodaj miejsce w ustawieniach widgetu (koło zębate).", fontSize = 13.sp, color = Luxury.TextMuted, modifier = Modifier.padding(end = 8.dp, bottom = 6.dp))
            return@LuxuryCard
        }
        HorizontalPager(state = pager, userScrollEnabled = interactive, modifier = Modifier.fillMaxWidth()) { index ->
            val forecast = forecasts.getOrNull(index)?.second
            // A tap (outside the arrows and the gear) opens the 16-day forecast (0.7.8, board 5).
            Box(
                Modifier.fillMaxWidth()
                    .then(if (interactive && onOpen != null) Modifier.clickable(onClickLabel = "Prognoza na 16 dni", onClick = onOpen) else Modifier)
                    .padding(end = 8.dp),
            ) {
                if (forecast == null) Text("Wczytywanie pogody…", fontSize = 13.sp, color = Luxury.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                else WeatherPage(forecast, widget, half, now)
            }
        }
        if (places.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 8.dp)
                    .semantics { contentDescription = "Miejsce ${page + 1} z ${places.size}" },
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            ) {
                repeat(places.size) { i ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == page) Luxury.GoldLight else Dot))
                }
            }
        }
    }
}

/** Night between today's sunset and the next sunrise (a clear sky shows the moon). */
private fun Forecast.isNight(now: LocalDateTime): Boolean {
    val up = daily.sunrise.getOrNull(0)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return now.hour !in 6..19
    val down = daily.sunset.getOrNull(0)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return now.hour !in 6..19
    return now.isBefore(up) || now.isAfter(down)
}

/**
 * One place (0.7.8, canvas boards 2 and 7): Mała = now; Średnia = now with the temperature in the middle, 4 hours
 * and Jutro, no rain % under the hours; Duża = also feels-like, humidity, pressure, sunrise–sunset, rain % and the
 * next 3 days. Temperatures in their colours; frost at ≤ −10°, heat haze at ≥ 32°.
 */
@Composable
private fun WeatherPage(forecast: Forecast, widget: DashboardWidget, half: Boolean, now: LocalDateTime) {
    val today = forecast.today()
    val code = forecast.current.code
    val t = forecast.current.temperature
    val night = forecast.isNight(now)
    val large = widget.height == WidgetHeight.LARGE
    Box {
        if (t <= -10) FrostOverlay(Modifier.matchParentSize())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (half) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeatherIcon(code, 32.dp, night)
                    Box { Temp(t, 30.sp); if (t >= 32) HeatHaze(Modifier.matchParentSize()) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MinMax(today, size = 12)
                    today.rain?.let { Text("· $it%", fontSize = 12.sp, color = Rain) }
                }
                forecast.warning(now)?.let { Text(it, fontSize = 11.sp, color = Warn, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                return@Column
            }
            // Icon left, temperature in the middle, the rest right (Michał, 26.11).
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { WeatherIcon(code, 48.dp, night) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { Temp(t, 44.sp); if (t >= 32) HeatHaze(Modifier.matchParentSize()) }
                    if (large) Text("odczuwalna ${deg(forecast.current.feelsLike)}", fontSize = 12.sp, color = Luxury.TextMuted)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    MinMax(today)
                    today.rain?.let { Text("deszcz $it%", fontSize = 12.sp, color = Rain) }
                    Text("wiatr ${Math.round(forecast.current.wind)} km/h", fontSize = 12.sp, color = Luxury.TextMuted)
                }
            }
            if (large) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    forecast.current.humidity?.let { Detail("wilgotność", "$it%", Modifier.weight(1f)) }
                    forecast.current.pressure?.let { Detail("ciśnienie", "${Math.round(it)} hPa", Modifier.weight(1f)) }
                    forecast.sun()?.let { Detail("słońce", it, Modifier.weight(1f)) }
                }
            }
            if (!widget.showsHours()) return@Column
            forecast.warning(now)?.let { MessageBar(MessageKind.WARNING, it) }
            LuxuryDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                forecast.hours(now).forEach { hour ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%d:00".format(hour.time.hour), fontSize = 12.sp, color = Luxury.TextMuted)
                        WeatherIcon(hour.code, 24.dp, night = hour.time.hour !in 6..19)
                        Temp(hour.temperature, 15.sp)
                        if (large) Text(if (hour.rain >= 10) "${hour.rain}%" else " ", fontSize = 11.sp, color = Rain)
                    }
                }
                val tomorrow = forecast.tomorrow()
                Column(modifier = Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Jutro", fontSize = 12.sp, color = Luxury.TextMuted)
                    WeatherIcon(tomorrow.code ?: 3, 24.dp)
                    Row {
                        tomorrow.min?.let { Temp(it, 14.sp) }
                        Text("/", fontSize = 14.sp, color = Luxury.TextMuted)
                        tomorrow.max?.let { Temp(it, 14.sp) }
                    }
                    if (large) Text(tomorrow.rain?.takeIf { it >= 10 }?.let { "$it%" } ?: " ", fontSize = 11.sp, color = Rain)
                }
            }
            if (large) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    forecast.days().drop(2).take(3).forEach { day ->
                        // Day over icon + min/max: three days fit side by side without wrapping.
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Luxury.Outline.copy(alpha = 0.25f)).padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(dayShort(day.date), fontSize = 12.sp, color = Luxury.TextMuted, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                WeatherIcon(day.code ?: 3, 18.dp)
                                day.min?.let { Temp(it, 13.sp) }
                                Text("/", fontSize = 13.sp, color = Luxury.TextMuted)
                                day.max?.let { Temp(it, 13.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Luxury.Outline.copy(alpha = 0.25f)).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 11.sp, color = Luxury.TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** "pt.", "sob." */
internal fun dayShort(date: java.time.LocalDate): String =
    listOf("pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz.")[date.dayOfWeek.value - 1]

@Composable
private fun MinMax(day: Forecast.Day, size: Int = 15) {
    if (day.min == null && day.max == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        day.min?.let { Temp(it, size.sp, FontWeight.Normal) }
        if (day.min != null && day.max != null) Text(" / ", fontSize = size.sp, color = Luxury.TextMuted)
        day.max?.let { Temp(it, size.sp, FontWeight.Normal) }
    }
}

// --- settings (board 13 C) ---

/** "Pogoda" settings: up to 5 places (rename, remove, drag to reorder, add by name or from the phone), size. */
@Composable
internal fun WeatherSettings(widget: DashboardWidget, frame: SettingsFrame, onSave: (DashboardWidget) -> Unit) {
    val context = LocalContext.current
    val source = remember { WeatherSource(context) }
    val places = remember(widget.id) { mutableStateListOf<WeatherPlace>().apply { addAll(WeatherPlace.of(widget)) } }
    var hours by remember(widget.id) { mutableStateOf(NO_HOURS !in widget.people) }
    var height by remember(widget.id) { mutableStateOf(widget.height) }
    var half by remember(widget.id) { mutableStateOf(widget.slot != WidgetSlot.FULL) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(widget.people.none { it.startsWith(WeatherPlace.PREFIX) }) }
    val draft = widget.copy(
        people = places.map { it.encode() } + if (hours) emptyList() else listOf(NO_HOURS),
        height = height, slot = slotFor(widget, half),
    )

    WidgetSettingsWindow(
        frame = frame, title = "Pogoda", module = "Ogólne · dane: Open-Meteo",
        half = half, onHalf = { half = it }, height = height, onHeight = { height = it },
        heightNote = {
            when (it) {
                WidgetHeight.SMALL -> "Tylko teraz: temperatura, min/max, deszcz i wiatr."
                WidgetHeight.MEDIUM -> "Do tego 4 godziny co 3 h, jutro i ostrzeżenie."
                WidgetHeight.LARGE -> "Do tego odczuwalna, wilgotność, ciśnienie, wschód i zachód słońca, deszcz co godzinę i 3 kolejne dni."
            }
        },
        ready = places.isNotEmpty(),
        heightFixed = if (half) "Przy pół szerokości wysokość się nie zmienia: teraz, min/max, deszcz i ostrzeżenie." else null,
        onSave = { onSave(draft) },
        preview = { WeatherWidget(draft, store = null, interactive = false, onSettings = {}) },
    ) {
        LuxurySectionTitle("Miejsca · do ${WeatherPlace.MAX}")
        val rowPx = with(LocalDensity.current) { 56.dp.toPx() }
        Column {
            places.forEachIndexed { index, place ->
                var drag by remember(place) { mutableStateOf(0f) }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(width = 36.dp, height = 48.dp).pointerInput(place, places.size) {
                            detectVerticalDragGestures(onDragEnd = { drag = 0f }) { change, dy ->
                                change.consume()
                                drag += dy
                                val from = places.indexOf(place)
                                if (drag > rowPx * 0.6f && from < places.lastIndex) {
                                    places.add(from + 1, places.removeAt(from)); drag = 0f
                                } else if (drag < -rowPx * 0.6f && from > 0) {
                                    places.add(from - 1, places.removeAt(from)); drag = 0f
                                }
                            }
                        }.semantics { contentDescription = "Przeciągnij: ${place.name}" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.DragHandle, contentDescription = null, tint = Luxury.TextMuted, modifier = Modifier.size(20.dp)) }
                    if (renaming == index) {
                        var name by remember(place) { mutableStateOf(place.name) }
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                            label = { Text("Nazwa na widgecie") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = FieldOutline),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            if (name.isNotBlank()) places[index] = renamed(place, name.trim())
                            renaming = null
                        }) { Icon(AppIcons.Check, contentDescription = "Zapisz nazwę", tint = Luxury.GoldText) }
                    } else {
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Zmień nazwę") { renaming = index }.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(place.name, fontSize = 15.sp)
                                if (index == 0) Text("  · domyślne", fontSize = 11.sp, color = Luxury.GoldText)
                            }
                            if (place.region.isNotBlank()) Text(place.region, fontSize = 12.sp, color = Luxury.TextMuted)
                        }
                        IconButton(onClick = { places.removeAt(index) }) {
                            Icon(AppIcons.Close, contentDescription = "Usuń: ${place.name}", tint = Warn, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                LuxuryDivider()
            }
        }
        if (places.size < WeatherPlace.MAX) {
            if (adding) AddPlace(source, onAdd = { places.add(it); adding = false }, onCancel = if (places.isEmpty()) null else ({ adding = false }))
            else Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { adding = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Plus, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
                Text("Dodaj miejsce (wpisz albo z telefonu)", fontSize = 15.sp, color = Luxury.GoldText)
            }
        }
        if (!half) Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Switch) { hours = !hours },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Godziny i ostrzeżenia", fontSize = 15.sp)
                Text("tylko przy wysokości Średnia/Duża", fontSize = 12.sp, color = Luxury.TextMuted)
            }
            androidx.compose.material3.Switch(checked = hours, onCheckedChange = { hours = it })
        }
    }
}

/** Finding a place by name (Open-Meteo), or taking the phone's position once. */
@Composable
private fun AddPlace(source: WeatherSource, onAdd: (WeatherPlace) -> Unit, onCancel: (() -> Unit)?) {
    var chosen by remember { mutableStateOf<WeatherPlace?>(null) }
    chosen?.let { place ->
        NamePlace(place, onAdd = onAdd, onBack = { chosen = null })
        return
    }
    FindPlace(source, onFound = { chosen = it }, onCancel = onCancel)
}

/**
 * The second step: what the place is called on the widget — "Dom", "Praca", "Działka"… (Michał, 23.11: places,
 * not only towns). The town stays under the name in the settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamePlace(place: WeatherPlace, onAdd: (WeatherPlace) -> Unit, onBack: () -> Unit) {
    val fromPhone = place.region == HERE_REGION
    var name by remember(place) { mutableStateOf(if (fromPhone) "" else place.name) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (fromPhone) "Położenie z telefonu" else listOf(place.name, place.region).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 13.sp, color = Luxury.TextMuted,
        )
        Text("Jak nazwać to miejsce na widgecie?", fontSize = 15.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Dom", "Praca", "Działka", "Szkoła") + if (fromPhone) emptyList() else listOf(place.name)).distinct().forEach { option ->
                Chip(option, on = name == option) { name = option }
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, singleLine = true,
            label = { Text("Nazwa na widgecie") }, placeholder = { Text("np. Dom, Działka u babci") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = FieldOutline),
            modifier = Modifier.fillMaxWidth(),
        )
        GoldButton("Dodaj miejsce", enabled = name.isNotBlank()) {
            val own = name.trim()
            val region = when {
                fromPhone -> "z telefonu"
                own == place.name -> place.region
                else -> listOf(place.name, place.region).filter { it.isNotBlank() }.joinToString(", ")
            }
            onAdd(place.copy(name = own, region = region))
        }
        Text(
            "Wybierz inne miejsce", fontSize = 13.sp, color = Luxury.TextMuted, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onBack).padding(vertical = 10.dp),
        )
    }
}

/** The first step: a town by name (Open-Meteo), or the phone's position once. */
@Composable
private fun FindPlace(source: WeatherSource, onFound: (WeatherPlace) -> Unit, onCancel: (() -> Unit)?) {
    val onAdd = onFound
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundPlace>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        found = null
        if (query.trim().length < 2) return@LaunchedEffect
        delay(400)
        found = source.search(query)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { here(context)?.let(onAdd) ?: run { problem = "Telefon nie podał położenia. Wpisz nazwę miejsca." } }
        else problem = "Bez zgody na położenie wpisz nazwę miejsca."
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Miejscowość") }, placeholder = { Text("np. Piaseczno") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = FieldOutline),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                problem = null
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    scope.launch { here(context)?.let(onAdd) ?: run { problem = "Telefon nie podał położenia. Wpisz nazwę miejsca." } }
                } else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Pin, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
            Text("Użyj miejsca z telefonu (raz)", fontSize = 15.sp, color = Luxury.GoldText)
        }
        problem?.let { FieldError(it) }
        when {
            query.trim().length < 2 -> Unit
            found == null -> Text("Szukam…", fontSize = 12.sp, color = Luxury.TextMuted)
            found!!.isEmpty() -> Text("Nic nie znaleziono (albo brak internetu).", fontSize = 12.sp, color = Luxury.TextMuted)
            else -> found!!.forEach { result ->
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, FieldOutline, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = "Dodaj") { onAdd(result.toPlace()) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(result.name, fontSize = 15.sp)
                    if (result.region.isNotBlank()) Text(result.region, fontSize = 12.sp, color = Luxury.TextMuted)
                }
            }
        }
        onCancel?.let {
            Text(
                "Anuluj dodawanie", fontSize = 13.sp, color = Luxury.TextMuted, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = it).padding(vertical = 10.dp),
            )
        }
    }
}

private const val HERE_REGION = "z telefonu"

/** "Warszawa" renamed "Dom": the town goes under the name ("Warszawa, mazowieckie"), so it is not lost. */
private fun renamed(place: WeatherPlace, name: String): WeatherPlace {
    if (name == place.name) return place
    val keepTown = place.region != HERE_REGION && !place.region.split(", ").contains(place.name) && place.name != "Tutaj"
    return place.copy(name = name, region = if (keepTown) listOf(place.name, place.region).filter { it.isNotBlank() }.joinToString(", ") else place.region)
}

/** The phone's rough position, asked once (no tracking): the last known one, else a fresh fix. */
@SuppressLint("MissingPermission")
private suspend fun here(context: Context): WeatherPlace? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    val location = last ?: providers.firstOrNull()?.let { provider ->
        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) { } }
            }
        }
    } ?: return null
    return WeatherPlace("Tutaj", location.latitude, location.longitude, HERE_REGION)
}
