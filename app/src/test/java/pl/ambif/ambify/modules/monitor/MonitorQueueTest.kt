package pl.ambif.ambify.modules.monitor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventType

class MonitorQueueTest {
    private fun queue() = MonitorQueue(File.createTempFile("monitor-queue", ".jsonl").apply { delete(); deleteOnExit() })
    private fun event(at: Long) = MonitorEvent(MonitorEventType.BOOT, at)

    @Test
    fun addPeekAndSize() {
        val q = queue()
        assertEquals(0, q.size())
        q.add(listOf(event(1), event(2)))
        q.add(listOf(event(3)))
        assertEquals(3, q.size())
        assertEquals(listOf(1L, 2L, 3L), q.peekAll().map { it.at })
    }

    @Test
    fun removeOldestAfterSend() {
        val q = queue()
        q.add(listOf(event(1), event(2), event(3)))
        q.removeFirst(2)
        assertEquals(listOf(3L), q.peekAll().map { it.at })
        q.removeFirst(5) // więcej niż jest → czyści do zera
        assertEquals(0, q.size())
    }

    @Test
    fun emptyAddDoesNothing() {
        val q = queue()
        q.add(emptyList())
        assertEquals(0, q.size())
        assertEquals(emptyList<MonitorEvent>(), q.peekAll())
    }

    @Test
    fun cappedToMax() {
        val q = queue()
        q.add((1..(MonitorQueue.MAX + 50)).map { event(it.toLong()) })
        assertEquals(MonitorQueue.MAX, q.size())
        // Zostają najnowsze: pierwszy w kolejce to 51 (50 najstarszych wypadło).
        assertEquals(51L, q.peekAll().first().at)
    }

    @Test
    fun survivesCorruptLine() {
        val file = File.createTempFile("monitor-queue", ".jsonl").apply { deleteOnExit() }
        file.writeText("to-nie-json\n")
        val q = MonitorQueue(file)
        q.add(listOf(event(7)))
        // Zła linia jest pomijana przy czytaniu, dobra wraca.
        assertEquals(listOf(7L), q.peekAll().map { it.at })
        assertFalse(q.peekAll().isEmpty())
    }
}
