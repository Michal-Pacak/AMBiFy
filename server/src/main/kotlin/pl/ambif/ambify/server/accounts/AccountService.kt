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
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.AccountRules
import pl.ambif.ambify.shared.PlatformAccount
import pl.ambif.ambify.shared.HiddenPhoto
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.AuthStatus
import pl.ambif.ambify.shared.Avatar
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.Dashboards
import pl.ambif.ambify.shared.WeatherPlace
import pl.ambif.ambify.shared.AvatarShape
import pl.ambif.ambify.shared.Avatars
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.DeleteCommunityRequest
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Entitlements
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.OwnerTransfer
import pl.ambif.ambify.shared.OwnerTransferAnswer
import pl.ambif.ambify.shared.OwnerTransferRequest
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.PasswordResetCode
import pl.ambif.ambify.shared.Permissions
import pl.ambif.ambify.shared.RoleCreate
import pl.ambif.ambify.shared.RequestQueued
import pl.ambif.ambify.shared.PermissionTable
import pl.ambif.ambify.shared.PermissionCell
import pl.ambif.ambify.shared.PendingRequest
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.ProfileUpdate
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ResetPasswordRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

/** A refusal shown to the user: HTTP status, stable code for the app, Polish message. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

/** The caller inside one community: their account row, the community, their role there and all its roles. */
class Membership internal constructor(val user: ResultRow, val organizationId: String, val role: Role, val roles: List<Role>)

