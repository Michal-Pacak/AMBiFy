package pl.ambif.ambify.server.accounts

import org.jetbrains.exposed.v1.core.Table

// Mirrors of the tables created by V002__accounts.sql (the SQL file is the source of truth).

object Organizations : Table("organizations") {
    val id = text("id")
    val name = text("name")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = text("id")
    val organizationId = text("organization_id")
    val name = text("name")
    val email = text("email")
    val passwordHash = text("password_hash")
    val role = text("role")
    val failedLogins = integer("failed_logins")
    val lockedUntil = long("locked_until").nullable()
    val moduleOrder = text("module_order").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ChildProfiles : Table("child_profiles") {
    val id = text("id")
    val organizationId = text("organization_id")
    val name = text("name")
    val color = text("color")
    val createdAt = long("created_at")
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
