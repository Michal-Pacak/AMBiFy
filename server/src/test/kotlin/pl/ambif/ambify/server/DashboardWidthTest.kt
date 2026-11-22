package pl.ambif.ambify.server

import kotlin.test.Test
import kotlin.test.assertEquals
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.Dashboards
import pl.ambif.ambify.shared.WidgetSlot

class DashboardWidthTest {
    @Test
    fun aHalfMadeFullPushesItsNeighbourBelow() {
        val before = Dashboard(listOf(
            DashboardWidget("top", "lessons", 0),
            DashboardWidget("a", "my_tasks", 1, WidgetSlot.LEFT),
            DashboardWidget("b", "deadlines", 1, WidgetSlot.RIGHT),
            DashboardWidget("end", "list", 2),
        ))
        val after = Dashboards.normalized(Dashboard(before.widgets.map { if (it.id == "b") it.copy(slot = WidgetSlot.FULL) else it }))
        assertEquals(listOf("top" to 0, "b" to 1, "a" to 2, "end" to 3), after.widgets.map { it.id to it.row })
        assertEquals(WidgetSlot.LEFT, after.widgets.single { it.id == "a" }.slot)
    }
}
