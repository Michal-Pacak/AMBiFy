package pl.ambif.ambify.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import pl.ambif.ambify.server.releases.AppReleases
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.AppRelease
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.MyReport
import pl.ambif.ambify.shared.NewReport
import pl.ambif.ambify.shared.PlatformReport
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ReportPaths
import pl.ambif.ambify.shared.ReportStatus
import pl.ambif.ambify.shared.ReportUpdate
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens
import pl.ambif.ambify.shared.versionAtLeast

class ReportsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var releasesDir: File

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dir = Files.createTempDirectory("ambify-reports").toFile()
        releasesDir = dir.resolve("releases").apply { mkdirs() }
        val database = Db.open(dir.resolve("test.db").path)
        application {
            module(database, Diagnostics(emptyList()), testTokens(), releases = AppReleases(releasesDir), reportsDir = dir.resolve("reports"))
        }
        block()
    }

    private fun publish(version: String) {
        releasesDir.resolve(AppReleases.LATEST)
            .writeText(Json.encodeToString(AppRelease(29, version, emptyList(), "ambify-$version.apk", "abc", 3, 0, "2026-11-27")))
    }

    private suspend inline fun <reified T> HttpClient.send(method: String, path: String, body: T? = null, token: String? = null): HttpResponse {
        val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(body))
            }
        }
        return when (method) {
            "GET" -> get(path, configure)
            "POST" -> post(path, configure)
            else -> put(path, configure)
        }
    }

    private suspend inline fun <reified T> HttpResponse.body(): T = json.decodeFromString(bodyAsText())

    private suspend fun HttpClient.admin(): Tokens =
        send("POST", AuthPaths.SETUP, SetupRequest("Pacakowie", "Michał", "michal@pacak.waw.pl", "sekretne1", "S24")).body()

    private suspend fun HttpClient.join(owner: Tokens, name: String): Tokens {
        val code = send<Unit>("POST", AccountPaths.INVITATIONS, token = owner.accessToken).body<Invitation>().code
        return send("POST", AuthPaths.REGISTER, RegisterRequest(code, name, "${name.lowercase()}@x.pl", "haslo1234", "Realme")).body()
    }

    private fun report(text: String, id: String = "c-$text".take(30), kids: Boolean = false, shots: Int = 0) = NewReport(
        clientId = id, text = text, screen = "Lista", appVersion = "0.7.9", appCode = 29, phone = "Realme RMX3493",
        android = "14", kids = kids, network = "Wi-Fi", createdAt = 1, shots = shots,
    )

    private fun jpeg(): ByteArray {
        val image = BufferedImage(40, 80, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    @Test
    fun aReportSentTwiceIsKeptOnceAndTheReporterNeverSeesKids() = app {
        val admin = client.admin()
        val filip = client.join(admin, "Filip")

        val first = client.send("POST", ReportPaths.REPORTS, report("plan sie przesuwa", id = "same", kids = true), filip.accessToken).body<MyReport>()
        val again = client.send("POST", ReportPaths.REPORTS, report("plan sie przesuwa", id = "same", kids = true), filip.accessToken).body<MyReport>()
        assertEquals(first.id, again.id)
        assertEquals(ReportStatus.SENT, first.status)

        val raw = client.send<Unit>("GET", ReportPaths.REPORTS, token = filip.accessToken).bodyAsText()
        assertFalse("kids" in raw, "the reporter's answer says nothing about the kids' variant")
        assertEquals(1, json.decodeFromString<List<MyReport>>(raw).size)

        val panel = client.send<Unit>("GET", ReportPaths.PLATFORM, token = admin.accessToken).body<List<PlatformReport>>()
        assertTrue(panel.single().kids)
        assertEquals("Filip", panel.single().reporterName)
        assertEquals("Pacakowie", panel.single().circle)
        assertEquals(HttpStatusCode.Forbidden, client.send<Unit>("GET", ReportPaths.PLATFORM, token = filip.accessToken).status)
    }

    @Test
    fun screenshotsOnlyForTheReporterAndTheAdmin() = app {
        val admin = client.admin()
        val filip = client.join(admin, "Filip")
        val ola = client.join(admin, "Ola")
        val sent = client.send("POST", ReportPaths.REPORTS, report("zrzut", shots = 1), filip.accessToken).body<MyReport>()

        val put = client.put(ReportPaths.shot(sent.id, 0)) { bearerAuth(filip.accessToken); contentType(ContentType.Image.JPEG); setBody(jpeg()) }
        assertEquals(HttpStatusCode.NoContent, put.status)
        val notMine = client.put(ReportPaths.shot(sent.id, 1)) { bearerAuth(ola.accessToken); contentType(ContentType.Image.JPEG); setBody(jpeg()) }
        assertEquals(HttpStatusCode.NotFound, notMine.status)

        val mine = client.send<Unit>("GET", ReportPaths.REPORTS, token = filip.accessToken).body<List<MyReport>>().single()
        assertEquals(listOf(ReportPaths.shot(sent.id, 0)), mine.shots)
        assertEquals(HttpStatusCode.OK, client.send<Unit>("GET", mine.shots.single(), token = admin.accessToken).status)
        assertEquals(HttpStatusCode.NotFound, client.send<Unit>("GET", mine.shots.single(), token = ola.accessToken).status)
    }

    @Test
    fun inFixTurnsFixedWhenThatVersionIsPublishedAndExplainedNeedsAReason() = app {
        val admin = client.admin()
        val ola = client.join(admin, "Ola")
        val bug = client.send("POST", ReportPaths.REPORTS, report("tapeta"), ola.accessToken).body<MyReport>()
        val notBug = client.send("POST", ReportPaths.REPORTS, report("spinacz"), ola.accessToken).body<MyReport>()

        assertEquals(HttpStatusCode.BadRequest, client.send("PUT", ReportPaths.platform(bug.id), ReportUpdate(ReportStatus.ACCEPTED, number = "7"), admin.accessToken).status)
        assertEquals(HttpStatusCode.NoContent, client.send("PUT", ReportPaths.platform(bug.id), ReportUpdate(ReportStatus.ACCEPTED, number = "b-007"), admin.accessToken).status)
        assertEquals(HttpStatusCode.NoContent, client.send("PUT", ReportPaths.platform(bug.id), ReportUpdate(ReportStatus.IN_FIX, fixedIn = "0.7.9"), admin.accessToken).status)
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", ReportPaths.platform(bug.id), ReportUpdate(ReportStatus.IN_FIX, fixedIn = "0.7.9"), ola.accessToken).status)
        assertEquals(HttpStatusCode.BadRequest, client.send("PUT", ReportPaths.platform(notBug.id), ReportUpdate(ReportStatus.EXPLAINED), admin.accessToken).status, "no bare Wyjaśnione")
        val why = "Tak ma działać: zdjęcie otwiera się po stuknięciu w pozycję."
        assertEquals(HttpStatusCode.NoContent, client.send("PUT", ReportPaths.platform(notBug.id), ReportUpdate(ReportStatus.EXPLAINED, explanation = why), admin.accessToken).status)

        publish("0.7.8")
        var mine = client.send<Unit>("GET", ReportPaths.REPORTS, token = ola.accessToken).body<List<MyReport>>().associateBy { it.text }
        assertEquals(ReportStatus.IN_FIX, mine.getValue("tapeta").status)
        assertEquals(why, mine.getValue("spinacz").explanation)

        publish("0.7.9")
        mine = client.send<Unit>("GET", ReportPaths.REPORTS, token = ola.accessToken).body<List<MyReport>>().associateBy { it.text }
        val fixed = mine.getValue("tapeta")
        assertEquals(ReportStatus.FIXED, fixed.status)
        assertEquals("0.7.9", fixed.fixedIn)
        assertEquals(listOf(ReportStatus.SENT, ReportStatus.ACCEPTED, ReportStatus.IN_FIX, ReportStatus.FIXED), fixed.history.map { it.status })
        assertEquals("B-007", client.send<Unit>("GET", ReportPaths.PLATFORM, token = admin.accessToken).body<List<PlatformReport>>().first { it.report.id == bug.id }.number)
    }

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(versionAtLeast("0.7.10", "0.7.9"))
        assertTrue(versionAtLeast("0.7.9", "0.7.9"))
        assertFalse(versionAtLeast("0.7.8", "0.7.9"))
        assertTrue(versionAtLeast("0.8.0", "0.7.91"))
    }
}
