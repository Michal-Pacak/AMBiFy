package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.features.account.Outcome
import pl.ambif.ambify.features.account.PendingTransfer
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxuryPopup

/** The phone's font makes these texts bigger only up to this much (Michał, 23.11: "Witaj" took half the screen). */
private const val MAX_FONT_SCALE = 1.15f

@Composable
private fun capped(size: Float): TextUnit {
    val scale = LocalDensity.current.fontScale
    return (size * minOf(scale, MAX_FONT_SCALE) / scale).sp
}

/**
 * "Witaj, …" and the community under it: at most 2 lines each, then "…". A tap on the community opens the
 * switcher; with one community it goes straight to "Społeczności" in the settings.
 */
@Composable
internal fun HomeHeader(userName: String, communityName: String, switchable: Boolean, onCommunity: () -> Unit) {
    val title = capped(28f)
    val sub = capped(16f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Witaj, $userName", fontSize = title, lineHeight = (title.value * 1.2f).sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = if (switchable) "Zmień społeczność" else "Społeczności", onClick = onCommunity)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                communityName, fontSize = sub, lineHeight = (sub.value * 1.25f).sp, color = Luxury.GoldText,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (switchable) Icon(AppIcons.ExpandMore, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The communities to switch to (board 14): the current one marked, "Opuść" by each (the Owner hands it over first,
 * board 16), a search field from 11 communities on; "Zarządzaj społecznościami" leads to the settings.
 */
@Composable
internal fun CommunitySwitcher(
    me: Me,
    onPick: (CommunitySummary) -> Unit,
    onLeave: (CommunitySummary) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    outcome: Outcome,
) {
    var query by remember { mutableStateOf("") }
    val all = me.communities.sortedByDescending { it.id == me.communityId }
    val searching = all.size > SEARCH_FROM
    val shown = if (query.isBlank()) all else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text(if (searching) "Twoje społeczności · ${all.size}" else "Twoje społeczności", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        if (searching) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Szukaj społeczności") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Color(0xFF3A3A40)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column {
            shown.forEach { community ->
                val current = community.id == me.communityId
                val owner = community.role.owner
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .clickable(role = Role.RadioButton) { if (current) onDismiss() else onPick(community) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, Luxury.GoldBase, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (current) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight)) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(community.name, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(community.role.label() + if (current) " · teraz tutaj" else "", fontSize = 12.sp, color = Luxury.TextMuted)
                    }
                    Text(
                        "Opuść", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LeaveColor,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, LeaveColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClickLabel = "Opuść: ${community.name}") { onLeave(community) }
                            .heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                if (owner) me.transfers.firstOrNull { it.communityId == community.id && !it.forMe }?.let { PendingTransfer(it, outcome) }
                LuxuryDivider()
            }
            if (query.isNotBlank() && shown.size < all.size) {
                Text("+ ${all.size - shown.size} innych ukrytych przez szukanie", fontSize = 12.sp, color = Luxury.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onManage),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Zarządzaj społecznościami", fontSize = 16.sp)
                    Text("załóż nową, dołącz z kodem, przekaż rolę Ownera", fontSize = 12.sp, color = Luxury.TextMuted)
                }
                LuxuryOpenMark(size = 32.dp)
            }
        }
        FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

private const val SEARCH_FROM = 10
private val LeaveColor = Color(0xFFE79A86)
