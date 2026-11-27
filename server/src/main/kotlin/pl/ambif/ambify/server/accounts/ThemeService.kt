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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.CommunityTheme
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.OwnTheme
import pl.ambif.ambify.shared.ThemeState
import pl.ambif.ambify.shared.Themes
import pl.ambif.ambify.shared.WallpaperPatterns

private val themeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val Hex = Regex("^#[0-9A-Fa-f]{6}$")
private val Effects = setOf("rings", "sparks", "laser", "rain", "hit")

/**
 * 0.7.7 themes (canvas "Motywy", see [Themes]): the account's theme and Premium konta, each member's pick, the
 * community's theme and its lock, own themes from the editor and their wallpaper photos ([root]/<id>.jpg).
 */
class ThemeService(private val database: Database, private val accounts: AccountService, val root: File) {

    fun state(session: UserSession): ThemeState = transaction(database) {
        val user = Users.selectAll().where { Users.id eq session.userId }.single()
        val rows = Memberships.selectAll().where { Memberships.userId eq session.userId }.orderBy(Memberships.joinedAt).toList()
        val organizationIds = rows.map { it[Memberships.organizationId] }
        val organizations = Organizations.selectAll().where { Organizations.id inList organizationIds }.associateBy { it[Organizations.id] }
        val communityOwn = OwnThemes.selectAll().where { OwnThemes.organizationId inList organizationIds }
            .groupBy({ it[OwnThemes.organizationId]!! }, { decode(it) })
        val setters = organizations.values.mapNotNull { it[Organizations.themeSetBy] }.distinct()
        val names = if (setters.isEmpty()) emptyMap() else Users.selectAll().where { Users.id inList setters }.associate { it[Users.id] to it[Users.name] }
        ThemeState(
            accountTheme = user[Users.theme],
            accountPremium = user[Users.accountPremium] || user[Users.platformAdmin],
            own = OwnThemes.selectAll().where { OwnThemes.userId eq session.userId }.orderBy(OwnThemes.createdAt).map { decode(it) },
            communities = rows.map { row ->
                val organizationId = row[Memberships.organizationId]
                val organization = organizations.getValue(organizationId)
                val membership = Membership(user, organizationId, accounts.role(row[Memberships.role]), accounts.rolesOf(organizationId))
                CommunityTheme(
                    communityId = organizationId,
                    theme = organization[Organizations.theme] ?: Themes.LUXURY,
                    locked = organization[Organizations.themeLocked],
                    premium = Themes.COMMUNITY_GRANT in accounts.grantsFor(organizationId, session.userId),
                    own = communityOwn[organizationId].orEmpty(),
                    myChoice = row[Memberships.theme],
                    canSet = accounts.can(membership, Action.THEME_SET) == Mode.ALLOWED,
                    canEdit = accounts.can(membership, Action.THEME_EDIT) == Mode.ALLOWED,
                    setBy = organization[Organizations.themeSetBy]?.let { names[it] },
                )
            },
        )
    }

    /** My theme everywhere in the app; null = as the community. */
    fun setAccount(session: UserSession, theme: String?): ThemeState {
        val state = state(session)
        if (theme != null && !Themes.allowedForMe(state, null, theme)) throw notAllowed()
        transaction(database) { Users.update({ Users.id eq session.userId }) { it[Users.theme] = theme } }
        return state(session)
    }

    /** My own pick in the current community; null = the community's theme. */
    fun setMember(session: UserSession, theme: String?): ThemeState {
        val state = state(session)
        val organizationId = transaction(database) { accounts.membership(session).organizationId }
        val community = state.communities.first { it.communityId == organizationId }
        if (theme != null && !Themes.allowedForMe(state, community, theme)) throw notAllowed()
        transaction(database) {
            Memberships.update({ (Memberships.userId eq session.userId) and (Memberships.organizationId eq organizationId) }) { it[Memberships.theme] = theme }
        }
        return state(session)
    }

