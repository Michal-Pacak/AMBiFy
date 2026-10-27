package pl.ambif.ambify.core.domain

data class UserAccount(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole = UserRole.MEMBER,
    val organizationId: String? = null,
)

enum class UserRole {
    OWNER,
    ADMIN,
    MEMBER,
}
