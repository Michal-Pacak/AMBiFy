package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/** How a role may do an action: at once, after an Admin's or the Owner's approval, or not at all. */
@Serializable
enum class Mode { ALLOWED, APPROVAL, DENIED }

/**
 * The catalog of actions the Owner sets per role (docs/08_Permissions.md, the table people see).
 * [top] and [bottom] are the defaults for the highest and the lowest role under the Owner; a role between
 * them starts "in between" ([Permissions.defaultFor]). [ownerOnly] actions belong to the Owner alone and
 * cannot be given away; an action that is not [approvable] has no "after approval" mode.
 * Grows with every module: a new module adds its actions here.
 */
@Serializable
enum class Action(
    val module: ModuleId?,
    val label: String,
    val scope: String,
    val top: Mode,
    val bottom: Mode,
    val ownerOnly: Boolean = false,
    val approvable: Boolean = true,
) {
    INVITE(null, "Zapraszanie osób", "kod zaproszenia do społeczności", Mode.ALLOWED, Mode.DENIED, approvable = false),
    ASSIGN_ROLES(null, "Nadawanie i odbieranie ról", "tylko rolom niższym od własnej", Mode.ALLOWED, Mode.DENIED, approvable = false),
    REMOVE_MEMBERS(null, "Usuwanie osób, wylogowanie, reset hasła", "tylko osobom z ról niższych", Mode.ALLOWED, Mode.DENIED, approvable = false),
    SWITCH_MODULES(null, "Włączanie i wyłączanie modułów", "posiadanych przez społeczność", Mode.ALLOWED, Mode.DENIED, approvable = false),
    RENAME_COMMUNITY(null, "Zmiana nazwy społeczności", "", Mode.DENIED, Mode.DENIED, ownerOnly = true),
    MANAGE_ROLES(null, "Tworzenie i zmiana ról", "nazwy i kolejność ról", Mode.DENIED, Mode.DENIED, ownerOnly = true),
    EDIT_PERMISSIONS(null, "Zmiana tej tabeli uprawnień", "", Mode.DENIED, Mode.DENIED, ownerOnly = true),
    BUY_PREMIUM(null, "Kupowanie modułów premium", "płaci Owner", Mode.DENIED, Mode.DENIED, ownerOnly = true),

    CALENDAR_VIEW(ModuleId.CALENDAR, "Przeglądanie wydarzeń", "całej społeczności", Mode.ALLOWED, Mode.ALLOWED),
    CALENDAR_ADD_SELF(ModuleId.CALENDAR, "Dodawanie wydarzeń", "dla siebie", Mode.ALLOWED, Mode.ALLOWED),
    CALENDAR_ADD_OTHERS(ModuleId.CALENDAR, "Dodawanie wydarzeń", "dla wybranych osób", Mode.ALLOWED, Mode.APPROVAL),
    CALENDAR_ADD_ALL(ModuleId.CALENDAR, "Dodawanie wydarzeń", "dla wszystkich", Mode.ALLOWED, Mode.APPROVAL),
    CALENDAR_EDIT_OTHERS(ModuleId.CALENDAR, "Edycja i usuwanie cudzych wydarzeń", "", Mode.ALLOWED, Mode.DENIED),
    CALENDAR_GOOGLE(ModuleId.CALENDAR, "Synchronizacja z Kalendarzem Google", "własnym", Mode.ALLOWED, Mode.ALLOWED),

    TIMETABLE_VIEW_SELF(ModuleId.TIMETABLE, "Przeglądanie planu", "własnego", Mode.ALLOWED, Mode.ALLOWED, approvable = false),
    TIMETABLE_VIEW_ALL(ModuleId.TIMETABLE, "Przeglądanie planu", "wszystkich uczniów w społeczności", Mode.ALLOWED, Mode.DENIED, approvable = false),
    TIMETABLE_EXTRA_SELF(ModuleId.TIMETABLE, "Dodawanie zajęć dodatkowych", "dla siebie", Mode.ALLOWED, Mode.ALLOWED),
    TIMETABLE_EXTRA_OTHERS(ModuleId.TIMETABLE, "Dodawanie zajęć dodatkowych", "dla innych osób", Mode.ALLOWED, Mode.DENIED),
    TIMETABLE_REMIND_OTHERS(ModuleId.TIMETABLE, "Ustawianie przypomnień", "innym osobom", Mode.ALLOWED, Mode.APPROVAL),
    TIMETABLE_LINK_VULCAN(ModuleId.TIMETABLE, "Łączenie z dziennikiem szkolnym", "eduVulcan, uczniowie i konta", Mode.ALLOWED, Mode.DENIED, approvable = false),

    LISTS_CREATE(ModuleId.LISTS, "Tworzenie list", "", Mode.ALLOWED, Mode.ALLOWED),
    LISTS_EDIT_ITEMS(ModuleId.LISTS, "Dodawanie i odhaczanie pozycji", "na wspólnych listach", Mode.ALLOWED, Mode.ALLOWED),
    LISTS_DELETE_OTHERS(ModuleId.LISTS, "Usuwanie cudzych list", "", Mode.ALLOWED, Mode.DENIED),

    CHAT_WRITE(ModuleId.COMMUNICATION, "Pisanie w czacie społeczności", "", Mode.ALLOWED, Mode.ALLOWED),
    CHAT_DELETE_OTHERS(ModuleId.COMMUNICATION, "Usuwanie cudzych wiadomości", "", Mode.ALLOWED, Mode.DENIED),

    MISSIONS_CREATE_SELF(ModuleId.MISSIONS, "Tworzenie misji", "dla siebie", Mode.ALLOWED, Mode.ALLOWED),
    MISSIONS_CREATE_OTHERS(ModuleId.MISSIONS, "Tworzenie misji", "dla innych osób", Mode.ALLOWED, Mode.APPROVAL),
    MISSIONS_COMPLETE(ModuleId.MISSIONS, "Oznaczanie misji jako wykonanej", "własnej", Mode.ALLOWED, Mode.APPROVAL),
    MISSIONS_REWARD(ModuleId.MISSIONS, "Przyznawanie nagród", "", Mode.ALLOWED, Mode.DENIED),
    ;

    companion object {
        fun of(module: ModuleId?): List<Action> = entries.filter { it.module == module }
    }
}

