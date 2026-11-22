package pl.ambif.ambify.server.lists

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.session
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ListPaths
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.Lists

fun Route.listRoutes(service: ListService, store: ListFileStore) {
    authenticate("user") {
        post(ListPaths.SYNC) { call.respond(service.sync(call.session(), call.receive<ListSyncRequest>())) }
        post("${ListPaths.CONFLICTS}/{id}") {
            call.respond(service.resolve(call.session(), call.parameters["id"]!!, call.receive<ConflictDecision>()))
        }
        post("${ListPaths.ITEMS}/{itemId}/files") {
            val session = call.session()
            val name = URLDecoder.decode(call.request.header(ListPaths.FILE_NAME) ?: "plik", Charsets.UTF_8)
            val fileId = call.request.header(ListPaths.FILE_ID) ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Nieprawidłowe dane.")
            val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            if (declared != null && declared > Lists.MAX_FILE_BYTES) {
                throw ApiException(HttpStatusCode.PayloadTooLarge, "file_refused", Lists.fileRefusal(Lists.cleanFileName(name), "", declared)!!)
            }
            val upload = store.incoming()
            // At most one byte over the limit is kept: enough for the service to refuse it with the right message.
            val size = withContext(Dispatchers.IO) {
                call.receiveChannel().toInputStream().use { input ->
                    upload.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (total <= Lists.MAX_FILE_BYTES) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                        }
                        total
                    }
                }
            }
            if (size > Lists.MAX_FILE_BYTES) {
                upload.delete()
                throw ApiException(HttpStatusCode.PayloadTooLarge, "file_refused", Lists.fileRefusal(Lists.cleanFileName(name), "", size)!!)
            }
            call.respond(service.addFile(session, call.parameters["itemId"]!!, fileId, name, call.request.contentType().toString(), upload))
        }
        get("${ListPaths.FILES}/{id}") {
            val (info, file) = service.file(call.session(), call.parameters["id"]!!)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, info.name).toString(),
            )
            call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
            // Stored without an ending, so the type comes from the database, not from the name.
            call.respond(LocalFileContent(file, runCatching { ContentType.parse(info.mime) }.getOrDefault(ContentType.Application.OctetStream)))
        }
        delete("${ListPaths.FILES}/{id}") {
            service.deleteFile(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
