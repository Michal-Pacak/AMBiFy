package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/**
 * Lists (Stage 5): one engine for shopping, tasks and deadlines; a new kind of list is configuration, not new code.
 * The phone works on its own copy (offline first) and exchanges changes with the server through [ListPaths.SYNC].
 */
object ListPaths {
    /** POST [ListSyncRequest] → [ListSyncResponse]: sends the phone's waiting changes, gets everything newer. */
    const val SYNC = "/lists/sync"
    /** POST …/{id} [ConflictDecision]: the list's creator (after 48 h the Owner/Admin) settles a conflict. */
    const val CONFLICTS = "/lists/conflicts"
    /**
     * 0.7.3, premium [Feature.LISTS_ATTACHMENTS]: POST /lists/items/{itemId}/files with the file as the body (its type in
     * Content-Type, its id and name in [FILE_ID] and [FILE_NAME]) adds a photo or a file to an item; GET [FILES]/{id}
     * gives it back, DELETE [FILES]/{id} removes it from the server.
     */
    const val ITEMS = "/lists/items"
    const val FILES = "/lists/files"
    const val FILE_ID = "X-Ambify-File-Id"
    /** URL-encoded (UTF-8), so "Faktura łazienka.pdf" survives an HTTP header. */
    const val FILE_NAME = "X-Ambify-File-Name"

    fun itemFiles(itemId: String) = "$ITEMS/$itemId/files"
}

/** [hasQuantity]: items have an amount with a unit; [needsDate]: every item has a date and they are sorted by it. */
@Serializable
enum class ListType(val label: String, val hasQuantity: Boolean, val hasAssignee: Boolean, val needsDate: Boolean) {
    /** "Lista rzeczy": shopping, packing or the person's own kind ([ListStyle]). */
    SHOPPING("Lista rzeczy", hasQuantity = true, hasAssignee = false, needsDate = false),
    TASKS("Zadania", hasQuantity = false, hasAssignee = true, needsDate = false),
    DEADLINES("Terminy", hasQuantity = false, hasAssignee = false, needsDate = true),
}

/** Ready-made kinds of a list of things (board 10); [CUSTOM]: the person names the sections and picks how it works. */
@Serializable
enum class ListPreset(val label: String) { SHOPPING("Zakupy"), PACKING("Pakowanie"), CUSTOM("Własna") }

/** What the button under a list of things does: done ones go to the history, or everything is unchecked for next time. */
@Serializable
enum class ListFinish { HISTORY, RESET }

@Serializable
enum class ListIcon { CART, SUITCASE, PIN, STAR, GIFT, LIST }

/** How a list of things looks and works (sections, amounts, the button at the bottom, its icon). */
@Serializable
data class ListStyle(
    val preset: ListPreset = ListPreset.SHOPPING,
    val openLabel: String = "do kupienia",
    val doneLabel: String = "kupione",
    val quantity: Boolean = true,
    val finish: ListFinish = ListFinish.HISTORY,
    val icon: ListIcon = ListIcon.CART,
) {
    companion object {
        val SHOPPING = ListStyle()
        val PACKING = ListStyle(ListPreset.PACKING, "do spakowania", "spakowane", true, ListFinish.RESET, ListIcon.SUITCASE)
        val CUSTOM = ListStyle(ListPreset.CUSTOM, "do zrobienia", "zrobione", false, ListFinish.HISTORY, ListIcon.LIST)
        fun of(preset: ListPreset) = when (preset) {
            ListPreset.SHOPPING -> SHOPPING
            ListPreset.PACKING -> PACKING
            ListPreset.CUSTOM -> CUSTOM
        }
    }
}

/**
 * What a person may do on a list (board 15); each level has the ones before it. [ADD] also changes and deletes the
 * person's own items, [EDIT_ALL] everyone's.
 */
@Serializable
enum class AccessLevel(val label: String) {
    NONE("nie widzi"), SEE("widzi"), CHECK("odhacza"), ADD("dodaje"), EDIT_ALL("edytuje i usuwa");

    fun atLeast(other: AccessLevel) = ordinal >= other.ordinal
}

/** One row of a list's access: a role ([roleId]) or a person ([userId], more important than their role). */
@Serializable
data class ListAccess(val roleId: String? = null, val userId: String? = null, val level: AccessLevel)

