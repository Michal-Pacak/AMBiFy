package pl.ambif.ambify.server.accounts

import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.SetupRequest

/**
 * Test accounts for the "AMBiFy TEST" app (only when the server runs with AMBIFY_DEMO=1 on an empty database).
 * The test app signs in with these in one tap, so switching roles needs no typing.
 */
object DemoSeed {
    const val PASSWORD = "test1234"
    const val OWNER = "owner@test.ambify"
    const val ADMIN = "admin@test.ambify"
    const val USER = "user@test.ambify"
    /** Created first, so it becomes the Platform Admin; Owner of a second test community. */
    const val PLATFORM = "platform@test.ambify"

    /** Seeds the accounts; returns the test Owner's session (for module test data), or null when already seeded. */
    fun seedIfEmpty(service: AccountService, tokens: AccessTokens): UserSession? {
        if (service.status().familyExists) return null
        service.setup(SetupRequest("Druga społeczność testowa", "Pat Platform", PLATFORM, PASSWORD, "seed"))
        val owner = session(tokens, service.setup(SetupRequest("Społeczność testowa", "Ola Owner", OWNER, PASSWORD, "seed")))
        for ((name, email) in listOf("Adam Admin" to ADMIN, "Ula User" to USER)) {
            val code = service.createInvitation(owner).code
            service.register(RegisterRequest(code, name, email, PASSWORD, "seed"))
        }
        val family = service.family(owner)
        val adminId = family.members.single { it.name == "Adam Admin" }.id
        service.setRole(owner, adminId, family.roles.single { it.name == "Admin" }.id)
        return owner
    }

    private fun session(tokens: AccessTokens, issued: pl.ambif.ambify.shared.Tokens): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString())
    }
}
