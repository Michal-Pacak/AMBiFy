package pl.ambif.ambify.server.reports

import io.ktor.http.HttpStatusCode
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.ResultRow
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.Organizations
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.accounts.Users
import pl.ambif.ambify.server.accounts.notFound
import pl.ambif.ambify.shared.MyReport
import pl.ambif.ambify.shared.NewReport
import pl.ambif.ambify.shared.PlatformReport
import pl.ambif.ambify.shared.ReportPaths
import pl.ambif.ambify.shared.ReportRules
import pl.ambif.ambify.shared.ReportStatus
import pl.ambif.ambify.shared.ReportStep
import pl.ambif.ambify.shared.ReportUpdate
import pl.ambif.ambify.shared.versionAtLeast

// Mirrors of V017__bug_reports.sql (the SQL file is the source of truth).
object BugReports : Table("bug_reports") {
    val id = text("id")
    val clientId = text("client_id")
    val userId = text("user_id")
    val organizationId = text("organization_id").nullable()
    val text = text("text")
    val screen = text("screen").nullable()
    val appVersion = text("app_version")
    val appCode = integer("app_code")
    val phone = text("phone")
    val android = text("android")
    val kids = bool("kids")
    val network = text("network").nullable()
    val createdAt = long("created_at")
    val receivedAt = long("received_at")
    val shots = integer("shots")
    val status = text("status")
    val statusAt = long("status_at")
    val number = text("number").nullable()
    val fixedIn = text("fixed_in").nullable()
    val explanation = text("explanation").nullable()
    override val primaryKey = PrimaryKey(id)
}

object BugReportHistory : Table("bug_report_history") {
    val reportId = text("report_id")
    val status = text("status")
    val at = long("at")
    val version = text("version").nullable()
}

/**
 * Bug reports (0.7.9). [releasedVersion] is the newest published app: a report "W naprawie" in that version or an
 * older one becomes "Naprawione" by itself.
 */
