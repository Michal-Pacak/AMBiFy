package pl.ambif.ambify.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleOrderTest {
    private val modules = listOf("1", "2", "3", "4", "5", "6", "7")

    @Test
    fun rotatedFrom_wrapsAround() {
        // Choosing module 6 from the panel: [6] [7] (logo) [1] [2], panel 3, 4, 5.
        assertEquals(listOf("6", "7", "1", "2", "3", "4", "5"), modules.rotatedFrom(5))
        assertEquals(modules, modules.rotatedFrom(7))
        assertEquals(listOf("7", "1", "2", "3", "4", "5", "6"), modules.rotatedFrom(-1))
    }

    @Test
    fun moved_reordersVisibleSequence() {
        // Window starts at 3: [3] [4] (logo) [5] [6], panel 7, 1, 2. Drag module 1 to the first slot;
        // the arrangement seen becomes the saved order.
        val visible = modules.rotatedFrom(2)
        val reordered = visible.moved(from = visible.indexOf("1"), to = 0)
        assertEquals(listOf("1", "3", "4", "5", "6", "7", "2"), reordered)
    }

    @Test
    fun resolveModuleOrder_dropsUnknownAndAppendsNew() {
        assertEquals(modules, resolveModuleOrder(null, modules))
        assertEquals(
            listOf("3", "1", "2", "4", "5", "6", "7"),
            resolveModuleOrder(listOf("3", "x", "1", "3", "2"), modules),
        )
    }

    @Test
    fun barLabels_fitTheRule() {
        BarModules.forEach { module ->
            assertTrue("Label too long for the bar: \"${module.label}\"", fitsBarLabelRule(module.label))
        }
    }

    @Test
    fun fitsBarLabelRule_limitsLinesAndWords() {
        assertTrue(fitsBarLabelRule("Kalendarz"))
        assertTrue(fitsBarLabelRule("Plan lekcji")) // "Plan" / "lekcji"
        assertFalse(fitsBarLabelRule("Komunikacja")) // one word of 11 characters
        assertFalse(fitsBarLabelRule("Lista rzeczy do kupienia")) // needs 3 lines
        assertFalse(fitsBarLabelRule(" "))
    }

    @Test
    fun carouselColumn_skipsLogoColumn() {
        assertEquals(0f, carouselColumn(0f))
        assertEquals(1f, carouselColumn(1f))
        assertEquals(2f, carouselColumn(1.5f)) // under the logo
        assertEquals(3f, carouselColumn(2f))
        assertEquals(4f, carouselColumn(3f))
        assertEquals(5f, carouselColumn(4f)) // just off the right edge
        assertEquals(-1f, carouselColumn(-1f)) // just off the left edge
    }
}
