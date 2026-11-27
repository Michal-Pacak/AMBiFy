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
    /** POST [OwnerTransferRequest] (the Owner asks someone to take over), DELETE (the Owner cancels it). */
    const val OWNER_TRANSFER = "/family/owner-transfer"
    /** POST [OwnerTransferAnswer]: the asked person accepts or declines (the community in the header). */
    const val OWNER_TRANSFER_ANSWER = "/family/owner-transfer/answer"
    /** DELETE: the Owner alone in the community removes it with everything in it (board 16 D). */
    const val COMMUNITY = "/family"
    /** PUT a JPEG (my profile photo, 0.7.6), DELETE removes it. */
    const val MY_PHOTO = "/me/photo"
    /** GET a person's photo: [photo] gives the whole path; `?size=s` the small one. */
    const val PEOPLE = "/people"
    /** PUT [PhotosSetting]: photos on or off in the current community ([Action.PHOTOS_MODERATE]). */
    const val FAMILY_PHOTOS = "/family/photos"

    fun photo(userId: String, photoId: String) = "$PEOPLE/$userId/photo/$photoId"
    /** PUT a JPEG: the current circle's photo; DELETE goes back to initials (the Owner only, 0.7.8). */
    const val FAMILY_AVATAR = "/family/avatar"
    /** PUT [CircleColor]. */
    const val FAMILY_AVATAR_COLOR = "/family/avatar/color"
    const val CIRCLES = "/circles"
    /** GET a circle's photo, for its members; `?size=s` the small one. */
    fun circlePhoto(circleId: String, photoId: String) = "$CIRCLES/$circleId/photo/$photoId"

    /** PUT [PhotoHidden] on `MEMBERS/{id}/photo-hidden`: one person's photo hidden in the current community. */
    const val PHOTO_HIDDEN = "photo-hidden"
}

object PhotoRules {
    /** The phone sends a square of this side; the server keeps it and a small copy of [SMALL_SIDE]. */
    const val SIDE = 1024
    const val SMALL_SIDE = 256
    const val MAX_BYTES = 5L * 1024 * 1024
}

@Serializable
data class PhotosSetting(val blocked: Boolean)

@Serializable
data class PhotoHidden(val hidden: Boolean)

/** Someone whose photo is hidden in the community, for those who may show it again (board 2 A). */
@Serializable
data class HiddenPhoto(val memberId: String, val name: String, val byName: String, val at: Long)

/** Only for the Platform Admin (a flag on the account, above all communities). */
object PlatformPaths {
    const val COMMUNITIES = "/platform/communities"
    const val FOUNDING_CREDITS = "/platform/founding-credits"
    const val FOUNDING_CREDITS_REVOKE = "/platform/founding-credits/revoke"
    const val RESET_CODES = "/platform/reset-codes"
    const val ACCOUNTS = "/platform/accounts"
}

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8
    const val INVITATION_DAYS = 7L
    const val OWNER_TRANSFER_DAYS = 7L
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
    /** Open requests to take over a community: asked of me ([OwnerTransfer.forMe]) or sent by me. */
    val transfers: List<OwnerTransfer> = emptyList(),
    /** My profile photo's path (0.7.6), null without one. */
    val photo: String? = null,
)

/** The Owner of [communityName] asks [toName] to become its Owner; [leave]: the Owner then leaves it. */
@Serializable
data class OwnerTransfer(
    val communityId: String,
    val communityName: String,
    val fromName: String,
    val toId: String,
    val toName: String,
    val leave: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val forMe: Boolean,
    /** The asked person declined: the Owner sees it until they pick someone else or close it. */
    val declined: Boolean = false,
)

@Serializable
data class OwnerTransferRequest(val toUserId: String, val leave: Boolean)

@Serializable
data class OwnerTransferAnswer(val accept: Boolean)

/** The name typed again to confirm removing the community. */
@Serializable
data class DeleteCommunityRequest(val name: String)

/** [avatar] is the person's own icon in that community. */
@Serializable
data class CommunitySummary(
    val id: String,
    val name: String,
    val role: Role,
    val avatar: Avatar? = null,
    /** Photos switched off there (everyone on initials). */
    val photosBlocked: Boolean = false,
    /** My photo hidden there by someone allowed to. */
    val myPhotoHidden: Boolean = false,
    /** The circle's own icon (0.7.8): a rounded square, bigger than people's. */
    val circle: CircleAvatar? = null,
)

/** A circle's icon: [initials] on [color] (null = gold), or its [photo] (path, [AccountPaths.circlePhoto]). */
@Serializable
data class CircleAvatar(val initials: String, val color: String? = null, val photo: String? = null)

/** PUT on [AccountPaths.FAMILY_AVATAR_COLOR]: the circle's color (null = gold); the Owner only. */
@Serializable
data class CircleColor(val color: String? = null)

/** New name, and a new e-mail address only together with the current password. */
@Serializable
data class ProfileUpdate(val name: String, val email: String? = null, val currentPassword: String? = null)

@Serializable
data class CreateCommunityRequest(val name: String)

@Serializable
data class JoinRequest(val code: String)

/** An account on the server, for the Platform Admin's e-mail hints. */
@Serializable
data class PlatformAccount(
    val name: String,
    val email: String,
    val communities: List<String> = emptyList(),
    /** Premium konta (themes) — the hint says whether to grant or take it back. */
    val accountPremium: Boolean = false,
    /** Free extra communities still to use. */
    val foundingCredits: Int = 0,
)

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
data class Member(val id: String, val name: String, val role: Role, val avatar: Avatar? = null, val joinedAt: Long? = null)

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
    val photosBlocked: Boolean = false,
    /** Only for those who may switch photos off ([Action.PHOTOS_MODERATE]). */
    val hiddenPhotos: List<HiddenPhoto> = emptyList(),
)

@Serializable
data class Device(val id: String, val name: String, val lastUsedAt: Long, val current: Boolean)

@Serializable
data class PasswordResetCode(val code: String, val expiresAt: Long)

@Serializable
data class ModuleOrder(val routes: List<String>)
