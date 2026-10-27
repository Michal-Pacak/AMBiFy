package pl.ambif.ambify.core.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildProfile
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.CreateCommunityRequest
import pl.ambif.ambify.shared.EmailRequest
import pl.ambif.ambify.shared.GrantRequest
import pl.ambif.ambify.shared.JoinRequest
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.DiagnosticsResponse
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.HealthResponse
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.ModulePaths
import pl.ambif.ambify.shared.ModuleSwitch
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.PlatformPaths
import pl.ambif.ambify.shared.RefreshRequest
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.RoleRequest
import pl.ambif.ambify.shared.Tokens

/** A server answer together with how long the request took. */
data class Timed<T>(val value: T, val millis: Long)

/** A refusal or failure to show the user; [message] is Polish text. */
class ServerError(val code: String, override val message: String) : Exception(message)

/** Where the signed-in tokens live; [onSessionExpired] is called when they stop working. */
interface TokenStore {
    var accessToken: String?
    var refreshToken: String?
    fun onSessionExpired()
}

/**
 * Access to the AMBiFy server at the fixed address from the build.
 * Every request tells the server the app version, so it can report an outdated app.
 * An expired access token is renewed once with the refresh token; if that fails the session ends.
 */
class ServerApi(
    private val baseUrl: String = BuildConfig.SERVER_URL,
    private val tokens: TokenStore? = null,
    /** The community the requests are about (sent in [ApiHeaders.COMMUNITY]); null = the person's first one. */
    private val community: () -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLock = Mutex()

    suspend fun health(): Timed<HealthResponse> = timed { get(ApiPaths.HEALTH, auth = false) }

    suspend fun diagnostics(): Timed<DiagnosticsResponse> = timed { get(ApiPaths.DIAGNOSTICS, auth = false) }

    suspend fun register(request: RegisterRequest): Tokens = post(AuthPaths.REGISTER, request, auth = false)
    suspend fun login(request: LoginRequest): Tokens = post(AuthPaths.LOGIN, request, auth = false)
    suspend fun resetPassword(request: ResetPasswordRequest) = send("POST", AuthPaths.RESET_PASSWORD, json.encodeToString(request), auth = false)
    suspend fun logout() = send("POST", AuthPaths.LOGOUT, null, auth = true)

    suspend fun me(): Me = get(AccountPaths.ME)
    suspend fun changePassword(request: ChangePasswordRequest) = send("POST", AccountPaths.PASSWORD, json.encodeToString(request), auth = true)
    suspend fun devices(): List<Device> = get(AccountPaths.SESSIONS)
    suspend fun signOutDevice(id: String) = send("DELETE", "${AccountPaths.SESSIONS}/$id", null, auth = true)
    suspend fun moduleOrder(): ModuleOrder = get(AccountPaths.MODULE_ORDER)
    suspend fun saveModuleOrder(order: ModuleOrder) = send("PUT", AccountPaths.MODULE_ORDER, json.encodeToString(order), auth = true)

    suspend fun family(): Family = get(AccountPaths.FAMILY)
    suspend fun createInvitation(): Invitation = post(AccountPaths.INVITATIONS, null as String?)
    suspend fun cancelInvitation(code: String) = send("DELETE", "${AccountPaths.INVITATIONS}/$code", null, auth = true)
    suspend fun addChild(request: ChildRequest): ChildProfile = post(AccountPaths.CHILDREN, request)
    suspend fun updateChild(id: String, request: ChildRequest) = send("PUT", "${AccountPaths.CHILDREN}/$id", json.encodeToString(request), auth = true)
    suspend fun deleteChild(id: String) = send("DELETE", "${AccountPaths.CHILDREN}/$id", null, auth = true)
    suspend fun removeMember(id: String) = send("DELETE", "${AccountPaths.MEMBERS}/$id", null, auth = true)
    suspend fun createResetCode(memberId: String): PasswordResetCode = post("${AccountPaths.MEMBERS}/$memberId/password-reset", null as String?)
    suspend fun setRole(memberId: String, role: Role) = send("PUT", "${AccountPaths.MEMBERS}/$memberId/role", json.encodeToString(RoleRequest(role)), auth = true)
    suspend fun signOutMember(memberId: String) = send("POST", "${AccountPaths.MEMBERS}/$memberId/sign-out", null, auth = true)

    suspend fun createCommunity(name: String): CommunitySummary = post(AccountPaths.COMMUNITIES, CreateCommunityRequest(name))
    suspend fun joinCommunity(code: String): CommunitySummary = post(AccountPaths.JOIN, JoinRequest(code))
    suspend fun modules(): CommunityModules = get(ModulePaths.MODULES)
    suspend fun setModule(module: ModuleId, enabled: Boolean): CommunityModules =
        json.decodeFromString(send("PUT", "${ModulePaths.MODULES}/${module.name}", json.encodeToString(ModuleSwitch(enabled)), auth = true))

    suspend fun platformCommunities(): List<PlatformCommunity> = get(PlatformPaths.COMMUNITIES)
    suspend fun setGrant(communityId: String, key: String, granted: Boolean) =
        send("PUT", "${PlatformPaths.COMMUNITIES}/$communityId/grants/$key", json.encodeToString(GrantRequest(granted)), auth = true)
    suspend fun addFoundingCredit(email: String) = send("POST", PlatformPaths.FOUNDING_CREDITS, json.encodeToString(EmailRequest(email)), auth = true)
    suspend fun platformResetCode(email: String): PasswordResetCode = post(PlatformPaths.RESET_CODES, EmailRequest(email))

    private suspend inline fun <reified T> get(path: String, auth: Boolean = true): T =
        json.decodeFromString(send("GET", path, null, auth))

    private suspend inline fun <reified B, reified T> post(path: String, body: B?, auth: Boolean = true): T =
        json.decodeFromString(send("POST", path, body?.let { json.encodeToString(it) }, auth))

    private suspend inline fun <T> timed(block: () -> T): Timed<T> {
        val started = System.nanoTime()
        val value = block()
        return Timed(value, (System.nanoTime() - started) / 1_000_000)
    }

    /** Sends one request and returns the body; renews the session once on 401. */
    private suspend fun send(method: String, path: String, body: String?, auth: Boolean): String {
        val usedToken = if (auth) tokens?.accessToken else null
        val first = execute(method, path, body, usedToken)
        if (!auth || first.code != HttpURLConnection.HTTP_UNAUTHORIZED) return first.bodyOrThrow()
        if (!renewTokens(usedToken)) {
            tokens?.onSessionExpired()
            return first.bodyOrThrow()
        }
        return execute(method, path, body, tokens?.accessToken).bodyOrThrow()
    }

    /** True when there is a fresh access token (renewed here or by a request running at the same time). */
    private suspend fun renewTokens(expiredAccessToken: String?): Boolean = refreshLock.withLock {
        val store = tokens ?: return false
        if (store.accessToken != null && store.accessToken != expiredAccessToken) return true
        val refreshToken = store.refreshToken ?: return false
        val answer = runCatching {
            execute("POST", AuthPaths.REFRESH, json.encodeToString(RefreshRequest(refreshToken)), null)
        }.getOrNull() ?: return false
        if (answer.code != HttpURLConnection.HTTP_OK) return false
        val renewed = json.decodeFromString<Tokens>(answer.body)
        store.accessToken = renewed.accessToken
        store.refreshToken = renewed.refreshToken
        true
    }

    private class Answer(val code: Int, val body: String)

    private fun Answer.bodyOrThrow(): String {
        if (code in 200..299) return body
        val error = runCatching { json.decodeFromString<ApiError>(body) }.getOrNull()
        throw ServerError(error?.code ?: "http_$code", error?.message ?: "Serwer odpowiedział błędem ($code).")
    }

    private suspend fun execute(method: String, path: String, body: String?, accessToken: String?): Answer =
        withContext(Dispatchers.IO) {
            try {
                val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty(ApiHeaders.APP_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
                connection.setRequestProperty(ApiHeaders.APP_VERSION_NAME, BuildConfig.VERSION_NAME)
                accessToken?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                    community()?.let { id -> connection.setRequestProperty(ApiHeaders.COMMUNITY, id) }
                }
                try {
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    Answer(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
            }
        }

    private companion object {
        const val TIMEOUT_MS = 6000
    }
}
