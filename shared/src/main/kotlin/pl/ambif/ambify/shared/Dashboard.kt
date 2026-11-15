package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

object DashboardPaths {
    /** GET / PUT [Dashboard]: the caller's own Dashboard in the current community (see [ApiHeaders.COMMUNITY]). */
    const val DASHBOARD = "/me/dashboard"
}

/**
 * The widget catalog. [module] decides where a widget is offered (only modules switched on in the community) and
 * whether it is shown at all; [multi]: a widget with settings may be on the Dashboard more than once (e.g.
 * "Lekcje" of each child), one without settings only once; [half]: it also fits half of the width.
 * Saved by [key], so a phone that does not know a newer widget keeps it untouched.
 */
enum class WidgetType(
    val key: String,
    val module: ModuleId,
    val label: String,
    val description: String,
    val multi: Boolean,
    val half: Boolean = false,
    val premium: Feature? = null,
) {
    LESSONS(
        "lessons", ModuleId.TIMETABLE, "Lekcje",
        "najbliższe lekcje i godzina końca; po lekcjach start w najbliższy dzień szkolny",
        multi = true,
    );

    companion object {
        fun of(key: String): WidgetType? = entries.firstOrNull { it.key == key }
        fun of(module: ModuleId): List<WidgetType> = entries.filter { it.module == module }
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
 * [people]: whose data it shows (for "Lekcje" the students' ids; several = a switch in the widget).
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