object Permissions {
    /**
     * Default mode of [action] for the role at [position] (0 = highest) among [count] roles under the Owner.
     * A role between the highest and the lowest starts in between: where the highest may and the lowest may not,
     * after approval (or not at all when the action has no approval); where the lowest needs approval, at once.
     */
    fun defaultFor(action: Action, position: Int, count: Int): Mode = when {
        action.ownerOnly -> Mode.DENIED
        position <= 0 -> action.top
        position >= count - 1 -> action.bottom
        action.top == action.bottom -> action.top
        action.top == Mode.ALLOWED && action.bottom == Mode.DENIED -> if (action.approvable) Mode.APPROVAL else Mode.DENIED
        action.top == Mode.ALLOWED && action.bottom == Mode.APPROVAL -> Mode.ALLOWED
        else -> action.bottom
    }

    /** What [role] may do: the Owner everything; others the Owner's choice, else the default for their place. */
    fun effective(action: Action, role: Role, roles: List<Role>, overrides: Map<Pair<String, Action>, Mode>): Mode {
        if (role.owner) return Mode.ALLOWED
        if (action.ownerOnly) return Mode.DENIED
        overrides[role.id to action]?.let { return it }
        val ordered = roles.filterNot { it.owner }.sortedByDescending { it.rank }
        return defaultFor(action, ordered.indexOfFirst { it.id == role.id }, ordered.size)
    }
}

object PermissionPaths {
    const val ROLES = "/family/roles"
    const val PERMISSIONS = "/family/permissions"
    const val REQUESTS = "/family/requests"
}

/** One cell of the table: what [roleId] may do with [action] and whether it is still the default. */
@Serializable
data class PermissionCell(val roleId: String, val action: Action, val mode: Mode, val isDefault: Boolean)

@Serializable
data class PermissionTable(val roles: List<Role>, val cells: List<PermissionCell>)

/** A new mode for one cell; null puts the default back. */
@Serializable
data class PermissionChange(val mode: Mode?)

/** A new role, placed right below [belowRoleId] (the Owner's id puts it at the top). */
@Serializable
data class RoleCreate(val name: String, val belowRoleId: String)

@Serializable
data class RoleRename(val name: String)

@Serializable
data class RoleMove(val up: Boolean)

/** An action someone may do only after approval, waiting for an Admin or the Owner. */
@Serializable
data class PendingRequest(
    val id: String,
    val action: Action,
    val requesterName: String,
    /** What will happen when approved, in words, e.g. "Basen dla: Filip, co tydzień w środę". */
    val summary: String,
    val createdAt: Long,
)

/** The answer to an action that needs approval: it is waiting in the queue, nothing happened yet. */
@Serializable
data class RequestQueued(val requestId: String, val message: String)
