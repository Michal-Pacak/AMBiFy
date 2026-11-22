package pl.ambif.ambify.server.lists

import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import pl.ambif.ambify.server.accounts.AccountService
import pl.ambif.ambify.server.accounts.ApiException
import pl.ambif.ambify.server.accounts.Membership
import pl.ambif.ambify.server.accounts.Secrets
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.server.accounts.Users
import pl.ambif.ambify.shared.AccessLevel
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ChangeResult
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ConflictVersion
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ItemFile
import pl.ambif.ambify.shared.ItemLink
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListConflict
import pl.ambif.ambify.shared.ListAccess
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListMemory
import pl.ambif.ambify.shared.ListSyncRequest
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.ListStyle
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.MemoryScope
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.ProductUnit
import pl.ambif.ambify.shared.ProductVariant
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.Recurrences
import org.slf4j.LoggerFactory

/** Who changed a field of an item last, in which rev, when they did it on the phone and when the server got it. */
@Serializable
internal data class FieldRev(val rev: Long, val by: String, val madeAt: Long, val at: Long)

/** A task for someone else waiting for approval: [assigneeId] set on an existing item, or the new item made visible. */
@Serializable
private data class QueuedTask(val itemId: String, val assigneeId: String? = null)

/**
 * Lists: shopping, tasks and deadlines of a community. Phones send their changes (also after being offline) and get
 * everything newer than the rev they saw ([sync]). Two people changing the same field of an item at the same time is
 * a conflict settled by the list's creator ([resolve]); checking off and changes of different fields never are.
 */
