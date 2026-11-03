package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

object AuthPaths {
    const val STATUS = "/auth/status"
    const val SETUP = "/auth/setup"
    const val REGISTER = "/auth/register"
    const val LOGIN = "/auth/login"
    const val REFRESH = "/auth/refresh"
    const val LOGOUT = "/auth/logout"
    const val RESET_PASSWORD = "/auth/reset-password"
}

object AccountPaths {
    const val ME = "/me"
    const val PASSWORD = "/me/password"
    const val SESSIONS = "/me/sessions"
    const val MODULE_ORDER = "/me/module-order"
    const val FAMILY = "/family"
    const val INVITATIONS = "/family/invitations"
    const val MEMBERS = "/family/members"
    const val COMMUNITIES = "/communities"
    const val JOIN = "/communities/join"
    /** POST: leave the current community (Admin and User; the Owner cannot). */
    const val LEAVE = "/family/leave"
}

/** Only for the Platform Admin (a flag on the account, above all communities). */
object PlatformPaths {
    const val COMMUNITIES = "/platform/communities"
    const val FOUNDING_CREDITS = "/platform/founding-credits"
    const val RESET_CODES = "/platform/reset-codes"
}

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val INVITATION_DAYS = 7L
    const val SESSION_IDLE_DAYS = 90L
    const val MAX_FAILED_LOGINS = 5
    const val LOCK_MINUTES = 15L
}

/**
 * A role in one community. The Owner's role is fixed ([owner]); the others are the community's own, named by
 * its Owner (a new community starts with Admin and User). A higher [rank] may manage people of lower ones.
 */
@Serializable
data class Role(val id: String, val name: String, val rank: Int, val owner: Boolean = false)

@Serializable
data class RoleRequest(val roleId: String)

/** Error body for every non-2xx answer; `code` is stable, `message` is Polish text for the user. */
@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class AuthStatus(val familyExists: Boolean)

@Serializable
data class SetupRequest(
    val familyName: String,
    val name: String,
    val email: String,
    val password: String,
    val deviceName: String,
)

/** A new account; with [code] it also joins that community, without it the person has no community yet. */
@Serializable
data class RegisterRequest(
    val code: String? = null,
    val name: String,
    val email: String,
    val password: String,
    val deviceName: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String, val deviceName: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class Tokens(val accessToken: String, val refreshToken: String)

/** The signed-in person; [role], [familyName] and [communityId] describe the current community (null: none yet). */
@Serializable
data class Me(
    val id: String,
    val name: String,
    val email: String,
    val role: Role? = null,
    val familyName: String? = null,
    val communityId: String? = null,
    val platformAdmin: Boolean = false,
    val communities: List<CommunitySummary> = emptyList(),
    /** What my role may do in the current community (every action of [Action]). */
    val permissions: Map<Action, Mode> = emptyMap(),
)

/** [avatar] is the person's own icon in that community. */
@Serializable
data class CommunitySummary(val id: String, val name: String, val role: Role, val avatar: Avatar? = null)

/** New name, and a new e-mail address only together with the current password. */
@Serializable
data class ProfileUpdate(val name: String, val email: String? = null, val currentPassword: String? = null)

@Serializable
data class CreateCommunityRequest(val name: String)

@Serializable
data class JoinRequest(val code: String)

/** One community on the Platform Admin's list. */
@Serializable
data class PlatformCommunity(
    val id: String,
    val name: String,
    val ownerName: String?,
    val members: Int,
    val createdAt: Long,
    /** False for the free first community of its Owner. */
    val paidFounding: Boolean,
    /** Granted premium modules and features (names from [Entitlements.grantKeys]). */
    val grants: List<String>,
)

@Serializable
data class GrantRequest(val granted: Boolean)

/** Lets the person with this e-mail found one more community without paying (or get a password reset code). */
@Serializable
data class EmailRequest(val email: String)

@Serializable
data class Member(val id: String, val name: String, val role: Role, val avatar: Avatar? = null)

@Serializable
data class Invitation(val code: String, val expiresAt: Long)

@Serializable
data class Family(
    val name: String,
    val members: List<Member>,
    /** The community's roles, highest first (the Owner's role included). */
    val roles: List<Role>,
    /** Only those who may invite see pending invitations. */
    val invitations: List<Invitation>,
)

@Serializable
data class Device(val id: String, val name: String, val lastUsedAt: Long, val current: Boolean)

@Serializable
data class PasswordResetCode(val code: String, val expiresAt: Long)

@Serializable
data class ModuleOrder(val routes: List<String>)
