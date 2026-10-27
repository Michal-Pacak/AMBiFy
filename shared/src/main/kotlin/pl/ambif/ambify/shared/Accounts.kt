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
    const val CHILDREN = "/family/children"
    const val MEMBERS = "/family/members"
}

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val INVITATION_DAYS = 7L
    const val SESSION_IDLE_DAYS = 90L
    const val MAX_FAILED_LOGINS = 5
    const val LOCK_MINUTES = 15L
}

/** Shown as Owner / Admin / User / Guest; a higher [rank] may manage lower ones. */
@Serializable
enum class Role(val rank: Int) {
    OWNER(3),
    ADMIN(2),
    MEMBER(1),
    GUEST(0);

    val canManage: Boolean get() = this == OWNER || this == ADMIN
}

@Serializable
data class RoleRequest(val role: Role)

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

@Serializable
data class RegisterRequest(
    val code: String,
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

@Serializable
data class Me(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val familyName: String,
)

@Serializable
data class Member(val id: String, val name: String, val role: Role)

@Serializable
data class ChildProfile(val id: String, val name: String, val color: String)

@Serializable
data class ChildRequest(val name: String, val color: String)

@Serializable
data class Invitation(val code: String, val expiresAt: Long)

@Serializable
data class Family(
    val name: String,
    val members: List<Member>,
    val children: List<ChildProfile>,
    /** Only the Owner and Admins see pending invitations. */
    val invitations: List<Invitation>,
)

@Serializable
data class Device(val id: String, val name: String, val lastUsedAt: Long, val current: Boolean)

@Serializable
data class PasswordResetCode(val code: String, val expiresAt: Long)

@Serializable
data class ModuleOrder(val routes: List<String>)
