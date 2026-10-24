package pl.ambif.ambify.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.CheckStatus
import pl.ambif.ambify.shared.DiagnosticResult
import pl.ambif.ambify.shared.DiagnosticsResponse
import pl.ambif.ambify.shared.HealthResponse
import pl.ambif.ambify.shared.HealthStatus

class ServerTest {
    private fun tempDbPath(): String =
        Files.createTempDirectory("ambify-test").resolve("test.db").toString()

    private fun result(status: CheckStatus) = DiagnosticResult("test", "Test", status, "")

    @Test
    fun healthReportsOkWhenAllChecksPass() = testApplication {
        val database = Db.open(tempDbPath())
        application { module(database, Diagnostics(listOf(DiagnosticCheck { result(CheckStatus.OK) }))) }

        val response = client.get(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        val health = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals(HealthStatus.OK, health.status)
        assertEquals("ok", health.database)
    }

    @Test
    fun healthIsDegradedWhenAnyCheckFails() = testApplication {
        val database = Db.open(tempDbPath())
        val checks = listOf(
            DiagnosticCheck { result(CheckStatus.OK) },
            DiagnosticCheck { result(CheckStatus.WARNING) },
        )
        application { module(database, Diagnostics(checks)) }

        val health = Json.decodeFromString<HealthResponse>(client.get(ApiPaths.HEALTH).bodyAsText())

        assertEquals(HealthStatus.DEGRADED, health.status)
    }

    @Test
    fun diagnosticsListsChecksAndOldAppIsRejected() = testApplication {
        val database = Db.open(tempDbPath())
        val config = ServerConfig.fromEnv(mapOf("AMBIFY_DB" to tempDbPath()))
        application { module(database, Diagnostics.platform(database, config, "test")) }

        val response = client.get(ApiPaths.DIAGNOSTICS) {
            header(ApiHeaders.APP_VERSION_CODE, Diagnostics.MIN_APP_VERSION_CODE - 1)
        }

        val checks = Json.decodeFromString<DiagnosticsResponse>(response.bodyAsText()).checks.associateBy { it.id }
        assertEquals(CheckStatus.ERROR, checks.getValue("version").status)
        assertEquals(CheckStatus.OK, checks.getValue("database").status)
        assertEquals(CheckStatus.OK, checks.getValue("migrations").status)
    }

    @Test
    fun versionCheckIsSkippedWithoutAppHeaders() {
        assertNull(Diagnostics.appVersion(AppInfo(null, null), "test"))
    }

    @Test
    fun nasIsOkOnlyWhenMounted() {
        val mounts = listOf("/dev/mapper/vg1000-lv /mnt/nas ext4 rw,relatime 0 0")

        assertEquals(CheckStatus.OK, Diagnostics.nas("/mnt/nas", mounts).status)
        assertEquals(CheckStatus.ERROR, Diagnostics.nas("/mnt/nas", emptyList()).status)
    }

    @Test
    fun failingCheckIsReportedInsteadOfCrashing() {
        val results = Diagnostics(listOf(DiagnosticCheck { error("boom") })).run(AppInfo(null, null))

        assertEquals(CheckStatus.ERROR, results.single().status)
    }

    @Test
    fun migrationsAreAppliedOnceAcrossRestarts() {
        val path = tempDbPath()
        Db.open(path)
        val database = Db.open(path)

        assertEquals(Migrations.FILES.toSet(), Migrations.appliedNames(database))
    }

    @Test
    fun configHasSafeDefaults() {
        val config = ServerConfig.fromEnv(emptyMap())

        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
        assertEquals("/mnt/nas", config.nasPath)
    }
}
