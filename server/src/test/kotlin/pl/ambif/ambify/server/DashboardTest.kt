package pl.ambif.ambify.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pl.ambif.ambify.server.accounts.AccessTokens
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.Dashboards
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.shared.WidgetSlot

class DashboardTest {
    private val tokens = testTokens()

    private fun AccountService.session(issued: Tokens, community: String? = null): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString(), community)
    }

    private fun service() = AccountService(Db.open(Files.createTempDirectory("ambify-dashboard").resolve("t.db").toString()), tokens)

    private fun widget(id: String, row: Int, slot: WidgetSlot = WidgetSlot.FULL, people: List<String> = emptyList()) =
        DashboardWidget(id, "lessons", row, slot, WidgetHeight.MEDIUM, people)

    @Test
    fun emptyAtFirstThenSavedPerPerson() {
        val service = service()
        val owner = service.session(service.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24")))
        val member = service.session(
            service.register(RegisterRequest(service.createInvitation(owner).code, "Bartosz", "b@pacak.waw.pl", "haslo1234", "X")),
        )
        assertEquals(Dashboard(), service.dashboard(owner))

        service.saveDashboard(owner, Dashboard(listOf(widget("a", 0, people = listOf("s1", "s2")))))

        assertEquals(listOf("s1", "s2"), service.dashboard(owner).widgets.single().people)
        assertTrue(service.dashboard(member).widgets.isEmpty(), "każdy ma własny Pulpit")
    }

    @Test
    fun weatherPlacesAreKeptOtherTextIsNot() {
        val service = service()
        val owner = service.session(service.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24")))
        val home = pl.ambif.ambify.shared.WeatherPlace("Dom", 52.0808, 21.0234, "mazowieckie").encode()
        service.saveDashboard(owner, Dashboard(listOf(widget("w", 0, people = listOf(home, "place|zle", "<script>")))))

        val kept = service.dashboard(owner).widgets.single().people
        assertEquals(listOf(home), kept)
        assertEquals("Dom", pl.ambif.ambify.shared.WeatherPlace.decode(kept.single())?.name)
    }

    @Test
    fun separateInEachCommunity() {
        val service = service()
        val issued = service.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24"))
        val first = service.session(issued)
        val second = service.createCommunity(first, "Klub").id
        service.saveDashboard(first, Dashboard(listOf(widget("a", 0))))

        assertTrue(service.dashboard(service.session(issued, second)).widgets.isEmpty())
        assertEquals(1, service.dashboard(service.session(issued, null)).widgets.size)
    }

    @Test
    fun rowsAreRenumberedAndHalvesKeepTheirSide() {
        val shown = Dashboards.normalized(
            Dashboard(
                listOf(
                    widget("full1", 3),
                    widget("right", 7, WidgetSlot.RIGHT),
                    widget("full2", 9),
                    widget("left", 12, WidgetSlot.LEFT),
                    widget("dup", 12, WidgetSlot.LEFT),
                ),
            ),
        ).widgets.associate { it.id to (it.row to it.slot) }

        assertEquals(0 to WidgetSlot.FULL, shown["full1"])
        assertEquals(1 to WidgetSlot.RIGHT, shown["right"])
        assertEquals(2 to WidgetSlot.FULL, shown["full2"])
        assertEquals(3 to WidgetSlot.LEFT, shown["left"])
        assertEquals(4 to WidgetSlot.LEFT, shown["dup"])
    }

    @Test
    fun badIdsAreDropped() {
        val service = service()
        val owner = service.session(service.setup(SetupRequest("Pacakowie", "Michał Pacak", "michal@pacak.waw.pl", "sekretne1", "S24")))
        service.saveDashboard(owner, Dashboard(listOf(widget("ok", 0), widget("zły id!", 1), widget("ok", 2))))
        assertEquals(listOf("ok"), service.dashboard(owner).widgets.map { it.id })
    }
}
