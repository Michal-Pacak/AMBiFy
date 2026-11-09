package pl.ambif.ambify.server.monitor

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import pl.ambif.ambify.server.accounts.session
import pl.ambif.ambify.shared.MonitorEventBatch
import pl.ambif.ambify.shared.MonitorPaths

/** Telefon dziecka (build kids) dosyła zdarzenia na swoje konto; serwer dopisuje je do pliku logu. */
fun Route.monitorRoutes(service: MonitorService) {
    authenticate("user") {
        post(MonitorPaths.EVENTS) {
            service.record(call.session(), call.receive<MonitorEventBatch>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
