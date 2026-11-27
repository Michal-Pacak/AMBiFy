package pl.ambif.ambify.core.network

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
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
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ItemFile
import pl.ambif.ambify.shared.ListConflict
import pl.ambif.ambify.shared.ListPaths
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.AppPaths
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.DashboardPaths
import pl.ambif.ambify.shared.AppRelease
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.ChangePasswordRequest
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
import pl.ambif.ambify.shared.MonitorEventBatch
import pl.ambif.ambify.shared.MonitorPaths
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.PlatformPaths
import pl.ambif.ambify.shared.ProfileUpdate
import pl.ambif.ambify.shared.RefreshRequest
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.RoleRequest
import pl.ambif.ambify.shared.Tokens
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.ExtraActivityRequest
import pl.ambif.ambify.shared.ExtraSaveResult
import pl.ambif.ambify.shared.DiaryConnectRequest
import pl.ambif.ambify.shared.DiaryConnection
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.PendingRequest
import pl.ambif.ambify.shared.PermissionChange
import pl.ambif.ambify.shared.PermissionPaths
import pl.ambif.ambify.shared.PermissionTable
import pl.ambif.ambify.shared.RoleCreate
import pl.ambif.ambify.shared.RoleMove
import pl.ambif.ambify.shared.RoleRename
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.StudentLink
import pl.ambif.ambify.shared.TimetablePaths
import pl.ambif.ambify.shared.TimetableWeek

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

    /** The same connection, but every request is about community [communityId] (not the current one). */
    fun forCommunity(communityId: String) = ServerApi(baseUrl, tokens) { communityId }

    suspend fun health(): Timed<HealthResponse> = timed { get(ApiPaths.HEALTH, auth = false) }

    suspend fun diagnostics(): Timed<DiagnosticsResponse> = timed { get(ApiPaths.DIAGNOSTICS, auth = false) }

    /** The newest published app; [ServerError] "no_release" when nothing is published yet. Build kids czyta kanał kids. */
    suspend fun latestRelease(): AppRelease = get(AppPaths.latest(BuildConfig.KIDS), auth = false)

    /** Saves the published APK [release] into [target]; [onProgress] gets the bytes so far. */
    suspend fun downloadApk(release: AppRelease, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val connection = URI("$baseUrl${AppPaths.apk(BuildConfig.KIDS)}/${release.file}").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw ServerError("http_${connection.responseCode}", "Serwer nie oddał nowej wersji (${connection.responseCode}).")
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
        }
    }

    suspend fun register(request: RegisterRequest): Tokens = post(AuthPaths.REGISTER, request, auth = false)
    suspend fun login(request: LoginRequest): Tokens = post(AuthPaths.LOGIN, request, auth = false)
    suspend fun resetPassword(request: ResetPasswordRequest) = send("POST", AuthPaths.RESET_PASSWORD, json.encodeToString(request), auth = false)
    suspend fun logout() = send("POST", AuthPaths.LOGOUT, null, auth = true)

    suspend fun me(): Me = get(AccountPaths.ME)
    suspend fun updateProfile(update: ProfileUpdate): Me = json.decodeFromString(send("PUT", AccountPaths.ME, json.encodeToString(update), auth = true))
    suspend fun changePassword(request: ChangePasswordRequest) = send("POST", AccountPaths.PASSWORD, json.encodeToString(request), auth = true)
    suspend fun devices(): List<Device> = get(AccountPaths.SESSIONS)
    suspend fun signOutDevice(id: String) = send("DELETE", "${AccountPaths.SESSIONS}/$id", null, auth = true)
    suspend fun moduleOrder(): ModuleOrder = get(AccountPaths.MODULE_ORDER)
    suspend fun saveModuleOrder(order: ModuleOrder) = send("PUT", AccountPaths.MODULE_ORDER, json.encodeToString(order), auth = true)

    suspend fun dashboard(): Dashboard = get(DashboardPaths.DASHBOARD)
    suspend fun saveDashboard(dashboard: Dashboard) = send("PUT", DashboardPaths.DASHBOARD, json.encodeToString(dashboard), auth = true)

    suspend fun family(): Family = get(AccountPaths.FAMILY)

    /** 0.7.6: my profile photo (a square JPEG); the answer is my profile with its new path. */
    suspend fun uploadMyPhoto(source: File): Me =
        json.decodeFromString(transfer("PUT", AccountPaths.MY_PHOTO, emptyMap(), upload = source to "image/jpeg", download = null, onProgress = {}))
    suspend fun deleteMyPhoto(): Me = json.decodeFromString(send("DELETE", AccountPaths.MY_PHOTO, null, auth = true))
    /** 0.7.8: the current circle's icon (the Owner only). */
    suspend fun uploadCirclePhoto(source: File): Me =
        json.decodeFromString(transfer("PUT", AccountPaths.FAMILY_AVATAR, emptyMap(), upload = source to "image/jpeg", download = null, onProgress = {}))
    suspend fun deleteCirclePhoto(): Me = json.decodeFromString(send("DELETE", AccountPaths.FAMILY_AVATAR, null, auth = true))
    suspend fun setCircleColor(color: String?): Me =
        json.decodeFromString(send("PUT", AccountPaths.FAMILY_AVATAR_COLOR, json.encodeToString(pl.ambif.ambify.shared.CircleColor(color)), auth = true))
    /** Saves a person's photo ([path] from their avatar) into [target]; [small]: the 256 px one. */
    suspend fun downloadPhoto(path: String, small: Boolean, target: File) {
        transfer("GET", if (small) "$path?size=s" else path, emptyMap(), upload = null, download = target, onProgress = {})
    }
    suspend fun setPhotosBlocked(blocked: Boolean) =
        send("PUT", AccountPaths.FAMILY_PHOTOS, json.encodeToString(pl.ambif.ambify.shared.PhotosSetting(blocked)), auth = true)
    suspend fun setPhotoHidden(memberId: String, hidden: Boolean) =
        send("PUT", "${AccountPaths.MEMBERS}/$memberId/${AccountPaths.PHOTO_HIDDEN}", json.encodeToString(pl.ambif.ambify.shared.PhotoHidden(hidden)), auth = true)
    suspend fun renameCommunity(name: String) = send("PUT", AccountPaths.FAMILY, json.encodeToString(CreateCommunityRequest(name)), auth = true)
    suspend fun leaveCommunity() = send("POST", AccountPaths.LEAVE, null, auth = true)
    suspend fun requestOwnerTransfer(toUserId: String, leave: Boolean) =
        send("POST", AccountPaths.OWNER_TRANSFER, json.encodeToString(pl.ambif.ambify.shared.OwnerTransferRequest(toUserId, leave)), auth = true)
    suspend fun cancelOwnerTransfer() = send("DELETE", AccountPaths.OWNER_TRANSFER, null, auth = true)
    suspend fun answerOwnerTransfer(accept: Boolean) =
        send("POST", AccountPaths.OWNER_TRANSFER_ANSWER, json.encodeToString(pl.ambif.ambify.shared.OwnerTransferAnswer(accept)), auth = true)
    suspend fun deleteCommunity(name: String) =
        send("DELETE", AccountPaths.COMMUNITY + "?name=" + java.net.URLEncoder.encode(name, "UTF-8"), null, auth = true)
    suspend fun createInvitation(): Invitation = post(AccountPaths.INVITATIONS, null as String?)
    suspend fun cancelInvitation(code: String) = send("DELETE", "${AccountPaths.INVITATIONS}/$code", null, auth = true)
    suspend fun removeMember(id: String) = send("DELETE", "${AccountPaths.MEMBERS}/$id", null, auth = true)
    suspend fun createResetCode(memberId: String): PasswordResetCode = post("${AccountPaths.MEMBERS}/$memberId/password-reset", null as String?)
    suspend fun setRole(memberId: String, roleId: String) = send("PUT", "${AccountPaths.MEMBERS}/$memberId/role", json.encodeToString(RoleRequest(roleId)), auth = true)
    suspend fun signOutMember(memberId: String) = send("POST", "${AccountPaths.MEMBERS}/$memberId/sign-out", null, auth = true)

    suspend fun createCommunity(name: String): CommunitySummary = post(AccountPaths.COMMUNITIES, CreateCommunityRequest(name))
    suspend fun joinCommunity(code: String): CommunitySummary = post(AccountPaths.JOIN, JoinRequest(code))
    suspend fun modules(): CommunityModules = get(ModulePaths.MODULES)
    suspend fun setModule(module: ModuleId, enabled: Boolean): CommunityModules =
        json.decodeFromString(send("PUT", "${ModulePaths.MODULES}/${module.name}", json.encodeToString(ModuleSwitch(enabled)), auth = true))

    suspend fun permissionTable(): PermissionTable = get(PermissionPaths.PERMISSIONS)
    suspend fun setPermission(roleId: String, action: Action, mode: Mode?): PermissionTable =
        json.decodeFromString(send("PUT", "${PermissionPaths.PERMISSIONS}/$roleId/${action.name}", json.encodeToString(PermissionChange(mode)), auth = true))
    suspend fun createRole(name: String, belowRoleId: String): List<Role> = post(PermissionPaths.ROLES, RoleCreate(name, belowRoleId))
    suspend fun renameRole(roleId: String, name: String): List<Role> =
        json.decodeFromString(send("PUT", "${PermissionPaths.ROLES}/$roleId", json.encodeToString(RoleRename(name)), auth = true))
    suspend fun moveRole(roleId: String, up: Boolean): List<Role> = post("${PermissionPaths.ROLES}/$roleId/move", RoleMove(up))
    suspend fun deleteRole(roleId: String): List<Role> = json.decodeFromString(send("DELETE", "${PermissionPaths.ROLES}/$roleId", null, auth = true))
    suspend fun requests(): List<PendingRequest> = get(PermissionPaths.REQUESTS)
    suspend fun decide(requestId: String, approve: Boolean) =
        send("POST", "${PermissionPaths.REQUESTS}/$requestId/${if (approve) "approve" else "reject"}", null, auth = true)

    suspend fun timetable(week: String? = null): TimetableWeek = get(TimetablePaths.TIMETABLE + (week?.let { "?week=$it" } ?: ""))
    suspend fun addExtras(request: ExtraActivityRequest): ExtraSaveResult = post(TimetablePaths.EXTRAS, request)
    suspend fun updateExtra(id: String, request: ExtraActivityRequest): ExtraActivity =
        json.decodeFromString(send("PUT", "${TimetablePaths.EXTRAS}/$id", json.encodeToString(request), auth = true))
    suspend fun deleteExtra(id: String) = send("DELETE", "${TimetablePaths.EXTRAS}/$id", null, auth = true)
    suspend fun linkStudent(studentId: String, memberId: String?): List<Student> =
        json.decodeFromString(send("PUT", "${TimetablePaths.STUDENTS}/$studentId", json.encodeToString(StudentLink(memberId)), auth = true))
    suspend fun diaryConnections(): List<DiaryConnection> = get(TimetablePaths.DIARY)
    suspend fun connectVulcan(tokens: List<String>): DiaryConnection = post(TimetablePaths.DIARY_VULCAN, DiaryConnectRequest(tokens))
    suspend fun syncDiary(id: String): DiaryConnection = post("${TimetablePaths.DIARY}/$id/sync", null as String?)
    suspend fun disconnectDiary(id: String) = send("DELETE", "${TimetablePaths.DIARY}/$id", null, auth = true)

    /** Build kids: dosyła paczkę zdarzeń telefonu dziecka na jego konto (serwer dopisuje je do pliku logu). */
    suspend fun listSync(request: ListSyncRequest): ListSyncResponse = post(ListPaths.SYNC, request)
    suspend fun resolveConflict(id: String, decision: ConflictDecision): ListConflict = post("${ListPaths.CONFLICTS}/$id", decision)

    /** 0.7.3: sends [source] as photo or file [fileId] of item [itemId]; [onProgress] gets the bytes sent so far. */
    suspend fun uploadListFile(itemId: String, fileId: String, name: String, mime: String, source: File, onProgress: (Long) -> Unit): ItemFile {
        val headers = mapOf(ListPaths.FILE_ID to fileId, ListPaths.FILE_NAME to URLEncoder.encode(name, "UTF-8"))
        val body = transfer("POST", ListPaths.itemFiles(itemId), headers, upload = source to mime, download = null, onProgress = onProgress)
        return json.decodeFromString(body)
    }

    /** Saves photo or file [fileId] into [target]; [onProgress] gets the bytes so far. */
    suspend fun downloadListFile(fileId: String, target: File, onProgress: (Long) -> Unit) {
        transfer("GET", "${ListPaths.FILES}/$fileId", emptyMap(), upload = null, download = target, onProgress = onProgress)
    }

    suspend fun deleteListFile(fileId: String) = send("DELETE", "${ListPaths.FILES}/$fileId", null, auth = true)

    suspend fun sendMonitorEvents(batch: MonitorEventBatch) =
        send("POST", MonitorPaths.EVENTS, json.encodeToString(batch), auth = true)

    // 0.7.7 themes
    suspend fun themes(): pl.ambif.ambify.shared.ThemeState = get(pl.ambif.ambify.shared.Themes.PATH)
    private suspend fun putTheme(path: String, body: String): pl.ambif.ambify.shared.ThemeState =
        json.decodeFromString(send("PUT", path, body, auth = true))
    suspend fun setAccountTheme(theme: String?) =
        putTheme(pl.ambif.ambify.shared.Themes.ACCOUNT, json.encodeToString(pl.ambif.ambify.shared.ThemeChoice(theme)))
    suspend fun setMemberTheme(theme: String?) =
        putTheme(pl.ambif.ambify.shared.Themes.MEMBER, json.encodeToString(pl.ambif.ambify.shared.ThemeChoice(theme)))
    suspend fun setCommunityTheme(theme: String, locked: Boolean) =
        putTheme(pl.ambif.ambify.shared.Themes.COMMUNITY, json.encodeToString(pl.ambif.ambify.shared.CommunityThemeUpdate(theme, locked)))
    suspend fun saveOwnTheme(theme: pl.ambif.ambify.shared.OwnTheme, community: Boolean) =
        putTheme(pl.ambif.ambify.shared.Themes.OWN + if (community) "?community=1" else "", json.encodeToString(theme))
    suspend fun deleteOwnTheme(id: String): pl.ambif.ambify.shared.ThemeState =
        json.decodeFromString(send("DELETE", "${pl.ambif.ambify.shared.Themes.OWN}/$id", null, auth = true))
    /** A wallpaper photo (JPEG) for an own theme; the answer's id goes into its [pl.ambif.ambify.shared.Wallpaper]. */
    suspend fun uploadWallpaper(source: File, community: Boolean): String {
        val path = pl.ambif.ambify.shared.Themes.WALLPAPERS + if (community) "?community=1" else ""
        val body = transfer("PUT", path, emptyMap(), upload = source to "image/jpeg", download = null, onProgress = {})
        return json.decodeFromString<pl.ambif.ambify.shared.WallpaperUploaded>(body).photoId
    }
    suspend fun downloadWallpaper(photoId: String, target: File) {
        transfer("GET", "${pl.ambif.ambify.shared.Themes.WALLPAPERS}/$photoId", emptyMap(), upload = null, download = target, onProgress = {})
    }
    suspend fun setAccountPremium(email: String, premium: Boolean) =
        send("POST", "${pl.ambif.ambify.shared.Themes.PATH}/platform/premium", json.encodeToString(pl.ambif.ambify.shared.AccountPremiumRequest(email, premium)), auth = true)

    suspend fun platformCommunities(): List<PlatformCommunity> = get(PlatformPaths.COMMUNITIES)
    suspend fun platformAccounts(): List<pl.ambif.ambify.shared.PlatformAccount> = get(PlatformPaths.ACCOUNTS)
    suspend fun setGrant(communityId: String, key: String, granted: Boolean) =
        send("PUT", "${PlatformPaths.COMMUNITIES}/$communityId/grants/$key", json.encodeToString(GrantRequest(granted)), auth = true)
    suspend fun addFoundingCredit(email: String) = send("POST", PlatformPaths.FOUNDING_CREDITS, json.encodeToString(EmailRequest(email)), auth = true)
    suspend fun removeFoundingCredit(email: String) = send("POST", PlatformPaths.FOUNDING_CREDITS_REVOKE, json.encodeToString(EmailRequest(email)), auth = true)
    suspend fun platformResetCode(email: String): PasswordResetCode = post(PlatformPaths.RESET_CODES, EmailRequest(email))

    // 0.7.9 bug reports
    suspend fun sendReport(report: pl.ambif.ambify.shared.NewReport): pl.ambif.ambify.shared.MyReport =
        post(pl.ambif.ambify.shared.ReportPaths.REPORTS, report)
    suspend fun uploadReportShot(reportId: String, index: Int, source: File) {
        transfer("PUT", pl.ambif.ambify.shared.ReportPaths.shot(reportId, index), emptyMap(), upload = source to "image/jpeg", download = null, onProgress = {})
    }
    suspend fun myReports(): List<pl.ambif.ambify.shared.MyReport> = get(pl.ambif.ambify.shared.ReportPaths.REPORTS)
    suspend fun platformReports(): List<pl.ambif.ambify.shared.PlatformReport> = get(pl.ambif.ambify.shared.ReportPaths.PLATFORM)
    suspend fun updateReport(reportId: String, change: pl.ambif.ambify.shared.ReportUpdate) =
        send("PUT", pl.ambif.ambify.shared.ReportPaths.platform(reportId), json.encodeToString(change), auth = true)
    /** A screenshot ([path] from the report) into [target]. */
    suspend fun downloadReportShot(path: String, target: File) {
        transfer("GET", path, emptyMap(), upload = null, download = target, onProgress = {})
    }

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
        when (renewTokens(usedToken)) {
            Renewal.RENEWED -> return execute(method, path, body, tokens?.accessToken).bodyOrThrow()
            // The server said no to the refresh token: the session really is over.
            Renewal.REJECTED -> {
                tokens?.onSessionExpired()
                return first.bodyOrThrow()
            }
            // No network or the server is restarting: keep the session, try again later.
            Renewal.UNAVAILABLE -> throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
        }
    }

    private enum class Renewal { RENEWED, REJECTED, UNAVAILABLE }

    /**
     * Renews the access token (or notices it was renewed by a request running at the same time).
     * The lock is shared by every [ServerApi] in the app: the refresh token changes on each renewal, so two
     * renewals at once with the same token would make the second one fail and sign the person out.
     */
    private suspend fun renewTokens(expiredAccessToken: String?): Renewal = refreshLock.withLock {
        val store = tokens ?: return Renewal.REJECTED
        if (store.accessToken != null && store.accessToken != expiredAccessToken) return Renewal.RENEWED
        val refreshToken = store.refreshToken ?: return Renewal.REJECTED
        val answer = runCatching {
            execute("POST", AuthPaths.REFRESH, json.encodeToString(RefreshRequest(refreshToken)), null)
        }.getOrNull() ?: return Renewal.UNAVAILABLE
        when (answer.code) {
            HttpURLConnection.HTTP_OK -> {
                val renewed = json.decodeFromString<Tokens>(answer.body)
                store.accessToken = renewed.accessToken
                store.refreshToken = renewed.refreshToken
                Renewal.RENEWED
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> Renewal.REJECTED
            else -> Renewal.UNAVAILABLE
        }
    }

    private class Answer(val code: Int, val body: String)

    /** A request with a file as the body ([upload]: the file and its type) or the answer saved to [download]. */
    private suspend fun transfer(
        method: String, path: String, headers: Map<String, String>, upload: Pair<File, String>?, download: File?, onProgress: (Long) -> Unit,
    ): String {
        val usedToken = tokens?.accessToken
        val first = executeTransfer(method, path, headers, upload, download, usedToken, onProgress)
        if (first.code != HttpURLConnection.HTTP_UNAUTHORIZED) return first.bodyOrThrow()
        return when (renewTokens(usedToken)) {
            Renewal.RENEWED -> executeTransfer(method, path, headers, upload, download, tokens?.accessToken, onProgress).bodyOrThrow()
            Renewal.REJECTED -> {
                tokens?.onSessionExpired()
                first.bodyOrThrow()
            }
            Renewal.UNAVAILABLE -> throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
        }
    }

    private suspend fun executeTransfer(
        method: String, path: String, headers: Map<String, String>, upload: Pair<File, String>?, download: File?,
        accessToken: String?, onProgress: (Long) -> Unit,
    ): Answer = withContext(Dispatchers.IO) {
        try {
            val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.setRequestProperty(ApiHeaders.APP_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
            connection.setRequestProperty(ApiHeaders.APP_VERSION_NAME, BuildConfig.VERSION_NAME)
            accessToken?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
                community()?.let { id -> connection.setRequestProperty(ApiHeaders.COMMUNITY, id) }
            }
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            try {
                if (upload != null) {
                    val (source, mime) = upload
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", mime)
                    connection.setFixedLengthStreamingMode(source.length())
                    source.inputStream().use { input -> connection.outputStream.use { output -> copy(input, output, onProgress) } }
                }
                val code = connection.responseCode
                if (download != null && code in 200..299) {
                    val partial = File(download.parentFile, download.name + ".part")
                    download.parentFile?.mkdirs()
                    connection.inputStream.use { input -> partial.outputStream().use { output -> copy(input, output, onProgress) } }
                    if (!partial.renameTo(download)) throw IOException("rename")
                    Answer(code, "")
                } else {
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    Answer(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
        }
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
    }

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
                // The server talks to the school diary while answering these: much slower than the rest.
                connection.readTimeout = if (path.startsWith(TimetablePaths.DIARY)) DIARY_TIMEOUT_MS else TIMEOUT_MS
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
        /** One for the whole app process, see [renewTokens]. */
        val refreshLock = Mutex()
        const val TIMEOUT_MS = 6000
        const val DIARY_TIMEOUT_MS = 60_000
        const val DOWNLOAD_TIMEOUT_MS = 30_000
    }
}