class ListService(
    private val database: Database,
    private val accounts: AccountService,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 0.7.3: photos and files of items; in lists-files/ next to the database (copied to the NAS by ambify-backup). */
    private val files: ListFileStore = ListFileStore(java.io.File("lists-files")),
) {
    private val log = LoggerFactory.getLogger(ListService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        accounts.approvalHandlers[Action.LISTS_TASKS_OTHERS] = { _, requesterId, payload ->
            val task = json.decodeFromString<QueuedTask>(payload)
            val item = ListItems.selectAll().where { ListItems.id eq task.itemId }.singleOrNull()
            if (item != null && !item[ListItems.deleted]) {
                val rev = nextRev(item[ListItems.organizationId])
                ListItems.update({ ListItems.id eq task.itemId }) {
                    it[pending] = false
                    it[ListItems.rev] = rev
                    if (task.assigneeId != null) {
                        it[values] = json.encodeToString(values(item).copy(assigneeId = task.assigneeId))
                        it[fieldRevs] = json.encodeToString(fieldRevs(item) + (ItemField.ASSIGNEE.name to FieldRev(rev, requesterId, clock(), clock())))
                        it[changedBy] = requesterId
                        it[changedAt] = clock()
                    }
                }
            }
        }
        accounts.rejectionHandlers[Action.LISTS_TASKS_OTHERS] = { _, _, payload ->
            val task = json.decodeFromString<QueuedTask>(payload)
            val item = ListItems.selectAll().where { ListItems.id eq task.itemId }.singleOrNull()
            // A new task nobody approved goes away; a refused change of the person just leaves the task as it was.
            if (item != null && item[ListItems.pending]) {
                val rev = nextRev(item[ListItems.organizationId])
                ListItems.update({ ListItems.id eq task.itemId }) { it[deleted] = true; it[ListItems.rev] = rev }
            }
        }
    }

    // --- synchronization ---

    fun sync(session: UserSession, request: ListSyncRequest): ListSyncResponse {
        accounts.requireModule(session, ModuleId.LISTS)
        val results = request.changes.take(MAX_CHANGES).map { apply(session, it) }
        transaction(database) { buildMemory(accounts.membership(session).organizationId) }
        return transaction(database) {
            val membership = accounts.membership(session)
            val organizationId = membership.organizationId
            val rev = currentRev(organizationId)
            val roleMayEdit = accounts.can(membership, Action.LISTS_EDIT_ITEMS) == Mode.ALLOWED
            // A phone that knows a newer rev than the server (the database was restored) gets everything again.
            val since = if (request.since > rev) 0 else request.since
            val visible = ListsTable.selectAll().where { (ListsTable.organizationId eq organizationId) and (ListsTable.deleted eq false) }
                .filter { level(membership, it).atLeast(AccessLevel.SEE) }
            val visibleIds = visible.map { it[ListsTable.id] }.toSet()
            val lists = ListsTable.selectAll().where { (ListsTable.organizationId eq organizationId) and (ListsTable.rev greater since) }
                .filter { it[ListsTable.deleted] || it[ListsTable.id] in visibleIds }
                .map { toInfo(it) }
            // A list changed since (e.g. the person was just added to it) comes with all its items and conflicts.
            val whole = lists.filterNot { it.deleted }.map { it.id }.toSet()
            val infos = visible.associate { it[ListsTable.id] to toInfo(it) }
            val filesByItem = ListFilesTable.selectAll()
                .where { (ListFilesTable.organizationId eq organizationId) and (ListFilesTable.deleted eq false) }
                .orderBy(ListFilesTable.addedAt)
                .groupBy({ it[ListFilesTable.itemId] }, { toFile(it) })
            val all = ListItems.selectAll().where { ListItems.organizationId eq organizationId }
                .filter { it[ListItems.listId] in visibleIds }
                .map { toItem(it).copy(files = filesByItem[it[ListItems.id]].orEmpty()) }
            val byId = all.associateBy { it.id }
            // An item with "Inny dostęp" (and its sub-items) the person may not see comes only as deleted, so the
            // phone forgets it.
            val items = all.filter { it.rev > since || it.listId in whole }.map { item ->
                val top = item.parentId?.let { byId[it] } ?: item
                val seen = top.access.isEmpty() || Lists.itemLevel(
                    infos.getValue(item.listId), top, membership.user[Users.id], membership.role.id, membership.role.owner, roleMayEdit,
                ).atLeast(AccessLevel.SEE)
                if (seen) item else ListItem(item.id, item.listId, ItemValues(), deleted = true, rev = item.rev)
            }
            val conflicts = ListConflicts.selectAll().where { ListConflicts.organizationId eq organizationId }
                .filter { it[ListConflicts.listId] in visibleIds && (it[ListConflicts.rev] > since || it[ListConflicts.listId] in whole) }
                .map { toConflict(it) }
            ListSyncResponse(
                rev = rev,
                results = results,
                lists = lists,
                items = items,
                conflicts = conflicts,
                units = ListUnits.selectAll().where { (ListUnits.organizationId eq organizationId) and (ListUnits.rev greater since) }
                    .map { ListUnit(it[ListUnits.name], json.decodeFromString(it[ListUnits.forms]), it[ListUnits.step], it[ListUnits.packUnit], it[ListUnits.rev]) },
                products = ListProducts.selectAll().where { (ListProducts.organizationId eq organizationId) and (ListProducts.rev greater since) }
                    .map { ProductUnit(it[ListProducts.product], it[ListProducts.unit], it[ListProducts.packSize], it[ListProducts.rev]) },
                listIds = visibleIds.toList(),
                variants = ListVariants.selectAll().where { (ListVariants.organizationId eq organizationId) and (ListVariants.rev greater since) }
                    .map { ProductVariant(it[ListVariants.product], it[ListVariants.variant], it[ListVariants.rev]) },
                memory = ListMemoryTable.selectAll().where { (ListMemoryTable.organizationId eq organizationId) and (ListMemoryTable.rev greater since) }
                    .map { toMemory(it) },
            )
        }
    }

    /** One change in its own transaction; a change already done (sent again) gets its first answer back. */
    private fun apply(session: UserSession, change: ListChange): ChangeResult {
        transaction(database) {
            ListChangesDone.selectAll().where { (ListChangesDone.id eq change.id) and (ListChangesDone.userId eq session.userId) }.singleOrNull()
        }?.let { return ChangeResult(change.id, it[ListChangesDone.ok], it[ListChangesDone.message], it[ListChangesDone.queued]) }
        val result = try {
            transaction(database) { applyNow(accounts.membership(session), change) }
        } catch (e: ApiException) {
            ChangeResult(change.id, ok = false, message = e.message)
        } catch (e: Exception) {
            log.error("List change ${change.kind} failed", e)
            ChangeResult(change.id, ok = false, message = "Nie udało się zapisać tej zmiany.")
        }
        transaction(database) {
            ListChangesDone.insert {
                it[id] = change.id
                it[userId] = session.userId
                it[ok] = result.ok
                it[message] = result.message
                it[queued] = result.queued
                it[createdAt] = clock()
            }
        }
        return result
    }

    private fun applyNow(membership: Membership, change: ListChange): ChangeResult {
        val me = membership.user[Users.id]
        val organizationId = membership.organizationId
        when (change.kind) {
            ListChangeKind.CREATE_LIST -> {
                require(membership, Action.LISTS_CREATE)
                val list = change.list ?: throw invalid()
                if (ListsTable.selectAll().where { ListsTable.id eq change.listId }.any()) throw invalid()
                val rev = nextRev(organizationId)
                ListsTable.insert {
                    it[id] = change.listId
                    it[ListsTable.organizationId] = organizationId
                    it[type] = list.type.name
                    it[name] = listName(list.name)
                    it[everyone] = list.everyone && list.access.isEmpty()
                    it[style] = styleJson(list)
                    it[access] = json.encodeToString(validAccess(membership, list.access))
                    it[createdBy] = me
                    it[createdAt] = clock()
                    it[archived] = false
                    it[deleted] = false
                    it[ListsTable.rev] = rev
                }
                setMembers(change.listId, organizationId, me, list)
            }
            ListChangeKind.UPDATE_LIST -> {
                val row = manageable(membership, change.listId)
                val list = change.list ?: throw invalid()
                val rev = nextRev(organizationId)
                ListsTable.update({ ListsTable.id eq change.listId }) {
                    it[name] = listName(list.name)
                    it[everyone] = list.everyone && list.access.isEmpty()
                    it[archived] = list.archived
                    it[style] = styleJson(list.copy(type = ListType.valueOf(row[ListsTable.type])))
                    it[access] = json.encodeToString(validAccess(membership, list.access))
                    it[ListsTable.rev] = rev
                }
                setMembers(change.listId, organizationId, row[ListsTable.createdBy], list)
            }
            ListChangeKind.DELETE_LIST -> {
                manageable(membership, change.listId)
                val rev = nextRev(organizationId)
                ListsTable.update({ ListsTable.id eq change.listId }) { it[deleted] = true; it[ListsTable.rev] = rev }
                dropFiles(ListFilesTable.listId eq change.listId)
            }
            ListChangeKind.ADD_ITEM -> {
                val list = editable(membership, change.listId, AccessLevel.ADD)
                val type = ListType.valueOf(list[ListsTable.type])
                val itemId = change.itemId ?: throw invalid()
                if (ListItems.selectAll().where { ListItems.id eq itemId }.any()) throw invalid()
                val values = validValues(membership, type, change.values ?: throw invalid())
                if (values.links.isNotEmpty()) requireFeature(organizationId, Feature.LISTS_ATTACHMENTS, "Linki są w pakiecie premium.")
                val parentId = change.parentId
                if (parentId != null) {
                    requireFeature(organizationId, Feature.LISTS_SUBITEMS, "Podlisty są w pakiecie premium.")
                    val parent = ListItems.selectAll().where { (ListItems.id eq parentId) and (ListItems.listId eq change.listId) }
                        .singleOrNull() ?: throw notFound()
                    if (parent[ListItems.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już tę pozycję.")
                    if (parent[ListItems.parentId] != null) {
                        throw ApiException(HttpStatusCode.BadRequest, "nested", "Podlista nie może mieć własnej podlisty.")
                    }
                    requireItemLevel(membership, list, parent, AccessLevel.ADD)
                }
                val forOther = values.assigneeId != null && values.assigneeId != me
                val mode = if (forOther) accounts.can(membership, Action.LISTS_TASKS_OTHERS) else Mode.ALLOWED
                if (mode == Mode.DENIED) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Twoja rola nie może dawać zadań innym osobom.")
                val rev = nextRev(organizationId)
                val now = clock()
                ListItems.insert {
                    it[id] = itemId
                    it[listId] = change.listId
                    it[ListItems.organizationId] = organizationId
                    it[ListItems.values] = json.encodeToString(values)
                    it[fieldRevs] = json.encodeToString(ItemField.entries.associate { f -> f.name to FieldRev(rev, me, change.madeAt, now) })
                    it[checked] = false
                    it[addedBy] = me
                    it[addedAt] = change.madeAt
                    it[cleared] = false
                    it[pending] = mode == Mode.APPROVAL
                    it[deleted] = false
                    it[ListItems.rev] = rev
                    it[ListItems.parentId] = parentId
                }
                if (type == ListType.SHOPPING) {
                    rememberUnit(organizationId, values, change.rememberUnit, rev)
                    rememberVariant(organizationId, values, rev)
                }
                remember(organizationId, toInfo(list), values, me, rev, added = true, at = change.madeAt)
                if (mode == Mode.APPROVAL) {
                    accounts.queueRequest(membership, Action.LISTS_TASKS_OTHERS, json.encodeToString(QueuedTask(itemId)), taskSummary(values, list))
                    return ChangeResult(change.id, ok = true, message = WAITS_FOR_APPROVAL, queued = true)
                }
            }
            ListChangeKind.EDIT_ITEM -> return edit(membership, change)
            ListChangeKind.CHECK_ITEM -> {
                val (list, item) = editableItem(membership, change, need = AccessLevel.CHECK)
                if (item[ListItems.pending]) throw ApiException(HttpStatusCode.Conflict, "pending", "To zadanie czeka jeszcze na zatwierdzenie.")
                val values = values(item)
                val rev = nextRev(organizationId)
                val recurring = ListType.valueOf(list[ListsTable.type]) != ListType.SHOPPING &&
                    values.recurrence.frequency != Frequency.NONE && values.dueDate != null
                val next = if (change.checked && recurring) nextDate(values) else null
                ListItems.update({ ListItems.id eq item[ListItems.id] }) {
                    if (next != null) {
                        // A recurring task or deadline comes back with its next date instead of being done for good.
                        it[ListItems.values] = json.encodeToString(values.copy(dueDate = next.toString()))
                        it[fieldRevs] = json.encodeToString(fieldRevs(item) + (ItemField.DATE.name to FieldRev(rev, me, change.madeAt, clock())))
                        it[lastDoneBy] = me
                        it[lastDoneAt] = change.madeAt
                        it[checked] = false
                    } else {
                        it[checked] = change.checked
                        it[checkedBy] = if (change.checked) me else null
                        it[checkedAt] = if (change.checked) change.madeAt else null
                        if (!change.checked) {
                            it[cleared] = false
                            it[clearedAt] = null
                            it[clearedBy] = null
                        }
                    }
                    it[ListItems.rev] = rev
                }
            }
            ListChangeKind.DELETE_ITEM -> {
                val (_, item) = editableItem(membership, change, allowDeleted = true, need = null)
                if (item[ListItems.deleted]) return ChangeResult(change.id, ok = true)
                val rev = nextRev(organizationId)
                val gone = ListItems.selectAll().where { (ListItems.id eq item[ListItems.id]) or (ListItems.parentId eq item[ListItems.id]) }
                    .map { it[ListItems.id] }
                ListItems.update({ (ListItems.id eq item[ListItems.id]) or (ListItems.parentId eq item[ListItems.id]) }) {
                    it[deleted] = true
                    it[ListItems.rev] = rev
                }
                dropFiles(ListFilesTable.itemId inList gone)
                ListConflicts.update({ (ListConflicts.itemId eq item[ListItems.id]) and (ListConflicts.resolved eq false) }) {
                    it[resolved] = true
                    it[resolvedBy] = me
                    it[resolvedAt] = clock()
                    it[resolution] = "Pozycja usunięta z listy."
                    it[ListConflicts.rev] = rev
                }
            }
            ListChangeKind.CLEAR_CHECKED -> {
                editable(membership, change.listId, AccessLevel.CHECK)
                val rev = nextRev(organizationId)
                // Done items go to the history with their sub-items; a done sub-item of an open item stays under it.
                val done = ListItems.selectAll().where {
                    (ListItems.listId eq change.listId) and (ListItems.checked eq true) and (ListItems.cleared eq false) and
                        (ListItems.deleted eq false) and ListItems.parentId.isNull()
                }.map { it[ListItems.id] }
                ListItems.update({
                    (ListItems.listId eq change.listId) and (ListItems.cleared eq false) and (ListItems.deleted eq false) and
                        ((ListItems.id inList done) or (ListItems.parentId inList done))
                }) {
                    it[cleared] = true
                    it[clearedAt] = change.madeAt
                    it[clearedBy] = me
                    it[ListItems.rev] = rev
                }
            }
            ListChangeKind.FORGET_PRODUCT -> {
                val list = editable(membership, change.listId, AccessLevel.ADD)
                val key = Lists.memoryKey(change.values ?: throw invalid())
                val scope = Lists.scopeOf(toInfo(list)).name
                val rev = nextRev(organizationId)
                ListMemoryTable.update({
                    (ListMemoryTable.organizationId eq organizationId) and (ListMemoryTable.scope eq scope) and (ListMemoryTable.productKey eq key)
                }) {
                    it[deleted] = true
                    it[ListMemoryTable.rev] = rev
                }
            }
            ListChangeKind.SAVE_UNIT -> {
                editable(membership, change.listId, AccessLevel.ADD)
                val unit = change.unit ?: throw invalid()
                val name = unit.name.trim()
                if (name.isEmpty() || name.length > Lists.MAX_UNIT) throw ApiException(HttpStatusCode.BadRequest, "invalid_unit", "Nazwa jednostki: od 1 do ${Lists.MAX_UNIT} znaków.")
                if (Lists.BUILT_IN_UNITS.any { it.name == name }) throw ApiException(HttpStatusCode.BadRequest, "built_in", "Wbudowanych jednostek nie można zmieniać.")
                val forms = unit.forms.map { it.trim() }
                if (forms.size != 3 || forms.any { it.isEmpty() || it.length > Lists.MAX_UNIT }) {
                    throw ApiException(HttpStatusCode.BadRequest, "invalid_forms", "Wpisz wszystkie trzy formy (1, 2–4, 5+).")
                }
                val rev = nextRev(organizationId)
                val found = ListUnits.update({ (ListUnits.organizationId eq organizationId) and (ListUnits.name eq name) }) {
                    it[ListUnits.forms] = json.encodeToString(forms)
                    it[ListUnits.rev] = rev
                }
                if (found == 0) ListUnits.insert {
                    it[ListUnits.organizationId] = organizationId
                    it[ListUnits.name] = name
                    it[ListUnits.forms] = json.encodeToString(forms)
                    it[step] = 1.0
                    it[packUnit] = null
                    it[ListUnits.rev] = rev
                }
            }
            ListChangeKind.ITEM_ACCESS -> {
                manageable(membership, change.listId)
                val item = ListItems.selectAll().where { (ListItems.id eq (change.itemId ?: "")) and (ListItems.listId eq change.listId) }
                    .singleOrNull() ?: throw notFound()
                if (item[ListItems.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już tę pozycję.")
                if (item[ListItems.parentId] != null) {
                    throw ApiException(HttpStatusCode.BadRequest, "sub_item", "Inny dostęp ustawia się dla całej pozycji z podlistą.")
                }
                val access = validAccess(membership, change.access)
                if (access.isNotEmpty()) requireFeature(organizationId, Feature.LISTS_SUBITEMS, "Inny dostęp do zadania jest w pakiecie premium.")
                val rev = nextRev(organizationId)
                ListItems.update({ ListItems.id eq item[ListItems.id] }) {
                    it[ListItems.access] = json.encodeToString(access)
                    it[ListItems.rev] = rev
                }
                // The sub-items go to the phones again: they are seen (or not) with their item.
                ListItems.update({ ListItems.parentId eq item[ListItems.id] }) { it[ListItems.rev] = rev }
            }
            ListChangeKind.UNCHECK_ALL -> {
                editable(membership, change.listId, AccessLevel.CHECK)
                val rev = nextRev(organizationId)
                ListItems.update({
                    (ListItems.listId eq change.listId) and (ListItems.checked eq true) and (ListItems.cleared eq false) and (ListItems.deleted eq false)
                }) {
                    it[checked] = false
                    it[checkedBy] = null
                    it[checkedAt] = null
                    it[ListItems.rev] = rev
                }
            }
        }
        return ChangeResult(change.id, ok = true)
    }

    /**
     * Changes [ListChange.fields] of an item. A field someone else changed after the phone last saw the item
     * ([ListChange.baseRev]) to another value is a conflict: both versions are kept for the list's creator and the
     * one made later is shown meanwhile. A field already in an open conflict takes the new version into it.
     */
    private fun edit(membership: Membership, change: ListChange): ChangeResult {
        val me = membership.user[Users.id]
        val organizationId = membership.organizationId
        val (list, item) = editableItem(membership, change, need = null)
        val type = ListType.valueOf(list[ListsTable.type])
        val current = values(item)
        var wanted = validValues(membership, type, change.values ?: throw invalid())
        val fields = change.fields.distinct()
        if (fields.isEmpty()) return ChangeResult(change.id, ok = true)
        if (ItemField.LINKS in fields && wanted.links.any { it !in current.links }) {
            requireFeature(organizationId, Feature.LISTS_ATTACHMENTS, "Linki są w pakiecie premium.")
        }

        var queued: ChangeResult? = null
        if (ItemField.ASSIGNEE in fields && wanted.assigneeId != null && wanted.assigneeId != me && wanted.assigneeId != current.assigneeId) {
            when (accounts.can(membership, Action.LISTS_TASKS_OTHERS)) {
                Mode.DENIED -> throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Twoja rola nie może dawać zadań innym osobom.")
                Mode.APPROVAL -> {
                    accounts.queueRequest(
                        membership, Action.LISTS_TASKS_OTHERS,
                        json.encodeToString(QueuedTask(item[ListItems.id], wanted.assigneeId)),
                        taskSummary(current.copy(assigneeId = wanted.assigneeId), list),
                    )
                    queued = ChangeResult(change.id, ok = true, message = WAITS_FOR_APPROVAL, queued = true)
                    wanted = wanted.copy(assigneeId = current.assigneeId)
                }
                Mode.ALLOWED -> Unit
            }
        }

        val rev = nextRev(organizationId)
        val now = clock()
        val revs = fieldRevs(item).toMutableMap()
        var shown = current
        val open = ListConflicts.selectAll().where { (ListConflicts.itemId eq item[ListItems.id]) and (ListConflicts.resolved eq false) }
            .associateBy { ItemField.valueOf(it[ListConflicts.field]) }
        val mine = ConflictVersion(me, membership.user[Users.name], wanted, change.madeAt, now)
        for (field in fields) {
            if (field == ItemField.ASSIGNEE && queued != null) continue
            val last = revs[field.name]
            val conflict = open[field]
            when {
                field == ItemField.LINKS -> shown = field.copy(wanted, shown)
                conflict != null -> {
                    val versions = json.decodeFromString<List<ConflictVersion>>(conflict[ListConflicts.versions])
                        .filterNot { it.userId == me } + mine
                    ListConflicts.update({ ListConflicts.id eq conflict[ListConflicts.id] }) {
                        it[ListConflicts.versions] = json.encodeToString(versions)
                        it[ListConflicts.rev] = rev
                    }
                    shown = field.copy(versions.maxBy { it.madeAt }.values, shown)
                }
                last != null && last.rev > change.baseRev && last.by != me && !field.same(current, wanted) -> {
                    val theirs = ConflictVersion(last.by, userName(last.by), current, last.madeAt, last.at)
                    ListConflicts.insert {
                        it[id] = Secrets.id()
                        it[ListConflicts.organizationId] = organizationId
                        it[listId] = list[ListsTable.id]
                        it[itemId] = item[ListItems.id]
                        it[ListConflicts.field] = field.name
                        it[versions] = json.encodeToString(listOf(theirs, mine))
                        it[createdAt] = now
                        it[resolved] = false
                        it[ListConflicts.rev] = rev
                    }
                    if (change.madeAt >= last.madeAt) shown = field.copy(wanted, shown)
                }
                else -> shown = field.copy(wanted, shown)
            }
            revs[field.name] = FieldRev(rev, me, change.madeAt, now)
        }
        ListItems.update({ ListItems.id eq item[ListItems.id] }) {
            it[values] = json.encodeToString(shown)
            it[fieldRevs] = json.encodeToString(revs)
            it[changedBy] = me
            it[changedAt] = change.madeAt
            it[ListItems.rev] = rev
        }
        if (type == ListType.SHOPPING && ItemField.QUANTITY in fields) rememberUnit(organizationId, shown, change.rememberUnit, rev)
        if (type == ListType.SHOPPING && ItemField.VARIANT in fields) rememberVariant(organizationId, shown, rev)
        remember(organizationId, toInfo(list), shown, me, rev, added = false, at = change.madeAt)
        return queued ?: ChangeResult(change.id, ok = true)
    }

    // --- conflicts ---

    /**
     * Settles a conflict: the list's creator at once, the Owner/Admin (who may delete others' lists) after 48 h
     * without a decision. Keeps one version, puts in [ConflictDecision.other], or removes the item.
     */
    fun resolve(session: UserSession, conflictId: String, decision: ConflictDecision): ListConflict = transaction(database) {
        val membership = accounts.membership(session)
        val me = membership.user[Users.id]
        val conflict = ListConflicts.selectAll()
            .where { (ListConflicts.id eq conflictId) and (ListConflicts.organizationId eq membership.organizationId) }
            .singleOrNull() ?: throw notFound()
        if (conflict[ListConflicts.resolved]) throw ApiException(HttpStatusCode.Conflict, "resolved", "Ten konflikt jest już rozstrzygnięty.")
        val list = ListsTable.selectAll().where { ListsTable.id eq conflict[ListConflicts.listId] }.single()
        val creator = list[ListsTable.createdBy] == me
        val escalated = clock() - conflict[ListConflicts.createdAt] >= Lists.CONFLICT_ESCALATION_MS &&
            accounts.can(membership, Action.LISTS_DELETE_OTHERS) == Mode.ALLOWED
        if (!creator && !escalated) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "O konflikcie decyduje twórca listy (po 48 h także Owner lub Admin).")
        }
        val item = ListItems.selectAll().where { ListItems.id eq conflict[ListConflicts.itemId] }.single()
        val field = ItemField.valueOf(conflict[ListConflicts.field])
        val versions = json.decodeFromString<List<ConflictVersion>>(conflict[ListConflicts.versions])
        val type = ListType.valueOf(list[ListsTable.type])
        val rev = nextRev(membership.organizationId)
        val itemName = values(item).name
        val resolution = when {
            decision.removeItem -> {
                ListItems.update({ ListItems.id eq item[ListItems.id] }) { it[deleted] = true; it[ListItems.rev] = rev }
                "$itemName: pozycja usunięta z listy."
            }
            else -> {
                val keep = decision.keep
                val other = decision.other
                val (source, how) = when {
                    keep != null -> {
                        val version = versions.getOrNull(keep) ?: throw invalid()
                        version.values to "wersja: ${version.userName}"
                    }
                    other != null -> other to "nowa wartość"
                    else -> throw invalid()
                }
                val values = validValues(membership, type, field.copy(source, values(item)))
                ListItems.update({ ListItems.id eq item[ListItems.id] }) {
                    it[ListItems.values] = json.encodeToString(values)
                    it[fieldRevs] = json.encodeToString(fieldRevs(item) + (field.name to FieldRev(rev, me, clock(), clock())))
                    it[changedBy] = me
                    it[changedAt] = clock()
                    it[ListItems.rev] = rev
                }
                "$itemName — ${field.label}: ${describe(field, values, membership.organizationId).ifEmpty { "puste" }} ($how)."
            }
        }
        ListConflicts.update({ ListConflicts.id eq conflictId }) {
            it[resolved] = true
            it[resolvedBy] = me
            it[resolvedAt] = clock()
            it[ListConflicts.resolution] = resolution
            it[ListConflicts.rev] = rev
        }
        toConflict(ListConflicts.selectAll().where { ListConflicts.id eq conflictId }.single())
    }

    // --- photos and files (0.7.3, boards 25–26) ---

    /**
     * Adds [upload] (already on the disk, at most [Lists.MAX_FILE_BYTES] + 1 bytes) to item [itemId] as file [fileId].
     * The same [fileId] sent again (the phone did not hear the answer) gets the first answer back.
     */
    fun addFile(session: UserSession, itemId: String, fileId: String, name: String, mime: String, upload: java.io.File): ItemFile {
        accounts.requireModule(session, ModuleId.LISTS)
        try {
            return transaction(database) {
                val membership = accounts.membership(session)
                val organizationId = membership.organizationId
                val item = ListItems.selectAll().where { (ListItems.id eq itemId) and (ListItems.organizationId eq organizationId) }
                    .singleOrNull() ?: throw notFound()
                ListFilesTable.selectAll().where { ListFilesTable.id eq fileId }.singleOrNull()?.let { done ->
                    if (done[ListFilesTable.itemId] != itemId || done[ListFilesTable.deleted]) throw invalid()
                    return@transaction toFile(done)
                }
                if (fileId.isBlank() || fileId.length > 64 || !fileId.all { it.isLetterOrDigit() || it == '-' }) throw invalid()
                requireFeature(organizationId, Feature.LISTS_ATTACHMENTS, "Zdjęcia i pliki są w pakiecie premium.")
                val list = editable(membership, item[ListItems.listId], AccessLevel.ADD)
                if (item[ListItems.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już tę pozycję.")
                requireItemLevel(membership, list, item, AccessLevel.ADD)
                val cleanName = Lists.cleanFileName(name)
                val size = upload.length()
                val type = mime.substringBefore(';').trim().lowercase().ifEmpty { "application/octet-stream" }
                Lists.fileRefusal(cleanName, type, size)?.let { throw ApiException(HttpStatusCode.BadRequest, "file_refused", it) }
                val photo = type == Lists.PHOTO_MIME && isJpeg(upload)
                val present = ListFilesTable.selectAll().where { (ListFilesTable.itemId eq itemId) and (ListFilesTable.deleted eq false) }
                    .map { it[ListFilesTable.photo] }
                if (photo && present.count { it } >= Lists.MAX_PHOTOS) {
                    throw ApiException(HttpStatusCode.Conflict, "limit", "Pozycja ma już ${Lists.MAX_PHOTOS} zdjęć — to limit. Usuń któreś, żeby dodać nowe.")
                }
                if (!photo && present.count { !it } >= Lists.MAX_FILES) {
                    throw ApiException(HttpStatusCode.Conflict, "limit", "Pozycja ma już ${Lists.MAX_FILES} plików — to limit. Usuń któryś, żeby dodać nowy.")
                }
                val used = ListFilesTable.selectAll()
                    .where { (ListFilesTable.organizationId eq organizationId) and (ListFilesTable.deleted eq false) }
                    .sumOf { it[ListFilesTable.size] }
                if (used + size > Lists.COMMUNITY_FILE_BYTES) {
                    throw ApiException(HttpStatusCode.Conflict, "full", "Miejsce na zdjęcia i pliki społeczności (${Lists.sizeText(Lists.COMMUNITY_FILE_BYTES)}) jest pełne. Usuń stare zdjęcia albo pliki.")
                }
                val now = clock()
                files.keep(organizationId, fileId, upload)
                ListFilesTable.insert {
                    it[id] = fileId
                    it[ListFilesTable.organizationId] = organizationId
                    it[listId] = item[ListItems.listId]
                    it[ListFilesTable.itemId] = itemId
                    it[ListFilesTable.name] = cleanName
                    it[ListFilesTable.mime] = if (photo) Lists.PHOTO_MIME else type
                    it[ListFilesTable.size] = size
                    it[ListFilesTable.photo] = photo
                    it[addedBy] = membership.user[Users.id]
                    it[addedAt] = now
                    it[deleted] = false
                }
                touch(itemId, organizationId)
                ItemFile(fileId, cleanName, if (photo) Lists.PHOTO_MIME else type, size, photo, membership.user[Users.id], now)
            }
        } finally {
            upload.delete()
        }
    }

    /** The file to send back, with its row; only to someone who sees the item. */
    fun file(session: UserSession, fileId: String): Pair<ItemFile, java.io.File> = transaction(database) {
        accounts.requireModule(session, ModuleId.LISTS)
        val membership = accounts.membership(session)
        val (row, _) = seenFile(membership, fileId)
        val stored = files.path(membership.organizationId, fileId)
        if (!stored.isFile) throw ApiException(HttpStatusCode.Gone, "deleted", "Tego pliku nie ma już na serwerze.")
        toFile(row) to stored
    }

    /** Removes a photo or a file: the person who added it, or who manages the list (its creator, an Admin). */
    fun deleteFile(session: UserSession, fileId: String) = transaction(database) {
        accounts.requireModule(session, ModuleId.LISTS)
        val membership = accounts.membership(session)
        val (row, list) = seenFile(membership, fileId)
        val me = membership.user[Users.id]
        if (row[ListFilesTable.addedBy] != me && list[ListsTable.createdBy] != me &&
            accounts.can(membership, Action.LISTS_DELETE_OTHERS) != Mode.ALLOWED
        ) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Usunąć może ten, kto dodał, i twórca listy.")
        }
        dropFiles(ListFilesTable.id eq fileId)
        touch(row[ListFilesTable.itemId], membership.organizationId)
    }

    private fun seenFile(membership: Membership, fileId: String): Pair<ResultRow, ResultRow> {
        val row = ListFilesTable.selectAll()
            .where { (ListFilesTable.id eq fileId) and (ListFilesTable.organizationId eq membership.organizationId) }
            .singleOrNull() ?: throw notFound()
        if (row[ListFilesTable.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już ten plik.")
        val list = visibleList(membership, row[ListFilesTable.listId])
        val item = ListItems.selectAll().where { ListItems.id eq row[ListFilesTable.itemId] }.singleOrNull() ?: throw notFound()
        if (item[ListItems.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już tę pozycję.")
        requireItemLevel(membership, list, item, AccessLevel.SEE)
        return row to list
    }

    /** Marks the files deleted and removes their bytes (also from the server's disk; the NAS copy follows the next night). */
    private fun dropFiles(where: org.jetbrains.exposed.v1.core.Op<Boolean>) {
        val gone = ListFilesTable.selectAll().where { where and (ListFilesTable.deleted eq false) }
            .map { it[ListFilesTable.organizationId] to it[ListFilesTable.id] }
        if (gone.isEmpty()) return
        ListFilesTable.update({ where }) { it[deleted] = true }
        gone.forEach { (organizationId, id) -> files.delete(organizationId, id) }
    }

    /** A new rev for the item, so phones get its new list of files with the next sync. */
    private fun touch(itemId: String, organizationId: String) {
        val rev = nextRev(organizationId)
        ListItems.update({ ListItems.id eq itemId }) { it[ListItems.rev] = rev }
    }

    private fun isJpeg(file: java.io.File): Boolean = file.inputStream().use { input ->
        val head = ByteArray(3)
        input.read(head) == 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
    }

    private fun toFile(row: ResultRow) = ItemFile(
        id = row[ListFilesTable.id],
        name = row[ListFilesTable.name],
        mime = row[ListFilesTable.mime],
        size = row[ListFilesTable.size],
        photo = row[ListFilesTable.photo],
        addedBy = row[ListFilesTable.addedBy],
        addedAt = row[ListFilesTable.addedAt],
    )

    // --- rights ---

    /** What the caller may do on [list] (board 15): its access, never more than the community's table allows. */
    private fun level(membership: Membership, list: ResultRow): AccessLevel = Lists.level(
        toInfo(list), membership.user[Users.id], membership.role.id, membership.role.owner,
        roleMayEdit = accounts.can(membership, Action.LISTS_EDIT_ITEMS) == Mode.ALLOWED,
    )

    private fun visibleList(membership: Membership, listId: String): ResultRow {
        val list = ListsTable.selectAll()
            .where { (ListsTable.id eq listId) and (ListsTable.organizationId eq membership.organizationId) }
            .singleOrNull()
        if (list == null || !level(membership, list).atLeast(AccessLevel.SEE)) throw notFound()
        if (list[ListsTable.deleted]) throw ApiException(HttpStatusCode.Gone, "deleted", "Ta lista została usunięta.")
        return list
    }

    /** The caller may do at least [need] on the list. */
    private fun editable(membership: Membership, listId: String, need: AccessLevel): ResultRow {
        val list = visibleList(membership, listId)
        val level = level(membership, list)
        if (!level.atLeast(need)) {
            if (accounts.can(membership, Action.LISTS_EDIT_ITEMS) != Mode.ALLOWED) require(membership, Action.LISTS_EDIT_ITEMS)
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Na tej liście możesz: ${level.label}. Dostęp ustala twórca listy.")
        }
        return list
    }

    /** [need] null: changing or deleting the item — own items with "dodaje", everyone's with "edytuje i usuwa". */
    private fun editableItem(membership: Membership, change: ListChange, allowDeleted: Boolean = false, need: AccessLevel?): Pair<ResultRow, ResultRow> {
        val list = editable(membership, change.listId, need ?: AccessLevel.ADD)
        val item = ListItems.selectAll().where { (ListItems.id eq (change.itemId ?: "")) and (ListItems.listId eq change.listId) }
            .singleOrNull() ?: throw notFound()
        if (need == null && item[ListItems.addedBy] != membership.user[Users.id] && !level(membership, list).atLeast(AccessLevel.EDIT_ALL)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cudze pozycje na tej liście zmienia tylko ktoś z dostępem „edytuje i usuwa”.")
        }
        if (item[ListItems.deleted] && !allowDeleted) throw ApiException(HttpStatusCode.Gone, "deleted", "Ktoś usunął już tę pozycję.")
        requireItemLevel(membership, list, item, need ?: AccessLevel.ADD)
        return list to item
    }

    /** "Inny dostęp do tego zadania" of the item (or of the item a sub-item belongs to) narrows the list's access. */
    private fun requireItemLevel(membership: Membership, list: ResultRow, item: ResultRow, need: AccessLevel) {
        val top = item[ListItems.parentId]?.let { id -> ListItems.selectAll().where { ListItems.id eq id }.singleOrNull() } ?: item
        val level = Lists.itemLevel(
            toInfo(list), toItem(top), membership.user[Users.id], membership.role.id, membership.role.owner,
            roleMayEdit = accounts.can(membership, Action.LISTS_EDIT_ITEMS) == Mode.ALLOWED,
        )
        if (!level.atLeast(need)) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Przy tym zadaniu możesz: ${level.label}. Dostęp ustala twórca listy.")
        }
    }

    private fun requireFeature(organizationId: String, feature: Feature, message: String) {
        if (feature !in accounts.communityFeatures(organizationId)) throw ApiException(HttpStatusCode.Forbidden, "premium", message)
    }

    /** The list's creator, or someone whose role may delete others' lists, may rename, share, archive and delete it. */
    private fun manageable(membership: Membership, listId: String): ResultRow {
        val list = visibleList(membership, listId)
        if (list[ListsTable.createdBy] != membership.user[Users.id] && accounts.can(membership, Action.LISTS_DELETE_OTHERS) != Mode.ALLOWED) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Tę listę może zmieniać jej twórca albo Admin.")
        }
        return list
    }

    /** Lists actions are done at once or not at all (no approval: changes are made offline). */
    private fun require(membership: Membership, action: Action) {
        if (accounts.can(membership, action) != Mode.ALLOWED) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Twoja rola nie może: ${action.label.lowercase()}.")
        }
    }

    // --- values ---

    private fun validValues(membership: Membership, type: ListType, values: ItemValues): ItemValues {
        val name = values.name.trim()
        if (name.isEmpty() || name.length > Lists.MAX_NAME) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_name", "Nazwa pozycji: wpisz od 1 do ${Lists.MAX_NAME} znaków.")
        }
        if (values.note.length > Lists.MAX_NOTE) throw ApiException(HttpStatusCode.BadRequest, "invalid_note", "Notatka jest za długa.")
        val dueDate = values.dueDate?.takeIf { it.isNotBlank() }?.also {
            runCatching { LocalDate.parse(it) }.getOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "invalid_date", "Nieprawidłowa data.")
        }
        val urgent = values.urgent && type != ListType.SHOPPING
        if (type.needsDate && dueDate == null && !urgent) throw ApiException(HttpStatusCode.BadRequest, "no_date", "Termin musi mieć datę albo być pilny.")
        val variant = values.variant.trim().takeIf { type == ListType.SHOPPING }.orEmpty()
        if (variant.length > Lists.MAX_VARIANT) throw ApiException(HttpStatusCode.BadRequest, "invalid_variant", "Rodzaj jest za długi.")
        val members = accounts.membersOf(membership.organizationId).map { it[Users.id] }.toSet()
        val assignee = values.assigneeId?.takeIf { type.hasAssignee }
        if (assignee != null && assignee !in members) throw ApiException(HttpStatusCode.BadRequest, "no_member", "Tej osoby nie ma w społeczności.")
        val quantity = values.quantity?.takeIf { type.hasQuantity }
        if (quantity != null && (quantity <= 0 || quantity >= 100_000)) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_quantity", "Nieprawidłowa ilość.")
        }
        val unit = values.unit?.trim()?.takeIf { it.isNotEmpty() && type.hasQuantity }
        if (unit != null && unit.length > 20) throw ApiException(HttpStatusCode.BadRequest, "invalid_unit", "Nazwa jednostki jest za długa.")
        val packSize = values.packSize?.trim()?.takeIf { it.isNotEmpty() && unit != null }?.take(10)
        val links = values.links.map { ItemLink(it.url.trim(), it.title.trim().take(60)) }.distinctBy { it.url }
        if (links.size > Lists.MAX_LINKS || links.any { !Lists.isLink(it.url) }) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_link", "Linki: do ${Lists.MAX_LINKS} adresów zaczynających się od http:// lub https://.")
        }
        if (values.reminders.isNotEmpty()) {
            if (Feature.LISTS_REMINDERS !in accounts.communityFeatures(membership.organizationId)) {
                throw ApiException(HttpStatusCode.Forbidden, "premium", "Przypomnienia są w pakiecie premium.")
            }
            if (values.reminders.size > 10 || values.reminders.any {
                    it.memberId !in members || it.daysBefore !in 0..60 || runCatching { LocalTime.parse(it.time) }.isFailure
                }) throw invalid()
        }
        return ItemValues(
            name = name,
            quantity = quantity,
            unit = unit?.takeIf { quantity != null },
            packSize = packSize?.takeIf { quantity != null },
            dueDate = dueDate.takeIf { !urgent },
            recurrence = if (dueDate != null && !urgent) values.recurrence else Recurrence(),
            urgent = urgent,
            variant = variant,
            assigneeId = assignee,
            note = values.note.trim(),
            reminders = values.reminders,
            links = links,
        )
    }

    private fun describe(field: ItemField, values: ItemValues, organizationId: String): String = when (field) {
        ItemField.NAME -> values.name
        ItemField.QUANTITY -> Lists.quantityText(values, units(organizationId))
        ItemField.DATE -> if (values.urgent) "pilne" else values.dueDate.orEmpty()
        ItemField.ASSIGNEE -> values.assigneeId?.let { userName(it) }.orEmpty()
        ItemField.NOTE -> values.note
        ItemField.REMINDERS -> if (values.reminders.isEmpty()) "" else "${values.reminders.size}"
        ItemField.VARIANT -> values.variant
        ItemField.LINKS -> values.links.joinToString { Lists.domain(it.url) }
    }

    private fun units(organizationId: String): List<ListUnit> = Lists.BUILT_IN_UNITS +
        ListUnits.selectAll().where { ListUnits.organizationId eq organizationId }
            .map { ListUnit(it[ListUnits.name], json.decodeFromString(it[ListUnits.forms]), it[ListUnits.step], it[ListUnits.packUnit]) }

    /** An unknown unit becomes the community's own; with "remember" the product keeps its unit for next time. */
    private fun rememberUnit(organizationId: String, values: ItemValues, remember: Boolean, rev: Long) {
        val unit = values.unit ?: return
        if (Lists.BUILT_IN_UNITS.none { it.name == unit } &&
            ListUnits.selectAll().where { (ListUnits.organizationId eq organizationId) and (ListUnits.name eq unit) }.empty()
        ) {
            ListUnits.insert {
                it[ListUnits.organizationId] = organizationId
                it[name] = unit
                it[forms] = json.encodeToString(emptyList<String>())
                it[step] = 1.0
                it[packUnit] = null
                it[ListUnits.rev] = rev
            }
        }
        if (!remember) return
        val product = Lists.productKey(values.name)
        ListProducts.deleteWhere { (ListProducts.organizationId eq organizationId) and (ListProducts.product eq product) }
        ListProducts.insert {
            it[ListProducts.organizationId] = organizationId
            it[ListProducts.product] = product
            it[ListProducts.unit] = unit
            it[packSize] = values.packSize
            it[ListProducts.rev] = rev
        }
    }

    /** A kind of a product, offered next time in "Rodzaj" (the newest first). */
    private fun rememberVariant(organizationId: String, values: ItemValues, rev: Long) {
        val variant = values.variant.trim().takeIf { it.isNotEmpty() } ?: return
        val product = Lists.productKey(values.name)
        ListVariants.deleteWhere { (ListVariants.organizationId eq organizationId) and (ListVariants.product eq product) and (ListVariants.variant eq variant) }
        ListVariants.insert {
            it[ListVariants.organizationId] = organizationId
            it[ListVariants.product] = product
            it[ListVariants.variant] = variant
            it[usedAt] = clock()
            it[ListVariants.rev] = rev
        }
    }

    /**
     * The community's memory (board 21): an added thing counts once more and keeps its newest parameters; a changed
     * one only updates them (a forgotten one stays forgotten until it is added again).
     */
    private fun remember(organizationId: String, list: ListInfo, values: ItemValues, me: String, rev: Long, added: Boolean, at: Long) {
        val scope = Lists.scopeOf(list).name
        val key = Lists.memoryKey(values)
        val kept = json.encodeToString(Lists.memoryValues(values))
        val row = ListMemoryTable.selectAll().where {
            (ListMemoryTable.organizationId eq organizationId) and (ListMemoryTable.scope eq scope) and (ListMemoryTable.productKey eq key)
        }.singleOrNull()
        if (row == null) {
            ListMemoryTable.insert {
                it[ListMemoryTable.organizationId] = organizationId
                it[ListMemoryTable.scope] = scope
                it[productKey] = key
                it[ListMemoryTable.values] = kept
                it[useCount] = 1
                it[lastUsedAt] = at
                it[lastBy] = me
                it[deleted] = false
                it[ListMemoryTable.rev] = rev
            }
            return
        }
        if (!added && row[ListMemoryTable.deleted]) return
        ListMemoryTable.update({
            (ListMemoryTable.organizationId eq organizationId) and (ListMemoryTable.scope eq scope) and (ListMemoryTable.productKey eq key)
        }) {
            it[ListMemoryTable.values] = kept
            if (added) {
                it[useCount] = if (row[ListMemoryTable.deleted]) 1 else row[ListMemoryTable.useCount] + 1
                it[lastUsedAt] = at
                it[lastBy] = me
                it[deleted] = false
            }
            it[ListMemoryTable.rev] = rev
        }
    }

    /**
     * The first sync of a community after 0.7.1: the memory is built from everything already on its lists (history
     * too), so the things bought so far are there at once. Nothing happens once the community has any memory.
     */
    private fun buildMemory(organizationId: String) {
        if (ListMemoryTable.selectAll().where { ListMemoryTable.organizationId eq organizationId }.limit(1).any()) return
        val lists = ListsTable.selectAll().where { (ListsTable.organizationId eq organizationId) and (ListsTable.deleted eq false) }
            .associate { it[ListsTable.id] to toInfo(it) }
        val items = ListItems.selectAll().where { (ListItems.organizationId eq organizationId) and (ListItems.deleted eq false) }
            .mapNotNull { row -> lists[row[ListItems.listId]]?.let { list -> Triple(list, row, values(row)) } }
            .filter { it.third.name.isNotBlank() }
        if (items.isEmpty()) return
        val rev = nextRev(organizationId)
        items.groupBy { (list, _, values) -> Lists.scopeOf(list) to Lists.memoryKey(values) }.forEach { (key, group) ->
            val (_, last, values) = group.maxBy { it.second[ListItems.addedAt] }
            ListMemoryTable.insert {
                it[ListMemoryTable.organizationId] = organizationId
                it[scope] = key.first.name
                it[productKey] = key.second
                it[ListMemoryTable.values] = json.encodeToString(Lists.memoryValues(values))
                it[useCount] = group.size
                it[lastUsedAt] = last[ListItems.addedAt]
                it[lastBy] = last[ListItems.addedBy]
                it[deleted] = false
                it[ListMemoryTable.rev] = rev
            }
        }
    }

    /** Only lists of things have a style; a preset keeps its own labels, the person's own kind gets them checked. */
    private fun styleJson(list: ListInfo): String? {
        if (list.type != ListType.SHOPPING) return null
        val style = list.style ?: return null
        fun label(text: String, fallback: String) = text.trim().take(30).ifEmpty { fallback }
        val clean = style.copy(
            openLabel = label(style.openLabel, ListStyle.CUSTOM.openLabel),
            doneLabel = label(style.doneLabel, ListStyle.CUSTOM.doneLabel),
        )
        return json.encodeToString(clean)
    }

    /** Rows of people and roles of this community only, one per person or role. */
    private fun validAccess(membership: Membership, access: List<ListAccess>): List<ListAccess> {
        if (access.size > 100) throw invalid()
        val members = accounts.membersOf(membership.organizationId).map { it[Users.id] }.toSet()
        val roles = membership.roles.map { it.id }.toSet()
        return access
            .filter { (it.userId != null && it.userId in members) || (it.userId == null && it.roleId != null && it.roleId in roles) }
            .distinctBy { it.userId ?: "role:${it.roleId}" }
    }

    private fun nextDate(values: ItemValues): LocalDate? {
        val due = LocalDate.parse(values.dueDate)
        return Recurrences.occurrences(due, values.recurrence.copy(endDate = null, count = null), due.plusDays(1), due.plusYears(5))
            .firstOrNull()
            ?.takeIf { next -> values.recurrence.endDate == null || !next.isAfter(LocalDate.parse(values.recurrence.endDate)) }
    }

    private fun taskSummary(values: ItemValues, list: ResultRow): String =
        "Zadanie „${values.name}” dla: ${values.assigneeId?.let { userName(it) }.orEmpty()} (lista ${list[ListsTable.name]})"

    private fun setMembers(listId: String, organizationId: String, creator: String, list: ListInfo) {
        ListMembers.deleteWhere { ListMembers.listId eq listId }
        if (list.everyone) return
        val members = accounts.membersOf(organizationId).map { it[Users.id] }.toSet()
        (list.memberIds.filter { it in members } + creator).distinct().forEach { userId ->
            ListMembers.insert {
                it[ListMembers.listId] = listId
                it[ListMembers.userId] = userId
            }
        }
    }

    private fun listName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 60) throw ApiException(HttpStatusCode.BadRequest, "invalid_name", "Nazwa listy: wpisz od 1 do 60 znaków.")
        return trimmed
    }

    // --- rows ---

    private fun currentRev(organizationId: String): Long =
        ListRevs.selectAll().where { ListRevs.organizationId eq organizationId }.singleOrNull()?.get(ListRevs.rev) ?: 0

    /** The community's next change number. Must run in a transaction. */
    private fun nextRev(organizationId: String): Long {
        val next = currentRev(organizationId) + 1
        if (next == 1L) {
            ListRevs.insert { it[ListRevs.organizationId] = organizationId; it[rev] = next }
        } else {
            ListRevs.update({ ListRevs.organizationId eq organizationId }) { it[rev] = next }
        }
        return next
    }

    private fun values(item: ResultRow): ItemValues = values(item[ListItems.values])

    private fun values(text: String): ItemValues = json.decodeFromString(text)

    private fun fieldRevs(item: ResultRow): Map<String, FieldRev> = json.decodeFromString(item[ListItems.fieldRevs])

    private fun userName(userId: String): String =
        Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.name) ?: "?"

    private fun toInfo(row: ResultRow) = ListInfo(
        id = row[ListsTable.id],
        type = ListType.valueOf(row[ListsTable.type]),
        name = row[ListsTable.name],
        everyone = row[ListsTable.everyone],
        memberIds = if (row[ListsTable.everyone]) emptyList()
        else ListMembers.selectAll().where { ListMembers.listId eq row[ListsTable.id] }.map { it[ListMembers.userId] },
        createdBy = row[ListsTable.createdBy],
        createdAt = row[ListsTable.createdAt],
        archived = row[ListsTable.archived],
        deleted = row[ListsTable.deleted],
        rev = row[ListsTable.rev],
        style = row[ListsTable.style]?.let { runCatching { json.decodeFromString<ListStyle>(it) }.getOrNull() },
        access = runCatching { json.decodeFromString<List<ListAccess>>(row[ListsTable.access]) }.getOrDefault(emptyList()),
    )

    private fun toItem(row: ResultRow) = ListItem(
        id = row[ListItems.id],
        listId = row[ListItems.listId],
        values = values(row),
        checked = row[ListItems.checked],
        checkedBy = row[ListItems.checkedBy],
        checkedAt = row[ListItems.checkedAt],
        addedBy = row[ListItems.addedBy],
        addedAt = row[ListItems.addedAt],
        changedBy = row[ListItems.changedBy],
        changedAt = row[ListItems.changedAt],
        cleared = row[ListItems.cleared],
        clearedAt = row[ListItems.clearedAt],
        clearedBy = row[ListItems.clearedBy],
        pending = row[ListItems.pending],
        lastDoneBy = row[ListItems.lastDoneBy],
        lastDoneAt = row[ListItems.lastDoneAt],
        deleted = row[ListItems.deleted],
        rev = row[ListItems.rev],
        parentId = row[ListItems.parentId],
        access = runCatching { json.decodeFromString<List<ListAccess>>(row[ListItems.access]) }.getOrDefault(emptyList()),
    )

    private fun toMemory(row: ResultRow) = ListMemory(
        scope = MemoryScope.valueOf(row[ListMemoryTable.scope]),
        key = row[ListMemoryTable.productKey],
        values = values(row[ListMemoryTable.values]),
        uses = row[ListMemoryTable.useCount],
        lastUsedAt = row[ListMemoryTable.lastUsedAt],
        lastBy = row[ListMemoryTable.lastBy],
        deleted = row[ListMemoryTable.deleted],
        rev = row[ListMemoryTable.rev],
    )

    private fun toConflict(row: ResultRow) = ListConflict(
        id = row[ListConflicts.id],
        listId = row[ListConflicts.listId],
        itemId = row[ListConflicts.itemId],
        field = ItemField.valueOf(row[ListConflicts.field]),
        versions = json.decodeFromString(row[ListConflicts.versions]),
        createdAt = row[ListConflicts.createdAt],
        resolved = row[ListConflicts.resolved],
        resolvedBy = row[ListConflicts.resolvedBy],
        resolvedAt = row[ListConflicts.resolvedAt],
        resolution = row[ListConflicts.resolution],
        rev = row[ListConflicts.rev],
    )

    private fun invalid() = ApiException(HttpStatusCode.BadRequest, "invalid_request", "Nieprawidłowe dane.")

    private fun notFound() = ApiException(HttpStatusCode.NotFound, "not_found", "Nie znaleziono tej listy albo pozycji.")

    companion object {
        /** At most this many changes per request; the phone sends the rest in the next one. */
        const val MAX_CHANGES = 200
        const val WAITS_FOR_APPROVAL = "Zadanie dla innej osoby czeka na zatwierdzenie przez Admina albo Ownera."
    }
}
