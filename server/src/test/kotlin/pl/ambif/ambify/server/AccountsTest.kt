package pl.ambif.ambify.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.Passwords
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.AuthStatus
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildProfile
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.RefreshRequest
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.RoleRequest
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class AccountsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun newDatabase() = Db.open(Files.createTempDirectory("ambify-accounts").resolve("test.db").toString())

    private fun accountsApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val database = newDatabase()
        application { module(database, Diagnostics(emptyList()), testTokens()) }
        block()
    }

    private suspend inline fun <reified T> HttpClient.send(
        method: String, path: String, body: T? = null, token: String? = null,
    ): HttpResponse {
        val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(body))
            }
        }
        return when (method) {
            "GET" -> get(path, configure)
            "POST" -> post(path, configure)
            "PUT" -> put(path, configure)
            else -> delete(path, configure)
        }
    }

    private suspend inline fun <reified T> HttpResponse.body(): T = json.decodeFromString(bodyAsText())

    private suspend fun HttpClient.setupOwner(): Tokens =
        send("POST", AuthPaths.SETUP, SetupRequest("Pacakowie", "Michał", "Michal@Pacak.waw.pl", "sekretne1", "S24")).body()

    private suspend fun HttpClient.inviteAndJoin(owner: Tokens, email: String = "ania@pacak.waw.pl"): Tokens {
        val invitation: Invitation = send<Unit>("POST", AccountPaths.INVITATIONS, token = owner.accessToken).body()
        return send("POST", AuthPaths.REGISTER, RegisterRequest(invitation.code.lowercase(), "Ania", email, "haslo1234", "Pixel")).body()
    }

    @Test
    fun firstPersonCreatesFamilyOnlyOnce() = accountsApp {
        assertFalse(client.send<Unit>("GET", AuthPaths.STATUS).body<AuthStatus>().familyExists)

        val owner = client.setupOwner()
        val me: Me = client.send<Unit>("GET", AccountPaths.ME, token = owner.accessToken).body()

        assertEquals(Role.OWNER, me.role)
        assertEquals("michal@pacak.waw.pl", me.email)
        assertEquals("Pacakowie", me.familyName)
        assertTrue(client.send<Unit>("GET", AuthPaths.STATUS).body<AuthStatus>().familyExists)
        val second = client.send("POST", AuthPaths.SETUP, SetupRequest("Inni", "X", "x@x.pl", "sekretne1", "A"))
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    @Test
    fun invitationCodeWorksOnceAndOnlyOwnerInvites() = accountsApp {
        val owner = client.setupOwner()
        val invitation: Invitation = client.send<Unit>("POST", AccountPaths.INVITATIONS, token = owner.accessToken).body()

        val ania = client.send("POST", AuthPaths.REGISTER, RegisterRequest(invitation.code, "Ania", "ania@pacak.waw.pl", "haslo1234", "Pixel"))
        val reused = client.send("POST", AuthPaths.REGISTER, RegisterRequest(invitation.code, "Ktoś", "k@pacak.waw.pl", "haslo1234", "X"))

        assertEquals(HttpStatusCode.OK, ania.status)
        assertEquals("invalid_code", reused.body<ApiError>().code)
        val member = ania.body<Tokens>()
        assertEquals(HttpStatusCode.Forbidden, client.send<Unit>("POST", AccountPaths.INVITATIONS, token = member.accessToken).status)
        val family: Family = client.send<Unit>("GET", AccountPaths.FAMILY, token = member.accessToken).body()
        assertEquals(listOf("Michał", "Ania"), family.members.map { it.name })
        assertTrue(family.invitations.isEmpty())
    }

    @Test
    fun fiveWrongPasswordsLockTheAccount() = accountsApp {
        client.setupOwner()
        repeat(4) {
            val wrong = client.send("POST", AuthPaths.LOGIN, LoginRequest("michal@pacak.waw.pl", "zle-haslo", "S24"))
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        }
        val fifth = client.send("POST", AuthPaths.LOGIN, LoginRequest("michal@pacak.waw.pl", "zle-haslo", "S24"))
        val correctButLocked = client.send("POST", AuthPaths.LOGIN, LoginRequest("michal@pacak.waw.pl", "sekretne1", "S24"))

        assertEquals(HttpStatusCode.TooManyRequests, fifth.status)
        assertEquals("locked", correctButLocked.body<ApiError>().code)
    }

    @Test
    fun refreshRotatesTokenAndSignedOutDeviceLosesAccessAtOnce() = accountsApp {
        val owner = client.setupOwner()
        val renewed: Tokens = client.send("POST", AuthPaths.REFRESH, RefreshRequest(owner.refreshToken)).body()
        val oldAgain = client.send("POST", AuthPaths.REFRESH, RefreshRequest(owner.refreshToken))

        assertNotEquals(owner.refreshToken, renewed.refreshToken)
        assertEquals(HttpStatusCode.Unauthorized, oldAgain.status)

        val member = client.inviteAndJoin(renewed)
        val ania = client.send<Unit>("GET", AccountPaths.ME, token = member.accessToken).body<Me>()
        client.send<Unit>("POST", "${AccountPaths.MEMBERS}/${ania.id}/sign-out", token = renewed.accessToken)

        assertEquals(HttpStatusCode.Unauthorized, client.send<Unit>("GET", AccountPaths.ME, token = member.accessToken).status)
        assertEquals(HttpStatusCode.Unauthorized, client.send("POST", AuthPaths.REFRESH, RefreshRequest(member.refreshToken)).status)
    }

    @Test
    fun resetCodeFromOwnerSetsNewPassword() = accountsApp {
        val owner = client.setupOwner()
        val member = client.inviteAndJoin(owner)
        val ania = client.send<Unit>("GET", AccountPaths.ME, token = member.accessToken).body<Me>()
        val reset: PasswordResetCode =
            client.send<Unit>("POST", "${AccountPaths.MEMBERS}/${ania.id}/password-reset", token = owner.accessToken).body()

        val wrongEmail = client.send("POST", AuthPaths.RESET_PASSWORD, ResetPasswordRequest("inny@pacak.waw.pl", reset.code, "nowehaslo1"))
        val ok = client.send("POST", AuthPaths.RESET_PASSWORD, ResetPasswordRequest("ania@pacak.waw.pl", reset.code, "nowehaslo1"))

        assertEquals(HttpStatusCode.BadRequest, wrongEmail.status)
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals(HttpStatusCode.Unauthorized, client.send<Unit>("GET", AccountPaths.ME, token = member.accessToken).status)
        assertEquals(HttpStatusCode.OK, client.send("POST", AuthPaths.LOGIN, LoginRequest("ania@pacak.waw.pl", "nowehaslo1", "Pixel")).status)
    }

    @Test
    fun changingPasswordKeepsOnlyThisDevice() = accountsApp {
        val phone = client.setupOwner()
        val tablet: Tokens = client.send("POST", AuthPaths.LOGIN, LoginRequest("michal@pacak.waw.pl", "sekretne1", "Tablet")).body()

        val wrong = client.send("POST", AccountPaths.PASSWORD, ChangePasswordRequest("nie-to", "nowehaslo1"), phone.accessToken)
        val ok = client.send("POST", AccountPaths.PASSWORD, ChangePasswordRequest("sekretne1", "nowehaslo1"), phone.accessToken)

        assertEquals("wrong_password", wrong.body<ApiError>().code)
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals(HttpStatusCode.OK, client.send<Unit>("GET", AccountPaths.ME, token = phone.accessToken).status)
        assertEquals(HttpStatusCode.Unauthorized, client.send<Unit>("GET", AccountPaths.ME, token = tablet.accessToken).status)
    }

    @Test
    fun devicesChildrenAndModuleOrder() = accountsApp {
        val phone = client.setupOwner()
        val tablet: Tokens = client.send("POST", AuthPaths.LOGIN, LoginRequest("michal@pacak.waw.pl", "sekretne1", "Tablet")).body()
        val devices: List<Device> = client.send<Unit>("GET", AccountPaths.SESSIONS, token = phone.accessToken).body()
        val tabletDevice = devices.single { !it.current }
        client.send<Unit>("DELETE", "${AccountPaths.SESSIONS}/${tabletDevice.id}", token = phone.accessToken)

        assertEquals("Tablet", tabletDevice.name)
        assertEquals(HttpStatusCode.Unauthorized, client.send<Unit>("GET", AccountPaths.ME, token = tablet.accessToken).status)

        val child: ChildProfile = client.send("POST", AccountPaths.CHILDREN, ChildRequest("Bartek", "#8ec3f0"), phone.accessToken).body()
        client.send("PUT", "${AccountPaths.CHILDREN}/${child.id}", ChildRequest("Bartuś", "#8EC3F0"), phone.accessToken)
        val badColor = client.send("POST", AccountPaths.CHILDREN, ChildRequest("Filip", "zielony"), phone.accessToken)
        val family: Family = client.send<Unit>("GET", AccountPaths.FAMILY, token = phone.accessToken).body()

        assertEquals(listOf(ChildProfile(child.id, "Bartuś", "#8EC3F0")), family.children)
        assertEquals(HttpStatusCode.BadRequest, badColor.status)

        client.send("PUT", AccountPaths.MODULE_ORDER, ModuleOrder(listOf("calendar", "home", "BAD;route")), phone.accessToken)
        val order: ModuleOrder = client.send<Unit>("GET", AccountPaths.MODULE_ORDER, token = phone.accessToken).body()
        assertEquals(listOf("calendar", "home"), order.routes)
    }

    @Test
    fun ownerGrantsAdminWhoManagesOnlyLowerRoles() = accountsApp {
        val owner = client.setupOwner()
        val ania = client.inviteAndJoin(owner)
        val anna = client.send<Unit>("GET", AccountPaths.ME, token = ania.accessToken).body<Me>()
        val ownerMe = client.send<Unit>("GET", AccountPaths.ME, token = owner.accessToken).body<Me>()

        assertEquals(HttpStatusCode.Forbidden, client.send<Unit>("POST", AccountPaths.INVITATIONS, token = ania.accessToken).status)
        client.send("PUT", "${AccountPaths.MEMBERS}/${anna.id}/role", RoleRequest(Role.ADMIN), owner.accessToken)

        val admin = ania
        val user = client.inviteAndJoin(admin, "ula@pacak.waw.pl")
        val ula = client.send<Unit>("GET", AccountPaths.ME, token = user.accessToken).body<Me>()
        assertEquals(Role.ADMIN, client.send<Unit>("GET", AccountPaths.ME, token = admin.accessToken).body<Me>().role)
        assertEquals(Role.MEMBER, ula.role)
        assertEquals(HttpStatusCode.OK, client.send<Unit>("POST", "${AccountPaths.MEMBERS}/${ula.id}/password-reset", token = admin.accessToken).status)
        assertEquals(HttpStatusCode.Forbidden, client.send<Unit>("POST", "${AccountPaths.MEMBERS}/${ownerMe.id}/sign-out", token = admin.accessToken).status)
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", "${AccountPaths.MEMBERS}/${ula.id}/role", RoleRequest(Role.ADMIN), admin.accessToken).status)
        assertEquals(HttpStatusCode.BadRequest, client.send("PUT", "${AccountPaths.MEMBERS}/${ula.id}/role", RoleRequest(Role.OWNER), owner.accessToken).status)
    }

    @Test
    fun demoSeedCreatesThreeRolesOnce() {
        val service = AccountService(newDatabase(), testTokens())
        pl.ambif.ambify.server.accounts.DemoSeed.seedIfEmpty(service, testTokens())
        pl.ambif.ambify.server.accounts.DemoSeed.seedIfEmpty(service, testTokens())

        val roles = listOf(
            pl.ambif.ambify.server.accounts.DemoSeed.OWNER,
            pl.ambif.ambify.server.accounts.DemoSeed.ADMIN,
            pl.ambif.ambify.server.accounts.DemoSeed.USER,
        ).map { email ->
            val tokens = service.login(LoginRequest(email, pl.ambif.ambify.server.accounts.DemoSeed.PASSWORD, "t"))
            val session = testTokens().verifier.verify(tokens.accessToken)
            service.me(pl.ambif.ambify.server.accounts.UserSession(session.subject, session.getClaim("sid").asString())).role
        }
        assertEquals(listOf(Role.OWNER, Role.ADMIN, Role.MEMBER), roles)
    }

    @Test
    fun expiredInvitationAndIdleSessionAreRefused() {
        var now = 1_000_000_000_000L
        val service = AccountService(newDatabase(), testTokens()) { now }
        val owner = service.setup(SetupRequest("Pacakowie", "Michał", "michal@pacak.waw.pl", "sekretne1", "S24"))
        val session = pl.ambif.ambify.server.accounts.UserSession(
            service.refresh(owner.refreshToken).let { testTokens().verifier.verify(it.accessToken).subject },
            "unused",
        )
        val invitation = service.createInvitation(session)

        now += 8 * DAY
        val expired = runCatching { service.register(RegisterRequest(invitation.code, "Ania", "a@pacak.waw.pl", "haslo1234", "X")) }
        assertEquals("invalid_code", (expired.exceptionOrNull() as ApiException).code)

        val fresh = service.login(LoginRequest("michal@pacak.waw.pl", "sekretne1", "S24"))
        now += 91 * DAY
        val idle = runCatching { service.refresh(fresh.refreshToken) }
        assertEquals("session_expired", (idle.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun passwordHashesVerifyAndDiffer() {
        val first = Passwords.hash("sekretne1")

        assertTrue(Passwords.verify("sekretne1", first))
        assertFalse(Passwords.verify("sekretne2", first))
        assertNotEquals(first, Passwords.hash("sekretne1"))
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
