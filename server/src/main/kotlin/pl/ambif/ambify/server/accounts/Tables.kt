package pl.ambif.ambify.server.accounts

import org.jetbrains.exposed.v1.core.Table

// Mirrors of the tables created by the SQL migrations V002–V005 (the SQL files are the source of truth).

object Organizations : Table("organizations") {
    val id = text("id")
    val name = text("name")
    val createdAt = long("created_at")
    val createdBy = text("created_by").nullable()
    val paidFounding = bool("paid_founding")
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
    override val primaryKey = PrimaryKey(id)
}

object PasswordResets : Table("password_resets") {
    val codeHash = text("code_hash")
    val userId = text("user_id")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(codeHash)
}
