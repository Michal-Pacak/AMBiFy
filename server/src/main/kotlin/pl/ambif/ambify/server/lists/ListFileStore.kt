package pl.ambif.ambify.server.lists

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The bytes of list photos and files (0.7.3): [root]/<community>/<file id>. Uploads land in [root]/.incoming first,
 * so a broken upload never looks like a file.
 */
class ListFileStore(val root: File) {
    /** A new empty file for an upload being received. */
    fun incoming(): File {
        val dir = root.resolve(".incoming").apply { mkdirs() }
        return dir.resolve(UUID.randomUUID().toString())
    }

    fun path(organizationId: String, fileId: String): File = root.resolve(safe(organizationId)).resolve(safe(fileId))

    fun keep(organizationId: String, fileId: String, upload: File) {
        val target = path(organizationId, fileId)
        target.parentFile.mkdirs()
        Files.move(upload.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete(organizationId: String, fileId: String) {
        path(organizationId, fileId).delete()
    }

    /** Ids come from the database or the phone; never a path. */
    private fun safe(id: String): String {
        require(id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }) { "bad id" }
        return id
    }
}
