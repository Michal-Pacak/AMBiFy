package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.settings.SettingsLayout
import pl.ambif.ambify.shared.WeatherPlace
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/**
 * Prognoza (0.7.8, canvas board 5): opened by a tap on the Pogoda widget [widgetId], on the place it showed.
 * Now in full, today hour by hour, and 16 days; a tap on a day opens its hours. A part of the Dashboard.
 */
@Composable
fun ForecastScreen(widgetId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = LocalAppSession.current
    val state by session.state.collectAsState()
    val communityId = (state as? SessionState.SignedIn)?.me?.communityId
    val store = remember { DashboardStore(context) }
    val source = remember { WeatherSource(context) }
    val places = remember(communityId, widgetId) {
        communityId?.let { store.load(it) }?.widgets?.firstOrNull { it.id == widgetId }?.let { WeatherPlace.of(it) }.orEmpty()
    }
    var index by remember(widgetId) { mutableStateOf(store.person(widgetId)?.toIntOrNull()?.coerceIn(0, (places.size - 1).coerceAtLeast(0)) ?: 0) }
    val place = places.getOrNull(index)
    var forecast by remember(place) { mutableStateOf(place?.let { source.cached(it) }) }
    var offline by remember(place) { mutableStateOf(false) }
    var openDay by remember(place) { mutableStateOf<java.time.LocalDate?>(null) }
    LaunchedEffect(place) {
        if (place == null) return@LaunchedEffect
        val fresh = source.forecast(place)
        if (fresh != null) forecast = fresh else offline = true
    }
    val now = LocalDateTime.now()

    SettingsLayout(title = "Prognoza", onBack = onBack, subtitle = place?.name.orEmpty(), help = pl.ambif.ambify.ui.help.Help.FORECAST) {
        if (places.size > 1) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEachIndexed { i, p ->
                    val on = i == index
                    val shape = RoundedCornerShape(20.dp)
                    Text(
                        p.name, fontSize = 14.sp, fontFamily = Luxury.HeadingFont, color = if (on) Luxury.OnGold else Luxury.Text,
                        modifier = Modifier.clip(shape)
                            .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.dp, Luxury.Outline, shape))
                            .clickable(role = Role.Tab) { index = i; store.savePerson(widgetId, i.toString()) }
                            .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        val f = forecast
        if (place == null) {
            MessageBar(MessageKind.INFO, "Brak miejsca", "Dodaj miejsce w ustawieniach widgetu Pogoda.")
            return@SettingsLayout
        }
        if (f == null) {
            Text(if (offline) "Bez internetu — prognoza pojawi się, gdy wróci połączenie." else "Wczytywanie prognozy…", color = Luxury.TextMuted)
            return@SettingsLayout
        }
        if (offline) MessageBar(MessageKind.STATUS, "Bez internetu", "Stan z ${java.text.SimpleDateFormat("H:mm", java.util.Locale("pl")).format(java.util.Date(f.fetchedAt))}.")

        // Now, in full.
        LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            val t = f.current.temperature
            Box {
                if (t <= -10) FrostOverlay(Modifier.matchParentSize())
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        WeatherIcon(f.current.code, 64.dp)
                        Box { Temp(t, 52.sp); if (t >= 32) HeatHaze(Modifier.matchParentSize()) }
                        Column {
                            Text(weatherWords(f.current.code), fontSize = 15.sp)
                            Text("odczuwalna ${deg(f.current.feelsLike)}", fontSize = 13.sp, color = Luxury.TextMuted)
                        }
                    }
                    val today = f.today()
                    val gust = f.hourly.gusts.getOrNull(f.hourly.time.indexOfFirst { runCatching { LocalDateTime.parse(it) }.getOrNull()?.let { h -> !h.isBefore(now.withMinute(0)) } == true })
                    val cells = listOfNotNull(
                        "deszcz" to "${today.rain ?: 0}%",
                        "wiatr" to "${Math.round(f.current.wind)} km/h",
                        gust?.let { "porywy" to "${Math.round(it)} km/h" },
                        f.current.humidity?.let { "wilgotność" to "$it%" },
                        f.current.pressure?.let { "ciśnienie" to "${Math.round(it)} hPa" },
                        f.sun()?.let { "słońce" to it },
                    )
                    cells.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, value) -> Cell(label, value, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // Today hour by hour.
        LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            LuxurySectionTitle("Dziś co godzinę")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                f.nextHours(now).forEachIndexed { i, hour -> HourCell(if (i == 0) "teraz" else "${hour.time.hour}", hour) }
            }
        }

        // 16 days, one shared min–max scale.
        val days = f.days()
        val lo = days.mapNotNull { it.min }.minOrNull() ?: 0.0
        val hi = days.mapNotNull { it.max }.maxOrNull() ?: 1.0
        LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp), spacing = 0.dp) {
            LuxurySectionTitle("${days.size} dni · ${place.name}", modifier = Modifier.padding(bottom = 6.dp))
            days.forEachIndexed { i, day ->
                val open = openDay == day.date
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Godziny: ${day.date}") { openDay = if (open) null else day.date },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.width(62.dp)) {
                            Text(if (i == 0) "Dziś" else dayShort(day.date).replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                            Text(day.date.format(DateTimeFormatter.ofPattern("d.MM")), fontSize = 11.sp, color = Luxury.TextMuted)
                        }
                        WeatherIcon(day.code ?: 3, 26.dp)
                        Text(day.rain?.takeIf { it >= 10 }?.let { "$it%" } ?: "", fontSize = 12.sp, color = Luxury.Info, modifier = Modifier.width(36.dp))
                        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) { day.min?.let { Temp(it, 13.sp, FontWeight.Normal) } }
                        RangeBar(day.min ?: lo, day.max ?: hi, lo, hi, Modifier.weight(1f))
                        Box(Modifier.width(34.dp)) { day.max?.let { Temp(it, 13.sp) } }
                        Icon(AppIcons.Forward, contentDescription = null, tint = Luxury.TextMuted, modifier = Modifier.rotate(if (open) 90f else 0f))
                    }
                    if (open) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Luxury.Outline.copy(alpha = 0.2f)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                f.hoursOn(day.date).forEach { hour -> HourCell("${hour.time.hour}:00", hour) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                day.windMax?.let { Cell("wiatr", "${Math.round(it)} km/h", Modifier.weight(1f)) }
                                day.precipitation?.let { Cell("opad", "%.1f mm".format(java.util.Locale("pl"), it), Modifier.weight(1f)) }
                                f.sun(i)?.let { Cell("słońce", it, Modifier.weight(1f)) }
                            }
                            if (f.hoursOn(day.date).isEmpty()) Text("Godzinowa prognoza tak daleko jest niedostępna.", fontSize = 12.sp, color = Luxury.TextMuted)
                        }
                    }
                    if (i == 9) Text("Dalsze dni: prognoza orientacyjna.", fontSize = 11.sp, color = Luxury.TextMuted, modifier = Modifier.padding(vertical = 4.dp))
                    if (i < days.lastIndex) LuxuryDivider()
                }
            }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Luxury.Outline.copy(alpha = 0.25f)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 11.sp, color = Luxury.TextMuted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun HourCell(label: String, hour: Forecast.Hour) {
    Column(modifier = Modifier.widthIn(min = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Luxury.TextMuted)
        WeatherIcon(hour.code, 24.dp, night = hour.time.hour !in 6..19)
        Temp(hour.temperature, 14.sp)
        Text(if (hour.rain >= 10) "${hour.rain}%" else " ", fontSize = 11.sp, color = Luxury.Info)
    }
}

/** The day's min–max on the 16 days' shared scale: at once you see when it gets warmer. */
@Composable
private fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, modifier: Modifier = Modifier) {
    val span = (hi - lo).coerceAtLeast(1.0)
    val from = ((min - lo) / span).toFloat().coerceIn(0f, 1f)
    val to = ((max - lo) / span).toFloat().coerceIn(from, 1f)
    androidx.compose.foundation.Canvas(modifier.height(6.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(Luxury.Outline.copy(alpha = 0.5f), cornerRadius = r)
        drawRoundRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(tempColor(min), tempColor(max)), startX = size.width * from, endX = size.width * to),
            topLeft = androidx.compose.ui.geometry.Offset(size.width * from, 0f),
            size = androidx.compose.ui.geometry.Size((size.width * (to - from)).coerceAtLeast(size.height), size.height),
            cornerRadius = r,
        )
    }
}
