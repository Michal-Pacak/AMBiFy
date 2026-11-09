package pl.ambif.ambify.modules.update

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.features.update.checkedText
import pl.ambif.ambify.shared.AppRelease

class UpdateRulesTest {
    private fun release(code: Int, min: Int = 0) = AppRelease(code, "0.7.0", file = "ambify-0.7.0-$code.apk", sha256 = "", sizeBytes = 1, minVersionCode = min)

    @Test
    fun newerAndForced() {
        assertTrue(UpdateRules.isNewer(release(10), installed = 9))
        assertFalse(UpdateRules.isNewer(release(9), installed = 9))
        assertFalse(UpdateRules.isForced(release(10, min = 9), installed = 9))
        assertTrue(UpdateRules.isForced(release(10, min = 10), installed = 9))
    }

    @Test
    fun checksOnEveryStartThenEverySixHours() {
        val now = 100 * UpdateRules.CHECK_EVERY_MS
        assertTrue(UpdateRules.shouldCheck(now, lastChecked = now - 1000, checkedThisRun = false))
        assertFalse(UpdateRules.shouldCheck(now, lastChecked = now - 1000, checkedThisRun = true))
        assertTrue(UpdateRules.shouldCheck(now, lastChecked = now - UpdateRules.CHECK_EVERY_MS, checkedThisRun = true))
    }

    @Test
    fun nightIsTheNextThreeOClock() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val evening = ZonedDateTime.of(2026, 11, 3, 21, 30, 0, 0, warsaw)
        assertEquals(ZonedDateTime.of(2026, 11, 4, 3, 0, 0, 0, warsaw), UpdateRules.nightInstallAt(evening))
        val earlyMorning = ZonedDateTime.of(2026, 11, 4, 1, 0, 0, 0, warsaw)
        assertEquals(ZonedDateTime.of(2026, 11, 4, 3, 0, 0, 0, warsaw), UpdateRules.nightInstallAt(earlyMorning))
    }

    @Test
    fun sha256OfTheDownload() {
        val file = File.createTempFile("ambify", ".apk").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateRules.sha256(file))
    }

    @Test
    fun whenLastChecked() {
        val now = System.currentTimeMillis()
        assertEquals("jeszcze nie sprawdzono", checkedText(0L, now))
        assertTrue(checkedText(now, now).startsWith("Sprawdzono dziś "))
        assertTrue(checkedText(now - 24 * 60 * 60 * 1000L, now).startsWith("Sprawdzono wczoraj "))
    }
}
