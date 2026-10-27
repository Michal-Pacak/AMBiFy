package pl.ambif.ambify.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.CreateCommunityRequest
import pl.ambif.ambify.shared.EmailRequest
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.GrantRequest
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.JoinRequest
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.ModulePaths
import pl.ambif.ambify.shared.ModuleSwitch
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.PlatformPaths
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.RoleRequest
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class CommunitiesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun newPath() = Files.createTempDirectory("ambify-communities").resolve("test.db").toString()

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val database = Db.open(newPath())
        application { module(database, Diagnostics(emptyList()), testTokens()) }
        block()
    }

    private suspend inline fun <reified T> HttpClient.send(
        method: String, path: String, body: T? = null, token: String? = null, community: String? = null,
    ): HttpResponse {
        val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            community?.let { header(ApiHeaders.COMMUNITY, it) }
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

    /** The first account on the server: Owner of "Pacakowie" and the Platform Admin. */
    private suspend fun HttpClient.michal(): Tokens =
        send("POST", AuthPaths.SETUP, SetupRequest("Pacakowie", "Michał", "michal@pacak.waw.pl", "sekretne1", "S24")).body()

    private suspend fun HttpClient.newAccount(email: String, code: String? = null): Tokens =
        send("POST", AuthPaths.REGISTER, RegisterRequest(code, "Ania", email, "haslo1234", "Pixel")).body()

    private suspend fun HttpClient.me(tokens: Tokens, community: String? = null): Me =
        send<Unit>("GET", AccountPaths.ME, token = tokens.accessToken, community = community).body()

    private suspend fun HttpClient.invitation(tokens: Tokens, community: String? = null): String =
        send<Unit>("POST", AccountPaths.INVITATIONS, token = tokens.accessToken, community = community).body<Invitation>().code

    @Test
    fun firstOwnCommunityIsFreeTheNextOneNeedsPaymentOrCredit() = app {
        client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl")
        assertNull(client.me(ania).communityId)
        assertEquals("no_community", client.send<Unit>("GET", AccountPaths.FAMILY, token = ania.accessToken).body<ApiError>().code)

        val first = client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Dziadkowie"), ania.accessToken)
        val second = client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Klasa 3b"), ania.accessToken)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.PaymentRequired, second.status)
        assertEquals("payment_required", second.body<ApiError>().code)
    }

    @Test
    fun platformAdminGivesFoundingCreditAndFoundsFreely() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl")
        client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Dziadkowie"), ania.accessToken)

        val notAdmin = client.send("POST", PlatformPaths.FOUNDING_CREDITS, EmailRequest("ania@pacak.waw.pl"), ania.accessToken)
        client.send("POST", PlatformPaths.FOUNDING_CREDITS, EmailRequest("Ania@Pacak.waw.pl"), michal.accessToken)
        val withCredit = client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Klasa 3b"), ania.accessToken)
        val creditUsedUp = client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Sąsiedzi"), ania.accessToken)
        val adminSecond = client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Praca"), michal.accessToken)

        assertEquals(HttpStatusCode.Forbidden, notAdmin.status)
        assertEquals(HttpStatusCode.OK, withCredit.status)
        assertEquals(HttpStatusCode.PaymentRequired, creditUsedUp.status)
        assertEquals(HttpStatusCode.OK, adminSecond.status)
        val list: List<PlatformCommunity> = client.send<Unit>("GET", PlatformPaths.COMMUNITIES, token = michal.accessToken).body()
        assertEquals(listOf("Pacakowie", "Dziadkowie", "Klasa 3b", "Praca"), list.map { it.name })
        assertEquals(listOf(false, false, true, true), list.map { it.paidFounding })
        assertEquals("Ania", list[1].ownerName)
    }

    @Test
    fun headerPicksCommunityAndRoleIsPerCommunity() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl", client.invitation(michal))
        val dziadkowie: CommunitySummary =
            client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Dziadkowie"), ania.accessToken).body()
        val pacakowie = client.me(michal).communityId!!

        assertEquals(Role.MEMBER, client.me(ania).role)
        assertEquals(Role.OWNER, client.me(ania, dziadkowie.id).role)
        assertEquals(listOf("Pacakowie", "Dziadkowie"), client.me(ania).communities.map { it.name })
        val michalInDziadkowie = client.send<Unit>("GET", AccountPaths.FAMILY, token = michal.accessToken, community = dziadkowie.id)
        assertEquals(HttpStatusCode.NotFound, michalInDziadkowie.status)
        assertEquals(pacakowie, client.me(michal, dziadkowie.id).communityId)

        // Module order is kept per community.
        client.send("PUT", AccountPaths.MODULE_ORDER, ModuleOrder(listOf("lists", "calendar")), ania.accessToken, dziadkowie.id)
        val inPacakowie: ModuleOrder = client.send<Unit>("GET", AccountPaths.MODULE_ORDER, token = ania.accessToken).body()
        val inDziadkowie: ModuleOrder =
            client.send<Unit>("GET", AccountPaths.MODULE_ORDER, token = ania.accessToken, community = dziadkowie.id).body()
        assertEquals(emptyList(), inPacakowie.routes)
        assertEquals(listOf("lists", "calendar"), inDziadkowie.routes)
    }

    @Test
    fun signedInPersonJoinsWithCodeAndRemovalKeepsTheAccount() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl")
        val code = client.invitation(michal)

        val joined = client.send("POST", AccountPaths.JOIN, JoinRequest(code.lowercase()), ania.accessToken)
        val again = client.send("POST", AccountPaths.JOIN, JoinRequest(client.invitation(michal)), ania.accessToken)
        assertEquals(HttpStatusCode.OK, joined.status)
        assertEquals("already_member", again.body<ApiError>().code)

        val aniaId = client.me(ania).id
        client.send<Unit>("DELETE", "${AccountPaths.MEMBERS}/$aniaId", token = michal.accessToken)
        val family: Family = client.send<Unit>("GET", AccountPaths.FAMILY, token = michal.accessToken).body()

        assertEquals(listOf("Michał"), family.members.map { it.name })
        assertTrue(client.me(ania).communities.isEmpty())
        assertEquals(HttpStatusCode.OK, client.send("POST", AuthPaths.LOGIN, LoginRequest("ania@pacak.waw.pl", "haslo1234", "Pixel")).status)
    }

    @Test
    fun basicModulesOnByDefaultAndOnlyManagersSwitchThem() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl", client.invitation(michal))
        val modules: CommunityModules = client.send<Unit>("GET", ModulePaths.MODULES, token = ania.accessToken).body()
        assertEquals(listOf(ModuleId.CALENDAR, ModuleId.LISTS, ModuleId.COMMUNICATION), modules.enabled)

        val byUser = client.send("PUT", "${ModulePaths.MODULES}/COMMUNICATION", ModuleSwitch(false), ania.accessToken)
        val premium = client.send("PUT", "${ModulePaths.MODULES}/TIMETABLE", ModuleSwitch(true), michal.accessToken)
        val off: CommunityModules =
            client.send("PUT", "${ModulePaths.MODULES}/COMMUNICATION", ModuleSwitch(false), michal.accessToken).body()

        assertEquals(HttpStatusCode.Forbidden, byUser.status)
        assertEquals("not_owned", premium.body<ApiError>().code)
        assertEquals(listOf(ModuleId.CALENDAR, ModuleId.LISTS), off.enabled)
        assertEquals(3, off.owned.size)
    }

    @Test
    fun platformAdminGrantsPremiumModulesAndFeatures() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl")
        val dziadkowie: CommunitySummary =
            client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Dziadkowie"), ania.accessToken).body()
        val grant = "${PlatformPaths.COMMUNITIES}/${dziadkowie.id}/grants"

        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", "$grant/TIMETABLE", GrantRequest(true), ania.accessToken).status)
        assertEquals(HttpStatusCode.BadRequest, client.send("PUT", "$grant/CALENDAR", GrantRequest(true), michal.accessToken).status)
        client.send("PUT", "$grant/TIMETABLE", GrantRequest(true), michal.accessToken)
        client.send("PUT", "$grant/LISTS_REMINDERS", GrantRequest(true), michal.accessToken)
        client.send("PUT", "$grant/COMMUNICATION_ATTACHMENTS", GrantRequest(true), michal.accessToken)
        client.send("PUT", "${ModulePaths.MODULES}/COMMUNICATION", ModuleSwitch(false), ania.accessToken)

        val modules: CommunityModules = client.send<Unit>("GET", ModulePaths.MODULES, token = ania.accessToken).body()
        assertEquals(listOf(ModuleId.CALENDAR, ModuleId.LISTS, ModuleId.TIMETABLE), modules.enabled)
        // A feature of a switched-off module does not work, but the community still has it.
        assertEquals(listOf(Feature.LISTS_REMINDERS), modules.features)
        assertEquals(listOf(Feature.LISTS_REMINDERS, Feature.COMMUNICATION_ATTACHMENTS), modules.ownedFeatures)

        client.send("PUT", "$grant/TIMETABLE", GrantRequest(false), michal.accessToken)
        val after: CommunityModules = client.send<Unit>("GET", ModulePaths.MODULES, token = ania.accessToken).body()
        assertFalse(ModuleId.TIMETABLE in after.owned)
    }

    @Test
    fun platformAdminResetCodeHelpsAnOwner() = app {
        val michal = client.michal()
        val ania = client.newAccount("ania@pacak.waw.pl")
        client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Dziadkowie"), ania.accessToken)

        val refused = client.send("POST", PlatformPaths.RESET_CODES, EmailRequest("michal@pacak.waw.pl"), ania.accessToken)
        val code: pl.ambif.ambify.shared.PasswordResetCode =
            client.send("POST", PlatformPaths.RESET_CODES, EmailRequest("ania@pacak.waw.pl"), michal.accessToken).body()
        val reset = client.send(
            "POST", AuthPaths.RESET_PASSWORD,
            pl.ambif.ambify.shared.ResetPasswordRequest("ania@pacak.waw.pl", code.code, "nowehaslo1"),
        )

        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(HttpStatusCode.NoContent, reset.status)
    }

    @Test
    fun emailWithPolishLetterIsRefused() = app {
        val answer = client.send("POST", AuthPaths.REGISTER, RegisterRequest(null, "Michał", "michał@pacak.waw.pl", "sekretne1", "S24"))

        assertEquals("invalid_email", answer.body<ApiError>().code)
    }

    @Test
    fun migrationKeepsVersion04Data() {
        val path = newPath()
        val database = Database.connect("jdbc:sqlite:$path?foreign_keys=on", "org.sqlite.JDBC")
        Migrations.apply(database, listOf("V001__init.sql", "V002__accounts.sql"))
        DriverManager.getConnection("jdbc:sqlite:$path").use { db ->
            db.createStatement().use {
                it.execute("INSERT INTO organizations VALUES ('org', 'Pacakowie', 1)")
                it.execute("INSERT INTO users VALUES ('u1', 'org', 'Michał', 'michal@pacak.waw.pl', 'h', 'OWNER', 0, NULL, 'calendar,home', 10)")
                it.execute("INSERT INTO users VALUES ('u2', 'org', 'Ania', 'ania@pacak.waw.pl', 'h', 'ADMIN', 0, NULL, NULL, 20)")
                it.execute("INSERT INTO sessions VALUES ('s1', 'u1', 'hash', 'S24', 10, 10)")
                it.execute("INSERT INTO child_profiles VALUES ('c1', 'org', 'Bartek', '#8EC3F0', 30)")
            }
        }

        val migrated = Db.open(path)
        val service = AccountService(migrated, testTokens())
        val michal = service.me(UserSession("u1", "s1"))

        assertEquals(Role.OWNER, michal.role)
        assertEquals("Pacakowie", michal.familyName)
        assertTrue(michal.platformAdmin)
        assertFalse(service.me(UserSession("u2", "s1")).platformAdmin)
        assertTrue(service.sessionActive("u1", "s1"))
        assertEquals(listOf("calendar", "home"), service.moduleOrder(UserSession("u1", "s1")))
        val family = service.family(UserSession("u1", "s1"))
        assertEquals(listOf("Michał" to Role.OWNER, "Ania" to Role.ADMIN), family.members.map { it.name to it.role })
        assertEquals(listOf("Bartek"), family.children.map { it.name })
        // Roles now live in memberships.
        service.setRole(UserSession("u1", "s1"), "u2", Role.MEMBER)
        assertEquals(Role.MEMBER, service.me(UserSession("u2", "s1")).role)
    }
}
