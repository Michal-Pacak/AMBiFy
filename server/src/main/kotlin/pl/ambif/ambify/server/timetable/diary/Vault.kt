package pl.ambif.ambify.server.timetable.diary

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import pl.ambif.ambify.server.accounts.Secrets

/** Encrypts the diary credentials (their private keys) in the database: AES-GCM with a key file beside the database. */
class Vault(key: ByteArray) {
    private val key = SecretKeySpec(key.copyOf(32), "AES")

    fun seal(plain: String): String {
        val iv = Secrets.randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun open(sealed: String): String {
        val bytes = Base64.getDecoder().decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }

    companion object {
        fun random() = Vault(Secrets.randomBytes(32))

        fun loadOrCreate(file: File): Vault {
            if (file.exists()) return Vault(file.readBytes())
            file.absoluteFile.parentFile?.mkdirs()
            val key = Secrets.randomBytes(32)
            Files.write(file.toPath(), key)
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
            return Vault(key)
        }
    }
}