class AccountService(
    private val database: Database,
    private val tokens: AccessTokens,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun status() = AuthStatus(familyExists = transaction(database) { Organizations.selectAll().count() > 0 })

    /** A new account together with its first (free) community, in which it becomes the Owner. */
    fun setup(request: SetupRequest): Tokens {
        val familyName = Validate.name(request.familyName, "Nazwa kręgu")
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

    /**
     * Swaps a refresh token for a new pair. The token before stops working once the phone uses the new one (B-007:
     * when the answer got lost — Wi-Fi to LTE, the app killed before saving — the phone still has the token before
     * and must not be signed out for it); an unused new token keeps the one before alive for [PREVIOUS_TOKEN_DAYS].
     */
    fun refresh(refreshToken: String): Tokens = transaction(database) {
        val now = clock()
        val hash = Secrets.sha256(refreshToken)
        val current = Sessions.selectAll().where { Sessions.tokenHash eq hash }.singleOrNull()
        val session = current ?: Sessions.selectAll().where { Sessions.previousTokenHash eq hash }.singleOrNull()
            ?.takeIf { now - (it[Sessions.renewedAt] ?: 0L) <= PREVIOUS_TOKEN_DAYS * DAY_MS }
        if (session == null || now - session[Sessions.lastUsedAt] > idleLimitMs()) {
            session?.let { expired -> Sessions.deleteWhere { id eq expired[Sessions.id] } }
            throw ApiException(HttpStatusCode.Unauthorized, "session_expired", "Sesja wygasła. Zaloguj się ponownie.")
        }
        val newToken = Secrets.token()
        Sessions.update({ Sessions.id eq session[Sessions.id] }) {
            it[tokenHash] = Secrets.sha256(newToken)
            // Used the newest token: the one before may go. Used the one before: it stays until the new one is used.
            it[previousTokenHash] = hash
            it[renewedAt] = now
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
        val permissions = current?.let { community ->
            val roles = rolesOf(community.id)
            val overrides = overrides(community.id)
            Action.entries.associateWith { Permissions.effective(it, community.role, roles, overrides) }
        } ?: emptyMap()
        Me(
            id = user[Users.id],
            name = user[Users.name],
            email = user[Users.email],
            role = current?.role,
            familyName = current?.name,
            communityId = current?.id,
            platformAdmin = user[Users.platformAdmin],
            communities = communities,
            permissions = permissions,
            transfers = transfers(session.userId),
            photo = user[Users.photoId]?.let { AccountPaths.photo(session.userId, it) },
        )
    }

    fun communities(userId: String): List<CommunitySummary> = transaction(database) {
        val name = user(userId)[Users.name]
        val rows = Memberships.join(Organizations, JoinType.INNER, Memberships.organizationId, Organizations.id).selectAll()
            .where { Memberships.userId eq userId }
            .orderBy(Memberships.joinedAt)
            .toList()
        rows.forEach { ensureAvatars(it[Organizations.id]) }
        rows.map { row ->
            val organizationId = row[Organizations.id]
            val avatar = Memberships.selectAll()
                .where { (Memberships.userId eq userId) and (Memberships.organizationId eq organizationId) }
                .single().memberAvatar(name)
            CommunitySummary(
                organizationId, row[Organizations.name], role(row[Memberships.role]), avatar,
                photosBlocked = row[Organizations.photosBlocked], myPhotoHidden = row[Memberships.photoHiddenBy] != null,
                circle = circleAvatar(row),
            )
        }
    }

    /** New name, and a new e-mail only with the current password; a new name may give new initials, so a new icon. */
    fun updateProfile(session: UserSession, update: ProfileUpdate): Me {
        val name = Validate.name(update.name, "Imię")
        val email = update.email?.let(Validate::email)
        val user = user(session.userId)
        if (email != null && email != user[Users.email]) {
            if (!Passwords.verify(update.currentPassword.orEmpty(), user[Users.passwordHash])) {
                throw ApiException(HttpStatusCode.BadRequest, "wrong_password", "Obecne hasło jest nieprawidłowe.")
            }
        }
        transaction(database) {
            if (email != null && email != user[Users.email] && Users.selectAll().where { Users.email eq email }.count() > 0) {
                throw ApiException(HttpStatusCode.Conflict, "email_taken", "Ten e-mail ma już inne konto.")
            }
            Users.update({ Users.id eq session.userId }) {
                it[Users.name] = name
                if (email != null) it[Users.email] = email
            }
            if (Avatars.initials(name) != Avatars.initials(user[Users.name])) {
                val organizations = Memberships.selectAll().where { Memberships.userId eq session.userId }.map { it[Memberships.organizationId] }
                Memberships.update({ Memberships.userId eq session.userId }) { it[avatarShape] = null }
                organizations.forEach { ensureAvatars(it) }
            }
        }
        return me(session)
    }

    /** Only the Owner renames the community. */
    fun renameCommunity(session: UserSession, name: String) {
        val communityName = Validate.name(name, "Nazwa kręgu")
        transaction(database) {
            val membership = requireAction(session, Action.RENAME_COMMUNITY)
            Organizations.update({ Organizations.id eq membership.organizationId }) { it[Organizations.name] = communityName }
        }
    }

    // --- handing the community over (board 16) ---

    /** Open requests for [userId] or from them; expired ones go away. Must run in a transaction. */
    private fun transfers(userId: String): List<OwnerTransfer> {
        val now = clock()
        OwnerTransfers.deleteWhere { (expiresAt greater 0L) and (expiresAt lessEq now) }
        return OwnerTransfers.join(Organizations, JoinType.INNER, OwnerTransfers.organizationId, Organizations.id).selectAll()
            .where { (OwnerTransfers.fromUser eq userId) or (OwnerTransfers.toUser eq userId) }
            .mapNotNull { row ->
                val forMe = row[OwnerTransfers.toUser] == userId
                val declined = row[OwnerTransfers.expiresAt] == 0L
                if (forMe && declined) return@mapNotNull null
                OwnerTransfer(
                    communityId = row[Organizations.id],
                    communityName = row[Organizations.name],
                    fromName = user(row[OwnerTransfers.fromUser])[Users.name],
                    toId = row[OwnerTransfers.toUser],
                    toName = user(row[OwnerTransfers.toUser])[Users.name],
                    leave = row[OwnerTransfers.leaveAfter],
                    createdAt = row[OwnerTransfers.createdAt],
                    expiresAt = row[OwnerTransfers.expiresAt],
                    forMe = forMe,
                    declined = declined,
                )
            }
    }

    /** The Owner asks a person of the community to take it over (a new request replaces the open one). */
    fun requestOwnerTransfer(session: UserSession, request: OwnerTransferRequest) {
        transaction(database) {
            val membership = membership(session)
            if (!membership.role.owner) throw forbidden()
            if (request.toUserId == session.userId) {
                throw ApiException(HttpStatusCode.BadRequest, "self", "Wskaż kogoś innego niż Ty.")
            }
            val member = Memberships.selectAll()
                .where { (Memberships.userId eq request.toUserId) and (Memberships.organizationId eq membership.organizationId) }
                .singleOrNull() ?: throw notFound()
            if (member[Memberships.userId].isEmpty()) throw notFound()
            val now = clock()
            OwnerTransfers.deleteWhere { organizationId eq membership.organizationId }
            OwnerTransfers.insert {
                it[organizationId] = membership.organizationId
                it[fromUser] = session.userId
                it[toUser] = request.toUserId
                it[leaveAfter] = request.leave
                it[createdAt] = now
                it[expiresAt] = now + AccountRules.OWNER_TRANSFER_DAYS * 24 * 60 * 60 * 1000
            }
        }
    }

    /** The Owner withdraws the request (or closes a declined one). */
    fun cancelOwnerTransfer(session: UserSession) {
        transaction(database) {
            val membership = membership(session)
            if (!membership.role.owner) throw forbidden()
            OwnerTransfers.deleteWhere { organizationId eq membership.organizationId }
        }
    }

    /**
     * The asked person answers. Yes: they get the Owner's role; the Owner leaves (when asked so) or keeps the highest
     * other role. No: the Owner sees "declined" and picks someone else.
     */
    fun answerOwnerTransfer(session: UserSession, answer: OwnerTransferAnswer) {
        transaction(database) {
            val membership = membership(session)
            val now = clock()
            val row = OwnerTransfers.selectAll().where { OwnerTransfers.organizationId eq membership.organizationId }.singleOrNull()
                ?.takeIf { it[OwnerTransfers.toUser] == session.userId && it[OwnerTransfers.expiresAt] > now }
                ?: throw ApiException(HttpStatusCode.NotFound, "transfer_gone", "Ta prośba wygasła albo została anulowana.")
            if (!answer.accept) {
                OwnerTransfers.update({ OwnerTransfers.organizationId eq membership.organizationId }) { it[expiresAt] = 0L }
                return@transaction
            }
            val roles = rolesOf(membership.organizationId)
            val ownerRole = roles.first { it.owner }
            val fromUser = row[OwnerTransfers.fromUser]
            val stillOwner = Memberships.selectAll()
                .where { (Memberships.userId eq fromUser) and (Memberships.organizationId eq membership.organizationId) }
                .singleOrNull()?.let { it[Memberships.role] == ownerRole.id } == true
            if (!stillOwner) {
                OwnerTransfers.deleteWhere { organizationId eq membership.organizationId }
                throw ApiException(HttpStatusCode.Conflict, "transfer_gone", "Ta prośba jest już nieaktualna.")
            }
            Memberships.update({ (Memberships.userId eq session.userId) and (Memberships.organizationId eq membership.organizationId) }) {
                it[role] = ownerRole.id
            }
            OwnerTransfers.deleteWhere { organizationId eq membership.organizationId }
            if (row[OwnerTransfers.leaveAfter]) {
                removeMembership(fromUser, membership.organizationId)
            } else {
                val next = roles.first { !it.owner }
                Memberships.update({ (Memberships.userId eq fromUser) and (Memberships.organizationId eq membership.organizationId) }) {
                    it[role] = next.id
                }
            }
        }
    }

    /** The Owner alone in the community removes it with everything in it; the name is typed again to confirm. */
    fun deleteCommunity(session: UserSession, request: DeleteCommunityRequest) {
        val organizationId = transaction(database) {
            val membership = membership(session)
            if (!membership.role.owner) throw forbidden()
            val others = Memberships.selectAll()
                .where { (Memberships.organizationId eq membership.organizationId) and (Memberships.userId neq session.userId) }.count()
            if (others > 0) {
                throw ApiException(HttpStatusCode.Conflict, "not_alone", "W kręgu są jeszcze inne osoby — najpierw przekaż go komuś.")
            }
            val name = Organizations.selectAll().where { Organizations.id eq membership.organizationId }.single()[Organizations.name]
            if (request.name.trim() != name.trim()) {
                throw ApiException(HttpStatusCode.BadRequest, "name", "Wpisz dokładnie nazwę kręgu.")
            }
            Invitations.deleteWhere { Invitations.organizationId eq membership.organizationId }
            Memberships.deleteWhere { Memberships.organizationId eq membership.organizationId }
            Organizations.deleteWhere { id eq membership.organizationId }
            membership.organizationId
        }
        // The photos and files of its lists (see ListFileStore); the id comes from the database.
        if (organizationId.matches(Regex("[A-Za-z0-9-]{1,64}"))) java.io.File("lists-files").resolve(organizationId).deleteRecursively()
    }

    /** Admin or User leaves the current community; the Owner cannot (handing the role over comes later). */
    fun leave(session: UserSession) {
        transaction(database) {
            val membership = membership(session)
            if (membership.role.owner) {
                throw ApiException(
                    HttpStatusCode.Conflict, "owner_cannot_leave",
                    "Owner nie może opuścić kręgu — najpierw przekaż go komuś.",
                )
            }
            removeMembership(session.userId, membership.organizationId)
        }
    }

    /**
     * Founding a community: the first one where the person is the Owner is free; every next one needs
     * a payment (not available yet), a founding credit from the Platform Admin, or being the Platform Admin.
     */
    fun createCommunity(session: UserSession, name: String): CommunitySummary {
        val communityName = Validate.name(name, "Nazwa kręgu")
        return transaction(database) {
            val user = user(session.userId)
            val owned = Memberships.join(CommunityRoles, JoinType.INNER, Memberships.role, CommunityRoles.id).selectAll()
                .where { (Memberships.userId eq session.userId) and (CommunityRoles.isOwner eq true) }.count()
            val paid = when {
                owned == 0L -> false
                user[Users.platformAdmin] -> true
                user[Users.foundingCredits] > 0 -> {
                    Users.update({ Users.id eq session.userId }) { it[foundingCredits] = user[Users.foundingCredits] - 1 }
                    true
                }
                else -> throw ApiException(
                    HttpStatusCode.PaymentRequired, "payment_required",
                    "Jesteś już Ownerem darmowego kręgu. Kolejny wymaga jednorazowej opłaty (wkrótce dostępne).",
                )
            }
            val organizationId = insertCommunity(session.userId, communityName, paid)
            CommunitySummary(organizationId, communityName, rolesOf(organizationId).first { it.owner })
        }
    }

    /** A signed-in person joins one more community with its invitation code. */
    fun join(session: UserSession, code: String): CommunitySummary = transaction(database) {
        val invitation = validInvitation(code.trim().uppercase())
        val organizationId = invitation[Invitations.organizationId]
        if (Memberships.selectAll().where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq organizationId) }.count() > 0) {
            throw ApiException(HttpStatusCode.Conflict, "already_member", "Już należysz do tego kręgu.")
        }
        acceptInvitation(invitation, session.userId)
        CommunitySummary(organizationId, communityName(organizationId), lowestRole(organizationId))
    }

    fun family(session: UserSession): Family = transaction(database) {
        val membership = membership(session)
        val organizationId = membership.organizationId
        ensureAvatars(organizationId)
        Family(
            name = communityName(organizationId),
            members = membersOf(organizationId)
                .orderBy(Memberships.joinedAt)
                .map { Member(it[Users.id], it[Users.name], role(it[Memberships.role]), it.memberAvatar(it[Users.name]), it[Memberships.joinedAt]) },
            roles = membership.roles,
            invitations = if (can(membership, Action.INVITE) != Mode.ALLOWED) emptyList() else Invitations.selectAll()
                .where { (Invitations.organizationId eq organizationId) and Invitations.usedBy.isNull() and (Invitations.expiresAt greater clock()) }
                .orderBy(Invitations.createdAt)
                .map { Invitation(it[Invitations.code], it[Invitations.expiresAt]) },
            photosBlocked = Organizations.select(Organizations.photosBlocked).where { Organizations.id eq organizationId }.single()[Organizations.photosBlocked],
            hiddenPhotos = if (can(membership, Action.PHOTOS_MODERATE) != Mode.ALLOWED) emptyList() else membersOf(organizationId)
                .filter { it[Memberships.photoHiddenBy] != null }
                .map { row ->
                    val by = Users.select(Users.name).where { Users.id eq row[Memberships.photoHiddenBy]!! }.singleOrNull()?.get(Users.name) ?: "?"
                    HiddenPhoto(row[Users.id], row[Users.name], by, row[Memberships.photoHiddenAt] ?: 0)
                },
        )
    }

    fun createInvitation(session: UserSession): Invitation = transaction(database) {
        val manager = requireAction(session, Action.INVITE)
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
            val manager = requireAction(session, Action.INVITE)
            Invitations.deleteWhere {
                (Invitations.code eq code) and (organizationId eq manager.organizationId) and usedBy.isNull()
            }
        }
    }

    /** Takes the person out of this community; their account and other communities stay. */
    fun removeMember(session: UserSession, memberId: String) {
        transaction(database) {
            removeMembership(memberId, requireManageable(session, memberId, Action.REMOVE_MEMBERS).organizationId)
        }
    }

    /**
     * A one-time code (valid 24 h) handed to someone of a lower role who forgot the password.
     * B-013: the password is the whole account's, and whoever holds the code can sign in as that person. So a
     * community may reset it only for someone who belongs to it alone (e.g. a child); for anyone else (other
     * communities, the Platform Admin) only the person or the AMBiFy team can.
     */
    fun createResetCode(session: UserSession, memberId: String): PasswordResetCode = transaction(database) {
        requireManageable(session, memberId, Action.REMOVE_MEMBERS, allowSelf = true)
        if (memberId != session.userId) {
            val circles = Memberships.selectAll().where { Memberships.userId eq memberId }.count()
            val platformAdmin = Users.selectAll().where { Users.id eq memberId }.single()[Users.platformAdmin]
            if (circles > 1 || platformAdmin) {
                throw ApiException(
                    HttpStatusCode.Conflict, "other_circles",
                    "Ta osoba należy też do innych kręgów. Nowe hasło może ustawić tylko ona albo zespół AMBiFy.",
                )
            }
        }
        insertResetCode(memberId)
    }

    /** Gives a person of a lower role another role that is also lower than the caller's (never the Owner's). */
    fun setRole(session: UserSession, memberId: String, roleId: String) {
        transaction(database) {
            val manager = requireManageable(session, memberId, Action.ASSIGN_ROLES)
            val target = manager.roles.firstOrNull { it.id == roleId } ?: throw notFound()
            if (target.owner || target.rank >= manager.role.rank) {
                throw ApiException(HttpStatusCode.BadRequest, "invalid_role", "Można nadać tylko rolę niższą od własnej.")
            }
            Memberships.update({ (Memberships.userId eq memberId) and (Memberships.organizationId eq manager.organizationId) }) {
                it[Memberships.role] = target.id
            }
        }
    }

    fun signOutMember(session: UserSession, memberId: String) {
        transaction(database) {
            requireManageable(session, memberId, Action.REMOVE_MEMBERS)
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

    /** The caller's own Dashboard in the current community; empty for someone without a community. */
    fun dashboard(session: UserSession): Dashboard = transaction(database) {
        val membership = membershipOrNull(session) ?: return@transaction Dashboard()
        Memberships.selectAll()
            .where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq membership.organizationId) }
            .single()[Memberships.dashboard]
            ?.let { runCatching { dashboardJson.decodeFromString<Dashboard>(it) }.getOrNull() } ?: Dashboard()
    }

    /** Saves the whole Dashboard (the app sends it after every change); short ids and keys only. */
    fun saveDashboard(session: UserSession, dashboard: Dashboard) {
        val token = Regex("[A-Za-z0-9_-]{1,64}")
        val cleaned = Dashboards.normalized(
            Dashboard(
                dashboard.widgets
                    .filter { it.id.matches(token) && it.type.matches(token) }
                    .map { it.copy(people = it.people.filter { p -> p.matches(token) || WeatherPlace.decode(p) != null }.distinct().take(20)) },
            ),
        )
        transaction(database) {
            val membership = membershipOrNull(session) ?: return@transaction
            Memberships.update({ (Memberships.userId eq session.userId) and (Memberships.organizationId eq membership.organizationId) }) {
                it[Memberships.dashboard] = dashboardJson.encodeToString(cleaned)
            }
        }
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
        communityModules(membership(session).organizationId, session.userId)
    }

    /** The Owner or an Admin switches a module the community has on or off for everyone. */
    fun setModule(session: UserSession, module: ModuleId, enabled: Boolean): CommunityModules = transaction(database) {
        val manager = requireAction(session, Action.SWITCH_MODULES)
        val organizationId = manager.organizationId
        if (module !in Entitlements.owned(grantsFor(organizationId, session.userId))) {
            throw ApiException(HttpStatusCode.Forbidden, "not_owned", "Ten krąg nie ma modułu ${module.label}.")
        }
        if (enabled) {
            DisabledModules.deleteWhere { (DisabledModules.organizationId eq organizationId) and (DisabledModules.module eq module.name) }
        } else {
            DisabledModules.insertIgnore {
                it[DisabledModules.organizationId] = organizationId
                it[DisabledModules.module] = module.name
            }
        }
        communityModules(organizationId, session.userId)
    }

    /** For module routes (from Stage 4 on): refuses a module the current community does not have switched on. */
    fun requireModule(session: UserSession, module: ModuleId) {
        transaction(database) {
            if (module !in communityModules(membership(session).organizationId, session.userId).enabled) {
                throw ApiException(HttpStatusCode.Forbidden, "module_off", "Moduł ${module.label} nie jest włączony w tym kręgu.")
            }
        }
    }

    // --- roles, the permission table and requests waiting for approval ---

    /**
     * Runs an approved request: registered by the module that queues it (e.g. the timetable), with the community,
     * the person who asked and what they asked for (JSON).
     */
    val approvalHandlers = mutableMapOf<Action, (organizationId: String, requesterId: String, payload: String) -> Unit>()

    /** Optional clean-up when a request is refused (e.g. a task for someone else shown as waiting goes away). */
    val rejectionHandlers = mutableMapOf<Action, (organizationId: String, requesterId: String, payload: String) -> Unit>()

    /** The whole table for the current community; anyone in it may look, only the Owner changes it. */
    fun permissionTable(session: UserSession): PermissionTable = transaction(database) {
        val membership = membership(session)
        val overrides = overrides(membership.organizationId)
        PermissionTable(
            roles = membership.roles,
            cells = membership.roles.flatMap { role ->
                Action.entries.map { action ->
                    PermissionCell(
                        role.id, action, Permissions.effective(action, role, membership.roles, overrides),
                        isDefault = (role.id to action) !in overrides,
                    )
                }
            },
        )
    }

    /** The Owner sets what [roleId] may do with [action]; null (or the default itself) puts the default back. */
    fun setPermission(session: UserSession, roleId: String, action: Action, mode: Mode?): PermissionTable {
        transaction(database) {
            val owner = requireAction(session, Action.EDIT_PERMISSIONS)
            val role = owner.roles.firstOrNull { it.id == roleId } ?: throw notFound()
            if (role.owner || action.ownerOnly) {
                throw ApiException(HttpStatusCode.BadRequest, "fixed", "To uprawnienie należy tylko do Ownera i nie można go zmienić.")
            }
            if (mode == Mode.APPROVAL && !action.approvable) {
                throw ApiException(HttpStatusCode.BadRequest, "no_approval", "Tego działania nie da się wykonywać po zatwierdzeniu.")
            }
            RolePermissions.deleteWhere { (RolePermissions.roleId eq roleId) and (RolePermissions.action eq action.name) }
            val ordered = owner.roles.filterNot { it.owner }
            val default = Permissions.defaultFor(action, ordered.indexOfFirst { it.id == roleId }, ordered.size)
            if (mode != null && mode != default) {
                RolePermissions.insert {
                    it[RolePermissions.roleId] = roleId
                    it[RolePermissions.action] = action.name
                    it[RolePermissions.mode] = mode.name
                }
            }
        }
        return permissionTable(session)
    }

    /** A new role right below [RoleCreate.belowRoleId] (the Owner's role: at the top). */
    fun createRole(session: UserSession, request: RoleCreate): List<Role> {
        val name = Validate.name(request.name, "Nazwa roli")
        return transaction(database) {
            val owner = requireAction(session, Action.MANAGE_ROLES)
            if (owner.roles.any { it.name.equals(name, ignoreCase = true) }) {
                throw ApiException(HttpStatusCode.Conflict, "role_exists", "Rola o tej nazwie już jest.")
            }
            val index = owner.roles.indexOfFirst { it.id == request.belowRoleId }.takeIf { it >= 0 } ?: throw notFound()
            val newId = Secrets.id()
            CommunityRoles.insert {
                it[id] = newId
                it[organizationId] = owner.organizationId
                it[CommunityRoles.name] = name
                it[rank] = 0
                it[isOwner] = false
                it[createdAt] = clock()
            }
            val order = owner.roles.map { it.id }.toMutableList().apply { add(index + 1, newId) }
            renumber(order)
            rolesOf(owner.organizationId)
        }
    }

    fun renameRole(session: UserSession, roleId: String, name: String): List<Role> {
        val roleName = Validate.name(name, "Nazwa roli")
        return transaction(database) {
            val owner = requireAction(session, Action.MANAGE_ROLES)
            val role = owner.roles.firstOrNull { it.id == roleId && !it.owner } ?: throw notFound()
            CommunityRoles.update({ CommunityRoles.id eq role.id }) { it[CommunityRoles.name] = roleName }
            rolesOf(owner.organizationId)
        }
    }

    /** One place up or down among the roles under the Owner. */
    fun moveRole(session: UserSession, roleId: String, up: Boolean): List<Role> = transaction(database) {
        val owner = requireAction(session, Action.MANAGE_ROLES)
        val order = owner.roles.filterNot { it.owner }.map { it.id }.toMutableList()
        val index = order.indexOf(roleId).takeIf { it >= 0 } ?: throw notFound()
        val other = if (up) index - 1 else index + 1
        if (other in order.indices) {
            order[index] = order[other].also { order[other] = order[index] }
            renumber(listOf(owner.roles.first { it.owner }.id) + order)
        }
        rolesOf(owner.organizationId)
    }

    /** Only a role nobody has; one role under the Owner always stays (new people join with the lowest). */
    fun deleteRole(session: UserSession, roleId: String): List<Role> = transaction(database) {
        val owner = requireAction(session, Action.MANAGE_ROLES)
        val role = owner.roles.firstOrNull { it.id == roleId && !it.owner } ?: throw notFound()
        if (Memberships.selectAll().where { Memberships.role eq role.id }.count() > 0) {
            throw ApiException(HttpStatusCode.Conflict, "role_in_use", "Ktoś ma tę rolę. Najpierw nadaj tym osobom inną.")
        }
        if (owner.roles.count { !it.owner } <= 1) {
            throw ApiException(HttpStatusCode.Conflict, "last_role", "Musi zostać co najmniej jedna rola poza Ownerem.")
        }
        CommunityRoles.deleteWhere { id eq role.id }
        rolesOf(owner.organizationId)
    }

    /** Requests the caller may decide on: waiting, for actions their role may do at once. */
    fun requests(session: UserSession): List<PendingRequest> = transaction(database) {
        val membership = membership(session)
        PermissionRequests.join(Users, JoinType.INNER, PermissionRequests.requesterId, Users.id).selectAll()
            .where { (PermissionRequests.organizationId eq membership.organizationId) and (PermissionRequests.status eq PENDING) }
            .orderBy(PermissionRequests.createdAt)
            .mapNotNull { row ->
                val action = Action.entries.firstOrNull { it.name == row[PermissionRequests.action] } ?: return@mapNotNull null
                if (can(membership, action) != Mode.ALLOWED) return@mapNotNull null
                PendingRequest(row[PermissionRequests.id], action, row[Users.name], row[PermissionRequests.summary], row[PermissionRequests.createdAt])
            }
    }

    /** Queues [action] for approval; the module runs it later through [approvalHandlers]. Must run in a transaction. */
    internal fun queueRequest(membership: Membership, action: Action, payload: String, summary: String): RequestQueued {
        val requestId = Secrets.id()
        PermissionRequests.insert {
            it[id] = requestId
            it[organizationId] = membership.organizationId
            it[requesterId] = membership.user[Users.id]
            it[PermissionRequests.action] = action.name
            it[PermissionRequests.payload] = payload
            it[PermissionRequests.summary] = summary
            it[status] = PENDING
            it[createdAt] = clock()
        }
        return RequestQueued(requestId, "Wysłano do zatwierdzenia. Zadziała, gdy zatwierdzi je Admin albo Owner.")
    }

    fun decide(session: UserSession, requestId: String, approve: Boolean) {
        transaction(database) {
            val membership = membership(session)
            val request = PermissionRequests.selectAll()
                .where { (PermissionRequests.id eq requestId) and (PermissionRequests.organizationId eq membership.organizationId) }
                .singleOrNull() ?: throw notFound()
            if (request[PermissionRequests.status] != PENDING) {
                throw ApiException(HttpStatusCode.Conflict, "decided", "Ta prośba została już rozpatrzona.")
            }
            val action = Action.valueOf(request[PermissionRequests.action])
            if (can(membership, action) != Mode.ALLOWED) throw forbidden()
            if (approve) {
                val handler = approvalHandlers[action] ?: throw ApiException(HttpStatusCode.Conflict, "no_handler", "Tej prośby nie da się teraz wykonać.")
                handler(membership.organizationId, request[PermissionRequests.requesterId], request[PermissionRequests.payload])
            } else {
                rejectionHandlers[action]?.invoke(membership.organizationId, request[PermissionRequests.requesterId], request[PermissionRequests.payload])
            }
            PermissionRequests.update({ PermissionRequests.id eq requestId }) {
                it[status] = if (approve) APPROVED else REJECTED
                it[decidedBy] = session.userId
                it[decidedAt] = clock()
            }
        }
    }

    /** Ranks spread evenly in [order] (highest first); the Owner's role keeps its fixed top rank. */
    private fun renumber(order: List<String>) {
        val roles = order.filter { id -> !CommunityRoles.selectAll().where { CommunityRoles.id eq id }.single()[CommunityRoles.isOwner] }
        roles.forEachIndexed { index, id ->
            CommunityRoles.update({ CommunityRoles.id eq id }) { it[rank] = (roles.size - index) * 100 }
        }
    }

    // --- Platform Admin ---

    fun platformCommunities(session: UserSession): List<PlatformCommunity> = transaction(database) {
        requirePlatformAdmin(session)
        Organizations.selectAll().orderBy(Organizations.createdAt).map { organization ->
            val organizationId = organization[Organizations.id]
            val members = membersOf(organizationId)
                .map { it[Users.name] to role(it[Memberships.role]) }
            PlatformCommunity(
                id = organizationId,
                name = organization[Organizations.name],
                ownerName = members.firstOrNull { it.second.owner }?.first,
                members = members.size,
                createdAt = organization[Organizations.createdAt],
                paidFounding = organization[Organizations.paidFounding],
                grants = grants(organizationId).sorted(),
            )
        }
    }

    /** Every account with its communities: the Platform Admin's hints while typing an e-mail. */
    fun platformAccounts(session: UserSession): List<PlatformAccount> = transaction(database) {
        requirePlatformAdmin(session)
        val names = Organizations.selectAll().associate { it[Organizations.id] to it[Organizations.name] }
        val communities = Memberships.selectAll().groupBy({ it[Memberships.userId] }) { names[it[Memberships.organizationId]] }
        Users.selectAll().orderBy(Users.name).map { user ->
            PlatformAccount(
                user[Users.name], user[Users.email], communities[user[Users.id]].orEmpty().filterNotNull().sorted(),
                accountPremium = user[Users.accountPremium], foundingCredits = user[Users.foundingCredits],
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

    /** Takes back one free extra community given by mistake (never below zero). */
    fun removeFoundingCredit(session: UserSession, email: String) {
        transaction(database) {
            requirePlatformAdmin(session)
            val user = userByEmail(email)
            Users.update({ Users.id eq user[Users.id] }) { it[foundingCredits] = (user[Users.foundingCredits] - 1).coerceAtLeast(0) }
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
        val ownerRole = "$organizationId:owner"
        for ((suffix, roleName, rank) in listOf(Triple("owner", "Owner", OWNER_RANK), Triple("admin", "Admin", 200), Triple("user", "User", 100))) {
            CommunityRoles.insert {
                it[id] = "$organizationId:$suffix"
                it[CommunityRoles.organizationId] = organizationId
                it[CommunityRoles.name] = roleName
                it[CommunityRoles.rank] = rank
                it[isOwner] = suffix == "owner"
                it[createdAt] = now
            }
        }
        insertMembership(ownerId, organizationId, ownerRole)
        return organizationId
    }

    /** The icon is handed out at once, so it never depends on who joins later. */
    private fun insertMembership(userId: String, organizationId: String, roleId: String) {
        Memberships.insert {
            it[Memberships.userId] = userId
            it[Memberships.organizationId] = organizationId
            it[Memberships.role] = roleId
            it[joinedAt] = clock()
        }
        ensureAvatars(organizationId)
    }

    private fun removeMembership(userId: String, organizationId: String) {
        Invitations.update({ (Invitations.usedBy eq userId) and (Invitations.organizationId eq organizationId) }) { it[usedBy] = null }
        Invitations.deleteWhere { (createdBy eq userId) and (Invitations.organizationId eq organizationId) }
        Memberships.deleteWhere { (Memberships.userId eq userId) and (Memberships.organizationId eq organizationId) }
        // A request asked of someone who is gone is void.
        OwnerTransfers.deleteWhere { (OwnerTransfers.organizationId eq organizationId) and (toUser eq userId) }
    }

    /**
     * Hands out icons to the people of a community who have none yet, in the order they joined: the first shape
     * nobody with the same initials uses, and the least-used color. Icons already handed out never change.
     */
    private fun ensureAvatars(organizationId: String) {
        val members = membersOf(organizationId).toList()
        if (members.none { it[Memberships.avatarShape] == null || it[Memberships.avatarColor] == null }) return

        class Person(val initials: String, val since: Long, var shape: AvatarShape?, var color: String?, val save: (AvatarShape, String) -> Unit)
        val people = members.map { row ->
            Person(
                Avatars.initials(row[Users.name]), row[Memberships.joinedAt],
                row[Memberships.avatarShape]?.let(AvatarShape::valueOf), row[Memberships.avatarColor],
            ) { shape, color ->
                Memberships.update({ (Memberships.userId eq row[Users.id]) and (Memberships.organizationId eq organizationId) }) {
                    it[avatarShape] = shape.name
                    it[avatarColor] = color
                }
            }
        }
        for (person in people.sortedBy { it.since }) {
            if (person.shape != null && person.color != null) continue
            val color = person.color ?: Avatars.freeColor(people.mapNotNull { it.color })
            val shape = person.shape ?: Avatars.freeShape(people.filter { it.initials == person.initials }.mapNotNull { it.shape })
            person.color = color
            person.shape = shape
            person.save(shape, color)
        }
    }

    /** A circle's icon from its [Organizations] row. */
    internal fun circleAvatar(row: ResultRow): pl.ambif.ambify.shared.CircleAvatar = pl.ambif.ambify.shared.CircleAvatar(
        Avatars.initials(row[Organizations.name]),
        row[Organizations.avatarColor],
        row[Organizations.avatarPhotoId]?.let { AccountPaths.circlePhoto(row[Organizations.id], it) },
    )

    internal fun ResultRow.memberAvatar(name: String): Avatar? {
        val shape = this[Memberships.avatarShape] ?: return null
        val color = this[Memberships.avatarColor] ?: return null
        return Avatar(Avatars.initials(name), color, AvatarShape.valueOf(shape), photoPath(this))
    }

    /** The person's photo as seen in the membership's community: none when switched off there or hidden. */
    internal fun photoPath(membership: ResultRow): String? {
        if (membership[Memberships.photoHiddenBy] != null) return null
        val userId = membership[Memberships.userId]
        val organizationId = membership[Memberships.organizationId]
        val blocked = Organizations.select(Organizations.photosBlocked).where { Organizations.id eq organizationId }.single()[Organizations.photosBlocked]
        if (blocked) return null
        val photoId = Users.select(Users.photoId).where { Users.id eq userId }.single()[Users.photoId] ?: return null
        return AccountPaths.photo(userId, photoId)
    }

    private fun validInvitation(code: String): ResultRow {
        val invitation = Invitations.selectAll().where { Invitations.code eq code }.singleOrNull()
        if (invitation == null || invitation[Invitations.usedBy] != null || invitation[Invitations.expiresAt] < clock()) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_code", "Kod zaproszenia jest nieprawidłowy, wykorzystany albo wygasł.")
        }
        return invitation
    }

    private fun acceptInvitation(invitation: ResultRow, userId: String) {
        insertMembership(userId, invitation[Invitations.organizationId], lowestRole(invitation[Invitations.organizationId]).id)
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

    /**
     * A new signed-in device. The person's older sessions with the same device name go: a phone that lost its
     * sign-in (reinstall, cleared data) would otherwise stay on the device list several times (owner's decision
     * 2026-11-16; two phones with the very same name sign each other out).
     */
    private fun startSession(userId: String, deviceName: String): Tokens {
        val now = clock()
        val sessionId = Secrets.id()
        val refreshToken = Secrets.token()
        val name = deviceName.trim().take(60).ifBlank { "Telefon" }
        Sessions.deleteWhere { (Sessions.userId eq userId) and (Sessions.deviceName eq name) }
        Sessions.insert {
            it[id] = sessionId
            it[Sessions.userId] = userId
            it[tokenHash] = Secrets.sha256(refreshToken)
            it[Sessions.deviceName] = name
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

    internal fun membersOf(organizationId: String) =
        Memberships.join(Users, JoinType.INNER, Memberships.userId, Users.id).selectAll()
            .where { Memberships.organizationId eq organizationId }

    private fun communityName(organizationId: String): String =
        Organizations.selectAll().where { Organizations.id eq organizationId }.single()[Organizations.name]

    internal fun grants(organizationId: String): Set<String> =
        OrganizationGrants.selectAll().where { OrganizationGrants.organizationId eq organizationId }
            .map { it[OrganizationGrants.grantKey] }.toSet()

    /** Premium features that work now in the community. Must run in a transaction. */
    internal fun communityFeatures(organizationId: String, userId: String? = null): List<Feature> = communityModules(organizationId, userId).features

    internal fun isPlatformAdmin(userId: String): Boolean =
        Users.select(Users.platformAdmin).where { Users.id eq userId }.singleOrNull()?.get(Users.platformAdmin) == true

    /** The grants as [userId] has them: the Platform Admin has every premium everywhere, always (Michał, 26.11). */
    internal fun grantsFor(organizationId: String, userId: String?): Set<String> =
        if (userId != null && isPlatformAdmin(userId)) Entitlements.grantKeys.toSet() else grants(organizationId)

    private fun communityModules(organizationId: String, userId: String? = null): CommunityModules {
        val grants = grantsFor(organizationId, userId)
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
        val organizationId = row[Memberships.organizationId]
        return Membership(user, organizationId, role(row[Memberships.role]), rolesOf(organizationId))
    }

    /** The caller in the current community; fails for someone without a community. Must run in a transaction. */
    internal fun membership(session: UserSession): Membership = membershipOrNull(session)
        ?: throw ApiException(HttpStatusCode.Conflict, "no_community", "Najpierw załóż krąg albo dołącz do niego z kodem.")

    /** How the caller's role may do [action] in their community. Must run in a transaction. */
    internal fun can(membership: Membership, action: Action): Mode =
        Permissions.effective(action, membership.role, membership.roles, overrides(membership.organizationId))

    /** Names of the community's members whose role may do [action] at once. Must run in a transaction. */
    internal fun membersAllowed(organizationId: String, action: Action): List<String> {
        val roles = rolesOf(organizationId)
        val overrides = overrides(organizationId)
        return membersOf(organizationId).orderBy(Memberships.joinedAt)
            .filter { Permissions.effective(action, role(it[Memberships.role]), roles, overrides) == Mode.ALLOWED }
            .map { it[Users.name] }
    }

    /** The caller, when their role may do [action] at once. Must run in a transaction. */
    internal fun requireAction(session: UserSession, action: Action): Membership {
        val membership = membership(session)
        if (can(membership, action) != Mode.ALLOWED) throw forbidden()
        return membership
    }

    /**
     * A person of the same community with a lower role than the caller (or the caller, when [allowSelf]), and the
     * caller's role may do [action]; returns the caller.
     */
    private fun requireManageable(session: UserSession, memberId: String, action: Action, allowSelf: Boolean = false): Membership {
        val manager = if (allowSelf && memberId == session.userId) membership(session) else requireAction(session, action)
        if (memberId == session.userId) {
            if (allowSelf) return manager
            throw ApiException(HttpStatusCode.BadRequest, "self", "Tej operacji nie można wykonać na własnym koncie.")
        }
        val member = Memberships.selectAll()
            .where { (Memberships.userId eq memberId) and (Memberships.organizationId eq manager.organizationId) }
            .singleOrNull() ?: throw notFound()
        if (role(member[Memberships.role]).rank >= manager.role.rank) throw forbidden()
        return manager
    }

    // --- roles and permissions ---

    /** A community's roles, highest first. */
    internal fun rolesOf(organizationId: String): List<Role> =
        CommunityRoles.selectAll().where { CommunityRoles.organizationId eq organizationId }
            .orderBy(CommunityRoles.rank, SortOrder.DESC)
            .map { it.toRole() }

    internal fun role(roleId: String): Role =
        CommunityRoles.selectAll().where { CommunityRoles.id eq roleId }.single().toRole()

    private fun lowestRole(organizationId: String): Role = rolesOf(organizationId).last()

    private fun ResultRow.toRole() = Role(this[CommunityRoles.id], this[CommunityRoles.name], this[CommunityRoles.rank], this[CommunityRoles.isOwner])

    internal fun overrides(organizationId: String): Map<Pair<String, Action>, Mode> =
        RolePermissions.join(CommunityRoles, JoinType.INNER, RolePermissions.roleId, CommunityRoles.id).selectAll()
            .where { CommunityRoles.organizationId eq organizationId }
            .mapNotNull { row ->
                val action = Action.entries.firstOrNull { it.name == row[RolePermissions.action] } ?: return@mapNotNull null
                (row[RolePermissions.roleId] to action) to Mode.valueOf(row[RolePermissions.mode])
            }.toMap()

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

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val PREVIOUS_TOKEN_DAYS = 7L
        const val GRANTED = "GRANTED"
        const val OWNER_RANK = 1_000_000
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val REJECTED = "REJECTED"
    }
}

internal object Validate {
    // Plain ASCII addresses only: a typo with a Polish letter would create an account nobody can sign in to.
    private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

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
}

private val dashboardJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
