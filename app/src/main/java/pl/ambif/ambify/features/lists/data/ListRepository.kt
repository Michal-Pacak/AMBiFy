package pl.ambif.ambify.features.lists.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.ListSyncResponse

/** How the copy stands against the server, for the line under the title ("zsynchronizowano", "bez połączenia"). */
sealed interface SyncStatus {
    data object Synced : SyncStatus
    data object Syncing : SyncStatus
    /** The server cannot be reached; [waiting] changes go out when it can. */
    data class Offline(val waiting: Int) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** Something to tell the person after a sync: a refused change, a task waiting for approval, a conflict. */
data class ListsEvent(val kind: Kind, val title: String, val text: String = "", val listId: String? = null, val conflictId: String? = null) {
    enum class Kind { REFUSED, QUEUED, CONFLICT, RESOLVED }
}

/** Who is in the community and which premium features it has: kept with the copy, so the screens work offline. */
@Serializable
data class ListPeople(
    val members: List<Member> = emptyList(),
    val features: List<Feature> = emptyList(),
    val me: String? = null,
    /** The community's roles, highest first (for "Dostęp do listy"). */
    val roles: List<pl.ambif.ambify.shared.Role> = emptyList(),
)

/**
 * The lists of one community on this phone (offline first): kept in a file, changed at once on the phone and sent to
 * the server in the background. Every change gets its own id, so sending it again after a lost answer is harmless.
 */
class ListRepository(
    private val file: File,
    private val send: suspend (ListSyncRequest) -> ListSyncResponse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ListsState> = _state.asStateFlow()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Synced)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    private val _events = MutableSharedFlow<ListsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ListsEvent> = _events.asSharedFlow()
    private val peopleFile = File(file.parentFile, file.nameWithoutExtension + PEOPLE)
    private val _people = MutableStateFlow(loadPeople())
    val people: StateFlow<ListPeople> = _people.asStateFlow()

    val hasPending: Boolean get() = _state.value.pending.isNotEmpty()

    /** Members and features from the server; offline the saved ones stay. */
    suspend fun refreshPeople(load: suspend () -> ListPeople) {
        val fresh = try { load() } catch (_: ServerError) { return }
        _people.value = fresh
        withContext(Dispatchers.IO) { runCatching { peopleFile.parentFile?.mkdirs(); peopleFile.writeText(json.encodeToString(fresh)) } }
    }

    private fun loadPeople(): ListPeople =
        peopleFile.takeIf { it.exists() }?.let { runCatching { json.decodeFromString<ListPeople>(it.readText()) }.getOrNull() } ?: ListPeople()

    /** Shown at once and queued; [sync] sends it. */
    suspend fun change(kind: ListChangeKind, listId: String, itemId: String? = null, block: ListChange.() -> ListChange = { this }) {
        val change = ListChange(UUID.randomUUID().toString(), kind, listId, itemId, clock()).block()
        lock.withLock { save(_state.value.withChange(change)) }
    }

    /**
     * Sends the waiting changes (in parts) and takes what is new on the server. Never throws. Returns what to tell
     * the person (also sent to [events], for the open screen).
     */
    suspend fun sync(): List<ListsEvent> {
        val told = mutableListOf<ListsEvent>()
        lock.withLock {
            _status.value = SyncStatus.Syncing
            try {
                do {
                    val current = _state.value
                    val sent = current.pending.take(BATCH)
                    val response = send(ListSyncRequest(current.base.since, sent))
                    val merge = current.merged(sent, response, clock())
                    save(merge.state)
                    val base = merge.state.base
                    fun itemName(id: String?) = base.items.firstOrNull { it.id == id }?.values?.name
                        ?: current.view.items.firstOrNull { it.id == id }?.values?.name
                    merge.refused.forEach { result ->
                        val change = sent.firstOrNull { it.id == result.id }
                        told += ListsEvent(ListsEvent.Kind.REFUSED, "Serwer nie przyjął zmiany", result.message ?: "Zmiana została cofnięta.", change?.listId)
                    }
                    merge.queued.forEach { result ->
                        val change = sent.firstOrNull { it.id == result.id }
                        told += ListsEvent(ListsEvent.Kind.QUEUED, "Czeka na zatwierdzenie", result.message ?: "", change?.listId)
                    }
                    merge.newConflicts.forEach { conflict ->
                        told += ListsEvent(
                            ListsEvent.Kind.CONFLICT, "Konflikt na liście",
                            "${itemName(conflict.itemId) ?: "Pozycja"} — ${conflict.field.label}. Do decyzji widać wersję zapisaną później.",
                            conflict.listId, conflict.id,
                        )
                    }
                    merge.resolved.forEach { conflict ->
                        told += ListsEvent(ListsEvent.Kind.RESOLVED, "Rozstrzygnięto konflikt", conflict.resolution ?: "", conflict.listId, conflict.id)
                    }
                } while (merge.state.pending.isNotEmpty() && sent.isNotEmpty() && merge.state.pending.size < current.pending.size)
                _status.value = SyncStatus.Synced
            } catch (e: ServerError) {
                _status.value = if (e.code == "network") SyncStatus.Offline(_state.value.pending.size) else SyncStatus.Failed(e.message)
            }
        }
        told.forEach { _events.tryEmit(it) }
        return told
    }

    /** Settling a conflict needs the server (the decision is final); then the lists are synced. */
    suspend fun resolve(api: ServerApi, conflictId: String, decision: ConflictDecision) {
        api.resolveConflict(conflictId, decision)
        sync()
    }

    private fun load(): ListsState =
        file.takeIf { it.exists() }?.let { runCatching { json.decodeFromString<ListsState>(it.readText()) }.getOrNull() } ?: ListsState()

    private suspend fun save(state: ListsState) {
        _state.value = state
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(state))
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val BATCH = 100
        private const val DIR = "lists"
        private const val PEOPLE = ".people.json"
        private val open = mutableMapOf<String, ListRepository>()

        /** One per community for the whole app process. */
        @Synchronized
        fun of(context: Context, api: ServerApi, communityId: String): ListRepository = open.getOrPut(communityId) {
            val scoped = api.forCommunity(communityId)
            ListRepository(File(File(context.filesDir, DIR), "$communityId.json"), { scoped.listSync(it) })
        }

        /** The communities with a copy on this phone (for the background sync). */
        fun communities(context: Context): List<String> =
            File(context.filesDir, DIR).listFiles()?.map { it.name }
                ?.filter { it.endsWith(".json") && !it.endsWith(PEOPLE) }?.map { it.removeSuffix(".json") }.orEmpty()

        /** Signing out: the copies and the waiting changes go away. */
        @Synchronized
        fun clear(context: Context) {
            open.clear()
            ListsSyncWorker.cancel(context)
            File(context.filesDir, DIR).deleteRecursively()
        }
    }
}
