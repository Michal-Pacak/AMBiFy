package pl.ambif.ambify.features.lists

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.features.lists.data.ListRepository
import pl.ambif.ambify.features.lists.data.SyncStatus
import pl.ambif.ambify.shared.ChangeResult
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.ListType

class ListRepositoryTest {
    private val biedronka = ListInfo("L1", ListType.SHOPPING, "Biedronka")

    /** A tiny server: keeps lists and items, answers every change with ok, or fails while [offline]. */
    private class FakeServer {
        var offline = false
        var rev = 0L
        val lists = mutableMapOf<String, ListInfo>()
        val items = mutableMapOf<String, ListItem>()
        val refuse = mutableSetOf<ListChangeKind>()
        val requests = mutableListOf<ListSyncRequest>()

        fun answer(request: ListSyncRequest): ListSyncResponse {
            if (offline) throw ServerError("network", "Brak połączenia z serwerem AMBiFy.")
            requests += request
            val results = request.changes.map { change ->
                if (change.kind in refuse) return@map ChangeResult(change.id, false, "Nie wolno.")
                rev++
                when (change.kind) {
                    ListChangeKind.CREATE_LIST -> lists[change.listId] = change.list!!.copy(id = change.listId, rev = rev)
                    ListChangeKind.ADD_ITEM -> items[change.itemId!!] = ListItem(change.itemId!!, change.listId, change.values!!, addedBy = "me", rev = rev)
                    ListChangeKind.CHECK_ITEM -> items[change.itemId!!] = items.getValue(change.itemId!!).copy(checked = change.checked, rev = rev)
                    else -> Unit
                }
                ChangeResult(change.id, true)
            }
            return ListSyncResponse(
                rev = rev,
                results = results,
                lists = lists.values.filter { it.rev > request.since },
                items = items.values.filter { it.rev > request.since },
                listIds = lists.keys.toList(),
            )
        }
    }

    private fun repo(server: FakeServer, dir: java.io.File = Files.createTempDirectory("lists").toFile()) =
        ListRepository(java.io.File(dir, "c.json"), { server.answer(it) })

    @Test
    fun changesMadeOfflineAreShownAtOnceAndSentLater() = runBlocking {
        val server = FakeServer().apply { offline = true }
        val dir = Files.createTempDirectory("lists").toFile()
        val repo = repo(server, dir)
        repo.change(ListChangeKind.CREATE_LIST, "L1") { copy(list = biedronka) }
        repo.change(ListChangeKind.ADD_ITEM, "L1", "i1") { copy(values = ItemValues("Mleko", 2.0, "l")) }
        repo.sync()
        assertEquals(SyncStatus.Offline(2), repo.status.value)
        assertEquals(listOf("Mleko"), repo.state.value.view.itemsOf("L1").map { it.values.name })

        // The app was closed meanwhile: the copy and the queue come back from the file.
        val again = repo(server, dir)
        assertEquals(2, again.state.value.pending.size)
        server.offline = false
        again.sync()
        assertEquals(SyncStatus.Synced, again.status.value)
        assertTrue(again.state.value.pending.isEmpty())
        assertEquals("me", again.state.value.view.items.single().addedBy)
    }

    @Test
    fun onlyNewerDataIsAskedForAndRefusedChangesAreDropped() = runBlocking {
        val server = FakeServer()
        val repo = repo(server)
        repo.change(ListChangeKind.CREATE_LIST, "L1") { copy(list = biedronka) }
        repo.sync()
        server.refuse += ListChangeKind.DELETE_LIST
        repo.change(ListChangeKind.DELETE_LIST, "L1")
        assertTrue(repo.state.value.view.lists.isEmpty())
        repo.sync()
        // Refused: the list is back, the change is gone, and it asked only for what is newer than rev 1.
        assertEquals(listOf("L1"), repo.state.value.view.lists.map { it.id })
        assertTrue(repo.state.value.pending.isEmpty())
        assertEquals(1L, server.requests.last().since)
    }

    @Test
    fun anEditShowsOnlyTheChangedFields() = runBlocking {
        val server = FakeServer()
        val repo = repo(server)
        repo.change(ListChangeKind.CREATE_LIST, "L1") { copy(list = biedronka) }
        repo.change(ListChangeKind.ADD_ITEM, "L1", "i1") { copy(values = ItemValues("Mleko", 1.0, "l")) }
        repo.sync()
        server.offline = true
        repo.change(ListChangeKind.EDIT_ITEM, "L1", "i1") { copy(values = ItemValues("x", 3.0, "l"), fields = listOf(ItemField.QUANTITY)) }
        assertEquals(ItemValues("Mleko", 3.0, "l"), repo.state.value.view.items.single().values)
    }
}
