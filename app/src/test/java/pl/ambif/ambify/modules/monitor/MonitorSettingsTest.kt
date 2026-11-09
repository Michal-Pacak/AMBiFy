package pl.ambif.ambify.modules.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.shared.MonitorEventType

class MonitorSettingsTest {
    private val base = MonitorSnapshot(
        autoTime = true,
        privateDns = "hostname:bartosz.dns.nextdns.io",
        safeMode = false,
        accessibilityServices = "com.google.android.apps.accessibility.familylink",
        devSettings = false,
        adb = false,
    )

    @Test
    fun firstReadReportsNothing() {
        assertEquals(emptyList<Any>(), MonitorSettings.diff(null, base, at = 1))
    }

    @Test
    fun noChangeNoEvents() {
        assertEquals(emptyList<Any>(), MonitorSettings.diff(base, base, at = 1))
    }

    @Test
    fun turningOffAutoTimeIsSuspicious() {
        val events = MonitorSettings.diff(base, base.copy(autoTime = false), at = 5)
        assertEquals(1, events.size)
        assertEquals(MonitorEventType.AUTO_TIME, events[0].type)
        assertTrue(events[0].suspicious)
        assertEquals(5L, events[0].at)
    }

    @Test
    fun turningAutoTimeBackOnIsNotSuspicious() {
        val off = base.copy(autoTime = false)
        val events = MonitorSettings.diff(off, base, at = 9)
        assertEquals(1, events.size)
        assertEquals(MonitorEventType.AUTO_TIME, events[0].type)
        assertTrue(!events[0].suspicious)
    }

    @Test
    fun privateDnsChangeIsSuspicious() {
        val events = MonitorSettings.diff(base, base.copy(privateDns = "off"), at = 2)
        assertEquals(listOf(MonitorEventType.PRIVATE_DNS), events.map { it.type })
        assertTrue(events[0].suspicious)
        assertTrue(events[0].detail.contains("off"))
    }

    @Test
    fun accessibilityAndSafeModeAndDevAllFlagged() {
        val changed = base.copy(
            safeMode = true,
            accessibilityServices = "",
            devSettings = true,
            adb = true,
        )
        val types = MonitorSettings.diff(base, changed, at = 3).map { it.type }
        assertTrue(types.contains(MonitorEventType.SAFE_MODE))
        assertTrue(types.contains(MonitorEventType.ACCESSIBILITY))
        assertTrue(types.contains(MonitorEventType.DEV_SETTINGS))
    }
}
