package pl.ambif.ambify.features.platform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import pl.ambif.ambify.shared.PlatformAccount
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.AccountCard
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ButtonsRow
import pl.ambif.ambify.features.account.CodeDialog
import pl.ambif.ambify.features.account.ExtrasLink
import pl.ambif.ambify.features.account.Gold
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.features.account.SwitchRow
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.PlatformCommunity
import pl.ambif.ambify.shared.Tier
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/**
 * Only for the Platform Admin: every community on the server; premium modules and features granted
 * free of charge, a free founding of one more community, a password reset code for an Owner.
 */
@Composable
fun PlatformScreen(resetKey: Int = 0, onOpenReports: () -> Unit = {}) {
    val session = LocalAppSession.current
    val api = session.api
    val scope = rememberCoroutineScope()
    var communities by remember { mutableStateOf<List<PlatformCommunity>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<PlatformAccount>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<AccountDialog.Code?>(null) }

    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                communities = api.platformCommunities()
                runCatching { accounts = api.platformAccounts() }
                notice = done
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    /** A grant for the admin's own current community changes their module bar at once. */
    suspend fun grant(communityId: String, key: String, granted: Boolean) {
        api.setGrant(communityId, key, granted)
        session.showModules(api.modules())
        runCatching { session.refreshThemes() }
    }

    LaunchedEffect(Unit) { act {} }
    // Platform Admin tapped again on the module bar: back to the list.
    LaunchedEffect(resetKey) { if (resetKey > 0) selectedId = null }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val selected = communities.firstOrNull { it.id == selectedId }
            if (selected == null) {
                ScreenTitle("Platform Admin", "Wszystkie kręgi na serwerze", onBack = null, help = pl.ambif.ambify.ui.help.Help.PLATFORM)
                notice?.let { pl.ambif.ambify.ui.components.MessageBar(pl.ambif.ambify.ui.components.MessageKind.INFO, it) }
                // 0.7.9: bug reports first (board 4).
                PlatformReportsCard(onOpen = onOpenReports)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Szukaj kręgu lub Ownera") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = communities.filter {
                    search.isBlank() || it.name.contains(search, true) || it.ownerName.orEmpty().contains(search, true)
                }
                LuxurySectionTitle("Kręgi (${shown.size})", modifier = Modifier.padding(start = 4.dp))
                shown.forEach { community -> CommunityRow(community) { selectedId = community.id } }
                suspend fun reload() {
                    communities = api.platformCommunities()
                    runCatching { accounts = api.platformAccounts() }
                }
                // B-003: one card per matter; the hint says what the person has, the button grants or takes back.
                EmailAction(
                    accounts = accounts,
                    title = "Premium konta (motywy)",
                    info = "Kod i Gaming, edytor motywów i tapeta dla tej osoby, w każdym kręgu bez blokady motywu.",
                    badge = { if (it.accountPremium) "MA PREMIUM" to true else "BEZ PREMIUM" to false },
                    ops = { picked ->
                        if (picked?.accountPremium == true) listOf(EmailOp("Odbierz Premium", danger = true) { email ->
                            api.setAccountPremium(email, false); runCatching { session.refreshThemes() }
                            "Odebrano Premium konta: ${picked.name} ($email). Własne motywy zostają zapisane."
                        }) else listOf(EmailOp("Przyznaj") { email ->
                            api.setAccountPremium(email, true); runCatching { session.refreshThemes() }
                            "Przyznano Premium konta: ${picked?.name ?: email}."
                        })
                    },
                    onDone = { reload() },
                )
                EmailAction(
                    accounts = accounts,
                    title = "Darmowy kolejny krąg",
                    info = "Ta osoba może bez opłaty założyć jeden kolejny krąg.",
                    badge = { if (it.foundingCredits > 0) "DARMOWE: ${it.foundingCredits}" to true else null },
                    ops = { picked ->
                        listOfNotNull(
                            EmailOp("Nadaj") { email -> api.addFoundingCredit(email); "${picked?.name ?: email} może założyć kolejny krąg za darmo." },
                            if ((picked?.foundingCredits ?: 0) > 0) EmailOp("Odbierz", danger = true) { email ->
                                api.removeFoundingCredit(email); "Odebrano jeden darmowy krąg: ${picked?.name ?: email}."
                            } else null,
                        )
                    },
                    onDone = { reload() },
                )
                EmailAction(
                    accounts = accounts,
                    title = "Kod resetu hasła dla Ownera",
                    info = "Gdy Owner zapomni hasła, nikt w jego kręgu nie może mu dać kodu.",
                    ops = {
                        listOf(EmailOp("Utwórz kod") { email ->
                            val reset = api.platformResetCode(email)
                            code = AccountDialog.Code(
                                title = "Kod resetu: $email",
                                text = "Wybiera na ekranie logowania „Nie pamiętam hasła” i wpisuje ten kod. Ważny 24 godziny.",
                                code = reset.code,
                                share = null,
                            )
                            "Utworzono kod resetu dla $email."
                        })
                    },
                    onDone = {},
                )
            } else {
                ScreenTitle(selected.name, "Platform Admin", onBack = { selectedId = null }, help = pl.ambif.ambify.ui.help.Help.PLATFORM)
                notice?.let { pl.ambif.ambify.ui.components.MessageBar(pl.ambif.ambify.ui.components.MessageKind.INFO, it) }
                AccountCard("Informacje") {
                    Text(summary(selected), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Założona ${dateFormat.format(Date(selected.createdAt))} · " + if (selected.paidFounding) "kolejna, opłacona lub nadana" else "pierwsza, darmowa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccountCard("Moduły") {
                    ModuleId.entries.forEachIndexed { index, module ->
                        if (index > 0) LuxuryDivider()
                        val extras = Feature.of(module)
                        val link: (@Composable () -> Unit)? = if (extras.isEmpty()) null else {
                            {
                                ExtrasLink(module, extras, extras.count { it.name in selected.grants }) {
                                    extras.forEachIndexed { i, feature ->
                                        if (i > 0) LuxuryDivider()
                                        GrantRow(feature.label, feature.name, selected) { key, granted -> act { grant(selected.id, key, granted) } }
                                    }
                                }
                            }
                        }
                        if (module.tier == Tier.BASIC) {
                            IncludedRow(module.label, link)
                        } else {
                            SwitchRow(module.label, module.name in selected.grants, enabled = true, below = link) {
                                act { grant(selected.id, module.name, it) }
                            }
                        }
                    }
                }
                AccountCard("Wygląd") {
                    SwitchRow(
                        "Premium kręgu: motywy", pl.ambif.ambify.shared.Themes.COMMUNITY_GRANT in selected.grants, enabled = true,
                        below = { Text("Kod, Gaming, motywy kręgu i tapeta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    ) { act { grant(selected.id, pl.ambif.ambify.shared.Themes.COMMUNITY_GRANT, it) } }
                }
            }
        }
    }

    code?.let { CodeDialog(it, onShare = null, onDismiss = { code = null }) }
}

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL"))

private fun summary(community: PlatformCommunity): String = buildString {
    append("Owner: ${community.ownerName ?: "brak"} · ${community.members} os.")
}

@Composable
private fun CommunityRow(community: PlatformCommunity, onClick: () -> Unit) {
    LuxuryCard(contentPadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(community.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(summary(community), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (community.grants.isEmpty()) "basic" else "basic + ${community.grants.size} premium",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold,
                )
            }
            LuxuryOpenButton("Otwórz: ${community.name}", onClick = onClick, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** A basic module: always included, so no switch; its premium extras are under [below]. */
@Composable
private fun IncludedRow(label: String, below: (@Composable () -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            below?.invoke()
        }
        Text("w cenie", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GrantRow(label: String, key: String, community: PlatformCommunity, onChange: (String, Boolean) -> Unit) {
    SwitchRow(label, key in community.grants, enabled = true) { onChange(key, it) }
}

/** One action on an account by e-mail; the button can run [EmailOp.run], whose text is the success message. */
private class EmailOp(val label: String, val danger: Boolean = false, val run: suspend (String) -> String)

/**
 * A card that acts on an account by e-mail (B-003): hints after every letter (with [badge]: what the person has),
 * the buttons from [ops] for the picked account; its own message in the card. Success clears the field; an error
 * keeps it. The message is gone after the next letter and when the screen is left (plain remember).
 */
@Composable
private fun EmailAction(
    accounts: List<PlatformAccount>,
    title: String,
    info: String,
    badge: (PlatformAccount) -> Pair<String, Boolean>? = { null },
    ops: (PlatformAccount?) -> List<EmailOp>,
    onDone: suspend () -> Unit,
) {
    var email by remember(title) { mutableStateOf("") }
    var result by remember(title) { mutableStateOf<Pair<pl.ambif.ambify.ui.components.MessageKind, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val hintsInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LuxuryCard(contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LuxurySectionTitle(title)
            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result?.let { (kind, text) -> pl.ambif.ambify.ui.components.MessageBar(kind, text) }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim(); result = null },
                label = { Text("E-mail konta") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            // After every letter: the accounts whose e-mail or name has it (tap = fill in the e-mail).
            val hints = if (email.isBlank()) emptyList() else accounts.filter {
                it.email.contains(email, true) || it.name.contains(email, true)
            }.filterNot { it.email.equals(email, true) }.take(6)
            // The hints show up under the field: scroll so they are above the keyboard.
            LaunchedEffect(hints.map { it.email }) { if (hints.isNotEmpty()) { kotlinx.coroutines.delay(150); hintsInView.bringIntoView() } }
            Column(modifier = Modifier.bringIntoViewRequester(hintsInView)) {
                hints.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            // The keyboard first finishes the word being typed (else it would write it back over the hint).
                            focus.clearFocus()
                            email = account.email
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.email, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (listOf(account.name) + account.communities).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        badge(account)?.let { (text, on) -> Badge(text, on) }
                    }
                }
            }
            val picked = accounts.firstOrNull { it.email.equals(email, true) }
            if (picked != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((listOf(picked.name) + picked.communities).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    badge(picked)?.let { (text, on) -> Badge(text, on) }
                }
            } else if (email.isNotBlank() && hints.isEmpty()) {
                Text("Brak konta pasującego do „$email”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ButtonsRow {
                ops(picked).forEach { op ->
                    FilledTonalButton(
                        enabled = email.isNotBlank() && !busy,
                        colors = if (op.danger) androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(contentColor = pl.ambif.ambify.ui.theme.Luxury.Danger) else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
                        onClick = {
                            focus.clearFocus()
                            busy = true
                            scope.launch {
                                try {
                                    val text = op.run(email)
                                    result = pl.ambif.ambify.ui.components.MessageKind.SUCCESS to text
                                    email = ""
                                    onDone()
                                } catch (e: ServerError) {
                                    result = pl.ambif.ambify.ui.components.MessageKind.ERROR to (e.message ?: "Nie udało się.")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(op.label) }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, on: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1,
        color = if (on) pl.ambif.ambify.ui.theme.Luxury.OnGold else pl.ambif.ambify.ui.theme.Luxury.TextMuted,
        modifier = Modifier.clip(shape)
            .then(if (on) Modifier.background(pl.ambif.ambify.ui.theme.Luxury.GoldFill) else Modifier.border(1.dp, pl.ambif.ambify.ui.theme.Luxury.Outline, shape))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