/**
 * A list. [access]: who sees it and what they may do, set by its creator (the creator and the Owner may always
 * everything). Lists made before 0.7 have no [access]: then [everyone] = the whole community, else only [memberIds].
 */
@Serializable
data class ListInfo(
    val id: String,
    val type: ListType,
    val name: String,
    val everyone: Boolean = true,
    val memberIds: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val rev: Long = 0,
    /** Lists of things only; null = [ListStyle.SHOPPING]. */
    val style: ListStyle? = null,
    val access: List<ListAccess> = emptyList(),
) {
    /** Lists made before 0.7.1 kept "w koszyku": shown as "kupione" (the list stays the same, nothing is migrated). */
    val look: ListStyle get() = (style ?: ListStyle.SHOPPING).let {
        if (it.preset == ListPreset.SHOPPING && it.doneLabel == "w koszyku") it.copy(doneLabel = ListStyle.SHOPPING.doneLabel) else it
    }
}

/** A reminder of an item (premium [Feature.LISTS_REMINDERS]) for one member, [daysBefore] its date at [time]. */
@Serializable
data class ListReminder(val memberId: String, val kind: ReminderKind, val daysBefore: Int = 0, val time: String = "09:00")

/** What a person may type or change in an item; which of them were changed is told by [ItemField]. */
@Serializable
data class ItemValues(
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    /** Size of one pack of a pack unit, e.g. "2,5" for "siatka po 2,5 kg". */
    val packSize: String? = null,
    val dueDate: String? = null,
    val recurrence: Recurrence = Recurrence(),
    val assigneeId: String? = null,
    val note: String = "",
    val reminders: List<ListReminder> = emptyList(),
    /** "3,2%" of "Mleko 3,2%": shown as a lighter part of the name. */
    val variant: String = "",
    /** "PILNE": at the top of the list, instead of a date. */
    val urgent: Boolean = false,
    /** Premium [Feature.LISTS_ATTACHMENTS] (0.7.2): up to [Lists.MAX_LINKS] web addresses, opened in the browser. */
    val links: List<ItemLink> = emptyList(),
)

/**
 * A photo or a file of an item (0.7.3, premium [Feature.LISTS_ATTACHMENTS]), kept on the server: phones get only this
 * (no thumbnails) and download the file when someone taps it. [photo]: sent by the app as a JPEG, opened full screen.
 */
@Serializable
data class ItemFile(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val photo: Boolean,
    val addedBy: String = "",
    val addedAt: Long = 0,
)

/** A web address of an item; shown as its [title] or, without one, its domain ("allegro.pl"). */
@Serializable
data class ItemLink(val url: String, val title: String = "")

/** The parts of an item that are changed (and may be in conflict) separately; checking off never conflicts. */
@Serializable
enum class ItemField(val label: String) {
    NAME("nazwa"), QUANTITY("ilość"), DATE("termin"), ASSIGNEE("osoba"), NOTE("notatka"), REMINDERS("przypomnienia"), VARIANT("rodzaj"),
    /** 0.7.2; never a conflict (the newest list of links wins), so phones before 0.7.2 never get it in one. */
    LINKS("linki");

    fun copy(from: ItemValues, to: ItemValues): ItemValues = when (this) {
        NAME -> to.copy(name = from.name)
        QUANTITY -> to.copy(quantity = from.quantity, unit = from.unit, packSize = from.packSize)
        DATE -> to.copy(dueDate = from.dueDate, recurrence = from.recurrence, urgent = from.urgent)
        ASSIGNEE -> to.copy(assigneeId = from.assigneeId)
        NOTE -> to.copy(note = from.note)
        REMINDERS -> to.copy(reminders = from.reminders)
        VARIANT -> to.copy(variant = from.variant)
        LINKS -> to.copy(links = from.links)
    }

    fun same(a: ItemValues, b: ItemValues): Boolean = copy(a, b) == b
}

