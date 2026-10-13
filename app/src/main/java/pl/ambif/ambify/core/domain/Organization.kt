package pl.ambif.ambify.core.domain

data class Organization(
    val id: String,
    val name: String,
    val type: OrganizationType = OrganizationType.FAMILY,
    val description: String = "",
)

enum class OrganizationType {
    FAMILY,
    COMPANY,
    CLUB,
    SCHOOL,
    ASSOCIATION,
    COMMUNITY,
}
