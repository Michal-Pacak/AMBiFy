package pl.ambif.ambify.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.CheckStatus
import pl.ambif.ambify.shared.DiagnosticsResponse
import pl.ambif.ambify.shared.HealthResponse
import pl.ambif.ambify.shared.HealthStatus

fun main() {
    val config = ServerConfig.fromEnv()
    val database = Db.open(config.databasePath)
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(database, Diagnostics.platform(database, config, serverVersion()))
    }.start(wait = true)
}

fun Application.module(database: Database, diagnostics: Diagnostics) {
    install(ContentNegotiation) {
        json()
    }
    routing {
        // Light status for the app; any failing check turns it into "degraded".
        get(ApiPaths.HEALTH) {
            val results = diagnostics.run(call.appInfo())
            val healthy = results.none { it.status == CheckStatus.WARNING || it.status == CheckStatus.ERROR }
            call.respond(
                HealthResponse(
                    status = if (healthy) HealthStatus.OK else HealthStatus.DEGRADED,
                    version = serverVersion(),
                    database = if (Db.isReachable(database)) "ok" else "error",
                )
            )
        }
        get(ApiPaths.DIAGNOSTICS) {
            call.respond(DiagnosticsResponse(serverVersion(), diagnostics.run(call.appInfo())))
        }
    }
}

private fun ApplicationCall.appInfo() = AppInfo(
    versionCode = request.headers[ApiHeaders.APP_VERSION_CODE]?.toIntOrNull(),
    versionName = request.headers[ApiHeaders.APP_VERSION_NAME],
)

// Read from the jar manifest; "dev" when started from Gradle without a jar.
fun serverVersion(): String =
    ServerConfig::class.java.`package`?.implementationVersion ?: "dev"