@Serializable
data class ListItem(
    val id: String,
    val listId: String,
    val values: ItemValues,
    val checked: Boolean = false,
    val checkedBy: String? = null,
    val checkedAt: Long? = null,
    val addedBy: String = "",
    val addedAt: Long = 0,
    val changedBy: String? = null,
    val changedAt: Long? = null,
    /** Lists of things: moved to the history by "Zakończ zakupy" ([clearedAt] by [clearedBy]), under the list. */
    val cleared: Boolean = false,
    val clearedAt: Long? = null,
    val clearedBy: String? = null,
    /** A task for someone else waiting for an Admin's or the Owner's approval: shown, cannot be checked. */
    val pending: Boolean = false,
    /** A recurring task or deadline done last time (it came back with the next date). */
    val lastDoneBy: String? = null,
    val lastDoneAt: Long? = null,
    val deleted: Boolean = false,
    val rev: Long = 0,
    /** A sub-item (premium [Feature.LISTS_SUBITEMS], 0.7.2) of this item; only one level: a sub-item has none. */
    val parentId: String? = null,
    /**
     * "Inny dostęp do tego zadania" of an item with sub-items: who may do what on it and its sub-items, never more
     * than on the list ([Lists.itemLevel]). Empty = as on the list.
     */
    val access: List<ListAccess> = emptyList(),
    /** 0.7.3: photos and files on the server ([ItemFile]); changed only through [ListPaths.FILES], never in a change. */
    val files: List<ItemFile> = emptyList(),
)

/** One change made on a phone, sent (also much later, after being offline) in [ListSyncRequest.changes]. */
@Serializable
enum class ListChangeKind {
    CREATE_LIST, UPDATE_LIST, DELETE_LIST,
    ADD_ITEM, EDIT_ITEM, CHECK_ITEM, DELETE_ITEM,
    /** Shopping "Zakończ zakupy": checked items go to history. */
    CLEAR_CHECKED,
    /** "Odznacz wszystko": the same list for the next time (packing). */
    UNCHECK_ALL,
    /** "Usuń z pamięci" (0.7.1): [ListChange.values] names the product, [ListChange.listId] the kind of lists. */
    FORGET_PRODUCT,
    /** 0.7.2: a community's own unit with its forms ([ListChange.unit]), new or corrected ("Popraw odmianę"). */
    SAVE_UNIT,
    /** 0.7.2: "Inny dostęp do tego zadania" ([ListChange.access]; empty = as on the list). */
    ITEM_ACCESS,
    /** 0.7.5, groups (board 27): the item goes into the group [ListChange.parentId], or out of its group (null). */
    MOVE_ITEM,
}

/**
 * [id] is made by the phone (also for a new list or item, so it can be used offline before the server knows it).
 * [baseRev]: the item's rev the phone saw when the person changed it; a field changed by someone else after that is
 * a conflict. [madeAt]: when the person did it on the phone (shown in a conflict, "dziś 10:09 · bez sieci").
 */
@Serializable
data class ListChange(
    val id: String,
    val kind: ListChangeKind,
    val listId: String,
    val itemId: String? = null,
    val madeAt: Long,
    val list: ListInfo? = null,
    val values: ItemValues? = null,
    val fields: List<ItemField> = emptyList(),
    val baseRev: Long = 0,
    val checked: Boolean = false,
    /** Remember the item's unit (and pack size) for this product in the whole community. */
    val rememberUnit: Boolean = false,
    /** [ListChangeKind.ADD_ITEM]: a sub-item of this item; [ListChangeKind.MOVE_ITEM]: the group it goes into (null = out). */
    val parentId: String? = null,
    val access: List<ListAccess> = emptyList(),
    val unit: ListUnit? = null,
)

/** The server's answer to one change; a refused change is dropped by the phone with [message] shown. */
@Serializable
data class ChangeResult(val id: String, val ok: Boolean, val message: String? = null, val queued: Boolean = false)

/**
 * A community's own unit; [forms]: one / two-four / five (e.g. "siatka", "siatki", "siatek"). Empty = never set by
 * anyone, guessed from the name ([Lists.formsOf]).
 */
@Serializable
data class ListUnit(
    val name: String,
    val forms: List<String> = listOf(name, name, name),
    val step: Double = 1.0,
    /** A pack unit ("siatka po 2,5 kg") names the unit of its size here. */
    val packUnit: String? = null,
    val rev: Long = 0,
)

/** A kind of a product used before in the community ("mleko": "3,2%"), offered in the item's "Rodzaj". */
@Serializable
data class ProductVariant(val product: String, val variant: String, val rev: Long = 0)

