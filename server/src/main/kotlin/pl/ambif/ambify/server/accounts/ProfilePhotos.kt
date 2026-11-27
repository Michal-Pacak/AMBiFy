package pl.ambif.ambify.server.accounts

import io.ktor.http.HttpStatusCode
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.PhotoRules

/**
 * Profile photos (0.7.6, canvas "Zdjęcia profilowe"): [root]/<user>/<photo>.jpg and <photo>-s.jpg. The server
 * decodes and saves every photo again, so only a real picture is kept and nothing the camera wrote in the file.
 */
class ProfilePhotos(private val database: Database, private val accounts: AccountService, val root: File) {
    /** A new empty file for an upload being received. */
    fun incoming(): File = root.resolve(".incoming").apply { mkdirs() }.resolve(UUID.randomUUID().toString())

    /** My new photo from [upload] (a JPEG or PNG); the old one goes away. Returns its path. */
    fun set(session: UserSession, upload: File): String {
        try {
            val image = runCatching { ImageIO.read(upload) }.getOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_photo", "To nie jest zdjęcie, które umiemy odczytać.")
            val photoId = UUID.randomUUID().toString()
            val dir = userDir(session.userId).apply { mkdirs() }
            val square = square(image, PhotoRules.SIDE)
            write(square, dir.resolve("$photoId.jpg"))
            write(scaled(square, PhotoRules.SMALL_SIDE), dir.resolve("$photoId-s.jpg"))
            val old = transaction(database) {
                val old = Users.select(Users.photoId).where { Users.id eq session.userId }.single()[Users.photoId]
                Users.update({ Users.id eq session.userId }) { it[Users.photoId] = photoId }
                old
            }
            old?.let { remove(session.userId, it) }
            return AccountPaths.photo(session.userId, photoId)
        } finally {
            upload.delete()
        }
    }

    fun delete(session: UserSession) {
        val old = transaction(database) {
            val old = Users.select(Users.photoId).where { Users.id eq session.userId }.single()[Users.photoId]
            Users.update({ Users.id eq session.userId }) { it[Users.photoId] = null }
            old
        }
        old?.let { remove(session.userId, it) }
    }

    /**
     * The file of [userId]'s photo [photoId], for me or for someone who shares a community with them where the
     * photo is shown; anything else looks like a missing photo.
     */
    fun file(session: UserSession, userId: String, photoId: String, small: Boolean): File = transaction(database) {
        val current = Users.select(Users.photoId).where { Users.id eq userId }.singleOrNull()?.get(Users.photoId)
        if (current == null || current != photoId) throw notFound()
        if (userId != session.userId) {
            val mine = Memberships.select(Memberships.organizationId).where { Memberships.userId eq session.userId }.map { it[Memberships.organizationId] }
            val shown = Memberships.join(Organizations, org.jetbrains.exposed.v1.core.JoinType.INNER, Memberships.organizationId, Organizations.id)
                .selectAll()
                .where { (Memberships.userId eq userId) and (Memberships.organizationId inList mine) }
                .any { it[Memberships.photoHiddenBy] == null && !it[Organizations.photosBlocked] }
            if (!shown) throw notFound()
        }
        userDir(userId).resolve(if (small) "$photoId-s.jpg" else "$photoId.jpg").takeIf { it.isFile } ?: throw notFound()
    }

    /** Photos on or off in the current community (board 2 A). */
    fun setBlocked(session: UserSession, blocked: Boolean) {
        transaction(database) {
            val manager = accounts.requireAction(session, Action.PHOTOS_MODERATE)
            Organizations.update({ Organizations.id eq manager.organizationId }) { it[photosBlocked] = blocked }
        }
    }

    /** One person's photo hidden (or shown again) in the current community (board 1 C). */
    fun setHidden(session: UserSession, memberId: String, hidden: Boolean) {
        transaction(database) {
            val manager = accounts.requireAction(session, Action.PHOTOS_MODERATE)
            val where = { (Memberships.userId eq memberId) and (Memberships.organizationId eq manager.organizationId) }
            val member = Memberships.selectAll().where(where).singleOrNull() ?: throw notFound()
            // Not the photo of someone above me (an Admin cannot hide the Owner's).
            if (memberId != session.userId && accounts.role(member[Memberships.role]).rank > manager.role.rank) {
                throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Nie masz uprawnień do tej operacji.")
            }
            Memberships.update(where) {
                it[photoHiddenBy] = if (hidden) session.userId else null
                it[photoHiddenAt] = if (hidden) System.currentTimeMillis() else null
            }
        }
    }

