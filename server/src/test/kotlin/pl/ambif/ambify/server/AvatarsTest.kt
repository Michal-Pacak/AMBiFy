package pl.ambif.ambify.server

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.jetbrains.exposed.v1.jdbc.Database
import pl.ambif.ambify.server.accounts.AccessTokens
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.shared.AvatarShape
import pl.ambif.ambify.shared.Avatars
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.ProfileUpdate
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class AvatarsTest {
    private fun newPath() = Files.createTempDirectory("ambify-avatars").resolve("test.db").toString()

    private val tokens = testTokens()

    private fun AccountService.session(issued: Tokens): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString())
    }

    /** Owner "Michał Pacak" plus people who join with invitation codes, in this order. */
    private fun community(vararg names: String): Pair<AccountService, UserSession> {
        val service = AccountService(Db.open(newPath()), tokens)
        val owner = service.session(service.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24")))
        names.forEachIndexed { index, name ->
            service.register(RegisterRequest(service.createInvitation(owner).code, name, "osoba$index@pacak.waw.pl", "haslo1234", "X"))
        }
        return service to owner
    }

    private fun AccountService.shapes(owner: UserSession) = family(owner).members.associate { it.name to it.avatar!!.shape }

    @Test
    fun initialsFromNames() {
        assertEquals("MP", Avatars.initials("michał  Pacak"))
        assertEquals("BA", Avatars.initials("Bartek"))
        assertEquals("J", Avatars.initials("J"))
        assertEquals("AK", Avatars.initials("Anna Maria Kowalska"))
    }

    @Test
    fun sameInitialsGetDifferentShapesThatNeverChange() {
        val (service, owner) = community("Maria Pacak", "Ula Nowak", "Marek Pawlak")
        val before = service.shapes(owner)

        assertEquals(AvatarShape.CIRCLE, before["Michał Pacak"])
        assertEquals(AvatarShape.SQUARE, before["Maria Pacak"])
        assertEquals(AvatarShape.CIRCLE, before["Ula Nowak"])
        assertEquals(AvatarShape.TRIANGLE, before["Marek Pawlak"])

        service.register(RegisterRequest(service.createInvitation(owner).code, "Mateusz Pyrka", "m@pacak.waw.pl", "haslo1234", "X"))
        val after = service.shapes(owner)
        assertEquals(before, after - "Mateusz Pyrka")
        assertEquals(AvatarShape.STAR, after["Mateusz Pyrka"])
        // Members get the colors least used so far, so the first ones all differ.
        val colors = service.family(owner).members.map { it.avatar!!.color }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun minorsShareTheShapeOrderAndKeepTheirColor() {
        val (service, owner) = community()
        val minor = service.addChild(owner, ChildRequest("Mikołaj Pacak", "#9AD7A4"))

        assertEquals(AvatarShape.SQUARE, minor.avatar!!.shape)
        assertEquals("#9AD7A4", minor.avatar!!.color)
        assertEquals("MP", minor.avatar!!.initials)
        val renamed = service.updateChild(owner, minor.id, ChildRequest("Bartek", "#9AD7A4"))
        assertEquals(AvatarShape.CIRCLE, renamed.avatar!!.shape)
    }

    @Test
    fun newNameGivesANewIconOnlyToThatPerson() {
        val (service, owner) = community("Maria Pacak")
        val maria = service.session(service.login(pl.ambif.ambify.shared.LoginRequest("osoba0@pacak.waw.pl", "haslo1234", "X")))

        service.updateProfile(maria, ProfileUpdate("Ula Nowak"))
        val shapes = service.shapes(owner)
        assertEquals(AvatarShape.CIRCLE, shapes["Michał Pacak"])
        assertEquals(AvatarShape.CIRCLE, shapes["Ula Nowak"])
        assertEquals("UN", service.communities(maria.userId).single().avatar!!.initials)
    }

    @Test
    fun newEmailNeedsTheCurrentPassword() {
        val (service, owner) = community()

        val wrong = assertFailsWith<ApiException> { service.updateProfile(owner, ProfileUpdate("Michał Pacak", "nowy@pacak.waw.pl", "zle")) }
        val me = service.updateProfile(owner, ProfileUpdate("Michał Pacak", "nowy@pacak.waw.pl", "sekretne1"))

        assertEquals("wrong_password", wrong.code)
        assertEquals("nowy@pacak.waw.pl", me.email)
    }

    @Test
    fun ownerRenamesAndCannotLeaveOthersCanLeave() {
        val (service, owner) = community("Maria Pacak")
        val maria = service.session(service.login(pl.ambif.ambify.shared.LoginRequest("osoba0@pacak.waw.pl", "haslo1234", "X")))

        assertEquals("forbidden", assertFailsWith<ApiException> { service.renameCommunity(maria, "Inni") }.code)
        service.renameCommunity(owner, "Rodzina Pacaków")
        assertEquals("owner_cannot_leave", assertFailsWith<ApiException> { service.leave(owner) }.code)
        service.leave(maria)

        assertEquals("Rodzina Pacaków", service.family(owner).name)
        assertEquals(listOf("Michał Pacak"), service.family(owner).members.map { it.name })
        assertEquals(emptyList(), service.communities(maria.userId))
    }

    @Test
    fun iconsAreHandedOutToPeopleFromBeforeTheMigration() {
        val path = newPath()
        val database = Database.connect("jdbc:sqlite:$path?foreign_keys=on", "org.sqlite.JDBC")
        Migrations.apply(database, listOf("V001__init.sql", "V002__accounts.sql", "V003__communities.sql"))
        DriverManager.getConnection("jdbc:sqlite:$path").use { db ->
            db.createStatement().use {
                it.execute("INSERT INTO organizations (id, name, created_at) VALUES ('org', 'Pacakowie', 1)")
                it.execute("INSERT INTO users (id, name, email, password_hash, created_at) VALUES ('u1', 'Michał Pacak', 'a@p.pl', 'h', 1)")
                it.execute("INSERT INTO users (id, name, email, password_hash, created_at) VALUES ('u2', 'Maria Pacak', 'b@p.pl', 'h', 2)")
                it.execute("INSERT INTO memberships (user_id, organization_id, role, joined_at) VALUES ('u2', 'org', 'MEMBER', 20)")
                it.execute("INSERT INTO memberships (user_id, organization_id, role, joined_at) VALUES ('u1', 'org', 'OWNER', 10)")
            }
        }

        val service = AccountService(Db.open(path), tokens)
        val members = service.family(UserSession("u1", "s")).members

        assertEquals(listOf(Role.OWNER to AvatarShape.CIRCLE, Role.MEMBER to AvatarShape.SQUARE), members.map { it.role to it.avatar!!.shape })
        assertNotEquals(members[0].avatar!!.color, members[1].avatar!!.color)
    }
}
