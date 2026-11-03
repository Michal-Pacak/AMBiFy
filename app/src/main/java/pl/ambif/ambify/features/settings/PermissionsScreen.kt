package pl.ambif.ambify.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.AccountCard
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ButtonsRow
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.InputDialog
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.PermissionTable
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val ApprovalColor = Color(0xFFF2C27B)

/**
 * Roles and the permission table of one community. Everyone may look; the Owner adds, renames, orders and removes
 * roles and changes a cell with a tap (allowed → after approval → not allowed). "domyślne" marks cells the Owner
 * never changed.
 */
@Composable
fun PermissionsScreen(communityId: String, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = remember(communityId) { session.api.forCommunity(communityId) }
    val scope = rememberCoroutineScope()
    var table by remember { mutableStateOf<PermissionTable?>(null) }
    var me by remember { mutableStateOf<Me?>(null) }
    var module by rememberSaveable { mutableStateOf<String?>(ModuleId.CALENDAR.name) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                notice = null
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    suspend fun reload() {
        me = api.me()
        table = api.permissionTable()
    }

    LaunchedEffect(communityId) { act { reload() } }

    val owner = me?.role?.owner == true
    SettingsLayout(title = if (owner) "Role i uprawnienia" else "Uprawnienia ról", onBack = onBack, subtitle = me?.familyName ?: "", notice = notice) {
        val current = table ?: return@SettingsLayout
        val roles = current.roles.filterNot { it.owner }

        AccountCard("Role") {
            current.roles.forEachIndexed { index, role ->
                if (index > 0) LuxuryDivider()
                RoleRow(
                    role = role,
                    editable = owner && !role.owner,
                    first = roles.firstOrNull()?.id == role.id,
                    last = roles.lastOrNull()?.id == role.id,
                    onMove = { up -> act { api.moveRole(role.id, up); reload() } },
                    onRename = {
                        dialog = AccountDialog.Input("Zmień nazwę roli", "Nazwa roli", null, "Zapisz", code = false) { name ->
                            api.renameRole(role.id, name)
                            reload()
                            dialog = null
                        }
                    },
                    onDelete = {
                        dialog = AccountDialog.Confirm("Usunąć rolę ${role.name}?", "Usuń") { act { api.deleteRole(role.id); reload() } }
                    },
                )
            }
            if (owner) {
                ButtonsRow {
                    FilledTonalButton(onClick = {
                        // A new role lands just above the lowest one; arrows move it where it belongs.
                        val below = if (roles.size >= 2) roles[roles.size - 2] else current.roles.first { it.owner }
                        dialog = AccountDialog.Input(
                            "Nowa rola", "Nazwa roli, np. Trener",
                            "Nowa rola dostaje uprawnienia „pomiędzy” sąsiednimi rolami. Strzałkami ustawisz ją w hierarchii.",
                            "Dodaj", code = false,
                        ) { name ->
                            api.createRole(name, below.id)
                            reload()
                            dialog = null
                        }
                    }) { Text("+ Dodaj rolę") }
                }
            }
        }

        AccountCard("Co może każda rola") {
            val tabs = listOf<Pair<String?, String>>(null to "Społeczność") + ModuleId.entries
                .filter { m -> Action.of(m).isNotEmpty() }
                .map { it.name to it.label }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { (key, label) ->
                    val on = module == key
                    OutlinedButton(
                        onClick = { module = key },
                        border = BorderStroke(1.dp, if (on) Luxury.GoldLink else Color(0xFF3A3A40)),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(label, color = if (on) Luxury.GoldLight else Luxury.TextMuted, fontSize = 13.sp) }
                }
            }
            val moduleId = module?.let { key -> ModuleId.entries.firstOrNull { it.name == key } }
            Action.of(moduleId).forEach { action ->
                LuxuryDivider()
                Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge)
                    if (action.scope.isNotBlank()) Text(action.scope, style = MaterialTheme.typography.bodySmall, color = Luxury.TextMuted)
                    roles.forEach { role ->
                        val cell = current.cells.first { it.roleId == role.id && it.action == action }
                        ModeRow(
                            roleName = role.name,
                            mode = cell.mode,
                            isDefault = cell.isDefault,
                            ownerOnly = action.ownerOnly,
                            editable = owner && !action.ownerOnly,
                            onTap = {
                                val next = when (cell.mode) {
                                    Mode.ALLOWED -> if (action.approvable) Mode.APPROVAL else Mode.DENIED
                                    Mode.APPROVAL -> Mode.DENIED
                                    Mode.DENIED -> Mode.ALLOWED
                                }
                                act { table = api.setPermission(role.id, action, next) }
                            },
                        )
                    }
                }
            }
            Text(
                "Owner może wszystko. " + if (owner) "Stuknij ustawienie, żeby je zmienić." else "Zmienia je tylko Owner.",
                style = MaterialTheme.typography.bodySmall,
                color = Luxury.TextMuted,
            )
        }
    }

    when (val current = dialog) {
        is AccountDialog.Input -> InputDialog(current, onDismiss = { dialog = null })
        is AccountDialog.Confirm -> ConfirmDialog(current, onDismiss = { dialog = null })
        else -> Unit
    }
}

