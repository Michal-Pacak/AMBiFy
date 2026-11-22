package pl.ambif.ambify.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** Font sizes every widget uses, so a half and a whole widget read the same in every module. */
object WidgetText {
    val Name = 15.sp
    val Item = 14.sp
    val Small = 11.sp
}

/**
 * The top of every Dashboard widget: the module ("LISTY", "LEKCJE") with the gear and the gold "open" mark, and
 * under it, on the whole width, what this widget shows (the chosen list, "Moje zadania", "Filip · pn 23.11"), so
 * the name fits also in a half widget. A tap on the module or the name opens it, like the gold mark.
 */
@Composable
fun WidgetHeader(
    module: String,
    name: String?,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    openLabel: String,
    leading: (@Composable () -> Unit)? = null,
    note: (@Composable () -> Unit)? = null,
) {
    val tap = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = interactive, onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        LuxurySectionTitle(module, modifier = Modifier.weight(1f).then(tap).padding(vertical = 8.dp))
        IconButton(onClick = onSettings, enabled = interactive) {
            Icon(AppIcons.Gear, contentDescription = "Ustawienia widgetu", tint = Luxury.TextMuted, modifier = Modifier.size(20.dp))
        }
        if (interactive) LuxuryOpenButton(openLabel, onOpen) else Box(Modifier.size(44.dp))
        Spacer(Modifier.width(4.dp))
    }
    if (name != null || note != null) {
        Column(modifier = Modifier.fillMaxWidth().padding(end = 10.dp).then(tap).padding(bottom = 6.dp)) {
            if (name != null) Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                Text(name, fontSize = WidgetText.Name, fontWeight = FontWeight.Medium, color = Luxury.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            note?.invoke()
        }
    }
}
