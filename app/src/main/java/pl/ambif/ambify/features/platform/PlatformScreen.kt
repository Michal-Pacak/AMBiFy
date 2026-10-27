package pl.ambif.ambify.features.platform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/**
 * Only for the Platform Admin: every community on the server; premium modules and features granted
 * free of charge, a free founding of one more community, a password reset code for an Owner.
 */
@Composable
fun PlatformScreen() {
    val session = LocalAppSession.current
    val api = session.api
    val scope = rememberCoroutineScope()
    var communities by remember { mutableStateOf<List<PlatformCommunity>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<AccountDialog.Code?>(null) }

    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                communities = api.platformCommunities()
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
    }

    LaunchedEffect(Unit) { act {} }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val selected = communities.firstOrNull { it.id == selectedId }
            if (selected == null) {
                ScreenTitle("Platform Admin", "Wszystkie społeczności na serwerze", onBack = null)
                notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Szukaj społeczności lub Ownera") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = communities.filter {
                    search.isBlank() || it.name.contains(search, true) || it.ownerName.orEmpty().contains(search, true)
                }
                LuxurySectionTitle("Społeczności (${shown.size})", modifier = Modifier.padding(start = 4.dp))
                shown.forEach { community -> CommunityRow(community) { selectedId = community.id } }
                EmailAction(
                    title = "Pozwól założyć kolejną społeczność za darmo",
                    info = "Ta osoba może bez opłaty założyć jedną kolejną społeczność.",
                    action = "Nadaj",
                ) { email -> act("$email może założyć kolejną społeczność.") { api.addFoundingCredit(email) } }
                EmailAction(
                    title = "Kod resetu hasła dla Ownera",
                    info = "Gdy Owner zapomni hasła, nikt w jego społeczności nie może mu dać kodu.",
                    action = "Utwórz kod",
                ) { email ->
                    act {
                        val reset = api.platformResetCode(email)
                        code = AccountDialog.Code(
                            title = "Kod resetu: $email",
                            text = "Wybiera na ekranie logowania „Nie pamiętam hasła” i wpisuje ten kod. Ważny 24 godziny.",
                            code = reset.code,
                            share = null,
                        )
                    }
                }
            } else {
                ScreenTitle(selected.name, "Platform Admin", onBack = { selectedId = null })
                notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
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
                Text(
                    "Przełącznik nadaje albo odbiera bez opłaty. Moduły basic są zawsze w cenie, ich dodatki premium są pod linkiem.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    code?.let { CodeDialog(it, onShare = null, onDismiss = { code = null }) }
}

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL"))

private fun summary(community: PlatformCommunity): String = buildString {
    append("Owner: ${community.ownerName ?: "brak"} · ${community.members} os.")
    if (community.minors > 0) append(" · ${community.minors} Nieletnich")
}

@Composable
private fun CommunityRow(community: PlatformCommunity, onClick: () -> Unit) {
    LuxuryCard(modifier = Modifier.clickable(onClick = onClick), contentPadding = PaddingValues(0.dp)) {
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
            Text("›", style = MaterialTheme.typography.headlineSmall, color = Gold)
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

@Composable
private fun EmailAction(title: String, info: String, action: String, onSubmit: (String) -> Unit) {
    var email by rememberSaveable(title) { mutableStateOf("") }
    LuxuryCard(contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LuxurySectionTitle(title)
            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("E-mail konta") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            ButtonsRow {
                FilledTonalButton(onClick = { onSubmit(email) }, enabled = email.isNotBlank()) { Text(action) }
            }
        }
    }
}
