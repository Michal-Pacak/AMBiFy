package pl.ambif.ambify.features.home

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.WeatherPlace
import pl.ambif.ambify.ui.icons.AppIcons

/**
 * The weather widget's data (board 13) straight from Open-Meteo (free, no key, no account): only the place's
 * coordinates are sent. Kept on the phone for [FRESH_MS], so the widget shows the last forecast also offline.
 */
@Serializable
data class Forecast(
    val current: Current,
    val hourly: Hourly,
    val daily: Daily,
    val fetchedAt: Long = 0,
) {
    @Serializable
    data class Current(
        val time: String,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("apparent_temperature") val feelsLike: Double,
        @SerialName("weather_code") val code: Int,
        @SerialName("wind_speed_10m") val wind: Double,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("pressure_msl") val pressure: Double? = null,
    )

    @Serializable
    data class Hourly(
        val time: List<String>,
        @SerialName("temperature_2m") val temperature: List<Double?>,
        @SerialName("precipitation_probability") val rain: List<Int?>,
        @SerialName("weather_code") val code: List<Int?>,
        @SerialName("wind_gusts_10m") val gusts: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m") val wind: List<Double?> = emptyList(),
    )

    @Serializable
    data class Daily(
        val time: List<String>,
        @SerialName("temperature_2m_max") val max: List<Double?>,
        @SerialName("temperature_2m_min") val min: List<Double?>,
        @SerialName("precipitation_probability_max") val rain: List<Int?>,
        @SerialName("weather_code") val code: List<Int?>,
        val sunrise: List<String> = emptyList(),
        val sunset: List<String> = emptyList(),
        @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
    )

    /** One day of the 16 (0.7.8, board 5). */
    data class FullDay(
        val date: java.time.LocalDate, val min: Double?, val max: Double?, val rain: Int?, val code: Int?,
        val sunrise: String?, val sunset: String?, val precipitation: Double?, val windMax: Double?,
    )

    fun days(): List<FullDay> = daily.time.indices.mapNotNull { i ->
        val date = runCatching { java.time.LocalDate.parse(daily.time[i]) }.getOrNull() ?: return@mapNotNull null
        FullDay(
            date, daily.min.getOrNull(i), daily.max.getOrNull(i), daily.rain.getOrNull(i), daily.code.getOrNull(i),
            daily.sunrise.getOrNull(i)?.substringAfter('T'), daily.sunset.getOrNull(i)?.substringAfter('T'),
            daily.precipitation.getOrNull(i), daily.windMax.getOrNull(i),
        )
    }

    /** "7:21–15:31" today (or on [index]). */
    fun sun(index: Int = 0): String? {
        val up = daily.sunrise.getOrNull(index)?.substringAfter('T')?.trimStart('0') ?: return null
        val down = daily.sunset.getOrNull(index)?.substringAfter('T') ?: return null
        return "$up–$down"
    }

    /** All hours of [date] every [step] hours. */
    fun hoursOn(date: java.time.LocalDate, step: Int = 3): List<Hour> = allHours().filter { it.time.toLocalDate() == date && it.time.hour % step == 0 }

    /** The next [count] whole hours from now. */
    fun nextHours(now: LocalDateTime, count: Int = 24): List<Hour> = allHours().filter { it.time.isAfter(now.minusHours(1)) }.take(count)

    private fun allHours(): List<Hour> = hourly.time.indices.mapNotNull { i ->
        val t = runCatching { LocalDateTime.parse(hourly.time[i]) }.getOrNull() ?: return@mapNotNull null
        Hour(t, hourly.temperature.getOrNull(i) ?: return@mapNotNull null, hourly.rain.getOrNull(i) ?: 0, hourly.code.getOrNull(i) ?: 0)
    }

    /** One of the next hours. */
    data class Hour(val time: LocalDateTime, val temperature: Double, val rain: Int, val code: Int)

    /** The next [count] hours every [step] hours, from the coming full hour. */
    fun hours(now: LocalDateTime, step: Int = 3, count: Int = 4): List<Hour> {
        val all = hourly.time.indices.mapNotNull { i ->
            val t = runCatching { LocalDateTime.parse(hourly.time[i]) }.getOrNull() ?: return@mapNotNull null
            Hour(t, hourly.temperature.getOrNull(i) ?: return@mapNotNull null, hourly.rain.getOrNull(i) ?: 0, hourly.code.getOrNull(i) ?: 0)
        }.filter { it.time.isAfter(now) }
        return all.filterIndexed { i, _ -> i % step == step - 1 }.take(count)
    }

    /** A warning for the next 24 hours, the most serious first: storm, strong wind, frost. */
    fun warning(now: LocalDateTime): String? {
        val next = hourly.time.indices.filter { i ->
            val t = runCatching { LocalDateTime.parse(hourly.time[i]) }.getOrNull() ?: return@filter false
            t.isAfter(now) && t.isBefore(now.plusHours(24))
        }
        if (next.any { (hourly.code.getOrNull(it) ?: 0) >= 95 }) return "Możliwa burza w ciągu doby"
        val gust = next.mapNotNull { hourly.gusts.getOrNull(it) }.maxOrNull()
        if (gust != null && gust >= 60) return "Silny wiatr, porywy do ${gust.toInt()} km/h"
        val cold = next.mapNotNull { hourly.temperature.getOrNull(it) }.minOrNull()
        if (cold != null && cold < 0) return "Mróz do ${deg(cold)} — rano może być ślisko"
        return null
    }

    fun today() = Day(daily.min.getOrNull(0), daily.max.getOrNull(0), daily.rain.getOrNull(0), daily.code.getOrNull(0))
    fun tomorrow() = Day(daily.min.getOrNull(1), daily.max.getOrNull(1), daily.rain.getOrNull(1), daily.code.getOrNull(1))

    data class Day(val min: Double?, val max: Double?, val rain: Int?, val code: Int?)
}

