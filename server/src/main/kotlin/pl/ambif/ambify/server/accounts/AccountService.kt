package pl.ambif.ambify.server.accounts

import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.shared.AccountRules
import pl.ambif.ambify.shared.AuthStatus
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildProfile
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Entitlements
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

/** A refusal shown to the user: HTTP status, stable code for the app, Polish message. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

/** The caller inside one community: their account row, the community and their role there. */
private class Membership(val user: ResultRow, val organizationId: String, val role: Role)

class AccountService(
    private val database: Database,
    private val tokens: AccessTokens,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun status() = AuthStatus(familyExists = transaction(database) { Organizations.selectAll().count() > 0 })

    /** A new account together with its first (free) community, in which it becomes the Owner. */
    fun setup(request: SetupRequest): Tokens {
        val familyName = Validate.name(request.familyName, "Nazwa społeczności")
        val name = Validate.name(request.name, "Imię")
        val email = Validate.email(request.email)
        Validate.password(request.password)
        val hash = Passwords.hash(request.password)
        return transaction(database) {
            val userId = insertUser(name, email, hash)
            insertCommunity(userId, familyName, paid = false)
            startSession(userId, request.deviceName)
        }
    }

    /** A new account; with an invitation code it joins that community, without one it has none yet. */
    fun register(request: RegisterRequest): Tokens {
        val name = Validate.name(request.name, "Imię")
        val email = Validate.email(request.email)
        Validate.password(request.password)
        val code = request.code?.trim()?.uppercase()?.ifEmpty { null }
        val hash = Passwords.hash(request.password)
        return transaction(database) {
            val invitation = code?.let { validInvitation(it) }
            val userId = insertUser(name, email, hash)
            invitation?.let { acceptInvitation(it, userId) }
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

    /** "I forgot my password": a one-time code sets a new password and signs out all devices. */
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

    /** The profile plus the current community: the requested one, else the first; none for a new account. */
    fun me(session: UserSession): Me = transaction(database) {
        val user = user(session.userId)
        val communities = communities(session.userId)
        val current = communities.firstOrNull { it.id == session.communityId } ?: communities.firstOrNull()
        Me(
            id = user[Users.id],
            name = user[Users.name],
            email = user[Users.email],
            role = current?.role,
            familyName = current?.name,
            communityId = current?.id,
            platformAdmin = user[Users.platformAdmin],
            communities = communities,
        )
    }

    fun communities(userId: String): List<CommunitySummary> = transaction(database) {
        Memberships.join(Organizations, JoinType.INNER, Memberships.organizationId, Organizations.id).selectAll()
            .where { Memberships.userId eq userId }
            .orderBy(Memberships.joinedAt)
            .map { CommunitySummary(it[Organizations.id], it[Organizations.name], Role.valueOf(it[Memberships.role])) }
    }

    /**
     * Founding a community: the first one where the person is the Owner is free; every next one needs
     * a payment (not available yet), a founding credit from the Platform Admin, or being the Platform Admin.
     */
    fun createCommunity(session: UserSession, name: String): CommunitySummary {
        val communityName = Validate.name(name, "Nazwa społeczności")
        return transaction(database) {
            val user = user(session.userId)
            val owned = Memberships.selectAll()
                .where { (Memberships.userId eq session.userId) and (Memberships.role eq Role.OWNER.name) }.count()
            val paid = when {
                owned == 0L -> false
                user[Users.platformAdmin] -> true
                user[Users.foundingCredits] > 0 -> {
                    Users.update({ Users.id eq session.userId }) { it[foundingCredits] = user[Users.foundingCredits] - 1 }
                    true
                }
                else -> throw ApiException(
                    HttpStatusCode.PaymentRequired, "payment_required",
                    "Jesteś już Ownerem darmowej społeczności. Kolejna wymaga jednorazowej opłaty (wkrótce dostępne).",
                )
            }
            CommunitySummary(insertCommunity(session.userId, communityName, paid), communityName, Role.OWNER)
        }
    }

    /** A signed-in person joins one more community with its invitation code. */
    fun join(session: UserSession, code: String): CommunitySummary = transaction(database) {
        val invitation = validInvitation(code.trim().uppercase())
        val organizationId = invitation[Invitations.organizationId]
        if (Memberships.selectAll().where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq organizationId) }.count() > 0) {
            throw ApiException(HttpStatusCode.Conflict, "already_member", "Już należysz do tej społeczności.")
        }
        acceptInvitation(invitation, session.userId)
        CommunitySummary(organizationId, communityName(organizationId), Role.MEMBER)
    }

    fun family(session: UserSession): Family = transaction(database) {
        val membership = membership(session)
        val organizationId = membership.organizationId
        Family(
            name = communityName(organizationId),
            members = membersOf(organizationId)
                .orderBy(Memberships.joinedAt)
                .map { Member(it[Users.id], it[Users.name], Role.valueOf(it[Memberships.role])) },
            children = ChildProfiles.selectAll().where { ChildProfiles.organizationId eq organizationId }
                .orderBy(ChildProfiles.createdAt)
                .map { it.toChild() },
            invitations = if (!membership.role.canManage) emptyList() else Invitations.selectAll()
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
            it[organizationId] = manager.organizationId
            it[createdBy] = session.userId
            it[createdAt] = now
            it[expiresAt] = invitation.expiresAt
        }
        invitation
    }

    fun cancelInvitation(session: UserSession, code: String) {
        transaction(database) {
            val manager = requireManager(session)
            Invitations.deleteWhere {
                (Invitations.code eq code) and (organizationId eq manager.organizationId) and usedBy.isNull()
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
                it[organizationId] = manager.organizationId
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
            val changed = ChildProfiles.update({ (ChildProfiles.id eq childId) and (ChildProfiles.organizationId eq manager.organizationId) }) {
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
            ChildProfiles.deleteWhere { (id eq childId) and (organizationId eq manager.organizationId) }
        }
    }

    /** Takes the person out of this community; their account and other communities stay. */
    fun removeMember(session: UserSession, memberId: String) {
        transaction(database) {
            val manager = requireManageable(session, memberId)
            val organizationId = manager.organizationId
            Invitations.update({ (Invitations.usedBy eq memberId) and (Invitations.organizationId eq organizationId) }) { it[usedBy] = null }
            Invitations.deleteWhere { (createdBy eq memberId) and (Invitations.organizationId eq organizationId) }
            Memberships.deleteWhere { (userId eq memberId) and (Memberships.organizationId eq organizationId) }
        }
    }

    /** A one-time code (valid 24 h) the Owner or an Admin hands to someone who forgot the password. */
    fun createResetCode(session: UserSession, memberId: String): PasswordResetCode = transaction(database) {
        requireManageable(session, memberId, allowSelf = true)
        insertResetCode(memberId)
    }

    /** Only the Owner hands out and takes back the Admin role. */
    fun setRole(session: UserSession, memberId: String, role: Role) {
        if (role != Role.ADMIN && role != Role.MEMBER) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_role", "Można nadać tylko rolę Admin albo User.")
        }
        transaction(database) {
            val manager = requireManageable(session, memberId)
            if (manager.role != Role.OWNER) throw forbidden()
            Memberships.update({ (Memberships.userId eq memberId) and (Memberships.organizationId eq manager.organizationId) }) {
                it[Memberships.role] = role.name
            }
        }
    }

    fun signOutMember(session: UserSession, memberId: String) {
        transaction(database) {
            requireManageable(session, memberId)
            Sessions.deleteWhere { userId eq memberId }
        }
    }

    fun devices(session: UserSession): List<Device> = transaction(database) {
        Sessions.selectAll().where { Sessions.userId eq session.userId }
            .orderBy(Sessions.lastUsedAt, SortOrder.DESC)
            .map { Device(it[Sessions.id], it[Sessions.deviceName], it[Sessions.lastUsedAt], it[Sessions.id] == session.sessionId) }
    }

    fun signOutDevice(session: UserSession, deviceId: String) {
        transaction(database) { Sessions.deleteWhere { (id eq deviceId) and (userId eq session.userId) } }
    }

    /** Kept per person and community; empty for someone without a community. */
    fun moduleOrder(session: UserSession): List<String> = transaction(database) {
        val membership = membershipOrNull(session) ?: return@transaction emptyList()
        Memberships.selectAll()
            .where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq membership.organizationId) }
            .single()[Memberships.moduleOrder]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun saveModuleOrder(session: UserSession, routes: List<String>) {
        val cleaned = routes.map { it.trim() }.filter { it.matches(Regex("[a-z_]{1,40}")) }.distinct().take(40)
        transaction(database) {
            val membership = membershipOrNull(session) ?: return@transaction
            Memberships.update({ (Memberships.userId eq session.userId) and (Memberships.organizationId eq membership.organizationId) }) {
                it[moduleOrder] = cleaned.joinToString(",")
            }
        }
    }

    // --- modules ---

    fun modules(session: UserSession): CommunityModules = transaction(database) {
        communityModules(membership(session).organizationId)
    }

    /** The Owner or an Admin switches a module the community has on or off for everyone. */
    fun setModule(session: UserSession, module: ModuleId, enabled: Boolean): CommunityModules = transaction(database) {
        val manager = requireManager(session)
        val organizationId = manager.organizationId
        if (module !in Entitlements.owned(grants(organizationId))) {
            throw ApiException(HttpStatusCode.Forbidden, "not_owned", "Ta społeczność nie ma modułu ${module.label}.")
        }
        if (enabled) {
            DisabledModules.deleteWhere { (DisabledModules.organizationId eq organizationId) and (DisabledModules.module eq module.name) }
        } else {
            DisabledModules.insertIgnore {
                it[DisabledModules.organizationId] = organizationId
                it[DisabledModules.module] = module.name
            }
        }
        communityModules(organizationId)
    }

    /** For module routes (from Stage 4 on): refuses a module the current community does not have switched on. */
    fun requireModule(session: UserSession, module: ModuleId) {
        transaction(database) {
            if (module !in communityModules(membership(session).organizationId).enabled) {
                throw ApiException(HttpStatusCode.Forbidden, "module_off", "Moduł ${module.label} nie jest włączony w tej społeczności.")
            }
        }
    }

    // --- Platform Admin ---

    fun platformCommunities(session: UserSession): List<PlatformCommunity> = transaction(database) {
        requirePlatformAdmin(session)
        Organizations.selectAll().orderBy(Organizations.createdAt).map { organization ->
            val organizationId = organization[Organizations.id]
            val members = membersOf(organizationId)
                .map { it[Users.name] to Role.valueOf(it[Memberships.role]) }
            PlatformCommunity(
                id = organizationId,
                name = organization[Organizations.name],
                ownerName = members.firstOrNull { it.second == Role.OWNER }?.first,
                members = members.size,
                minors = ChildProfiles.selectAll().where { ChildProfiles.organizationId eq organizationId }.count().toInt(),
                createdAt = organization[Organizations.createdAt],
                paidFounding = organization[Organizations.paidFounding],
                grants = grants(organizationId).sorted(),
            )
        }
    }

    /** Grants or takes back a premium module or feature, free of charge. */
    fun setGrant(session: UserSession, organizationId: String, key: String, granted: Boolean) {
        if (key !in Entitlements.grantKeys) {
            throw ApiException(HttpStatusCode.BadRequest, "unknown_grant", "Nieznany moduł albo funkcja: $key.")
        }
        transaction(database) {
            requirePlatformAdmin(session)
            if (Organizations.selectAll().where { Organizations.id eq organizationId }.count() == 0L) throw notFound()
            if (granted) {
                OrganizationGrants.insertIgnore {
                    it[OrganizationGrants.organizationId] = organizationId
                    it[grantKey] = key
                    it[grantSource] = GRANTED
                    it[grantedBy] = session.userId
                    it[grantedAt] = clock()
                }
            } else {
                OrganizationGrants.deleteWhere { (OrganizationGrants.organizationId eq organizationId) and (grantKey eq key) }
            }
        }
    }

    /** The person with this e-mail may found one more community for free. */
    fun addFoundingCredit(session: UserSession, email: String) {
        transaction(database) {
            requirePlatformAdmin(session)
            val user = userByEmail(email)
            Users.update({ Users.id eq user[Users.id] }) { it[foundingCredits] = user[Users.foundingCredits] + 1 }
        }
    }

    /** For an Owner who forgot the password: nobody in their community can hand them a code. */
    fun platformResetCode(session: UserSession, email: String): PasswordResetCode = transaction(database) {
        requirePlatformAdmin(session)
        insertResetCode(userByEmail(email)[Users.id])
    }

    // --- helpers; all run inside a transaction ---

    /** The very first account on a server becomes its Platform Admin. */
    private fun insertUser(name: String, email: String, hash: String): String {
        if (Users.selectAll().where { Users.email eq email }.count() > 0) {
            throw ApiException(HttpStatusCode.Conflict, "email_taken", "Ten e-mail ma już konto. Zaloguj się.")
        }
        val first = Users.selectAll().count() == 0L
        val userId = Secrets.id()
        Users.insert {
            it[id] = userId
            it[Users.name] = name
            it[Users.email] = email
            it[passwordHash] = hash
            it[failedLogins] = 0
            it[platformAdmin] = first
            it[foundingCredits] = 0
            it[createdAt] = clock()
        }
        return userId
    }

    private fun insertCommunity(ownerId: String, name: String, paid: Boolean): String {
        val now = clock()
        val organizationId = Secrets.id()
        Organizations.insert {
            it[id] = organizationId
            it[Organizations.name] = name
            it[createdAt] = now
            it[createdBy] = ownerId
            it[paidFounding] = paid
        }
        insertMembership(ownerId, organizationId, Role.OWNER)
        return organizationId
    }

    private fun insertMembership(userId: String, organizationId: String, role: Role) {
        Memberships.insert {
            it[Memberships.userId] = userId
            it[Memberships.organizationId] = organizationId
            it[Memberships.role] = role.name
            it[joinedAt] = clock()
        }
    }

    private fun validInvitation(code: String): ResultRow {
        val invitation = Invitations.selectAll().where { Invitations.code eq code }.singleOrNull()
        if (invitation == null || invitation[Invitations.usedBy] != null || invitation[Invitations.expiresAt] < clock()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_code", "Kod zaproszenia jest nieprawidłowy, wykorzystany albo wygasł.")
        }
        return invitation
    }

    private fun acceptInvitation(invitation: ResultRow, userId: String) {
        insertMembership(userId, invitation[Invitations.organizationId], Role.MEMBER)
        Invitations.update({ Invitations.code eq invitation[Invitations.code] }) { it[usedBy] = userId }
    }

    private fun insertResetCode(userId: String): PasswordResetCode {
        val code = PasswordResetCode(Secrets.code(8), clock() + DAY_MS)
        PasswordResets.insert {
            it[codeHash] = Secrets.sha256(code.code)
            it[PasswordResets.userId] = userId
            it[expiresAt] = code.expiresAt
        }
        return code
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

    private fun userByEmail(email: String): ResultRow =
        Users.selectAll().where { Users.email eq email.trim().lowercase() }.singleOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "no_account", "Nie ma konta z tym e-mailem.")

    private fun membersOf(organizationId: String) =
        Memberships.join(Users, JoinType.INNER, Memberships.userId, Users.id).selectAll()
            .where { Memberships.organizationId eq organizationId }

    private fun communityName(organizationId: String): String =
        Organizations.selectAll().where { Organizations.id eq organizationId }.single()[Organizations.name]

    private fun grants(organizationId: String): Set<String> =
        OrganizationGrants.selectAll().where { OrganizationGrants.organizationId eq organizationId }
            .map { it[OrganizationGrants.grantKey] }.toSet()

    private fun communityModules(organizationId: String): CommunityModules {
        val grants = grants(organizationId)
        val disabled = DisabledModules.selectAll().where { DisabledModules.organizationId eq organizationId }
            .mapNotNull { row -> ModuleId.entries.firstOrNull { it.name == row[DisabledModules.module] } }.toSet()
        return CommunityModules(
            owned = Entitlements.owned(grants),
            enabled = Entitlements.enabled(grants, disabled),
            features = Entitlements.features(grants, disabled),
            ownedFeatures = Entitlements.ownedFeatures(grants),
        )
    }

    /** The community named in the request (it must be one of the caller's), else the caller's first one. */
    private fun membershipOrNull(session: UserSession): Membership? {
        val user = user(session.userId)
        val rows = Memberships.selectAll().where { Memberships.userId eq session.userId }.orderBy(Memberships.joinedAt).toList()
        val row = if (session.communityId != null) {
            rows.firstOrNull { it[Memberships.organizationId] == session.communityId } ?: throw notFound()
        } else {
            rows.firstOrNull() ?: return null
        }
        return Membership(user, row[Memberships.organizationId], Role.valueOf(row[Memberships.role]))
    }

    private fun membership(session: UserSession): Membership = membershipOrNull(session)
        ?: throw ApiException(HttpStatusCode.Conflict, "no_community", "Najpierw załóż społeczność albo dołącz do niej z kodem.")

    /** Owner or Admin: may invite, manage minors and manage people with a lower role. */
    private fun requireManager(session: UserSession): Membership {
        val membership = membership(session)
        if (!membership.role.canManage) throw forbidden()
        return membership
    }

    /** A person of the same community with a lower role than the caller (or the caller, when [allowSelf]); returns the caller. */
    private fun requireManageable(session: UserSession, memberId: String, allowSelf: Boolean = false): Membership {
        val manager = requireManager(session)
        if (memberId == session.userId) {
            if (allowSelf) return manager
            throw ApiException(HttpStatusCode.BadRequest, "self", "Tej operacji nie można wykonać na własnym koncie.")
        }
        val member = Memberships.selectAll()
            .where { (Memberships.userId eq memberId) and (Memberships.organizationId eq manager.organizationId) }
            .singleOrNull() ?: throw notFound()
        if (Role.valueOf(member[Memberships.role]).rank >= manager.role.rank) throw forbidden()
        return manager
    }

    private fun requirePlatformAdmin(session: UserSession) {
        if (!user(session.userId)[Users.platformAdmin]) throw forbidden()
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
        const val GRANTED = "GRANTED"
    }
}

private object Validate {
    // Plain ASCII addresses only: a typo with a Polish letter would create an account nobody can sign in to.
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")
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
            throw ApiException(
                HttpStatusCode.BadRequest, "invalid_email",
                "Wpisz poprawny adres e-mail (bez polskich znaków i spacji).",
            )
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
