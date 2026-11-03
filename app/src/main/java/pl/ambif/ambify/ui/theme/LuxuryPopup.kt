package pl.ambif.ambify.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Gold popup (lighter graphite, brighter border) over the screen; a tap outside closes it. Scrolls when tall. */
@Composable
fun LuxuryPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, LuxuryCardShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(LuxuryCardShape)
                .background(Luxury.PopupBrush)
                .border(1.dp, Luxury.PopupBorderBrush, LuxuryCardShape)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
