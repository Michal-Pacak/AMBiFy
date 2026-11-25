package pl.ambif.ambify.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.server.accounts.AccessTokens
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.DemoSeed
import pl.ambif.ambify.server.accounts.accountRoutes
import pl.ambif.ambify.server.accounts.themeRoutes
import pl.ambif.ambify.server.lists.ListFileStore
import pl.ambif.ambify.server.lists.ListService
import pl.ambif.ambify.server.lists.listRoutes
import pl.ambif.ambify.server.timetable.DiarySchedule
import pl.ambif.ambify.server.timetable.TimetableService
import pl.ambif.ambify.server.timetable.diary.Vault
import pl.ambif.ambify.server.timetable.timetableRoutes
import pl.ambif.ambify.server.accounts.installAccountAuth
import pl.ambif.ambify.server.monitor.MonitorService
import pl.ambif.ambify.server.monitor.monitorRoutes
import pl.ambif.ambify.server.releases.AppReleases
import pl.ambif.ambify.server.releases.releaseRoutes
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.CheckStatus
import pl.ambif.ambify.shared.DiagnosticsResponse
import pl.ambif.ambify.shared.HealthResponse
import pl.ambif.ambify.shared.HealthStatus

fun main() {
    val config = ServerConfig.fromEnv()
    val database = Db.open(config.databasePath)
    val dataDir = File(config.databasePath).absoluteFile.parentFile
    val tokens = AccessTokens(AccessTokens.loadOrCreateKey(dataDir.resolve("jwt.key")))
    val vault = Vault.loadOrCreate(dataDir.resolve("diary.key"))
    val accounts = AccountService(database, tokens)
    val timetable = TimetableService(database, accounts, vault)
    if (System.getenv("AMBIFY_DEMO") == "1") {
        DemoSeed.seedIfEmpty(accounts, tokens)?.let { timetable.seedDemo(it) }
    }
    DiarySchedule.start(timetable)
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(
            database,
            Diagnostics.platform(database, config, serverVersion()),
            tokens,
            vault,
            AppReleases(File(config.releasesDir)),
            MonitorService(File(config.monitorDir)),
            dataDir.resolve("lists-files"),
            dataDir.resolve("avatars"),
            dataDir.resolve("wallpapers"),
        )
    }.start(wait = true)
}

fun Application.module(
    database: Database,
    diagnostics: Diagnostics,
    tokens: AccessTokens,
    vault: Vault = Vault.random(),
    releases: AppReleases = AppReleases(File("releases")),
    monitor: MonitorService = MonitorService(File("monitor")),
    /** 0.7.3: photos and files of list items; next to the database, so ambify-backup copies them with it. */
    listFilesDir: File = File("lists-files"),
    /** 0.7.6: profile photos, next to the database too. */
    photosDir: File = File("avatars"),
    /** 0.7.7: wallpaper photos of own themes, next to the database too. */
    wallpapersDir: File = File("wallpapers"),
) {
    val accounts = AccountService(database, tokens)
    val timetable = TimetableService(database, accounts, vault)
    val listFiles = ListFileStore(listFilesDir)
    val lists = ListService(database, accounts, files = listFiles)
    val photos = pl.ambif.ambify.server.accounts.ProfilePhotos(database, accounts, photosDir)
    val themes = pl.ambif.ambify.server.accounts.ThemeService(database, accounts, wallpapersDir)
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.code, e.message)) }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "Nieprawidłowe dane."))
        }
    }
    installAccountAuth(accounts, tokens)
    routing {
        accountRoutes(accounts, photos)
        themeRoutes(themes)
        timetableRoutes(timetable)
        listRoutes(lists, listFiles)
        releaseRoutes(releases)
        monitorRoutes(monitor)
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
            val results = diagnostics.run(call.appInfo())
            // From outside home only what the app needs to tell "works / update the app"; disks, NAS and backups stay home.
            val shown = if (call.request.headers[ApiHeaders.PUBLIC] == "1") results.filter { it.id in Diagnostics.PUBLIC_CHECKS } else results
            call.respond(DiagnosticsResponse(serverVersion(), shown))
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
