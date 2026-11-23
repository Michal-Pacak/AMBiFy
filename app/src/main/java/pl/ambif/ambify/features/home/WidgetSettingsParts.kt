package pl.ambif.ambify.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val PartOutline = Color(0xFF3A3A40)
private val PartDanger = Color(0xFFE79A86)

/** Where a widget's settings window came from, and what it can do with the Dashboard. */
internal class SettingsFrame(
    val isNew: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (up: Boolean) -> Unit,
    val onDismiss: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * The window every newer widget shares (like "Lekcje"): title and module, its own [content], then width, height,
 * moving, a preview and the buttons. [heightNote] explains each height.
 */
@Composable
internal fun WidgetSettingsWindow(
    frame: SettingsFrame,
    title: String,
    module: String,
    half: Boolean,
    onHalf: (Boolean) -> Unit,
    height: WidgetHeight,
    onHeight: (WidgetHeight) -> Unit,
    heightNote: (WidgetHeight) -> String,
    ready: Boolean,
    /** Set when the height does nothing (weather at half width): the options are greyed out and this is the note. */
    heightFixed: String? = null,
    onSave: () -> Unit,
    preview: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LuxuryPopup(onDismiss = frame.onDismiss) {
        Column {
            Text(if (frame.isNew) "Dodaj: „$title”" else "Ustawienia: „$title”", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(module, fontSize = 13.sp, color = Luxury.TextMuted)
        }
        content()
        WidthSetting(half, onHalf)
        LuxurySectionTitle("Wysokość", modifier = Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, PartOutline, RoundedCornerShape(24.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val enabled = heightFixed == null
            WidgetHeight.entries.forEach { option ->
                val on = enabled && option == height
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(21.dp))
                        .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier)
                        .clickable(role = Role.RadioButton, enabled = enabled) { onHeight(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        color = when { on -> Luxury.Surface; enabled -> Luxury.TextMuted; else -> Luxury.Disabled.copy(alpha = 0.4f) },
                    )
                }
            }
        }
        Text(heightFixed ?: heightNote(height), fontSize = 12.sp, color = Luxury.TextMuted)
        if (!frame.isNew) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                LuxurySectionTitle("Przesuń", modifier = Modifier.weight(1f))
                IconButton(onClick = { frame.onMove(true) }, enabled = frame.canMoveUp) {
                    Icon(AppIcons.Up, contentDescription = "Przesuń w górę", tint = if (frame.canMoveUp) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
                IconButton(onClick = { frame.onMove(false) }, enabled = frame.canMoveDown) {
                    Icon(AppIcons.ExpandMore, contentDescription = "Przesuń w dół", tint = if (frame.canMoveDown) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
            }
        }
        LuxurySectionTitle("Podgląd", modifier = Modifier.padding(top = 4.dp))
        WidgetPreview(half, preview)
        Spacer(Modifier.height(4.dp))
        if (frame.isNew) {
            GoldButton("Dodaj na pulpit", enabled = ready, onClick = onSave)
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = frame.onDismiss)
        } else {
            GoldButton("Zapisz", enabled = ready, onClick = onSave)
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .border(1.dp, PartDanger.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = frame.onDelete),
                contentAlignment = Alignment.Center,
            ) { Text("Usuń z pulpitu", color = PartDanger, fontSize = 14.sp) }
        }
    }
}

/**
 * The preview in a widget's settings at the width it will have on the Dashboard: a half widget as wide as half of
 * the Dashboard's row (Michał, 23.11: the half preview was shown on the whole width), a whole one on the whole width.
 */
@Composable
fun WidgetPreview(half: Boolean, content: @Composable () -> Unit) {
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    // DashboardGrid: 16 dp on each side, 12 dp between the halves.
    val halfWidth = ((screen - 32 - 12) / 2).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(if (half) Modifier.width(halfWidth) else Modifier.fillMaxWidth()) { content() }
    }
    if (half) Text("pół szerokości — obok może stać drugi widget", fontSize = 11.sp, color = Luxury.TextMuted)
}