    // --- the circle's own icon (0.7.8): [root]/circle-<id>/<photo>.jpg; only the Owner changes it ---

    /** The current circle's new photo from [upload]; the old one goes away. */
    fun setCircle(session: UserSession, upload: File) {
        try {
            val owner = transaction(database) { accounts.requireAction(session, Action.RENAME_COMMUNITY) }
            val image = runCatching { ImageIO.read(upload) }.getOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_photo", "To nie jest zdjęcie, które umiemy odczytać.")
            val photoId = UUID.randomUUID().toString()
            val dir = circleDir(owner.organizationId).apply { mkdirs() }
            val square = square(image, PhotoRules.SIDE)
            write(square, dir.resolve("$photoId.jpg"))
            write(scaled(square, PhotoRules.SMALL_SIDE), dir.resolve("$photoId-s.jpg"))
            val old = transaction(database) {
                val old = Organizations.select(Organizations.avatarPhotoId).where { Organizations.id eq owner.organizationId }.single()[Organizations.avatarPhotoId]
                Organizations.update({ Organizations.id eq owner.organizationId }) { it[avatarPhotoId] = photoId }
                old
            }
            old?.let { removeCircle(owner.organizationId, it) }
        } finally {
            upload.delete()
        }
    }

    /** Back to the initials. */
    fun deleteCircle(session: UserSession) {
        val (organizationId, old) = transaction(database) {
            val owner = accounts.requireAction(session, Action.RENAME_COMMUNITY)
            val old = Organizations.select(Organizations.avatarPhotoId).where { Organizations.id eq owner.organizationId }.single()[Organizations.avatarPhotoId]
            Organizations.update({ Organizations.id eq owner.organizationId }) { it[avatarPhotoId] = null }
            owner.organizationId to old
        }
        old?.let { removeCircle(organizationId, it) }
    }

    /** The initials' color: one of [Avatars.PALETTE]-like "#RRGGBB", or null = gold. */
    fun setCircleColor(session: UserSession, color: String?) {
        if (color != null && !Regex("#[0-9A-Fa-f]{6}").matches(color)) {
            throw ApiException(HttpStatusCode.BadRequest, "bad_color", "Nieznany kolor.")
        }
        transaction(database) {
            val owner = accounts.requireAction(session, Action.RENAME_COMMUNITY)
            Organizations.update({ Organizations.id eq owner.organizationId }) { it[avatarColor] = color?.uppercase() }
        }
    }

    /** A circle's photo, for its members only. */
    fun circleFile(session: UserSession, circleId: String, photoId: String, small: Boolean): File = transaction(database) {
        val member = Memberships.selectAll().where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq circleId) }.any()
        val current = Organizations.select(Organizations.avatarPhotoId).where { Organizations.id eq circleId }.singleOrNull()?.get(Organizations.avatarPhotoId)
        if (!member || current != photoId) throw notFound()
        circleDir(circleId).resolve(if (small) "$photoId-s.jpg" else "$photoId.jpg").takeIf { it.isFile } ?: throw notFound()
    }

    private fun circleDir(circleId: String): File {
        require(circleId.isNotBlank() && circleId.all { it.isLetterOrDigit() || it == '-' }) { "bad id" }
        return root.resolve("circle-$circleId")
    }

    private fun removeCircle(circleId: String, photoId: String) {
        circleDir(circleId).resolve("$photoId.jpg").delete()
        circleDir(circleId).resolve("$photoId-s.jpg").delete()
    }

    private fun userDir(userId: String): File {
        require(userId.isNotBlank() && userId.all { it.isLetterOrDigit() || it == '-' }) { "bad id" }
        return root.resolve(userId)
    }

    private fun remove(userId: String, photoId: String) {
        userDir(userId).resolve("$photoId.jpg").delete()
        userDir(userId).resolve("$photoId-s.jpg").delete()
    }

    private fun square(image: BufferedImage, side: Int): BufferedImage {
        val edge = minOf(image.width, image.height)
        val crop = image.getSubimage((image.width - edge) / 2, (image.height - edge) / 2, edge, edge)
        return scaled(crop, minOf(side, edge))
    }

    private fun scaled(image: BufferedImage, side: Int): BufferedImage {
        val out = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, side, side, null)
        g.dispose()
        return out
    }

    private fun write(image: BufferedImage, target: File) {
        val temp = File(target.parentFile, target.name + ".part")
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        ImageIO.createImageOutputStream(temp).use { output ->
            writer.output = output
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.88f
            }
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
