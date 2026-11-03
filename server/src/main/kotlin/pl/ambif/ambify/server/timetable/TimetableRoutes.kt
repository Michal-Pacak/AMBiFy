package pl.ambif.ambify.server.timetable

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.ambif.ambify.server.accounts.session
import pl.ambif.ambify.shared.DiaryConnectRequest
import pl.ambif.ambify.shared.DiaryKind
import pl.ambif.ambify.shared.ExtraActivityRequest
import pl.ambif.ambify.shared.StudentLink
import pl.ambif.ambify.shared.TimetablePaths

fun Route.timetableRoutes(service: TimetableService) {
    authenticate("user") {
        get(TimetablePaths.TIMETABLE) {
            val day = call.request.queryParameters["week"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            call.respond(service.week(call.session(), day))
        }
        post(TimetablePaths.EXTRAS) { call.respond(service.addActivities(call.session(), call.receive<ExtraActivityRequest>())) }
        put("${TimetablePaths.EXTRAS}/{id}") {
            call.respond(service.updateActivity(call.session(), call.parameters["id"]!!, call.receive<ExtraActivityRequest>()))
        }
        delete("${TimetablePaths.EXTRAS}/{id}") {
            service.deleteActivity(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        put("${TimetablePaths.STUDENTS}/{id}") {
            call.respond(service.linkStudent(call.session(), call.parameters["id"]!!, call.receive<StudentLink>().memberId))
        }
        get(TimetablePaths.DIARY) { call.respond(service.diaryConnections(call.session())) }
        // Talks to the diary over the network: off the request threads.
        post(TimetablePaths.DIARY_VULCAN) {
            val request = call.receive<DiaryConnectRequest>()
            call.respond(withContext(Dispatchers.IO) { service.connectDiary(call.session(), DiaryKind.VULCAN, request.tokens) })
        }
        post("${TimetablePaths.DIARY}/{id}/sync") {
            call.respond(withContext(Dispatchers.IO) { service.syncDiary(call.session(), call.parameters["id"]!!) })
        }
        delete("${TimetablePaths.DIARY}/{id}") {
            withContext(Dispatchers.IO) { service.disconnectDiary(call.session(), call.parameters["id"]!!) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
