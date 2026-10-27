package pl.ambif.ambify.server.accounts

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Date

/** Who is calling: set by the JWT check on every authenticated request. */
data class UserSession(val userId: String, val sessionId: String)

/**
 * Short-lived JWT access tokens. Each one names its device session, and the server checks that
 * session on every request, so signing a device out takes effect immediately.
 */
class AccessTokens(secret: ByteArray, private val clock: () -> Long = System::currentTimeMillis) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    fun issue(userId: String, sessionId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withSubject(userId)
        .withClaim(SESSION_CLAIM, sessionId)
        .withExpiresAt(Date(clock() + LIFETIME_MS))
        .sign(algorithm)

    companion object {
        const val ISSUER = "ambify"
        const val SESSION_CLAIM = "sid"
        private const val LIFETIME_MS = 60 * 60 * 1000L

        /** The signing key lives next to the database; created on first start, readable only by the server. */
        fun loadOrCreateKey(file: File): ByteArray {
            if (file.exists()) return file.readBytes()
            file.absoluteFile.parentFile?.mkdirs()
            val key = Secrets.randomBytes(64)
            Files.write(file.toPath(), key)
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
            return key
        }
    }
}