/** The unit remembered for a product (lower-case name) in the community, offered the next time. */
@Serializable
data class ProductUnit(val product: String, val unit: String, val packSize: String? = null, val rev: Long = 0)

@Serializable
data class ConflictVersion(
    val userId: String,
    val userName: String,
    val values: ItemValues,
    /** When the person changed it on the phone, and when the server got it (later, when they were offline). */
    val madeAt: Long,
    val receivedAt: Long,
)

/**
 * Two (or more) people changed the same [field] of an item at the same time. Until it is settled the list shows the
 * version made later. [resolvedBy]/[resolution]: who settled it and how, so the others get to know.
 */
@Serializable
data class ListConflict(
    val id: String,
    val listId: String,
    val itemId: String,
    val field: ItemField,
    val versions: List<ConflictVersion>,
    val createdAt: Long,
    val resolved: Boolean = false,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    val resolution: String? = null,
    val rev: Long = 0,
)

/** Keep version [keep] (index in [ListConflict.versions]), or type [other] (may be empty), or remove the item. */
@Serializable
data class ConflictDecision(val keep: Int? = null, val other: ItemValues? = null, val removeItem: Boolean = false)

@Serializable
data class ListSyncRequest(val since: Long = 0, val changes: List<ListChange> = emptyList())

/**
 * Everything newer than the phone's [ListSyncRequest.since] (deleted lists and items too, as [ListInfo.deleted]).
 * [listIds]: every list the person may see now; the phone forgets the others (e.g. taken off a list's people).
 */
@Serializable
data class ListSyncResponse(
    val rev: Long,
    val results: List<ChangeResult> = emptyList(),
    val lists: List<ListInfo> = emptyList(),
    val items: List<ListItem> = emptyList(),
    val conflicts: List<ListConflict> = emptyList(),
    val units: List<ListUnit> = emptyList(),
    val products: List<ProductUnit> = emptyList(),
    val listIds: List<String> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    /** The community's memory of things added before (0.7.1), newer than "since"; forgotten ones as [ListMemory.deleted]. */
    val memory: List<ListMemory> = emptyList(),
)

/** Which lists share a memory: things by their kind (shopping is not mixed with packing), tasks, deadlines. */
@Serializable
enum class MemoryScope { SHOPPING, PACKING, CUSTOM, TASKS, DEADLINES }

/**
 * A thing added to a list before, remembered for the whole community ("Z pamięci", board 21): its last parameters
 * ([values] without dates), how often ([uses], only for sorting, never shown) and when it was added last, by whom.
 */
@Serializable
data class ListMemory(
    val scope: MemoryScope,
    val key: String,
    val values: ItemValues,
    val uses: Int = 1,
    val lastUsedAt: Long = 0,
    val lastBy: String? = null,
    val deleted: Boolean = false,
    val rev: Long = 0,
)

object Lists {
    const val MAX_NAME = 200
    const val MAX_NOTE = 2000
    const val MAX_VARIANT = 40
    const val MAX_LINKS = 5
    const val MAX_URL = 2000
    /** 0.7.3 (board 25): per item at most this many photos and this many other files, each up to [MAX_FILE_BYTES]. */
    const val MAX_PHOTOS = 5
    const val MAX_FILES = 5
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    /** All photos and files of one community together. */
    const val COMMUNITY_FILE_BYTES = 1024L * 1024 * 1024
    /** The longer side of a photo the app sends (JPEG, about 1–2 MB). */
    const val PHOTO_SIDE = 3000
    const val PHOTO_MIME = "image/jpeg"

