package pl.ambif.ambify.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pl.ambif.ambify.server.accounts.AccessTokens
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.lists.ListService
import pl.ambif.ambify.shared.AccessLevel
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListAccess
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListStyle
import pl.ambif.ambify.shared.ListReminder
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.LoginRequest
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.ProductUnit
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class ListsTest {
    private val tokens = testTokens()
    private var now = 1_800_000_000_000L
    private var changeNo = 0

    private fun AccountService.session(issued: Tokens): UserSession {
        val token = tokens.verifier.verify(issued.accessToken)
        return UserSession(token.subject, token.getClaim(AccessTokens.SESSION_CLAIM).asString())
    }

    private class World(val database: org.jetbrains.exposed.v1.jdbc.Database, val accounts: AccountService, val lists: ListService, val owner: UserSession, val user: UserSession, val third: UserSession)

    /** Owner Michał, Users Bartosz and Filip in one community. */
    private fun world(): World {
        val database = Db.open(Files.createTempDirectory("ambify-lists").resolve("l.db").toString())
        val accounts = AccountService(database, tokens)
        val lists = ListService(database, accounts) { now }
        val owner = accounts.session(accounts.setup(SetupRequest("Pacakowie", "Michał", "michal@example.com", "sekretne1", "S24")))
        fun join(name: String, email: String): UserSession {
            accounts.register(RegisterRequest(accounts.createInvitation(owner).code, name, email, "haslo1234", "X"))
            return accounts.session(accounts.login(LoginRequest(email, "haslo1234", "X")))
        }
        return World(database, accounts, lists, owner, join("Bartosz", "bartosz@example.com"), join("Filip", "filip@example.com"))
    }

    private fun change(kind: ListChangeKind, listId: String, itemId: String? = null, madeAt: Long = now, block: ListChange.() -> ListChange = { this }) =
        ListChange("c${changeNo++}", kind, listId, itemId, madeAt).block()

    private fun World.send(who: UserSession, vararg changes: ListChange, since: Long = 0): ListSyncResponse =
        lists.sync(who, ListSyncRequest(since, changes.toList()))

    private fun World.shoppingList(id: String = "L1", everyone: Boolean = true, members: List<String> = emptyList()) =
        send(owner, change(ListChangeKind.CREATE_LIST, id) { copy(list = ListInfo(id, ListType.SHOPPING, "Biedronka", everyone, members)) })

    private fun World.add(who: UserSession, itemId: String, values: ItemValues, listId: String = "L1", remember: Boolean = false) =
        send(who, change(ListChangeKind.ADD_ITEM, listId, itemId) { copy(values = values, rememberUnit = remember) }).results.single()

    private fun World.edit(who: UserSession, itemId: String, values: ItemValues, baseRev: Long, madeAt: Long, vararg fields: ItemField) =
        send(who, change(ListChangeKind.EDIT_ITEM, "L1", itemId, madeAt) { copy(values = values, fields = fields.toList(), baseRev = baseRev) }).results.single()

    @Test
    fun quickAddUnderstandsUnitsAndPolishForms() {
        assertEquals(ItemValues("Mleko", 2.0), Lists.quickAdd("mleko 2"))
        assertEquals(ItemValues("Ziemniaki", 2.0, "siatka"), Lists.quickAdd("ziemniaki 2 siatki"))
        assertEquals(ItemValues("Szynka", 20.0, "dag"), Lists.quickAdd("szynka 20 dag"))
        assertEquals(ItemValues("Bułki 4 duże"), Lists.quickAdd("bułki 4 duże"))
        assertEquals(ItemValues("Papier", 1.0, "zgrzewka", "8"), Lists.quickAdd("papier") { ProductUnit(it, "zgrzewka", "8") })
        assertEquals("2 siatki po 2,5 kg", Lists.quantityText(ItemValues("Ziemniaki", 2.0, "siatka", "2,5")))
        assertEquals("5 zgrzewek", Lists.quantityText(ItemValues("Woda", 5.0, "zgrzewka")))
        assertEquals("12 opakowań", Lists.quantityText(ItemValues("x", 12.0, "opak.")))
        assertEquals("22 siatki", Lists.quantityText(ItemValues("x", 22.0, "siatka")))
        assertEquals("0,5 kg", Lists.quantityText(ItemValues("x", 0.5, "kg")))
    }

    @Test
    fun sharedListIsSeenByEveryoneAndOnlyChangesAreSentAgain() {
        val w = world()
        w.shoppingList()
        assertTrue(w.add(w.user, "i1", ItemValues("Mleko", 2.0, "l")).ok)

        val first = w.send(w.third)
        assertEquals(listOf("L1"), first.listIds)
        assertEquals("Mleko", first.items.single().values.name)

        assertTrue(w.send(w.third, since = first.rev).items.isEmpty())
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = true) })
        val next = w.send(w.third, since = first.rev)
        assertTrue(next.items.single().checked)
        assertEquals(w.accounts.me(w.owner).id, next.items.single().checkedBy)
    }

    @Test
    fun privateListIsHiddenUntilThePersonIsAdded() {
        val w = world()
        val bartosz = w.accounts.me(w.user).id
        w.shoppingList(everyone = false, members = listOf(bartosz))
        w.add(w.user, "i1", ItemValues("Kawa"))

        val filip = w.send(w.third)
        assertTrue(filip.listIds.isEmpty() && filip.items.isEmpty())
        val refused = w.add(w.third, "i2", ItemValues("Cukier"))
        assertFalse(refused.ok)

        // Added to the list later: the whole list with its older items comes in the next sync.
        val filipId = w.accounts.me(w.third).id
        w.send(w.owner, change(ListChangeKind.UPDATE_LIST, "L1") { copy(list = ListInfo("L1", ListType.SHOPPING, "Biedronka", false, listOf(bartosz, filipId))) })
        val after = w.send(w.third, since = filip.rev)
        assertEquals(listOf("Kawa"), after.items.map { it.values.name })

        // Only the creator (or an Admin) may change the list itself.
        val rename = w.send(w.user, change(ListChangeKind.UPDATE_LIST, "L1") { copy(list = ListInfo("L1", ListType.SHOPPING, "Lidl")) }).results.single()
        assertFalse(rename.ok)
    }

    @Test
    fun aChangeSentTwiceIsDoneOnce() {
        val w = world()
        w.shoppingList()
        val add = change(ListChangeKind.ADD_ITEM, "L1", "i1") { copy(values = ItemValues("Chleb")) }
        assertTrue(w.send(w.user, add).results.single().ok)
        assertTrue(w.send(w.user, add).results.single().ok)
        assertEquals(1, w.send(w.owner).items.size)
    }

    @Test
    fun sameFieldChangedByTwoPeopleIsAConflictTheCreatorSettles() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Mleko", 1.0, "l"))
        val base = w.send(w.user).items.single().rev

        // Bartosz offline at 10:09 sets 3 l; Michał at 10:12 sets 2 l and is online first.
        assertTrue(w.edit(w.owner, "i1", ItemValues("Mleko", 2.0, "l"), base, madeAt = now + 12, ItemField.QUANTITY).ok)
        now += 100
        assertTrue(w.edit(w.user, "i1", ItemValues("Mleko", 3.0, "l"), base, madeAt = now - 100 + 9, ItemField.QUANTITY).ok)

        val state = w.send(w.owner)
        val conflict = state.conflicts.single()
        assertEquals(ItemField.QUANTITY, conflict.field)
        assertEquals(listOf(2.0, 3.0), conflict.versions.map { it.values.quantity })
        // Meanwhile the version made later (Michał's, 10:12) is shown.
        assertEquals(2.0, state.items.single().values.quantity)

        // Only the list's creator decides (before 48 h); "Wprowadź inne" with an empty field leaves no quantity.
        val refused = runCatching { w.lists.resolve(w.user, conflict.id, ConflictDecision(keep = 1)) }
        assertTrue(refused.isFailure)
        val settled = w.lists.resolve(w.owner, conflict.id, ConflictDecision(other = ItemValues()))
        assertTrue(settled.resolved)
        val item = w.send(w.user).items.single()
        assertNull(item.values.quantity)
        assertEquals("Mleko", item.values.name)
    }

    @Test
    fun checkingOffAndDifferentFieldsAreNeverConflicts() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Mleko", 1.0, "l"))
        val base = w.send(w.user).items.single().rev
        w.edit(w.owner, "i1", ItemValues("Mleko UHT", 1.0, "l"), base, now, ItemField.NAME)
        w.edit(w.user, "i1", ItemValues("Mleko", 2.0, "l"), base, now, ItemField.QUANTITY)
        w.send(w.user, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = true) })

        val state = w.send(w.owner)
        assertTrue(state.conflicts.isEmpty())
        assertEquals(ItemValues("Mleko UHT", 2.0, "l"), state.items.single().values)
        assertTrue(state.items.single().checked)
    }

    @Test
    fun conflictGoesToOwnerAfter48Hours() {
        val w = world()
        val bartosz = w.accounts.me(w.user).id
        // Bartosz's list; Michał and Filip change the same name.
        w.send(w.user, change(ListChangeKind.CREATE_LIST, "L1") { copy(list = ListInfo("L1", ListType.SHOPPING, "Lidl")) })
        w.add(w.user, "i1", ItemValues("Ser"))
        val base = w.send(w.owner).items.single().rev
        w.edit(w.owner, "i1", ItemValues("Ser żółty"), base, now, ItemField.NAME)
        w.edit(w.third, "i1", ItemValues("Ser biały"), base, now + 1, ItemField.NAME)
        val conflict = w.send(w.user).conflicts.single()
        assertEquals(bartosz, w.send(w.user).lists.single().createdBy)

        assertTrue(runCatching { w.lists.resolve(w.owner, conflict.id, ConflictDecision(keep = 0)) }.isFailure)
        now += Lists.CONFLICT_ESCALATION_MS
        assertEquals("Ser żółty", w.lists.resolve(w.owner, conflict.id, ConflictDecision(keep = 0)).let { w.send(w.user).items.single().values.name })
    }

    @Test
    fun taskForSomeoneElseWaitsForApproval() {
        val w = world()
        val filip = w.accounts.me(w.third).id
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "T1") { copy(list = ListInfo("T1", ListType.TASKS, "Dom")) })

        val queued = w.add(w.user, "t1", ItemValues("Umyć auto", assigneeId = filip), listId = "T1")
        assertTrue(queued.ok && queued.queued)
        assertTrue(w.send(w.owner).items.single().pending)
        val checkPending = w.send(w.third, change(ListChangeKind.CHECK_ITEM, "T1", "t1") { copy(checked = true) }).results.single()
        assertFalse(checkPending.ok)

        val request = w.accounts.requests(w.owner).single()
        assertEquals(Action.LISTS_TASKS_OTHERS, request.action)
        w.accounts.decide(w.owner, request.id, approve = true)
        assertFalse(w.send(w.owner).items.single().pending)

        // Refused: the waiting task goes away.
        w.add(w.user, "t2", ItemValues("Odkurzyć", assigneeId = filip), listId = "T1")
        w.accounts.decide(w.owner, w.accounts.requests(w.owner).single().id, approve = false)
        assertEquals(listOf("t1"), w.send(w.owner).items.filterNot { it.deleted }.map { it.id })

        // The Owner sets it "allowed": at once.
        val userRole = w.accounts.permissionTable(w.owner).roles.last().id
        w.accounts.setPermission(w.owner, userRole, Action.LISTS_TASKS_OTHERS, Mode.ALLOWED)
        assertFalse(w.add(w.user, "t3", ItemValues("Śmieci", assigneeId = filip), listId = "T1").queued)
    }

    @Test
    fun recurringDeadlineComesBackWithTheNextDate() {
        val w = world()
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "D1") { copy(list = ListInfo("D1", ListType.DEADLINES, "Opłaty")) })
        assertFalse(w.add(w.owner, "d0", ItemValues("Bez daty"), listId = "D1").ok)
        w.add(w.owner, "d1", ItemValues("Czynsz", dueDate = "2026-11-18", recurrence = Recurrence(Frequency.MONTHLY)), listId = "D1")
        w.send(w.user, change(ListChangeKind.CHECK_ITEM, "D1", "d1") { copy(checked = true) })

        val item = w.send(w.owner).items.single()
        assertFalse(item.checked)
        assertEquals("2026-12-18", item.values.dueDate)
        assertEquals(w.accounts.me(w.user).id, item.lastDoneBy)
    }

    @Test
    fun unitsAreRememberedForTheCommunityAndRemindersArePremium() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Ziemniaki", 2.0, "siatka", "2,5"), remember = true)
        w.add(w.owner, "i2", ItemValues("Pizza", 1.0, "karton"))
        val state = w.send(w.user)
        assertEquals(listOf(ProductUnit("ziemniaki", "siatka", "2,5", state.products.single().rev)), state.products)
        assertEquals(listOf("karton"), state.units.map { it.name })

        val me = w.accounts.me(w.owner).id
        val reminder = ItemValues("Mleko", reminders = listOf(ListReminder(me, ReminderKind.NOTIFICATION)))
        assertFalse(w.add(w.owner, "i3", reminder).ok)
        w.accounts.setGrant(w.owner, w.accounts.me(w.owner).communityId!!, "LISTS_REMINDERS", true)
        assertTrue(w.add(w.owner, "i4", reminder).ok)

        // "Zakończ zakupy": checked items leave the list for the history.
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = true) })
        w.send(w.owner, change(ListChangeKind.CLEAR_CHECKED, "L1"))
        assertEquals(listOf("i1"), w.send(w.owner).items.filter { it.cleared }.map { it.id })
    }

    // --- 0.7: after the first test ---

    @Test
    fun aDeadlineNeedsADateOrUrgent() {
        val w = world()
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "D") { copy(list = ListInfo("D", ListType.DEADLINES, "Opłaty")) })
        assertFalse(w.add(w.owner, "d1", ItemValues("Piec"), listId = "D").ok)
        assertTrue(w.add(w.owner, "d2", ItemValues("Zakręcić wodę", urgent = true, dueDate = "2027-01-01"), listId = "D").ok)
        val item = w.send(w.owner).items.single { it.id == "d2" }
        assertTrue(item.values.urgent)
        assertNull(item.values.dueDate, "pilne zastępuje datę")
    }

    @Test
    fun amountsAddUpAcrossUnits() {
        assertEquals(2.5, Lists.sum(ItemValues("Mleko", 2.0, "l"), ItemValues("Mleko", 500.0, "ml"))?.quantity)
        assertEquals(3.0, Lists.sum(ItemValues("Mleko", 2.0, "l"), ItemValues("Mleko", 1.0, "l"))?.quantity)
        assertEquals(1.3, Lists.sum(ItemValues("Ser", 1.0, "kg"), ItemValues("Ser", 30.0, "dag"))?.quantity)
        assertNull(Lists.sum(ItemValues("Mleko", 2.0, "l"), ItemValues("Mleko", 1.0, "zgrzewka")))
        assertTrue(Lists.sameThing(ItemValues("Mleko", variant = "3,2%"), ItemValues("mleko ", variant = "3,2 %")))
        assertFalse(Lists.sameThing(ItemValues("Mleko", variant = "3,2%"), ItemValues("Mleko", variant = "2%")))
    }

    @Test
    fun finishedShoppingStaysInTheHistoryAndCanComeBack() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Masło"))
        w.send(w.user, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = true) })
        now += 1000
        w.send(w.user, change(ListChangeKind.CLEAR_CHECKED, "L1"))
        val done = w.send(w.owner).items.single()
        assertTrue(done.cleared)
        assertEquals(now, done.clearedAt)
        assertEquals(w.user.userId, done.clearedBy)
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = false) })
        val back = w.send(w.owner).items.single()
        assertFalse(back.cleared)
        assertNull(back.clearedAt)
    }

    @Test
    fun packingIsUncheckedForTheNextTime() {
        val w = world()
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "P") { copy(list = ListInfo("P", ListType.SHOPPING, "Walizka", style = ListStyle.PACKING)) })
        w.add(w.owner, "p1", ItemValues("Szczoteczka"), listId = "P")
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "P", "p1") { copy(checked = true) })
        w.send(w.owner, change(ListChangeKind.UNCHECK_ALL, "P"))
        val response = w.send(w.user)
        assertFalse(response.items.single().checked)
        assertEquals(ListStyle.PACKING, response.lists.single().style)
    }

    @Test
    fun theCreatorDecidesWhoSeesAndWhatTheyMayDo() {
        val w = world()
        val roles = org.jetbrains.exposed.v1.jdbc.transactions.transaction(w.database) { w.accounts.membership(w.user).role }
        val access = listOf(ListAccess(roleId = roles.id, level = AccessLevel.SEE), ListAccess(userId = w.third.userId, level = AccessLevel.ADD))
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "A") { copy(list = ListInfo("A", ListType.TASKS, "Dom", everyone = false, access = access)) })
        w.add(w.owner, "a1", ItemValues("Odkurzyć"), listId = "A")
        // Bartosz (User → widzi) sees it, cannot add or check.
        assertTrue("A" in w.send(w.user).listIds)
        assertFalse(w.add(w.user, "a2", ItemValues("Okna"), listId = "A").ok)
        assertFalse(w.send(w.user, change(ListChangeKind.CHECK_ITEM, "A", "a1") { copy(checked = true) }).results.single().ok)
        // Filip (his own row → dodaje) adds and changes his own, not the Owner's.
        assertTrue(w.add(w.third, "a3", ItemValues("Śmieci"), listId = "A").ok)
        assertTrue(w.send(w.third, change(ListChangeKind.EDIT_ITEM, "A", "a3") { copy(values = ItemValues("Śmieci i szkło"), fields = listOf(ItemField.NAME), baseRev = 99) }).results.single().ok)
        assertFalse(w.send(w.third, change(ListChangeKind.EDIT_ITEM, "A", "a1") { copy(values = ItemValues("X"), fields = listOf(ItemField.NAME), baseRev = 99) }).results.single().ok)
        // The role off the list: Bartosz no longer sees it.
        w.send(w.owner, change(ListChangeKind.UPDATE_LIST, "A") { copy(list = ListInfo("A", ListType.TASKS, "Dom", everyone = false, access = listOf(ListAccess(roleId = roles.id, level = AccessLevel.NONE)))) })
        assertFalse("A" in w.send(w.user).listIds)
    }

    @Test
    fun aKindOfProductIsRemembered() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Mleko", 1.0, "l", variant = "3,2%"))
        val response = w.send(w.user)
        assertEquals("3,2%", response.items.single().values.variant)
        assertEquals(listOf("mleko" to "3,2%"), response.variants.map { it.product to it.variant })
    }

    @Test
    fun theMemoryGrowsWithEveryAddAndKeepsTheNewestParameters() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Mleko", 1.0, "l", variant = "3,2%"))
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "L1", "i1") { copy(checked = true) })
        w.add(w.user, "i2", ItemValues("mleko", 2.0, "l", variant = "3,2 %"))
        w.add(w.user, "i3", ItemValues("Mleko", 1.0, "l", variant = "2%"))
        val memory = w.send(w.third).memory.associateBy { it.key }
        assertEquals(setOf("mleko|3,2%", "mleko|2%"), memory.keys)
        assertEquals(2, memory.getValue("mleko|3,2%").uses)
        assertEquals(2.0, memory.getValue("mleko|3,2%").values.quantity)
        assertEquals(w.user.userId, memory.getValue("mleko|3,2%").lastBy)

        // A change of the item changes the remembered parameters, not the count.
        val rev = w.send(w.owner).items.first { it.id == "i3" }.rev
        w.edit(w.user, "i3", ItemValues("Mleko", 3.0, "l", variant = "2%"), rev, now, ItemField.QUANTITY)
        val after = w.send(w.owner).memory.single { it.key == "mleko|2%" }
        assertEquals(3.0, after.values.quantity)
        assertEquals(1, after.uses)
    }

    @Test
    fun packingIsRememberedApartFromShoppingAndDatesAreNot() {
        val w = world()
        w.shoppingList()
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "P") { copy(list = ListInfo("P", ListType.SHOPPING, "Walizka", style = ListStyle.PACKING)) })
        w.send(w.owner, change(ListChangeKind.CREATE_LIST, "D") { copy(list = ListInfo("D", ListType.DEADLINES, "Opłaty")) })
        w.add(w.owner, "i1", ItemValues("Ręcznik"))
        w.add(w.owner, "p1", ItemValues("Ręcznik", 2.0, "szt."), listId = "P")
        w.add(w.owner, "d1", ItemValues("Prąd", dueDate = "2027-01-10", recurrence = Recurrence(Frequency.MONTHLY)), listId = "D")
        val memory = w.send(w.user).memory
        assertEquals(setOf(pl.ambif.ambify.shared.MemoryScope.SHOPPING, pl.ambif.ambify.shared.MemoryScope.PACKING, pl.ambif.ambify.shared.MemoryScope.DEADLINES), memory.map { it.scope }.toSet())
        val deadline = memory.single { it.key == "prąd|" }
        assertNull(deadline.values.dueDate)
        assertEquals(Frequency.MONTHLY, deadline.values.recurrence.frequency)
    }

    @Test
    fun aForgottenProductGoesAwayForEveryoneUntilAddedAgain() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Kawa", variant = "ziarnista"))
        w.add(w.owner, "i2", ItemValues("Kawa", variant = "ziarnista"))
        val first = w.send(w.user)
        assertTrue(w.send(w.user, change(ListChangeKind.FORGET_PRODUCT, "L1") { copy(values = ItemValues("kawa", variant = "ziarnista")) }).results.single().ok)
        val forgotten = w.send(w.third, since = first.rev).memory.single()
        assertTrue(forgotten.deleted)
        // The items on the list stay.
        assertEquals(2, w.send(w.third).items.size)
        w.add(w.third, "i3", ItemValues("Kawa", variant = "ziarnista"))
        val back = w.send(w.owner).memory.single()
        assertFalse(back.deleted)
        assertEquals(1, back.uses)
    }

    @Test
    fun theMemoryIsBuiltFromTheListsOnceAfterTheUpdate() {
        val w = world()
        w.shoppingList()
        w.add(w.owner, "i1", ItemValues("Masło"))
        w.add(w.owner, "i2", ItemValues("Masło"))
        w.add(w.owner, "i3", ItemValues("Chleb"))
        w.send(w.owner, change(ListChangeKind.CHECK_ITEM, "L1", "i3") { copy(checked = true) })
        w.send(w.owner, change(ListChangeKind.CLEAR_CHECKED, "L1"))
        // As before 0.7.1: no memory at all.
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(w.database) {
            exec("DELETE FROM list_memory")
        }
        val memory = w.send(w.user).memory.associateBy { it.key }
        assertEquals(2, memory.getValue("masło|").uses)
        assertTrue("chleb|" in memory)
    }

    @Test
    fun searchingIgnoresPolishLetters() {
        assertEquals("ser zolty", Lists.searchKey("Ser Żółty"))
        assertEquals("kupione", ListInfo("x", ListType.SHOPPING, "x", style = ListStyle.SHOPPING.copy(doneLabel = "w koszyku")).look.doneLabel)
    }
}
