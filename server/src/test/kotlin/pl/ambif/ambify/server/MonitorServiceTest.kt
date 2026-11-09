package pl.ambif.ambify.server

import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.monitor.MonitorService
import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventBatch
import pl.ambif.ambify.shared.MonitorEventType

class MonitorServiceTest {
    private val dir = Files.createTempDirectory("monitor").toFile().apply { deleteOnExit() }
    private val service = MonitorService(dir) { Instant.parse("2026-11-06T22:30:00Z") }
    private val session = UserSession(userId = "u1", sessionId = "s1", communityId = "c1")

    @Test
    fun writesOneJsonlLinePerEvent() {
        service.record(
            session,
            MonitorEventBatch(
                device = "SM-A155",
                events = listOf(
                    MonitorEvent(MonitorEventType.TIME_CHANGED, at = 1000, detail = "cofnięto o 2 h", suspicious = true),
                    MonitorEvent(MonitorEventType.APP_FOREGROUND, at = 2000, detail = "com.google.android.youtube"),
                ),
            ),
        )
        val lines = service.fileFor("c1", "u1").readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"type\":\"TIME_CHANGED\""))
        assertTrue(lines[0].contains("\"suspicious\":true"))
        assertTrue(lines[0].contains("\"device\":\"SM-A155\""))
        assertTrue(lines[0].contains("\"receivedAt\":\"2026-11-06T22:30:00Z\""))
        assertTrue(lines[1].contains("\"type\":\"APP_FOREGROUND\""))
    }

    @Test
    fun appendsAcrossCalls() {
        val one = MonitorEventBatch("dev", listOf(MonitorEvent(MonitorEventType.BOOT, at = 1)))
        service.record(session, one)
        service.record(session, one)
        assertEquals(2, service.fileFor("c1", "u1").readLines().filter { it.isNotBlank() }.size)
    }

    @Test
    fun emptyBatchWritesNothing() {
        service.record(session, MonitorEventBatch("dev", emptyList()))
        assertFalse(service.fileFor("c1", "u1").exists())
    }

    @Test
    fun separateFilePerChildAndSafeName() {
        service.record(UserSession("../etc", "s", "c/1"), MonitorEventBatch("d", listOf(MonitorEvent(MonitorEventType.BOOT, 1))))
        val file = service.fileFor("c/1", "../etc")
        assertEquals("c_1-.._etc.jsonl", file.name)
        assertTrue(file.exists())
    }

    @Test
    fun missingCommunityStillWritesToABrakFile() {
        service.record(UserSession("solo", "s", null), MonitorEventBatch("d", listOf(MonitorEvent(MonitorEventType.BOOT, 1))))
        assertTrue(service.fileFor(null, "solo").name.startsWith("brak-solo"))
        assertTrue(service.fileFor(null, "solo").exists())
    }
}