@Composable
private fun RoleRow(
    role: Role,
    editable: Boolean,
    first: Boolean,
    last: Boolean,
    onMove: (up: Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(role.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (role.owner) FontWeight.Medium else FontWeight.Normal)
            if (role.owner) Text("stała rola, może wszystko", style = MaterialTheme.typography.bodySmall, color = Luxury.TextMuted)
        }
        if (editable) {
            IconButton(onClick = { onMove(true) }, enabled = !first) {
                Icon(AppIcons.ExpandMore, contentDescription = "Wyżej: ${role.name}", modifier = Modifier.size(20.dp).rotate(180f), tint = if (first) Luxury.Disabled else Luxury.GoldText)
            }
            IconButton(onClick = { onMove(false) }, enabled = !last) {
                Icon(AppIcons.ExpandMore, contentDescription = "Niżej: ${role.name}", modifier = Modifier.size(20.dp), tint = if (last) Luxury.Disabled else Luxury.GoldText)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(AppIcons.More, contentDescription = "Opcje: ${role.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Zmień nazwę") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Usuń rolę") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ModeRow(roleName: String, mode: Mode, isDefault: Boolean, ownerOnly: Boolean, editable: Boolean, onTap: () -> Unit) {
    val (label, color) = when {
        ownerOnly -> "tylko Owner" to Luxury.Disabled
        mode == Mode.ALLOWED -> "Dozwolone" to Luxury.GoldText
        mode == Mode.APPROVAL -> "Po zatwierdzeniu" to ApprovalColor
        else -> "Niedozwolone" to Luxury.Disabled
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (editable) Modifier.clickable(onClickLabel = "Zmień: $roleName", onClick = onTap) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(roleName, style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!ownerOnly) Text(if (isDefault) "domyślne" else "zmienione", fontSize = 10.sp, color = if (isDefault) Luxury.Disabled else Luxury.GoldLight)
        }
    }
}

/** Requests waiting for my approval in one community: who asks, for what, and what will happen. */
@Composable
fun RequestsScreen(communityId: String, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = remember(communityId) { session.api.forCommunity(communityId) }
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<pl.ambif.ambify.shared.PendingRequest>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun act(done: String?, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                requests = api.requests()
                notice = done
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    LaunchedEffect(communityId) { act(null) {} }

    SettingsLayout(title = "Prośby do zatwierdzenia", onBack = onBack, notice = notice) {
        val current = requests ?: return@SettingsLayout
        if (current.isEmpty()) {
            Text("Nic nie czeka na zatwierdzenie.", style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted)
        }
        current.forEach { request ->
            AccountCard(request.action.label + (if (request.action.scope.isNotBlank()) " · " + request.action.scope else "")) {
                Text(request.summary, style = MaterialTheme.typography.bodyLarge)
                Text("prosi: ${request.requesterName}", style = MaterialTheme.typography.bodySmall, color = Luxury.TextMuted)
                ButtonsRow {
                    FilledTonalButton(onClick = { act("Odrzucono.") { api.decide(request.id, approve = false) } }) { Text("Odrzuć") }
                    FilledTonalButton(onClick = { act("Zatwierdzono.") { api.decide(request.id, approve = true) } }) { Text("Zatwierdź") }
                }
            }
        }
        LuxurySectionTitle("Skąd się biorą prośby", modifier = Modifier.padding(top = 8.dp))
        Text(
            "Gdy w tabeli uprawnień działanie ma „po zatwierdzeniu”, trafia tutaj do osób, których rola może je wykonać od razu.",
            style = MaterialTheme.typography.bodySmall,
            color = Luxury.TextMuted,
        )
    }
}