    /** The current community's theme for everyone, and whether it is locked (board 2 C). */
    fun setCommunity(session: UserSession, theme: String, locked: Boolean): ThemeState {
        val state = state(session)
        transaction(database) {
            val manager = accounts.requireAction(session, Action.THEME_SET)
            val community = state.communities.first { it.communityId == manager.organizationId }
            if (!Themes.allowedForCommunity(community, theme)) throw notAllowed()
            Organizations.update({ Organizations.id eq manager.organizationId }) {
                it[Organizations.theme] = theme
                it[themeLocked] = locked
                it[themeSetBy] = session.userId
            }
        }
        return state(session)
    }

    /**
     * Saves an own theme (new or changed): mine with Premium konta, or the current community's ([community]) with
     * Premium społeczności and [Action.THEME_EDIT].
     */
    fun saveOwn(session: UserSession, theme: OwnTheme, community: Boolean): ThemeState {
        val clean = validated(theme)
        transaction(database) {
            val owner = owner(session, community)
            val existing = OwnThemes.selectAll().where { OwnThemes.id eq clean.id }.singleOrNull()
            if (existing != null) {
                if (existing[OwnThemes.userId] != owner.first || existing[OwnThemes.organizationId] != owner.second) throw notFoundError()
                val old = decode(existing)
                OwnThemes.update({ OwnThemes.id eq clean.id }) { it[spec] = themeJson.encodeToString(OwnTheme.serializer(), clean) }
                val oldPhoto = old.wallpaper?.photoId
                if (oldPhoto != null && oldPhoto != clean.wallpaper?.photoId) removeWallpaper(oldPhoto)
            } else {
                val count = OwnThemes.selectAll().where {
                    if (owner.first != null) OwnThemes.userId eq owner.first!! else OwnThemes.organizationId eq owner.second!!
                }.count()
                if (count >= Themes.MAX_OWN) throw ApiException(HttpStatusCode.Conflict, "too_many_themes", "Najwyżej ${Themes.MAX_OWN} własnych motywów.")
                OwnThemes.insert {
                    it[id] = clean.id
                    it[userId] = owner.first
                    it[organizationId] = owner.second
                    it[spec] = themeJson.encodeToString(OwnTheme.serializer(), clean)
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            clean.wallpaper?.photoId?.let { photo ->
                val row = Wallpapers.selectAll().where { Wallpapers.id eq photo }.singleOrNull() ?: throw notFoundError()
                if (row[Wallpapers.userId] != owner.first || row[Wallpapers.organizationId] != owner.second) throw notFoundError()
            }
        }
        return state(session)
    }

    fun deleteOwn(session: UserSession, id: String): ThemeState {
        transaction(database) {
            val row = OwnThemes.selectAll().where { OwnThemes.id eq id }.singleOrNull() ?: throw notFoundError()
            val community = row[OwnThemes.organizationId] != null
            val owner = owner(session, community, premiumNeeded = false)
            if (row[OwnThemes.userId] != owner.first || row[OwnThemes.organizationId] != owner.second) throw notFoundError()
            OwnThemes.deleteWhere { OwnThemes.id eq id }
            decode(row).wallpaper?.photoId?.let { removeWallpaper(it) }
        }
        return state(session)
    }

    /** A new wallpaper photo from [upload] (JPEG or PNG), kept as a JPEG no longer than [WallpaperPatterns.MAX_SIDE]. */
    fun addWallpaper(session: UserSession, upload: File, community: Boolean): String {
        try {
            val owner = transaction(database) { owner(session, community) }
            val image = runCatching { ImageIO.read(upload) }.getOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_photo", "To nie jest zdjęcie, które umiemy odczytać.")
            val id = UUID.randomUUID().toString()
            write(scaled(image, WallpaperPatterns.MAX_SIDE), fileOf(id))
            transaction(database) {
                Wallpapers.insert {
                    it[Wallpapers.id] = id
                    it[userId] = owner.first
                    it[organizationId] = owner.second
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            return id
        } finally {
            upload.delete()
        }
    }

    /** A wallpaper's file for its owner or a member of its community (an account's theme is seen only by its owner). */
    fun wallpaper(session: UserSession, id: String): File = transaction(database) {
        val row = Wallpapers.selectAll().where { Wallpapers.id eq id }.singleOrNull() ?: throw notFoundError()
        val mine = row[Wallpapers.userId] == session.userId
        val ours = row[Wallpapers.organizationId]?.let { organization ->
            Memberships.selectAll().where { (Memberships.userId eq session.userId) and (Memberships.organizationId eq organization) }.count() > 0
        } == true
        if (!mine && !ours) throw notFoundError()
        fileOf(id).takeIf { it.isFile } ?: throw notFoundError()
    }

    fun incoming(): File = root.resolve(".incoming").apply { mkdirs() }.resolve(UUID.randomUUID().toString())

    /** Platform Admin: Premium konta on or off for the account with this e-mail. */
    fun setAccountPremium(session: UserSession, email: String, premium: Boolean) {
        transaction(database) {
            val admin = Users.selectAll().where { Users.id eq session.userId }.single()
            if (!admin[Users.platformAdmin]) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Nie masz uprawnień do tej operacji.")
            val updated = Users.update({ Users.email eq email.trim().lowercase() }) { it[accountPremium] = premium }
            if (updated == 0) throw notFoundError()
        }
    }

    /** (user, community) owning an own theme or wallpaper; checks Premium and the permission. */
    private fun owner(session: UserSession, community: Boolean, premiumNeeded: Boolean = true): Pair<String?, String?> {
        if (community) {
            val manager = accounts.requireAction(session, Action.THEME_EDIT)
            if (premiumNeeded && Themes.COMMUNITY_GRANT !in accounts.grantsFor(manager.organizationId, session.userId)) throw premium()
            return null to manager.organizationId
        }
        if (premiumNeeded && Users.selectAll().where { Users.id eq session.userId }.single().let { !it[Users.accountPremium] && !it[Users.platformAdmin] }) throw premium()
        return session.userId to null
    }

    private fun validated(theme: OwnTheme): OwnTheme {
        val name = theme.name.trim().take(Themes.MAX_NAME)
        if (name.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "no_name", "Wpisz nazwę motywu.")
        if (theme.colors.isEmpty() || theme.colors.size > 3 || theme.colors.any { !Hex.matches(it) }) {
            throw ApiException(HttpStatusCode.BadRequest, "bad_colors", "Motyw ma od 1 do 3 kolorów.")
        }
        val id = theme.id.takeIf { it.length in 8..40 && it.all { c -> c.isLetterOrDigit() || c == '-' } } ?: UUID.randomUUID().toString()
        val wallpaper = theme.wallpaper?.let { w ->
            if (w.pattern != null && w.pattern !in WallpaperPatterns.ALL) throw ApiException(HttpStatusCode.BadRequest, "bad_pattern", "Nieznany wzór tapety.")
            w.copy(dim = w.dim.coerceIn(40, 95), focusX = w.focusX.coerceIn(0f, 1f), focusY = w.focusY.coerceIn(0f, 1f))
                .takeIf { it.pattern != null || it.photoId != null }
        }
        return theme.copy(
            id = id, name = name, bgHue = theme.bgHue.mod(360), bgSat = theme.bgSat.coerceIn(0, 100),
            effect = theme.effect.takeIf { it in Effects } ?: "rings", wallpaper = wallpaper,
        )
    }

    private fun decode(row: org.jetbrains.exposed.v1.core.ResultRow): OwnTheme =
        themeJson.decodeFromString(OwnTheme.serializer(), row[OwnThemes.spec]).copy(id = row[OwnThemes.id])

    private fun removeWallpaper(id: String) {
        Wallpapers.deleteWhere { Wallpapers.id eq id }
        fileOf(id).delete()
    }

    private fun fileOf(id: String): File {
        require(id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }) { "bad id" }
        return root.apply { mkdirs() }.resolve("$id.jpg")
    }

    private fun scaled(image: BufferedImage, maxSide: Int): BufferedImage {
        val k = minOf(1.0, maxSide.toDouble() / maxOf(image.width, image.height))
        val w = (image.width * k).toInt().coerceAtLeast(1)
        val h = (image.height * k).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, w, h, null)
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
                compressionQuality = 0.85f
            }
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun notAllowed() = ApiException(HttpStatusCode.Forbidden, "theme_not_allowed", "Ten motyw nie jest tu dostępny.")
    private fun premium() = ApiException(HttpStatusCode.PaymentRequired, "premium_needed", "To wymaga Premium.")
    private fun notFoundError() = ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono.")
}
