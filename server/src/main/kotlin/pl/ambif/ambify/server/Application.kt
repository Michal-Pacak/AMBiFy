package pl.ambif.ambify.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.HealthResponse

fun main() {
    val config = ServerConfig.fromEnv()
    val database = Db.open(config.databasePath)
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(database)
    }.start(wait = true)
}

fun Application.module(database: Database) {
    install(ContentNegotiation) {
        json()
    }
    routing {
        get(ApiPaths.HEALTH) {
            val databaseOk = Db.isReachable(database)
            call.respond(
                HealthResponse(
                    status = if (databaseOk) "ok" else "degraded",
                    version = serverVersion(),
                    database = if (databaseOk) "ok" else "error",
                )
            )
        }
    }
}

// Read from the jar manifest; "dev" when started from Gradle without a jar.
private fun serverVersion(): String =
    ServerConfig::class.java.`package`?.implementationVersion ?: "dev"
