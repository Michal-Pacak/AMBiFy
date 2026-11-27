package pl.ambif.ambify.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.AppSession
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.ui.components.CircleBadge
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton

/** Colors for a circle's initials; the first (null) is the theme's gold. */
private val CircleColors = listOf(
    null, "#8EC3F0", "#E3A8C0", "#7FD3CF", "#B9A7EE", "#F2C27B", "#9AD7A4", "#F29C8B", "#C9C9CF", "#F7F7F7",
)

/**
 * Kręgi → ⋮ → Awatar kręgu (0.7.8, board 1, the Owner only): the circle's photo from the gallery (cropped to a
 * square), or its initials on a color. Everyone in the circle sees it on the Dashboard and in the circles' list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CircleAvatarDialog(community: CommunitySummary, session: AppSession, onNotice: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cropping by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropping = uri }

    fun run(done: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
                onNotice(done)
            } catch (e: ServerError) {
                onNotice(if (e.code == "network") "Zapiszesz, gdy wróci połączenie." else e.message)
            } finally {
                busy = false
            }
        }
    }

    LuxuryPopup(onDismiss = onDismiss) {
        LuxurySectionTitle("Awatar kręgu")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircleBadge(community.name, community.circle, size = 88.dp)
            Text(community.name, fontSize = 18.sp, fontFamily = Luxury.HeadingFont, modifier = Modifier.weight(1f))
        }
        FramedButton("Zdjęcie z galerii", modifier = Modifier.fillMaxWidth()) {
            if (!busy) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (community.circle?.photo != null) {
            FramedButton("Usuń zdjęcie — wróć do liter", gold = false, modifier = Modifier.fillMaxWidth()) {
                if (!busy) run("Awatar kręgu: litery.") { session.setCirclePhoto(community.id, null) }
            }
        }
        LuxurySectionTitle("Kolor liter", modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircleColors.forEach { hex ->
                val on = community.circle?.color.equals(hex, ignoreCase = true) || (hex == null && community.circle?.color == null)
                val base = Modifier.size(40.dp).clip(CircleShape)
                    .then(if (hex == null) Modifier.background(Luxury.GoldFill) else Modifier.background(Color(android.graphics.Color.parseColor(hex))))
                Row(
                    modifier = Modifier
                        .then(if (on) Modifier.border(2.5.dp, Luxury.GoldLight, CircleShape).padding(4.dp) else Modifier)
                        .then(base)
                        .clickable(enabled = !busy) { run("Zapisano kolor awatara.") { session.setCircleColor(community.id, hex) } }
                        .semantics { contentDescription = if (hex == null) "Złoty" else "Kolor $hex" },
                ) {}
            }
        }
        if (community.circle?.photo != null) Text("Kolor liter widać, gdy krąg nie ma zdjęcia.", fontSize = 12.sp, color = Luxury.TextMuted)
        GoldButton("Gotowe", onClick = onDismiss)
    }

    cropping?.let { uri ->
        PhotoCropDialog(uri, onDismiss = { cropping = null }) { jpeg ->
            cropping = null
            run("Zapisano zdjęcie kręgu.") {
                try { session.setCirclePhoto(community.id, jpeg) } finally { jpeg.delete() }
            }
        }
    }
}
