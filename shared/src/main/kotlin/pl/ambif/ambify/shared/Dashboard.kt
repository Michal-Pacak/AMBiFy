package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

object DashboardPaths {
    /** GET / PUT [Dashboard]: the caller's own Dashboard in the current community (see [ApiHeaders.COMMUNITY]). */
    const val DASHBOARD = "/me/dashboard"
}

/**
 * The widget catalog. [module] (null = a general widget, always offered) decides where a widget is offered (only modules switched on in the community) and
 * whether it is shown at all; [multi]: a widget with settings may be on the Dashboard more than once (e.g.
 * "Lekcje" of each child), one without settings only once; [half]: it also fits half of the width.
 * Saved by [key], so a phone that does not know a newer widget keeps it untouched.
 */
enum class WidgetType(
    val key: String,
    val module: ModuleId?,
    val label: String,
    val description: String,
    val multi: Boolean,
    val half: Boolean = false,
    val premium: Feature? = null,
) {
    LESSONS(
        "lessons", ModuleId.TIMETABLE, "Lekcje",
        "Bieżąca lekcja, kolejne i godzina końca – zawsze pod ręką.",
        multi = true,
    ),

    /** One chosen list ([DashboardWidget.people] = its id): check things off from the Dashboard (board 19). */
    LIST(
        "list", ModuleId.LISTS, "Lista",
        "Wybrana lista: odhaczaj prosto z pulpitu. Można dodać kilka, każdy z inną listą.",
        multi = true,
    ),
    MY_TASKS(
        "my_tasks", ModuleId.LISTS, "Moje zadania",
        "Zadania dla Ciebie ze wszystkich list, pilne na górze.",
        multi = false, half = true,
    ),
    DEADLINES(
        "deadlines", ModuleId.LISTS, "Najbliższe terminy",
        "Pilne, po terminie i najbliższe ze wszystkich list terminów.",
        multi = false, half = true,
    ),

    /** All the lists one sees, folded to their kinds ("Listy rzeczy (2)"); a kind opens to names and states. */
    ACTIVE_LISTS(
        "active_lists", ModuleId.LISTS, "Aktywne listy",
        "Wszystkie listy zwinięte do typów — rozwiń typ, by zobaczyć nazwy i stan.",
        multi = false,
    ),

    /** Extra activities of the chosen children ([DashboardWidget.people]) today and the next days (board 15 A). */
    EXTRAS(
        "extras", ModuleId.TIMETABLE, "Zajęcia dodatkowe",
        "Zajęcia dodatkowe dzieci dziś i w najbliższe dni, z miejscem.",
        multi = true, half = true,
    ),

    /** The next school day of one child: start, end, number of lessons, changes and activities after (board 15 B). */
    TOMORROW(
        "tomorrow", ModuleId.TIMETABLE, "Jutro w szkole",
        "Od której do której jutro, ile lekcji, zastępstwa i odwołane, zajęcia po szkole.",
        multi = true,
    ),

    /** The weather in up to 5 places ([DashboardWidget.people] = [WeatherPlace.encode]), swiped (board 13). */
    WEATHER(
        "weather", null, "Pogoda",
        "Pogoda teraz i w najbliższych godzinach, dla kilku miejsc — przełączasz palcem albo strzałkami.",
        multi = false, half = true,
    );

    companion object {
        fun of(key: String): WidgetType? = entries.firstOrNull { it.key == key }
        fun of(module: ModuleId): List<WidgetType> = entries.filter { it.module == module }
        val general: List<WidgetType> get() = entries.filter { it.module == null }
    }
}

/** How much a widget shows (never more than [Dashboards.MAX_HEIGHT_DP]); e.g. "Lekcje": 1, 3 or 5 lessons. */
@Serializable
enum class WidgetHeight(val label: String) { SMALL("Mała"), MEDIUM("Średnia"), LARGE("Duża") }

/** Where in its row: the whole width, or the left / right half (the other half may stay empty). */
@Serializable
enum class WidgetSlot { FULL, LEFT, RIGHT }

/**
 * One widget on the Dashboard. [row] orders the rows from the top; two half widgets share a row (LEFT and RIGHT).
 * [people]: whose data it shows (for "Lekcje" the students' ids; several = a switch in the widget; for "Lista" the
 * list's id — kept here so that older phones keep it untouched).
 */
@Serializable
data class DashboardWidget(
    val id: String,
    val type: String,
    val row: Int,
    val slot: WidgetSlot = WidgetSlot.FULL,
    val height: WidgetHeight = WidgetHeight.MEDIUM,
    val people: List<String> = emptyList(),
)

@Serializable
data class Dashboard(val widgets: List<DashboardWidget> = emptyList())

object Dashboards {
    const val MAX_WIDGETS = 30
    const val MAX_HEIGHT_DP = 300
    const val MAX_HALF_HEIGHT_DP = 220

    /**
     * The same Dashboard with rows numbered 0, 1, 2… without gaps, at most one widget per place (a full widget
     * alone in its row, at most one LEFT and one RIGHT), ids unique. Used by the server before saving and by the
     * app after every change.
     */
    fun normalized(dashboard: Dashboard): Dashboard {
        val seen = mutableSetOf<String>()
        val unique = dashboard.widgets.filter { it.id.isNotBlank() && seen.add(it.id) }.take(MAX_WIDGETS)
        val result = mutableListOf<DashboardWidget>()
        var row = 0
        for ((_, inRow) in unique.groupBy { it.row }.toSortedMap()) {
            val full = inRow.filter { it.slot == WidgetSlot.FULL }
            val halves = inRow.filter { it.slot != WidgetSlot.FULL }
            full.forEach { result += it.copy(row = row++) }
            val left = halves.firstOrNull { it.slot == WidgetSlot.LEFT }
            val right = halves.firstOrNull { it.slot == WidgetSlot.RIGHT }
            if (left != null || right != null) {
                listOfNotNull(left, right).forEach { result += it.copy(row = row) }
                row++
            }
            // Extra halves on a taken side: each in a row of its own.
            (halves - setOfNotNull(left, right)).forEach { result += it.copy(row = row++) }
        }
        return Dashboard(result)
    }
}

/**
 * One place of the weather widget, kept in [DashboardWidget.people] as text ("place|name|lat|lon|region"), so
 * older phones keep it untouched. [name] is what the widget shows ("Dom"), [region] where it is.
 */
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double, val region: String = "") {
    fun encode(): String = listOf(PREFIX, clean(name), "%.4f".format(java.util.Locale.ROOT, latitude), "%.4f".format(java.util.Locale.ROOT, longitude), clean(region)).joinToString("|")

    companion object {
        const val PREFIX = "place"
        const val MAX = 5

        private fun clean(text: String) = text.replace("|", " ").trim().take(40)

        fun decode(text: String): WeatherPlace? {
            val parts = text.split("|")
            if (parts.size < 4 || parts[0] != PREFIX) return null
            val lat = parts[2].toDoubleOrNull() ?: return null
            val lon = parts[3].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || parts[1].isBlank()) return null
            return WeatherPlace(parts[1], lat, lon, parts.getOrElse(4) { "" })
        }

        fun of(widget: DashboardWidget): List<WeatherPlace> = widget.people.mapNotNull(::decode).take(MAX)
    }
}
