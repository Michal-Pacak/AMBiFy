package pl.ambif.ambify.server.accounts

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.RefreshRequest
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.RoleRequest
import pl.ambif.ambify.shared.SetupRequest

private const val AUTH = "user"

fun Application.installAccountAuth(service: AccountService, tokens: AccessTokens) {
    install(Authentication) {
        jwt(AUTH) {
            verifier(tokens.verifier)
            validate { credential ->
                val userId = credential.payload.subject
                val sessionId = credential.payload.getClaim(AccessTokens.SESSION_CLAIM).asString()
                if (userId != null && sessionId != null && service.sessionActive(userId, sessionId)) {
                    UserSession(userId, sessionId)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorized", "Zaloguj się ponownie."))
            }
        }
    }
}

fun Route.accountRoutes(service: AccountService) {
    get(AuthPaths.STATUS) { call.respond(service.status()) }
    post(AuthPaths.SETUP) { call.respond(service.setup(call.receive<SetupRequest>())) }
    post(AuthPaths.REGISTER) { call.respond(service.register(call.receive<RegisterRequest>())) }
    post(AuthPaths.LOGIN) { call.respond(service.login(call.receive<LoginRequest>())) }
    post(AuthPaths.REFRESH) { call.respond(service.refresh(call.receive<RefreshRequest>().refreshToken)) }
    post(AuthPaths.RESET_PASSWORD) {
        service.resetPassword(call.receive<ResetPasswordRequest>())
        call.respond(HttpStatusCode.NoContent)
    }

    authenticate(AUTH) {
        post(AuthPaths.LOGOUT) {
            service.logout(call.session())
            call.respond(HttpStatusCode.NoContent)
        }
        get(AccountPaths.ME) { call.respond(service.me(call.session())) }
        post(AccountPaths.PASSWORD) {
            service.changePassword(call.session(), call.receive<ChangePasswordRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
        get(AccountPaths.SESSIONS) { call.respond(service.devices(call.session())) }
        delete("${AccountPaths.SESSIONS}/{id}") {
            service.signOutDevice(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        get(AccountPaths.MODULE_ORDER) { call.respond(ModuleOrder(service.moduleOrder(call.session()))) }
        put(AccountPaths.MODULE_ORDER) {
            service.saveModuleOrder(call.session(), call.receive<ModuleOrder>().routes)
            call.respond(HttpStatusCode.NoContent)
        }

        get(AccountPaths.FAMILY) { call.respond(service.family(call.session())) }
        post(AccountPaths.INVITATIONS) { call.respond(service.createInvitation(call.session())) }
        delete("${AccountPaths.INVITATIONS}/{code}") {
            service.cancelInvitation(call.session(), call.parameters["code"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        post(AccountPaths.CHILDREN) { call.respond(service.addChild(call.session(), call.receive<ChildRequest>())) }
        put("${AccountPaths.CHILDREN}/{id}") {
            call.respond(service.updateChild(call.session(), call.parameters["id"]!!, call.receive<ChildRequest>()))
        }
        delete("${AccountPaths.CHILDREN}/{id}") {
            service.deleteChild(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("${AccountPaths.MEMBERS}/{id}") {
            service.removeMember(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        post("${AccountPaths.MEMBERS}/{id}/password-reset") {
            call.respond(service.createResetCode(call.session(), call.parameters["id"]!!))
        }
        put("${AccountPaths.MEMBERS}/{id}/role") {
            service.setRole(call.session(), call.parameters["id"]!!, call.receive<RoleRequest>().role)
            call.respond(HttpStatusCode.NoContent)
        }
        post("${AccountPaths.MEMBERS}/{id}/sign-out") {
            service.signOutMember(call.session(), call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.session(): UserSession = principal<UserSession>()!!
