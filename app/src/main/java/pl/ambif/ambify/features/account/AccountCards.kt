package pl.ambif.ambify.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.features.auth.checkPasswords
import pl.ambif.ambify.features.auth.rememberSubmit
import pl.ambif.ambify.shared.ChangePasswordRequest
import pl.ambif.ambify.shared.ChildProfile
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

internal sealed interface AccountDialog {
    data object ChangePassword : AccountDialog
    data class Code(val title: String, val text: String, val code: String, val share: String?) : AccountDialog
    data class Child(val child: ChildProfile?) : AccountDialog
    data class Confirm(val question: String, val action: String, val onConfirm: () -> Unit) : AccountDialog
    /** One text field, e.g. the name of a new community or an invitation code. */
    data class Input(
        val title: String,
        val label: String,
        val info: String?,
        val action: String,
        val code: Boolean,
        val onSave: suspend (String) -> Unit,
    ) : AccountDialog
}

internal val ChildColors = listOf("#8EC3F0", "#9AD7A4", "#E3A8C0", "#F2C27B", "#B9A7EE", "#7FD3CF", "#F29C8B", "#C9C9CF")
private val MemberColors = listOf(Color(0xFFE3A8C0), Color(0xFFB9A7EE), Color(0xFF7FD3CF), Color(0xFFF29C8B))

internal fun Role.label() = when (this) {
    Role.OWNER -> "Owner"
    Role.ADMIN -> "Admin"
    Role.MEMBER -> "User"
}

