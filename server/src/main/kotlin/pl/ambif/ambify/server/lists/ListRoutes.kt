package pl.ambif.ambify.server.lists

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import pl.ambif.ambify.server.accounts.session
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ListPaths
import pl.ambif.ambify.shared.ListSyncRequest

fun Route.listRoutes(service: ListService) {
    authenticate("user") {
        post(ListPaths.SYNC) { call.respond(service.sync(call.session(), call.receive<ListSyncRequest>())) }
        post("${ListPaths.CONFLICTS}/{id}") {
            call.respond(service.resolve(call.session(), call.parameters["id"]!!, call.receive<ConflictDecision>()))
        }
    }
}
