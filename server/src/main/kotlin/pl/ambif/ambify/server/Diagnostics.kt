package pl.ambif.ambify.server

import java.io.File
import java.time.Duration
import java.time.Instant
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.shared.CheckStatus
import pl.ambif.ambify.shared.DiagnosticResult

/** Data about the calling app, taken from request headers (absent for curl and scripts). */
data class AppInfo(val versionCode: Int?, val versionName: String?)

/** One critical function of the platform or a module; each module adds its own checks. */
fun interface DiagnosticCheck {
    fun run(app: AppInfo): DiagnosticResult?
}

class Diagnostics(private val checks: List<DiagnosticCheck>) {
    fun run(app: AppInfo): List<DiagnosticResult> = checks.mapNotNull { check ->
        runCatching { check.run(app) }.getOrElse { e ->
            DiagnosticResult("internal", "Kontrola", CheckStatus.ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        // Oldest app versionCode the server still works with; raise it when the API changes incompatibly.
        const val MIN_APP_VERSION_CODE = 5

        private const val GB = 1024L * 1024 * 1024

        fun platform(database: Database, config: ServerConfig, serverVersion: String) = Diagnostics(
            listOf(
                DiagnosticCheck { app -> appVersion(app, serverVersion) },
                DiagnosticCheck { database(database) },
                DiagnosticCheck { migrations(database) },
                DiagnosticCheck { diskSpace(File(config.databasePath).absoluteFile.parentFile) },
                DiagnosticCheck { nas(config.nasPath) },
                DiagnosticCheck { backup(File(config.backupDir)) },
                // Modules planned in the roadmap; replaced by real checks when they are built.
                DiagnosticCheck { DiagnosticResult("assistant", "Asystent AI", CheckStatus.NOT_AVAILABLE, "od Stage 1A") },
                DiagnosticCheck { DiagnosticResult("timetable", "Plan lekcji", CheckStatus.NOT_AVAILABLE, "od Stage 4") },
            )
        )

        fun appVersion(app: AppInfo, serverVersion: String): DiagnosticResult? {
            val code = app.versionCode ?: return null
            val versions = "aplikacja ${app.versionName ?: code} / serwer $serverVersion"
            return if (code >= MIN_APP_VERSION_CODE) {
                DiagnosticResult("version", "Zgodność wersji", CheckStatus.OK, versions)
            } else {
                DiagnosticResult("version", "Zgodność wersji", CheckStatus.ERROR, "Zaktualizuj aplikację ($versions)")
            }
        }

        fun database(database: Database) =
            if (Db.isReachable(database)) DiagnosticResult("database", "Baza danych", CheckStatus.OK, "działa")
            else DiagnosticResult("database", "Baza danych", CheckStatus.ERROR, "brak dostępu do bazy")

        fun migrations(database: Database): DiagnosticResult {
            val missing = Migrations.FILES - Migrations.appliedNames(database)
            return if (missing.isEmpty()) {
                DiagnosticResult("migrations", "Struktura bazy", CheckStatus.OK, "aktualna (${Migrations.FILES.size})")
            } else {
                DiagnosticResult("migrations", "Struktura bazy", CheckStatus.ERROR, "brak: ${missing.joinToString()}")
            }
        }

        fun diskSpace(directory: File): DiagnosticResult {
            val free = directory.usableSpace
            val status = when {
                free < 1 * GB -> CheckStatus.ERROR
                free < 5 * GB -> CheckStatus.WARNING
                else -> CheckStatus.OK
            }
            return DiagnosticResult("disk", "Wolne miejsce", status, "${free / GB} GB")
        }

        /** Daily copies are written by ambify-backup.timer; older than two days means it stopped working. */
        fun backup(directory: File, now: Instant = Instant.now()): DiagnosticResult {
            val newest = directory.listFiles { file -> file.name.startsWith("ambify-") && file.name.endsWith(".db") }
                ?.maxByOrNull { it.lastModified() }
                ?: return DiagnosticResult("backup", "Kopia na NAS", CheckStatus.ERROR, "brak kopii")
            val hours = Duration.between(Instant.ofEpochMilli(newest.lastModified()), now).toHours()
            val age = if (hours < 24) "dziś" else "${hours / 24} dni temu"
            val status = if (hours <= 48) CheckStatus.OK else CheckStatus.WARNING
            return DiagnosticResult("backup", "Kopia na NAS", status, "ostatnia: $age")
        }

        /** The NAS disk must be mounted; an empty directory on the system disk is not enough. */
        fun nas(path: String, mounts: List<String> = File("/proc/self/mounts").readLines()): DiagnosticResult {
            val mounted = mounts.any { it.split(' ').getOrNull(1) == path }
            return if (mounted) {
                val free = File(path).usableSpace / GB
                DiagnosticResult("nas", "Dysk NAS", CheckStatus.OK, "podłączony, wolne $free GB")
            } else {
                DiagnosticResult("nas", "Dysk NAS", CheckStatus.ERROR, "niepodłączony ($path)")
            }
        }
    }
}
