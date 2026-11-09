package pl.ambif.ambify.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import pl.ambif.ambify.server.releases.AppReleases
import pl.ambif.ambify.server.releases.polishDate
import pl.ambif.ambify.shared.AppPaths
import pl.ambif.ambify.shared.AppRelease

class ReleasesTest {
    private val dir: File = Files.createTempDirectory("ambify-releases").toFile()

    private fun publish(minVersionCode: Int = 0) {
        dir.resolve("ambify-0.7.0.apk").writeBytes(byteArrayOf(1, 2, 3))
        val release = AppRelease(10, "0.7.0", listOf("Listy zakupów"), "ambify-0.7.0.apk", "abc", 3, minVersionCode, "2026-11-03")
        dir.resolve(AppReleases.LATEST).writeText(Json.encodeToString(release))
    }

    private fun publishKids() {
        val kids = dir.resolve(AppReleases.KIDS_DIR).apply { mkdirs() }
        kids.resolve("ambify-0.7.0-11.apk").writeBytes(byteArrayOf(4, 5))
        val release = AppRelease(11, "0.7.0", emptyList(), "ambify-0.7.0-11.apk", "def", 2, 0, "2026-11-06")
        kids.resolve(AppReleases.LATEST).writeText(Json.encodeToString(release))
    }

    private fun app(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        val database = Db.open(dir.resolve("test.db").path)
        application { module(database, Diagnostics(emptyList()), testTokens(), releases = AppReleases(dir)) }
        block()
    }

    @Test
    fun nothingPublishedYet() = app {
        assertEquals(HttpStatusCode.NotFound, client.get(AppPaths.LATEST).status)
        assertContains(client.get(AppPaths.DOWNLOAD).bodyAsText(), "nie jest jeszcze opublikowana")
    }

    @Test
    fun newestReleaseAndItsFileWithoutSigningIn() = app {
        publish()
        val latest = Json.decodeFromString<AppRelease>(client.get(AppPaths.LATEST).bodyAsText())
        assertEquals(10, latest.versionCode)
        // Never below what the server itself still supports.
        assertEquals(Diagnostics.MIN_APP_VERSION_CODE, latest.minVersionCode)

        val apk = client.get("${AppPaths.APK}/ambify-0.7.0.apk")
        assertEquals(HttpStatusCode.OK, apk.status)
        assertEquals(listOf<Byte>(1, 2, 3), apk.bodyAsBytes().toList())

        val page = client.get(AppPaths.DOWNLOAD).bodyAsText()
        assertContains(page, "${AppPaths.APK}/ambify-0.7.0.apk")
        assertContains(page, "3 listopada 2026")
    }

    @Test
    fun higherMinimumFromThePublishWins() = app {
        publish(minVersionCode = 9)
        assertEquals(9, Json.decodeFromString<AppRelease>(client.get(AppPaths.LATEST).bodyAsText()).minVersionCode)
    }

    @Test
    fun onlyPublishedApkFiles() {
        publish()
        dir.resolve("secret.txt").writeText("x")
        val releases = AppReleases(dir)
        assertNull(releases.apk("secret.txt"))
        assertNull(releases.apk("../ambify-0.7.0.apk"))
        assertNull(releases.apk("ambify-0.6.0.apk"))
        assertEquals("ambify-0.7.0.apk", releases.apk("ambify-0.7.0.apk")?.name)
    }

    @Test
    fun kidsChannelIsSeparateFromTheMainOne() = app {
        publish()      // zwykły kanał: versionCode 10
        publishKids()  // kanał kids: versionCode 11, osobny plik
        assertEquals(10, Json.decodeFromString<AppRelease>(client.get(AppPaths.LATEST).bodyAsText()).versionCode)
        assertEquals(11, Json.decodeFromString<AppRelease>(client.get(AppPaths.KIDS_LATEST).bodyAsText()).versionCode)

        val apk = client.get("${AppPaths.KIDS_APK}/ambify-0.7.0-11.apk")
        assertEquals(HttpStatusCode.OK, apk.status)
        assertEquals(listOf<Byte>(4, 5), apk.bodyAsBytes().toList())
        // APK zwykłego kanału nie jest widoczny przez kanał kids i odwrotnie.
        assertEquals(HttpStatusCode.NotFound, client.get("${AppPaths.KIDS_APK}/ambify-0.7.0.apk").status)
    }

    @Test
    fun kidsDownloadPageWarnsToInstallOnlyOnAChildPhone() = app {
        publishKids()
        val page = client.get(AppPaths.KIDS_DOWNLOAD).bodyAsText()
        assertContains(page, "${AppPaths.KIDS_APK}/ambify-0.7.0-11.apk")
        assertContains(page, "tylko na telefonie dziecka")
    }

    @Test
    fun kidsChannelEmptyWhenNothingPublishedThere() = app {
        publish() // tylko zwykły kanał
        assertEquals(HttpStatusCode.NotFound, client.get(AppPaths.KIDS_LATEST).status)
    }

    @Test
    fun datesInPolish() {
        assertEquals("3 listopada 2026", polishDate("2026-11-03"))
        assertEquals("zle", polishDate("zle"))
    }
}
