package pl.ambif.ambify.ui.navigation

/** Number of module slots on the collapsed bar: two on each side of the logo. */
internal const val BarSlots = 4

/** Navigation bar label limits (docs/03_Naming_Convention.md, "Navigation Bar Labels"). */
internal const val BarLabelMaxLineLength = 10
internal const val BarLabelMaxLines = 2

/** True when [label] fits in [BarLabelMaxLines] lines of [BarLabelMaxLineLength] characters, broken between words. */
internal fun fitsBarLabelRule(label: String): Boolean {
    val words = label.split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty() || words.any { it.length > BarLabelMaxLineLength }) return false
    var lines = 1
    var lineLength = words.first().length
    for (word in words.drop(1)) {
        if (lineLength + 1 + word.length <= BarLabelMaxLineLength) {
            lineLength += 1 + word.length
        } else {
            lines++
            lineLength = word.length
        }
    }
    return lines <= BarLabelMaxLines
}

/** Routes saved by older app versions under a name that has changed since. */
private val RenamedRoutes = mapOf("shopping" to "lists", "account" to "settings")

/** The circular list read from [start]: element 0 is the first module of the bar window. */
internal fun <T> List<T>.rotatedFrom(start: Int): List<T> =
    if (isEmpty()) this else List(size) { this[(start + it).mod(size)] }

/** Copy of the list with the element at [from] moved to index [to]. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

/**
 * Saved module order reconciled with the modules the app knows:
 * unknown or repeated routes are dropped, modules missing from the saved order are appended.
 */
internal fun resolveModuleOrder(saved: List<String>?, known: List<String>): List<String> {
    val kept = saved.orEmpty().map { RenamedRoutes[it] ?: it }.filter { it in known }.distinct()
    return kept + known.filterNot { it in kept }
}

/**
 * Bar column (0..4, the logo takes column 2) of a module at carousel position [position]
 * (0..3 are the visible slots). Between positions 1 and 2 a module passes under the logo.
 */
internal fun carouselColumn(position: Float): Float = when {
    position <= 1f -> position
    position < 2f -> 1f + 2f * (position - 1f)
    else -> position + 1f
}
