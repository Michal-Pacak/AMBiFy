package pl.ambif.ambify.server.accounts

import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.Role
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

    fun seedIfEmpty(service: AccountService, tokens: AccessTokens) {
        if (service.status().familyExists) return
        val owner = session(tokens, service.setup(SetupRequest("Społeczność testowa", "Ola Owner", OWNER, PASSWORD, "seed")))
        for ((name, email) in listOf("Adam Admin" to ADMIN, "Ula User" to USER)) {
            val code = service.createInvitation(owner).code
            service.register(RegisterRequest(code, name, email, PASSWORD, "seed"))
        }
        val adminId = service.family(owner).members.single { it.name == "Adam Admin" }.id
        service.setRole(owner, adminId, Role.ADMIN)
        service.addChild(owner, ChildRequest("Bartek", "#8EC3F0"))
        service.addChild(owner, ChildRequest("Filip", "#9AD7A4"))
    }

    private fun session(tokens: AccessTokens, issued: pl.ambif.ambify.shared.Tokens): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString())
    }
}
