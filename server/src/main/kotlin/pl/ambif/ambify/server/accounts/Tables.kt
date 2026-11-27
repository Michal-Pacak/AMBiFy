package pl.ambif.ambify.server.accounts

import org.jetbrains.exposed.v1.core.Table

// Mirrors of the tables created by the SQL migrations V002–V007 (the SQL files are the source of truth).

object Organizations : Table("organizations") {
    val id = text("id")
    val name = text("name")
    val createdAt = long("created_at")
    val createdBy = text("created_by").nullable()
    val paidFounding = bool("paid_founding")
    val photosBlocked = bool("photos_blocked").default(false)
    val theme = text("theme").nullable()
    val themeLocked = bool("theme_locked").default(false)
    val themeSetBy = text("theme_set_by").nullable()
    val avatarColor = text("avatar_color").nullable()
    val avatarPhotoId = text("avatar_photo_id").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = text("id")
    val name = text("name")
    val email = text("email")
    val passwordHash = text("password_hash")
    val failedLogins = integer("failed_logins")
    val lockedUntil = long("locked_until").nullable()
    val platformAdmin = bool("platform_admin")
    val foundingCredits = integer("founding_credits")
    val createdAt = long("created_at")
    val photoId = text("photo_id").nullable()
    val theme = text("theme").nullable()
    val accountPremium = bool("account_premium").default(false)
    override val primaryKey = PrimaryKey(id)
}

object Memberships : Table("memberships") {
    val userId = text("user_id")
    val organizationId = text("organization_id")
    val role = text("role")
    val moduleOrder = text("module_order").nullable()
    val joinedAt = long("joined_at")
    val avatarShape = text("avatar_shape").nullable()
    val avatarColor = text("avatar_color").nullable()
    val dashboard = text("dashboard").nullable()
    val photoHiddenBy = text("photo_hidden_by").nullable()
    val photoHiddenAt = long("photo_hidden_at").nullable()
    val theme = text("theme").nullable()
    override val primaryKey = PrimaryKey(userId, organizationId)
}

object OrganizationGrants : Table("organization_grants") {
    val organizationId = text("organization_id")
    val grantKey = text("grant_key")
    val grantSource = text("source")
    val grantedBy = text("granted_by").nullable()
    val grantedAt = long("granted_at")
    override val primaryKey = PrimaryKey(organizationId, grantKey)
}

object DisabledModules : Table("disabled_modules") {
    val organizationId = text("organization_id")
    val module = text("module")
    override val primaryKey = PrimaryKey(organizationId, module)
}

object CommunityRoles : Table("community_roles") {
    val id = text("id")
    val organizationId = text("organization_id")
    val name = text("name")
    val rank = integer("rank")
    val isOwner = bool("is_owner")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RolePermissions : Table("role_permissions") {
    val roleId = text("role_id")
    val action = text("action")
    val mode = text("mode")
    override val primaryKey = PrimaryKey(roleId, action)
}

object PermissionRequests : Table("permission_requests") {
    val id = text("id")
    val organizationId = text("organization_id")
    val requesterId = text("requester_id")
    val action = text("action")
    val payload = text("payload")
    val summary = text("summary")
    val status = text("status")
    val createdAt = long("created_at")
    val decidedBy = text("decided_by").nullable()
    val decidedAt = long("decided_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Invitations : Table("invitations") {
    val code = text("code")
    val organizationId = text("organization_id")
    val createdBy = text("created_by")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val usedBy = text("used_by").nullable()
    override val primaryKey = PrimaryKey(code)
}

object Sessions : Table("sessions") {
    val id = text("id")
    val userId = text("user_id")
    val tokenHash = text("token_hash")
    val deviceName = text("device_name")
    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at")
    /** B-007 (V017): the refresh token before the last renewal, until the phone uses the new one. */
    val previousTokenHash = text("previous_token_hash").nullable()
    val renewedAt = long("renewed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PasswordResets : Table("password_resets") {
    val codeHash = text("code_hash")
    val userId = text("user_id")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(codeHash)
}

/** V013: the Owner's open request to hand the community over (one per community). */
object OwnerTransfers : Table("owner_transfers") {
    val organizationId = text("organization_id")
    val fromUser = text("from_user")
    val toUser = text("to_user")
    val leaveAfter = bool("leave_after")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(organizationId)
}

object OwnThemes : Table("own_themes") {
    val id = text("id")
    val userId = text("user_id").nullable()
    val organizationId = text("organization_id").nullable()
    val spec = text("spec")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Wallpapers : Table("wallpapers") {
    val id = text("id")
    val userId = text("user_id").nullable()
    val organizationId = text("organization_id").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