/** A place found by name ("Piaseczno, mazowieckie"). */
@Serializable
data class FoundPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("admin1") val province: String? = null,
    val country: String? = null,
) {
    val region: String get() = listOfNotNull(province, country?.takeIf { it != "Polska" }).joinToString(", ")
    fun toPlace() = WeatherPlace(name, latitude, longitude, region)
}

@Serializable
private data class FoundPlaces(val results: List<FoundPlace> = emptyList())

/** "3°", "−4°" (a real minus). */
fun deg(value: Double): String {
    val rounded = Math.round(value).toInt()
    return (if (rounded < 0) "−${-rounded}" else "$rounded") + "°"
}

/** The WMO weather code in Polish words, and its line icon. */
fun weatherWords(code: Int): String = when (code) {
    0 -> "bezchmurnie"
    1 -> "prawie bezchmurnie"
    2 -> "częściowe zachmurzenie"
    3 -> "pochmurno"
    45, 48 -> "mgła"
    in 51..57 -> "mżawka"
    in 61..67 -> "deszcz"
    in 71..77 -> "śnieg"
    in 80..82 -> "przelotny deszcz"
    85, 86 -> "przelotny śnieg"
    in 95..99 -> "burza"
    else -> ""
}

fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> AppIcons.Sun
    2 -> AppIcons.SunCloud
    3 -> AppIcons.Cloud
    45, 48 -> AppIcons.Fog
    in 51..67, in 80..82 -> AppIcons.Rain
    in 71..77, 85, 86 -> AppIcons.Snow
    in 95..99 -> AppIcons.Storm
    else -> AppIcons.Cloud
}

class WeatherSource(context: Context) {
    private val preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // "v2": 0.7.8 asks for 16 days and more fields; older copies on the phone are skipped.
    private fun key(place: WeatherPlace) = "v2:%.2f,%.2f".format(Locale.ROOT, place.latitude, place.longitude)

    /** The forecast kept on the phone (maybe old), for showing at once. */
    fun cached(place: WeatherPlace): Forecast? =
        preferences.getString(key(place), null)?.let { runCatching { json.decodeFromString<Forecast>(it) }.getOrNull() }

    /** A fresh forecast (from the phone when younger than [FRESH_MS]); null without a connection. */
    suspend fun forecast(place: WeatherPlace, now: Long = System.currentTimeMillis()): Forecast? {
        cached(place)?.takeIf { now - it.fetchedAt < FRESH_MS }?.let { return it }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, place.latitude, place.longitude) +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m,pressure_msl" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,wind_gusts_10m,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise,sunset,precipitation_sum,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=16"
        val text = fetch(url) ?: return null
        val forecast = runCatching { json.decodeFromString<Forecast>(text) }.getOrNull()?.copy(fetchedAt = now) ?: return null
        preferences.edit().putString(key(place), json.encodeToString(Forecast.serializer(), forecast)).apply()
        return forecast
    }

    /** Places by name, Polish names first; empty without a connection. */
    suspend fun search(name: String): List<FoundPlace> {
        if (name.trim().length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=pl&format=json&name=" + URLEncoder.encode(name.trim(), "UTF-8")
        val text = fetch(url) ?: return emptyList()
        return runCatching { json.decodeFromString<FoundPlaces>(text).results }.getOrDefault(emptyList())
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "AMBiFy")
            try {
                if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        const val FRESH_MS = 30 * 60 * 1000L
    }
}
