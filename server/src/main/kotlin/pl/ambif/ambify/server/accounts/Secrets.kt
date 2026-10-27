package pl.ambif.ambify.server.accounts

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Argon2id password hashes in the standard PHC string format ($argon2id$v=19$m=…,t=…,p=…$salt$hash). */
object Passwords {
    // OWASP minimum for Argon2id: 19 MiB memory, 2 iterations, 1 lane.
    private const val MEMORY_KB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = Secrets.randomBytes(SALT_BYTES)
        val hash = derive(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM, HASH_BYTES)
        return "\$argon2id\$v=19\$m=$MEMORY_KB,t=$ITERATIONS,p=$PARALLELISM" +
            "\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val expected = decoder.decode(parts[5])
        val actual = derive(
            password, decoder.decode(parts[4]),
            params.getValue("m"), params.getValue("t"), params.getValue("p"), expected.size,
        )
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, memoryKb: Int, iterations: Int, lanes: Int, length: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(lanes)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(length).also { generator.generateBytes(password.toByteArray(Charsets.UTF_8), it) }
    }
}

object Secrets {
    // No 0/O, 1/I/L: codes are read aloud and retyped by people.
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    fun code(length: Int): String = buildString { repeat(length) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }

    /** Refresh tokens: 256 random bits, URL-safe. */
    fun token(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32))

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun id(): String = UUID.randomUUID().toString()
}