    /** Programs and archives are never taken (board 25); checked by the name's ending and by the type. */
    private val BLOCKED_ENDINGS = setOf("apk", "aab", "xapk", "exe", "msi", "bat", "cmd", "com", "scr", "sh", "jar", "js", "vbs", "ps1",
        "dll", "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "dmg")
    private val BLOCKED_TYPES = setOf("application/vnd.android.package-archive", "application/zip", "application/x-zip-compressed",
        "application/x-rar-compressed", "application/vnd.rar", "application/x-7z-compressed", "application/x-tar", "application/gzip",
        "application/x-msdownload", "application/x-executable", "application/java-archive", "application/x-sh")

    /** Null when the file may be added, else why not (Polish, for the error bar). */
    fun fileRefusal(name: String, mime: String, size: Long): String? {
        val ending = name.substringAfterLast('.', "").lowercase()
        return when {
            size > MAX_FILE_BYTES -> "„$name” ma ${sizeText(size)} — plik może mieć do ${sizeText(MAX_FILE_BYTES)}."
            ending in BLOCKED_ENDINGS || mime.lowercase().substringBefore(';').trim() in BLOCKED_TYPES ->
                "Programów (.apk, .exe) i archiwów (.zip, .rar) nie przyjmujemy."
            size <= 0 -> "„$name” jest pusty."
            else -> null
        }
    }

    /** "240 KB", "1,6 MB". */
    fun sizeText(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 1023) / 1024} KB"
        else -> {
            val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
            if (tenths % 10 == 0L) "${tenths / 10} MB" else "${tenths / 10},${tenths % 10} MB"
        }
    }

    /** "faktura.pdf" of a name typed or picked; no folders, no control characters, at most 120 characters. */
    fun cleanFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').filter { it >= ' ' && it != '\u007F' }.trim()
        val cut = if (base.length <= 120) base else {
            val ending = base.substringAfterLast('.', "").take(10)
            base.take(120 - ending.length - 1) + if (ending.isEmpty()) "" else ".$ending"
        }
        return cut.ifEmpty { "plik" }
    }
    const val MAX_UNIT = 20
    /** After this long without the creator's decision the Owner/Admin may settle a conflict. */
    const val CONFLICT_ESCALATION_MS = 48L * 60 * 60 * 1000

    /** The units every community has; its own ones come on top ([ListUnit]). */
    val BUILT_IN_UNITS = listOf(
        ListUnit("szt.", step = 1.0),
        ListUnit("kg", step = 0.5),
        ListUnit("dag", step = 10.0),
        ListUnit("l", step = 0.5),
        ListUnit("opak.", listOf("opakowanie", "opakowania", "opakowań"), 1.0, packUnit = "szt."),
        ListUnit("siatka", listOf("siatka", "siatki", "siatek"), 1.0, packUnit = "kg"),
        ListUnit("zgrzewka", listOf("zgrzewka", "zgrzewki", "zgrzewek"), 1.0, packUnit = "szt."),
    )

    /** "2 l", "1 zgrzewka", "2 siatki po 2,5 kg", "0,5 kg"; empty without a quantity. */
    fun quantityText(values: ItemValues, units: List<ListUnit> = BUILT_IN_UNITS): String {
        val quantity = values.quantity ?: return ""
        val number = number(quantity)
        val unit = values.unit ?: return number
        val known = units.firstOrNull { it.name == unit }
        val form = known?.let { form(it, quantity) } ?: unit
        val pack = if (known?.packUnit != null && !values.packSize.isNullOrBlank()) " po ${values.packSize} ${known.packUnit}" else ""
        return "$number $form$pack"
    }

    fun number(value: Double): String =
        if (value == Math.floor(value) && value < 1e9) value.toLong().toString() else value.toString().replace('.', ',')

    /** Polish plural: 1 siatka, 2–4 siatki (not 12–14), 5+ siatek; fractions take the "2–4" form (0,5 siatki). */
    fun form(unit: ListUnit, quantity: Double): String {
        val forms = formsOf(unit)
        fun at(i: Int) = forms.getOrElse(i) { forms.last() }
        if (quantity != Math.floor(quantity)) return at(1)
        val n = quantity.toLong()
        return when {
            n == 1L -> at(0)
            n % 10 in 2..4 && n % 100 !in 12..14 -> at(1)
            else -> at(2)
        }
    }

    private val QUANTITY = Regex("""^(.*?)\s+(\d+(?:[.,]\d+)?)\s*(\S+)?$""")

    /**
     * Quick add: "mleko 2" → mleko, 2; "ziemniaki 2 siatki" → ziemniaki, 2 siatka; "szynka 20 dag" → szynka, 20 dag.
     * A word after the number that is no known unit form stays in the name ("bułki 4 duże" → just the name).
     * [remembered]: the product's remembered unit, used when only a number is typed.
     */
    fun quickAdd(text: String, units: List<ListUnit> = BUILT_IN_UNITS, remembered: (String) -> ProductUnit? = { null }): ItemValues {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        val match = QUANTITY.matchEntire(trimmed)
        if (match == null) return withRemembered(ItemValues(name = capitalized(trimmed)), null, remembered)
        val (name, amount, word) = match.destructured
        val quantity = amount.replace(',', '.').toDoubleOrNull()
            ?: return ItemValues(name = capitalized(trimmed))
        if (word.isEmpty()) return withRemembered(ItemValues(name = capitalized(name)), quantity, remembered)
        val unit = unitOf(word, units) ?: return ItemValues(name = capitalized(trimmed))
        return ItemValues(name = capitalized(name), quantity = quantity, unit = unit.name,
            packSize = remembered(productKey(name))?.takeIf { it.unit == unit.name }?.packSize)
    }

    private fun withRemembered(values: ItemValues, quantity: Double?, remembered: (String) -> ProductUnit?): ItemValues {
        val memory = remembered(productKey(values.name))
        return if (memory == null) values.copy(quantity = quantity)
        else values.copy(quantity = quantity ?: 1.0, unit = memory.unit, packSize = memory.packSize)
    }

    private fun unitOf(word: String, units: List<ListUnit>): ListUnit? {
        val w = word.lowercase().trimEnd('.')
        return units.firstOrNull { unit -> (formsOf(unit) + unit.name).any { it.lowercase().trimEnd('.') == w } }
            ?: units.firstOrNull { it.name == "opak." && w in setOf("op", "opak") }
    }

    /**
     * What the person may do on [list]: its creator and the Owner everything; else the person's own row, else
     * their role's row. A list without [ListInfo.access] (made before 0.7): everyone or its members edit everything.
     * [roleMayEdit]: the community's table lets the role add and check items at all (else at most "widzi").
     */
    fun level(list: ListInfo, userId: String, roleId: String?, owner: Boolean, roleMayEdit: Boolean): AccessLevel {
        if (list.createdBy == userId || owner) return AccessLevel.EDIT_ALL
        val level = if (list.access.isEmpty()) {
            if (list.everyone || userId in list.memberIds) AccessLevel.EDIT_ALL else AccessLevel.NONE
        } else {
            list.access.firstOrNull { it.userId == userId }?.level
                ?: list.access.firstOrNull { it.userId == null && it.roleId != null && it.roleId == roleId }?.level
                ?: AccessLevel.NONE
        }
        return if (!roleMayEdit && level.atLeast(AccessLevel.CHECK)) AccessLevel.SEE else level
    }

    /** The unit's forms for 1 / 2–4 / 5+: as saved, or guessed from its name when nobody set them. */
    fun formsOf(unit: ListUnit): List<String> = unit.forms.ifEmpty { guessForms(unit.name) }

    /**
     * Forms of a new unit guessed by its ending (board 24): wiaderko → wiaderka, wiaderek; puszka → puszki, puszek;
     * worek → worki, worków. Short forms ("ml", "op.") stay the same. The person corrects what sounds wrong.
     */
    fun guessForms(word: String): List<String> {
        val t = word.trim()
        if (t.isEmpty()) return listOf("", "", "")
        if (t.endsWith(".") || t.none { it.lowercaseChar() in "aąeęioóuy" }) return listOf(t, t, t)
        fun cut(n: Int) = t.dropLast(n)
        return when {
            t.endsWith("ko") -> listOf(t, cut(1) + "a", cut(2) + "ek")
            t.endsWith("ka") -> listOf(t, cut(1) + "i", cut(2) + "ek")
            t.endsWith("ga") -> listOf(t, cut(1) + "i", cut(1))
            t.endsWith("ja") -> listOf(t, cut(1) + "e", cut(1) + "i")
            t.endsWith("a") -> listOf(t, cut(1) + "y", cut(1))
            t.endsWith("o") -> listOf(t, cut(1) + "a", cut(1))
            t.endsWith("ek") -> listOf(t, cut(2) + "ki", cut(2) + "ków")
            t.endsWith("k") || t.endsWith("g") -> listOf(t, t + "i", t + "ów")
            listOf("sz", "cz", "rz", "ż", "c", "l", "j").any { t.endsWith(it) } -> listOf(t, t + "e", t + "y")
            else -> listOf(t, t + "y", t + "ów")
        }
    }

    /** "allegro.pl" of "https://www.allegro.pl/oferta/…". */
    fun domain(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').removePrefix("www.")

    /** A link that can be opened: http or https with a host. */
    fun isLink(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) &&
            domain(t).contains('.') && t.length <= MAX_URL && t.none { it.isWhitespace() }
    }

    /** The first web address in [text] (a note), if any. */
    fun linkIn(text: String): String? = Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ';')?.takeIf { isLink(it) }

    /**
     * What a person may do on an item: as on the list, narrowed by "Inny dostęp do tego zadania" of [top] (the item
     * itself, or the item a sub-item belongs to). The list's creator and the Owner keep everything.
     */
    fun itemLevel(list: ListInfo, top: ListItem?, userId: String, roleId: String?, owner: Boolean, roleMayEdit: Boolean): AccessLevel {
        val onList = level(list, userId, roleId, owner, roleMayEdit)
        val access = top?.access.orEmpty()
        if (access.isEmpty() || list.createdBy == userId || owner) return onList
        val own = level(list.copy(access = access, createdBy = ""), userId, roleId, owner = false, roleMayEdit = roleMayEdit)
        return if (own.ordinal < onList.ordinal) own else onList
    }

    /** "Mleko 3,2%" and "mleko  3,2 %" are the same thing on a list (a duplicate). */
    fun sameThing(a: ItemValues, b: ItemValues): Boolean =
        productKey(a.name) == productKey(b.name) && productKey(a.variant).replace(" ", "") == productKey(b.variant).replace(" ", "")

    /** Grams or millilitres in one [unit] (kg/dag/g, l/ml); null for units that cannot be turned into each other. */
    private fun base(unit: String?): Pair<String, Double>? = when (unit?.trimEnd('.')) {
        "kg" -> "g" to 1000.0
        "dag" -> "g" to 10.0
        "g" -> "g" to 1.0
        "l" -> "ml" to 1000.0
        "ml" -> "ml" to 1.0
        else -> null
    }

    /**
     * [to] with [add]'s amount added, in [to]'s unit (2 l + 500 ml = 2,5 l); null when the units do not add up.
     * Without amounts it counts pieces (no amount = 1).
     */
    fun sum(to: ItemValues, add: ItemValues): ItemValues? {
        val a = to.quantity ?: 1.0
        val b = add.quantity ?: 1.0
        if (to.unit == add.unit && (to.packSize ?: "") == (add.packSize ?: "")) {
            return to.copy(quantity = round(a + b), unit = to.unit ?: add.unit.takeIf { to.quantity == null && add.quantity != null })
        }
        if (to.unit == null && to.quantity == null) return add.copy(name = to.name, variant = to.variant, quantity = round(a + b))
        val (kindA, factorA) = base(to.unit) ?: return null
        val (kindB, factorB) = base(add.unit) ?: return null
        if (kindA != kindB) return null
        return to.copy(quantity = round(a + b * factorB / factorA))
    }

    private fun round(value: Double) = Math.round(value * 1000) / 1000.0

    fun scopeOf(list: ListInfo): MemoryScope = when (list.type) {
        ListType.TASKS -> MemoryScope.TASKS
        ListType.DEADLINES -> MemoryScope.DEADLINES
        ListType.SHOPPING -> when (list.look.preset) {
            ListPreset.SHOPPING -> MemoryScope.SHOPPING
            ListPreset.PACKING -> MemoryScope.PACKING
            ListPreset.CUSTOM -> MemoryScope.CUSTOM
        }
    }

    /** The memory's key of a thing: name and kind ("mleko|3,2%"), like [sameThing]. */
    fun memoryKey(values: ItemValues): String = productKey(values.name) + "|" + productKey(values.variant).replace(" ", "")

    /** What the memory keeps of an item: no dates, reminders, "pilne" or links (they are chosen each time). */
    fun memoryValues(values: ItemValues): ItemValues = values.copy(dueDate = null, urgent = false, reminders = emptyList(), links = emptyList())

    /** For searching: lower case without Polish letters ("Żółty" → "zolty"). */
    fun searchKey(text: String): String = buildString {
        for (c in text.lowercase()) append(PLAIN[c] ?: c)
    }

    private val PLAIN = mapOf('ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z')

    /** How a product is remembered: lower case, single spaces. */
    fun productKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun capitalized(text: String) = text.replaceFirstChar { it.uppercase() }
}
