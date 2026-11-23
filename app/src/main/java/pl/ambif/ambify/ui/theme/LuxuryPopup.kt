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
import androidx.compose.ui.unit.sp
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

/**
 * The gold popup with [header] pinned at the top and [footer] (the buttons) at the bottom; only the middle
 * [content] scrolls, so the buttons are always visible however long the list is.
 */
@Composable
fun LuxuryPopup(
    onDismiss: () -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, LuxuryCardShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(LuxuryCardShape)
                .background(Luxury.PopupBrush)
                .border(1.dp, Luxury.PopupBorderBrush, LuxuryCardShape),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = header,
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            LuxuryDivider()
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = footer,
            )
        }
    }
}

/**
 * The older screens' dialogs in the gold popup (the messages canvas): the same slots as Material's AlertDialog,
 * so a screen only swaps the name. The buttons stay at the bottom, on the right.
 */
@Composable
fun LuxuryAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    LuxuryPopup(onDismiss = onDismissRequest) {
        title?.let {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = Luxury.Text)) { it() }
        }
        text?.let {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Luxury.Text)) { it() }
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
        ) {
            dismissButton?.invoke()
            confirmButton()
        }
    }
}