/** The current community, switching to another one, founding or joining one, and its modules screen. */
@Composable
internal fun CommunityCard(
    me: Me,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpenModules: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AccountCard("Społeczność") {
        Box {
            OutlinedButton(onClick = { menuOpen = true }, border = BorderStroke(1.5.dp, Gold)) {
                Text(me.familyName ?: "", color = Gold, fontWeight = FontWeight.Medium)
                Icon(AppIcons.ExpandMore, contentDescription = "Zmień społeczność", tint = Gold)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                me.communities.forEach { community ->
                    DropdownMenuItem(
                        text = { Text(community.name) },
                        leadingIcon = { if (community.id == me.communityId) Icon(AppIcons.Check, contentDescription = "Bieżąca") },
                        trailingIcon = { Text(community.role.label(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            menuOpen = false
                            if (community.id != me.communityId) onSwitch(community.id)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("+ Załóż nową społeczność") }, onClick = {
                    menuOpen = false
                    onCreate()
                })
                DropdownMenuItem(text = { Text("+ Dołącz z kodem") }, onClick = {
                    menuOpen = false
                    onJoin()
                })
            }
        }
        me.role?.let { Text("Twoja rola: ${it.label()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ButtonsRow {
            FilledTonalButton(onClick = onOpenModules) { Text("Moduły społeczności") }
        }
    }
}

@Composable
internal fun ProfileCard(me: Me, onChangePassword: () -> Unit, onLogout: () -> Unit) {
    AccountCard("Ty") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(me.name, Gold)
            Column {
                Text(me.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ButtonsRow {
            FilledTonalButton(onClick = onChangePassword) { Text("Zmień hasło") }
            FilledTonalButton(onClick = onLogout) { Text("Wyloguj") }
        }
    }
}

@Composable
internal fun FamilyCard(
    family: Family,
    me: Me,
    onInvite: () -> Unit,
    onAddChild: () -> Unit,
    onEditChild: (ChildProfile) -> Unit,
    onDeleteChild: (ChildProfile) -> Unit,
    onSignOutMember: (Member) -> Unit,
    onResetPassword: (Member) -> Unit,
    onRemoveMember: (Member) -> Unit,
    onSetRole: (Member, Role) -> Unit,
) {
    val myRole = me.role ?: Role.MEMBER
    val manager = myRole.canManage
    AccountCard("Członkowie") {
        Column {
            family.members.forEachIndexed { index, member ->
                val color = if (member.role == Role.OWNER) Gold else MemberColors[index % MemberColors.size]
                // Owner and Admin manage only people with a lower role; only the Owner changes roles.
                val actions = if (manager && member.role.rank < myRole.rank) {
                    buildList {
                        if (myRole == Role.OWNER) {
                            if (member.role == Role.ADMIN) add("Zmień rolę na User" to { onSetRole(member, Role.MEMBER) })
                            else add("Nadaj rolę Admin" to { onSetRole(member, Role.ADMIN) })
                        }
                        add("Wyloguj urządzenia" to { onSignOutMember(member) })
                        add("Resetuj hasło" to { onResetPassword(member) })
                        add("Usuń ze społeczności" to { onRemoveMember(member) })
                    }
                } else {
                    emptyList()
                }
                PersonRow(member.name, member.role.label(), color, actions)
            }
            family.children.forEach { child ->
                val actions = if (manager) listOf("Edytuj" to { onEditChild(child) }, "Usuń" to { onDeleteChild(child) }) else emptyList()
                PersonRow(child.name, "Nieletni", parseColor(child.color), actions)
            }
        }
        if (manager) {
            ButtonsRow {
                FilledTonalButton(onClick = onInvite) { Text("+ Zaproś osobę") }
                FilledTonalButton(onClick = onAddChild) { Text("+ Dodaj nieletniego") }
            }
        }
    }
}

@Composable
internal fun InvitationsCard(invitations: List<Invitation>, onShare: (Invitation) -> Unit, onCancel: (Invitation) -> Unit) {
    AccountCard("Zaproszenia") {
        invitations.forEach { invitation ->
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invitation.code, fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 3.sp)
                    Text("ważny do ${formatDay(invitation.expiresAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onShare(invitation) }) { Text("Udostępnij", color = Gold) }
                TextButton(onClick = { onCancel(invitation) }) { Text("Anuluj", color = Gold) }
            }
        }
    }
}

@Composable
internal fun DevicesCard(devices: List<Device>, onSignOut: (Device) -> Unit) {
    AccountCard("Moje urządzenia") {
        devices.forEach { device ->
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (device.current) "ten telefon" else lastUsed(device.lastUsedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!device.current) TextButton(onClick = { onSignOut(device) }) { Text("Wyloguj", color = Gold) }
            }
        }
    }
}

@Composable
internal fun ChangePasswordDialog(onDismiss: () -> Unit, onSave: suspend (ChangePasswordRequest) -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit {
        checkPasswords(new, repeat)
        onSave(ChangePasswordRequest(current, new))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zmień hasło") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordInput("Obecne hasło", current) { current = it }
                PasswordInput("Nowe hasło (min. 8 znaków)", new) { new = it }
                PasswordInput("Powtórz nowe hasło", repeat) { repeat = it }
                submit.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = submit.run, enabled = !submit.busy) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
internal fun CodeDialog(dialog: AccountDialog.Code, onShare: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    dialog.code,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 32.sp,
                    letterSpacing = 6.sp,
                    color = Gold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(dialog.text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
        dismissButton = onShare?.let { share -> { TextButton(onClick = share) { Text("Udostępnij") } } },
    )
}

@Composable
internal fun ChildDialog(child: ChildProfile?, onDismiss: () -> Unit, onSave: suspend (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(child?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(child?.color ?: ChildColors.first()) }
    val submit = rememberSubmit { onSave(name, color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (child == null) "Dodaj nieletniego" else "Edytuj profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Imię") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Text("Kolor (np. w kalendarzu)", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChildColors.forEach { option ->
                        val selected = option == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(parseColor(option), CircleShape)
                                .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = option }
                                .semantics { contentDescription = if (selected) "Kolor $option, wybrany" else "Kolor $option" },
                        )
                    }
                }
                submit.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = submit.run, enabled = !submit.busy) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
internal fun InputDialog(dialog: AccountDialog.Input, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    val submit = rememberSubmit { dialog.onSave(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dialog.info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = if (dialog.code) it.uppercase().filter(Char::isLetterOrDigit).take(8) else it },
                    label = { Text(dialog.label) },
                    singleLine = true,
                    textStyle = if (dialog.code) TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 4.sp) else TextStyle.Default,
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (dialog.code) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                        keyboardType = if (dialog.label.contains("e-mail", ignoreCase = true)) KeyboardType.Email else KeyboardType.Text,
                    ),
                )
                submit.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = submit.run, enabled = !submit.busy) { Text(dialog.action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
internal fun ConfirmDialog(dialog: AccountDialog.Confirm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.question) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                dialog.onConfirm()
            }) { Text(dialog.action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// --- building blocks ---

@Composable
internal fun AccountCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    LuxuryCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        LuxurySectionTitle(title)
        content()
    }
}

@Composable
internal fun ColumnScope.ButtonsRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.align(Alignment.End),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun PersonRow(name: String, detail: String, color: Color, actions: List<Pair<String, () -> Unit>>) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(name, color)
        // Detail under the name: with large phone fonts a side-by-side detail squeezed names into two lines.
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.More, contentDescription = "Opcje: $name")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menuOpen = false
                            action()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, color: Color) {
    Box(modifier = Modifier.size(36.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun PasswordInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

private fun lastUsed(millis: Long): String {
    val days = ((System.currentTimeMillis() - millis) / (24 * 60 * 60 * 1000L)).toInt()
    return when {
        days <= 0 -> "dziś"
        days == 1 -> "wczoraj"
        else -> "$days dni temu"
    }
}
