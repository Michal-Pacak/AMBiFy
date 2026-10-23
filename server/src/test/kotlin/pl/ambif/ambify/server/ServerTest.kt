package pl.ambif.ambify.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.HealthResponse

class ServerTest {
    private fun tempDbPath(): String =
        Files.createTempDirectory("ambify-test").resolve("test.db").toString()

    @Test
    fun healthReportsOkWithWorkingDatabase() = testApplication {
        val database = Db.open(tempDbPath())
        application { module(database) }

        val response = client.get(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        val health = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", health.status)
        assertEquals("ok", health.database)
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
    }
}
