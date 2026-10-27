package pl.ambif.ambify.server.accounts

import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.shared.AccountRules
import pl.ambif.ambify.shared.AuthStatus
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildProfile
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

/** A refusal shown to the user: HTTP status, stable code for the app, Polish message. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

class AccountService(
    private val database: Database,
    private val tokens: AccessTokens,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun status() = AuthStatus(familyExists = transaction(database) { Organizations.selectAll().count() > 0 })

    /** The very first account: creates the family and becomes its Owner. Refused once a family exists. */
    fun setup(request: SetupRequest): Tokens {
        val familyName = Validate.name(request.familyName, "Nazwa społeczności")
        val name = Validate.name(request.name, "Imię")
        val email = Validate.email(request.email)
        Validate.password(request.password)
        val hash = Passwords.hash(request.password)
        return transaction(database) {
            if (Organizations.selectAll().count() > 0) {
                throw ApiException(HttpStatusCode.Conflict, "family_exists", "Społeczność jest już założona. Zaloguj się albo dołącz z kodem.")
            }
            val now = clock()
            val organizationId = Secrets.id()
            Organizations.insert {
                it[id] = organizationId
                it[Organizations.name] = familyName
                it[createdAt] = now
            }
            val userId = insertUser(organizationId, name, email, hash, Role.OWNER)
            startSession(userId, request.deviceName)
        }
    }

    /** Joining with a one-time invitation code from a parent. */
    fun register(request: RegisterRequest): Tokens {
        val name = Validate.name(request.name, "Imię")
        val email = Validate.email(request.email)
        Validate.password(request.password)
        val code = request.code.trim().uppercase()
        val hash = Passwords.hash(request.password)
        return transaction(database) {
            val now = clock()
            val invitation = Invitations.selectAll().where { Invitations.code eq code }.singleOrNull()
            if (invitation == null || invitation[Invitations.usedBy] != null || invitation[Invitations.expiresAt] < now) {
                throw ApiException(HttpStatusCode.BadRequest, "invalid_code", "Kod zaproszenia jest nieprawidłowy, wykorzystany albo wygasł.")
            }
            val userId = insertUser(invitation[Invitations.organizationId], name, email, hash, Role.MEMBER)
            Invitations.update({ Invitations.code eq code }) { it[usedBy] = userId }
            startSession(userId, request.deviceName)
        }
    }

    fun login(request: LoginRequest): Tokens {
        val email = request.email.trim().lowercase()
        val wrong = ApiException(HttpStatusCode.Unauthorized, "wrong_credentials", "Nieprawidłowy e-mail lub hasło.")
        val user = transaction(database) { Users.selectAll().where { Users.email eq email }.singleOrNull() } ?: run {
            // Same work as a real check, so the answer time does not reveal which e-mails exist.
            Passwords.hash(request.password)
            throw wrong
        }
        val userId = user[Users.id]
        val now = clock()
        user[Users.lockedUntil]?.takeIf { it > now }?.let { throw locked(it) }

        if (!Passwords.verify(request.password, user[Users.passwordHash])) {
            // Saved before throwing: an exception inside a transaction would roll the counter back.
            val failures = user[Users.failedLogins] + 1
            val until = if (failures >= AccountRules.MAX_FAILED_LOGINS) now + AccountRules.LOCK_MINUTES * 60_000 else null
            transaction(database) {
                Users.update({ Users.id eq userId }) {
                    it[failedLogins] = if (until != null) 0 else failures
                    if (until != null) it[lockedUntil] = until
                }
            }
            throw until?.let(::locked) ?: wrong
        }
        return transaction(database) {
            Users.update({ Users.id eq userId }) {
                it[failedLogins] = 0
                it[lockedUntil] = null
            }
            startSession(userId, request.deviceName)
        }
    }

    /** Swaps a refresh token for a new pair (the old refresh token stops working). */
    fun refresh(refreshToken: String): Tokens = transaction(database) {
        val now = clock()
        val session = Sessions.selectAll().where { Sessions.tokenHash eq Secrets.sha256(refreshToken) }.singleOrNull()
        if (session == null || now - session[Sessions.lastUsedAt] > idleLimitMs()) {
            session?.let { expired -> Sessions.deleteWhere { id eq expired[Sessions.id] } }
            throw ApiException(HttpStatusCode.Unauthorized, "session_expired", "Sesja wygasła. Zaloguj się ponownie.")
        }
        val newToken = Secrets.token()
        Sessions.update({ Sessions.id eq session[Sessions.id] }) {
            it[tokenHash] = Secrets.sha256(newToken)
            it[lastUsedAt] = now
        }
        Tokens(tokens.issue(session[Sessions.userId], session[Sessions.id]), newToken)
    }

    /** Checked on every authenticated request, so a signed-out device loses access at once. */
    fun sessionActive(userId: String, sessionId: String): Boolean = transaction(database) {
        Sessions.selectAll().where { (Sessions.id eq sessionId) and (Sessions.userId eq userId) }.count() > 0
    }

    fun logout(session: UserSession) {
        transaction(database) { Sessions.deleteWhere { id eq session.sessionId } }
    }

    /** "I forgot my password": a code from a parent sets a new password and signs out all devices. */
    fun resetPassword(request: ResetPasswordRequest) {
        Validate.password(request.newPassword)
        val email = request.email.trim().lowercase()
        val codeHash = Secrets.sha256(request.code.trim().uppercase())
        val hash = Passwords.hash(request.newPassword)
        transaction(database) {
            val reset = PasswordResets.selectAll().where { PasswordResets.codeHash eq codeHash }.singleOrNull()
            val user = reset?.let { Users.selectAll().where { Users.id eq it[PasswordResets.userId] }.singleOrNull() }
            if (reset == null || user == null || user[Users.email] != email || reset[PasswordResets.expiresAt] < clock()) {
                throw ApiException(HttpStatusCode.BadRequest, "invalid_code", "Kod jest nieprawidłowy albo wygasł. Poproś Ownera lub Admina o nowy.")
            }
            val userId = user[Users.id]
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = hash
                it[failedLogins] = 0
                it[lockedUntil] = null
            }
            PasswordResets.deleteWhere { PasswordResets.userId eq userId }
            Sessions.deleteWhere { Sessions.userId eq userId }
        }
    }

    fun changePassword(session: UserSession, request: ChangePasswordRequest) {
        Validate.password(request.newPassword)
        val user = user(session.userId)
        if (!Passwords.verify(request.currentPassword, user[Users.passwordHash])) {
            throw ApiException(HttpStatusCode.BadRequest, "wrong_password", "Obecne hasło jest nieprawidłowe.")
        }
        val hash = Passwords.hash(request.newPassword)
        transaction(database) {
            Users.update({ Users.id eq session.userId }) { it[passwordHash] = hash }
            // Other devices must sign in again with the new password; this one stays signed in.
            Sessions.deleteWhere { (userId eq session.userId) and (id neq session.sessionId) }
        }
    }

    fun me(session: UserSession): Me = transaction(database) {
        val user = user(session.userId)
        Me(
            id = user[Users.id],
            name = user[Users.name],
            email = user[Users.email],
            role = Role.valueOf(user[Users.role]),
            familyName = familyName(user[Users.organizationId]),
        )
    }

    fun family(session: UserSession): Family = transaction(database) {
        val user = user(session.userId)
        val organizationId = user[Users.organizationId]
        val manager = Role.valueOf(user[Users.role]).canManage
        Family(
            name = familyName(organizationId),
            members = Users.selectAll().where { Users.organizationId eq organizationId }
                .orderBy(Users.createdAt)
                .map { Member(it[Users.id], it[Users.name], Role.valueOf(it[Users.role])) },
            children = ChildProfiles.selectAll().where { ChildProfiles.organizationId eq organizationId }
                .orderBy(ChildProfiles.createdAt)
                .map { it.toChild() },
            invitations = if (!manager) emptyList() else Invitations.selectAll()
                .where { (Invitations.organizationId eq organizationId) and Invitations.usedBy.isNull() and (Invitations.expiresAt greater clock()) }
                .orderBy(Invitations.createdAt)
                .map { Invitation(it[Invitations.code], it[Invitations.expiresAt]) },
        )
    }

    fun createInvitation(session: UserSession): Invitation = transaction(database) {
        val manager = requireManager(session)
        val now = clock()
        val invitation = Invitation(Secrets.code(6), now + AccountRules.INVITATION_DAYS * DAY_MS)
        Invitations.insert {
            it[code] = invitation.code
            it[organizationId] = manager[Users.organizationId]
            it[createdBy] = manager[Users.id]
            it[createdAt] = now
            it[expiresAt] = invitation.expiresAt
        }
        invitation
    }

    fun cancelInvitation(session: UserSession, code: String) {
        transaction(database) {
            val manager = requireManager(session)
            Invitations.deleteWhere {
                (Invitations.code eq code) and (organizationId eq manager[Users.organizationId]) and usedBy.isNull()
            }
        }
    }

    fun addChild(session: UserSession, request: ChildRequest): ChildProfile {
        val name = Validate.name(request.name, "Imię")
        val color = Validate.color(request.color)
        return transaction(database) {
            val manager = requireManager(session)
            val child = ChildProfile(Secrets.id(), name, color)
            ChildProfiles.insert {
                it[id] = child.id
                it[organizationId] = manager[Users.organizationId]
                it[ChildProfiles.name] = name
                it[ChildProfiles.color] = color
                it[createdAt] = clock()
            }
            child
        }
    }

    fun updateChild(session: UserSession, childId: String, request: ChildRequest): ChildProfile {
        val name = Validate.name(request.name, "Imię")
        val color = Validate.color(request.color)
        return transaction(database) {
            val manager = requireManager(session)
            val changed = ChildProfiles.update({ (ChildProfiles.id eq childId) and (ChildProfiles.organizationId eq manager[Users.organizationId]) }) {
                it[ChildProfiles.name] = name
                it[ChildProfiles.color] = color
            }
            if (changed == 0) throw notFound()
            ChildProfile(childId, name, color)
        }
    }

    fun deleteChild(session: UserSession, childId: String) {
        transaction(database) {
            val manager = requireManager(session)
            ChildProfiles.deleteWhere { (id eq childId) and (organizationId eq manager[Users.organizationId]) }
        }
    }

    fun removeMember(session: UserSession, memberId: String) {
        transaction(database) {
            val member = requireManageable(session, memberId)
            val id = member[Users.id]
            Invitations.update({ Invitations.usedBy eq id }) { it[usedBy] = null }
            Invitations.deleteWhere { createdBy eq id }
            Users.deleteWhere { Users.id eq id }
        }
    }

    /** A one-time code (valid 24 h) the Owner hands to someone who forgot the password. */
    fun createResetCode(session: UserSession, memberId: String): PasswordResetCode = transaction(database) {
        val member = requireManageable(session, memberId, allowSelf = true)
        val code = PasswordResetCode(Secrets.code(8), clock() + DAY_MS)
        PasswordResets.insert {
            it[codeHash] = Secrets.sha256(code.code)
            it[userId] = member[Users.id]
            it[expiresAt] = code.expiresAt
        }
        code
    }

    /** Only the Owner hands out and takes back the Admin role. */
    fun setRole(session: UserSession, memberId: String, role: Role) {
        if (role != Role.ADMIN && role != Role.MEMBER) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_role", "Można nadać tylko rolę Admin albo User.")
        }
        transaction(database) {
            if (Role.valueOf(user(session.userId)[Users.role]) != Role.OWNER) throw forbidden()
            val member = requireManageable(session, memberId)
            Users.update({ Users.id eq member[Users.id] }) { it[Users.role] = role.name }
        }
    }

    fun signOutMember(session: UserSession, memberId: String) {
        transaction(database) {
            val member = requireManageable(session, memberId)
            Sessions.deleteWhere { userId eq member[Users.id] }
        }
    }

    fun devices(session: UserSession): List<Device> = transaction(database) {
        Sessions.selectAll().where { Sessions.userId eq session.userId }
            .orderBy(Sessions.lastUsedAt, org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .map { Device(it[Sessions.id], it[Sessions.deviceName], it[Sessions.lastUsedAt], it[Sessions.id] == session.sessionId) }
    }

    fun signOutDevice(session: UserSession, deviceId: String) {
        transaction(database) { Sessions.deleteWhere { (id eq deviceId) and (userId eq session.userId) } }
    }

    fun moduleOrder(session: UserSession): List<String> = transaction(database) {
        user(session.userId)[Users.moduleOrder]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun saveModuleOrder(session: UserSession, routes: List<String>) {
        val cleaned = routes.map { it.trim() }.filter { it.matches(Regex("[a-z_]{1,40}")) }.distinct().take(40)
        transaction(database) {
            Users.update({ Users.id eq session.userId }) { it[moduleOrder] = cleaned.joinToString(",") }
        }
    }

    // --- helpers; all run inside a transaction ---

    private fun insertUser(organizationId: String, name: String, email: String, hash: String, role: Role): String {
        if (Users.selectAll().where { Users.email eq email }.count() > 0) {
            throw ApiException(HttpStatusCode.Conflict, "email_taken", "Ten e-mail ma już konto. Zaloguj się.")
        }
        val userId = Secrets.id()
        Users.insert {
            it[id] = userId
            it[Users.organizationId] = organizationId
            it[Users.name] = name
            it[Users.email] = email
            it[passwordHash] = hash
            it[Users.role] = role.name
            it[failedLogins] = 0
            it[createdAt] = clock()
        }
        return userId
    }

    private fun startSession(userId: String, deviceName: String): Tokens {
        val now = clock()
        val sessionId = Secrets.id()
        val refreshToken = Secrets.token()
        Sessions.insert {
            it[id] = sessionId
            it[Sessions.userId] = userId
            it[tokenHash] = Secrets.sha256(refreshToken)
            it[Sessions.deviceName] = deviceName.trim().take(60).ifBlank { "Telefon" }
            it[createdAt] = now
            it[lastUsedAt] = now
        }
        return Tokens(tokens.issue(userId, sessionId), refreshToken)
    }

    private fun user(userId: String): ResultRow = transaction(database) {
        Users.selectAll().where { Users.id eq userId }.singleOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Zaloguj się ponownie.")
    }

    private fun familyName(organizationId: String): String =
        Organizations.selectAll().where { Organizations.id eq organizationId }.single()[Organizations.name]

    /** Owner or Admin: may invite, manage minors and manage people with a lower role. */
    private fun requireManager(session: UserSession): ResultRow {
        val user = user(session.userId)
        if (!Role.valueOf(user[Users.role]).canManage) throw forbidden()
        return user
    }

    /** A person of the same community with a lower role than the caller (or the caller, when [allowSelf]). */
    private fun requireManageable(session: UserSession, memberId: String, allowSelf: Boolean = false): ResultRow {
        val manager = requireManager(session)
        if (memberId == manager[Users.id]) {
            if (allowSelf) return manager
            throw ApiException(HttpStatusCode.BadRequest, "self", "Tej operacji nie można wykonać na własnym koncie.")
        }
        val member = Users.selectAll().where { (Users.id eq memberId) and (Users.organizationId eq manager[Users.organizationId]) }
            .singleOrNull() ?: throw notFound()
        if (Role.valueOf(member[Users.role]).rank >= Role.valueOf(manager[Users.role]).rank) throw forbidden()
        return member
    }

    private fun forbidden() = ApiException(HttpStatusCode.Forbidden, "forbidden", "Nie masz uprawnień do tej operacji.")

    private fun notFound() = ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono.")

    private fun locked(until: Long): ApiException {
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Warsaw")).format(Instant.ofEpochMilli(until))
        return ApiException(HttpStatusCode.TooManyRequests, "locked", "Za dużo nieudanych prób. Spróbuj ponownie po $time.")
    }

    private fun idleLimitMs() = AccountRules.SESSION_IDLE_DAYS * DAY_MS

    private fun ResultRow.toChild() = ChildProfile(this[ChildProfiles.id], this[ChildProfiles.name], this[ChildProfiles.color])

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private object Validate {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    fun name(value: String, field: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 50) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_name", "$field: wpisz od 1 do 50 znaków.")
        }
        return trimmed
    }

    fun email(value: String): String {
        val trimmed = value.trim().lowercase()
        if (!EMAIL.matches(trimmed) || trimmed.length > 120) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_email", "Wpisz poprawny adres e-mail.")
        }
        return trimmed
    }

    fun password(value: String) {
        if (value.length < AccountRules.MIN_PASSWORD_LENGTH || value.length > 200) {
            throw ApiException(
                HttpStatusCode.BadRequest, "weak_password",
                "Hasło musi mieć co najmniej ${AccountRules.MIN_PASSWORD_LENGTH} znaków.",
            )
        }
    }

    fun color(value: String): String {
        if (!COLOR.matches(value)) throw ApiException(HttpStatusCode.BadRequest, "invalid_color", "Nieprawidłowy kolor.")
        return value.uppercase()
    }
}