class ReportService(
    private val database: Database,
    private val accounts: AccountService,
    val root: File,
    private val releasedVersion: () -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun incoming(): File = root.resolve(".incoming").apply { mkdirs() }.resolve(UUID.randomUUID().toString())

    fun send(session: UserSession, report: NewReport): MyReport {
        val text = report.text.trim()
        if (text.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "empty_report", "Opisz, co się stało.")
        if (text.length > ReportRules.MAX_TEXT) {
            throw ApiException(HttpStatusCode.BadRequest, "report_too_long", "Opis jest za długi (najwyżej ${ReportRules.MAX_TEXT} znaków).")
        }
        if (report.clientId.isBlank() || report.clientId.length > 64) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Nieprawidłowe dane.")
        return transaction(database) {
            BugReports.selectAll().where { (BugReports.userId eq session.userId) and (BugReports.clientId eq report.clientId) }
                .singleOrNull()?.let { return@transaction mine(it) }
            val now = clock()
            val circle = runCatching { accounts.membership(session).organizationId }.getOrNull()
            val id = UUID.randomUUID().toString()
            BugReports.insert {
                it[BugReports.id] = id
                it[clientId] = report.clientId
                it[userId] = session.userId
                it[organizationId] = circle
                it[BugReports.text] = text
                it[screen] = report.screen?.take(80)
                it[appVersion] = report.appVersion.take(40)
                it[appCode] = report.appCode
                it[phone] = report.phone.take(80)
                it[android] = report.android.take(40)
                it[kids] = report.kids
                it[network] = report.network?.take(40)
                it[createdAt] = report.createdAt.takeIf { at -> at in 1..now } ?: now
                it[receivedAt] = now
                it[shots] = 0
                it[status] = ReportStatus.SENT.name
                it[statusAt] = now
            }
            step(id, ReportStatus.SENT, now, null)
            mine(row(id))
        }
    }

    /** A screenshot of my report, saved again as a JPEG (nothing the phone wrote in the file stays). */
    fun addShot(session: UserSession, reportId: String, index: Int, upload: File) {
        try {
            transaction(database) {
                val report = row(reportId)
                if (report[BugReports.userId] != session.userId) throw notFound()
                if (index !in 0 until ReportRules.MAX_SHOTS) throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Nieprawidłowe dane.")
            }
            val image = runCatching { ImageIO.read(upload) }.getOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_photo", "To nie jest zdjęcie, które umiemy odczytać.")
            val target = dir(reportId).apply { mkdirs() }.resolve("$index.jpg")
            val rgb = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            rgb.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
            val temp = File(target.parentFile, target.name + ".part")
            ImageIO.write(rgb, "jpg", temp)
            temp.renameTo(target)
            transaction(database) {
                val count = (0 until ReportRules.MAX_SHOTS).count { dir(reportId).resolve("$it.jpg").isFile }
                BugReports.update({ BugReports.id eq reportId }) { it[shots] = count }
            }
        } finally {
            upload.delete()
        }
    }

    fun shot(session: UserSession, reportId: String, index: Int): File = transaction(database) {
        val report = row(reportId)
        if (report[BugReports.userId] != session.userId && !accounts.isPlatformAdmin(session.userId)) throw notFound()
        dir(reportId).resolve("$index.jpg").takeIf { index in 0 until ReportRules.MAX_SHOTS && it.isFile } ?: throw notFound()
    }

    fun mine(session: UserSession): List<MyReport> = transaction(database) {
        settleReleased()
        BugReports.selectAll().where { BugReports.userId eq session.userId }
            .orderBy(BugReports.createdAt, SortOrder.DESC).map { mine(it) }
    }

    fun all(session: UserSession): List<PlatformReport> = transaction(database) {
        requireAdmin(session)
        settleReleased()
        val rows = BugReports.selectAll().orderBy(BugReports.receivedAt, SortOrder.DESC).toList()
        val names = Users.selectAll().where { Users.id inList rows.map { it[BugReports.userId] }.distinct() }
            .associate { it[Users.id] to it[Users.name] }
        val circles = Organizations.selectAll().where { Organizations.id inList rows.mapNotNull { it[BugReports.organizationId] }.distinct() }
            .associate { it[Organizations.id] to it[Organizations.name] }
        rows.map { row ->
            PlatformReport(
                report = mine(row),
                reporterName = names[row[BugReports.userId]] ?: "?",
                circle = row[BugReports.organizationId]?.let { circles[it] },
                appVersion = row[BugReports.appVersion],
                appCode = row[BugReports.appCode],
                phone = row[BugReports.phone],
                android = row[BugReports.android],
                kids = row[BugReports.kids],
                network = row[BugReports.network],
                number = row[BugReports.number],
            )
        }
    }

    fun update(session: UserSession, reportId: String, change: ReportUpdate) {
        transaction(database) {
            requireAdmin(session)
            row(reportId)
            val now = clock()
            when (change.status) {
                ReportStatus.SENT, ReportStatus.FIXED ->
                    throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Ten stan ustawia się sam.")
                ReportStatus.ACCEPTED -> {
                    val number = change.number?.trim()?.uppercase()?.takeIf { Regex("B-\\d{3,}").matches(it) }
                        ?: throw ApiException(HttpStatusCode.BadRequest, "bad_number", "Numer ma postać B-007.")
                    BugReports.update({ BugReports.id eq reportId }) {
                        it[status] = change.status.name; it[statusAt] = now; it[BugReports.number] = number
                        it[fixedIn] = null; it[explanation] = null
                    }
                    step(reportId, change.status, now, null)
                }
                ReportStatus.IN_FIX -> {
                    val version = change.fixedIn?.trim()?.takeIf { Regex("\\d+(\\.\\d+){1,3}").matches(it) }
                        ?: throw ApiException(HttpStatusCode.BadRequest, "bad_version", "Wpisz wersję z naprawą, np. 0.7.9.")
                    BugReports.update({ BugReports.id eq reportId }) {
                        it[status] = change.status.name; it[statusAt] = now; it[fixedIn] = version; it[explanation] = null
                    }
                    step(reportId, change.status, now, version)
                }
                ReportStatus.EXPLAINED -> {
                    val text = change.explanation?.trim().orEmpty()
                    if (text.length < ReportRules.MIN_EXPLANATION) {
                        throw ApiException(HttpStatusCode.BadRequest, "no_explanation", "Napisz, dlaczego to nie błąd.")
                    }
                    BugReports.update({ BugReports.id eq reportId }) {
                        it[status] = change.status.name; it[statusAt] = now; it[fixedIn] = null
                        it[explanation] = text.take(ReportRules.MAX_EXPLANATION)
                    }
                    step(reportId, change.status, now, null)
                }
            }
            settleReleased()
        }
    }

    /** "W naprawie w X" turns "Naprawione w X" once X (or newer) is published. Must run in a transaction. */
    private fun settleReleased() {
        val released = releasedVersion() ?: return
        val now = clock()
        BugReports.selectAll().where { BugReports.status eq ReportStatus.IN_FIX.name }.forEach { row ->
            val version = row[BugReports.fixedIn] ?: return@forEach
            if (versionAtLeast(released, version)) {
                BugReports.update({ BugReports.id eq row[BugReports.id] }) {
                    it[status] = ReportStatus.FIXED.name; it[statusAt] = now
                }
                step(row[BugReports.id], ReportStatus.FIXED, now, version)
            }
        }
    }

    private fun step(reportId: String, status: ReportStatus, at: Long, version: String?) {
        BugReportHistory.insert {
            it[BugReportHistory.reportId] = reportId
            it[BugReportHistory.status] = status.name
            it[BugReportHistory.at] = at
            it[BugReportHistory.version] = version
        }
    }

    private fun mine(row: ResultRow): MyReport {
        val id = row[BugReports.id]
        val history = BugReportHistory.selectAll().where { BugReportHistory.reportId eq id }.orderBy(BugReportHistory.at)
            .map { ReportStep(ReportStatus.valueOf(it[BugReportHistory.status]), it[BugReportHistory.at], it[BugReportHistory.version]) }
        return MyReport(
            id = id,
            text = row[BugReports.text],
            screen = row[BugReports.screen],
            createdAt = row[BugReports.createdAt],
            status = ReportStatus.valueOf(row[BugReports.status]),
            statusAt = row[BugReports.statusAt],
            fixedIn = row[BugReports.fixedIn],
            explanation = row[BugReports.explanation],
            shots = (0 until row[BugReports.shots]).map { ReportPaths.shot(id, it) },
            history = history,
            appVersion = row[BugReports.appVersion],
            appCode = row[BugReports.appCode],
            phone = row[BugReports.phone],
            android = row[BugReports.android],
            network = row[BugReports.network],
        )
    }

    private fun row(reportId: String): ResultRow =
        BugReports.selectAll().where { BugReports.id eq reportId }.singleOrNull() ?: throw notFound()

    private fun requireAdmin(session: UserSession) {
        if (!accounts.isPlatformAdmin(session.userId)) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Nie masz uprawnień do tej operacji.")
    }

    private fun dir(reportId: String): File {
        require(reportId.isNotBlank() && reportId.all { it.isLetterOrDigit() || it == '-' }) { "bad id" }
        return root.resolve(reportId)
    }
}
