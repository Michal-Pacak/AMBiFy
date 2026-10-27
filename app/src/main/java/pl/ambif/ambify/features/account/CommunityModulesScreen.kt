package pl.ambif.ambify.features.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.Tier
import pl.ambif.ambify.ui.icons.AppIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCardShape
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/**
 * Modules of one community (opened from its popup in Ustawienia → Społeczności): available ones have a switch
 * (the Owner and Admins use it), the others a "Kup" button for the Owner, who alone pays. How a module became
 * available is not shown here. A change in the current community updates the module bar at once.
 */
@Composable
fun CommunityModulesScreen(communityId: String, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = remember(communityId) { session.api.forCommunity(communityId) }
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val community = me.communities.firstOrNull { it.id == communityId }
    var modules by remember { mutableStateOf<CommunityModules?>(null) }
    val manager = community?.role?.canManage == true
    val owner = community?.role == Role.OWNER
    var notice by remember { mutableStateOf<String?>(null) }
    val buy = { label: String -> notice = "Zakup modułu $label będzie dostępny wkrótce." }

    fun show(answer: CommunityModules) {
        modules = answer
        if (communityId == me.communityId) session.showModules(answer)
    }

    LaunchedEffect(communityId) {
        try {
            show(api.modules())
        } catch (e: ServerError) {
            notice = e.message
        }
    }

    fun switch(module: ModuleId, enabled: Boolean) {
        scope.launch {
            try {
                show(api.setModule(module, enabled))
                notice = null
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle("Moduły", community?.name ?: "", onBack)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
            val current = modules ?: return@Column

            for ((title, tier) in listOf("Basic · w cenie" to Tier.BASIC, "Premium" to Tier.PREMIUM)) {
                AccountCard(title) {
                    ModuleId.entries.filter { it.tier == tier }.forEachIndexed { index, module ->
                        if (index > 0) LuxuryDivider()
                        val extras = Feature.of(module)
                        val link: (@Composable () -> Unit)? = if (extras.isEmpty()) null else {
                            {
                                ExtrasLink(module, extras, current.ownedFeatures.count { it in extras }) { close ->
                                    extras.forEachIndexed { i, feature ->
                                        if (i > 0) LuxuryDivider()
                                        when {
                                            feature in current.features -> AvailableRow(feature.label, active = true)
                                            feature in current.ownedFeatures -> AvailableRow(feature.label, active = false)
                                            else -> BuyRow(feature.label, owner) {
                                                close()
                                                buy(feature.fullLabel)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (module in current.owned) {
                            SwitchRow(module.label, module in current.enabled, enabled = manager, below = link) { switch(module, it) }
                        } else {
                            BuyRow(module.label, owner, below = link) { buy(module.label) }
                        }
                    }
                }
            }
            Text(
                when {
                    owner -> "Wyłączony moduł znika z paska u wszystkich członków społeczności. Moduły i dodatki premium kupuje Owner."
                    manager -> "Wyłączony moduł znika z paska u wszystkich członków społeczności. Moduły premium kupuje Owner."
                    else -> "Moduły włączają i wyłączają Owner i Admin."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Screen title; without [onBack] (a screen opened from the module bar) there is no back arrow. */
@Composable
internal fun ScreenTitle(title: String, subtitle: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Wróć") }
        else Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A row with a switch; [below] goes under the label (e.g. the "Dodatki premium" link). */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    below: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            below?.invoke()
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Luxury.GoldButton,
                checkedThumbColor = Luxury.CardBottom,
                checkedBorderColor = Luxury.GoldLight,
            ),
        )
    }
}

/**
 * The small "Dodatki premium · 1 z 3 ›" link under a module name; a tap opens a popup with the extras
 * and their status, closed by a tap outside it (or by [content] calling `close`).
 */
@Composable
internal fun ExtrasLink(module: ModuleId, extras: List<Feature>, owned: Int, content: @Composable (close: () -> Unit) -> Unit) {
    var open by rememberSaveable(module) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                // No grey highlight inside a Luxury card: the card itself answers the touch.
                .clickable(interactionSource = null, indication = null, onClickLabel = "Pokaż dodatki premium") { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dodatki premium · $owned z ${extras.size}", color = Luxury.GoldLink, fontSize = 12.sp)
            Icon(AppIcons.Forward, contentDescription = null, tint = Luxury.GoldLink, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(310.dp),
            shape = LuxuryCardShape,
            containerColor = Luxury.PopupTop,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Luxury.PopupBorderBrush),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                LuxurySectionTitle("${module.label} · dodatki premium", modifier = Modifier.padding(vertical = 8.dp))
                content { open = false }
            }
        }
    }
}

/** Not available: the Owner gets "Kup", everyone else sees that only the Owner can buy it. */
@Composable
private fun BuyRow(label: String, owner: Boolean, below: (@Composable () -> Unit)? = null, onBuy: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            below?.invoke()
        }
        if (owner) {
            OutlinedButton(
                onClick = onBuy,
                modifier = Modifier.height(36.dp),
                border = BorderStroke(1.dp, Luxury.GoldLink),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Luxury.GoldBase.copy(alpha = 0.08f), contentColor = Luxury.GoldText),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) { Text("Kup", fontWeight = FontWeight.Medium) }
        } else {
            Text("niedostępny", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A premium extra the community has: it works whenever its module is on, so it has no switch of its own.
 * While the module is off ([active] false) the row is greyed out.
 */
@Composable
private fun AvailableRow(label: String, active: Boolean) {
    val accent = if (active) Luxury.GoldText else Luxury.Disabled
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (active) Luxury.Text else Luxury.Disabled, modifier = Modifier.weight(1f))
        Icon(AppIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text("dostępny", style = MaterialTheme.typography.bodyMedium, color = accent)
    }
}
