package pl.ambif.ambify.server.reports

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.ambif.ambify.server.accounts.AUTH
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.session
import pl.ambif.ambify.shared.NewReport
import pl.ambif.ambify.shared.ReportPaths
import pl.ambif.ambify.shared.ReportRules
import pl.ambif.ambify.shared.ReportUpdate

fun Route.reportRoutes(reports: ReportService) {
    authenticate(AUTH) {
        post(ReportPaths.REPORTS) { call.respond(reports.send(call.session(), call.receive<NewReport>())) }
        get(ReportPaths.REPORTS) { call.respond(reports.mine(call.session())) }
        put("${ReportPaths.REPORTS}/{id}/shots/{index}") {
            val session = call.session()
            val index = call.parameters["index"]?.toIntOrNull() ?: -1
            val upload = reports.incoming()
            val size = withContext(Dispatchers.IO) {
                call.receiveChannel().toInputStream().use { input ->
                    upload.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (total <= ReportRules.MAX_SHOT_BYTES) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                        }
                        total
                    }
                }
            }
            if (size > ReportRules.MAX_SHOT_BYTES) {
                upload.delete()
                throw ApiException(HttpStatusCode.PayloadTooLarge, "photo_too_big", "Zrzut jest za duży (najwyżej 5 MB).")
            }
            withContext(Dispatchers.IO) { reports.addShot(session, call.parameters["id"]!!, index, upload) }
            call.respond(HttpStatusCode.NoContent)
        }
        get("${ReportPaths.REPORTS}/{id}/shots/{index}") {
            val file = reports.shot(call.session(), call.parameters["id"]!!, call.parameters["index"]?.toIntOrNull() ?: -1)
            call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
            call.respond(LocalFileContent(file, ContentType.Image.JPEG))
        }
        get(ReportPaths.PLATFORM) { call.respond(reports.all(call.session())) }
        put("${ReportPaths.PLATFORM}/{id}") {
            reports.update(call.session(), call.parameters["id"]!!, call.receive<ReportUpdate>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
