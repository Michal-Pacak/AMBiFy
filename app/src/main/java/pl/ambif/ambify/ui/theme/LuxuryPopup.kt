package pl.ambif.ambify.ui.theme

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/** Where [PopupButtons] put the buttons of the popup they are in (null outside a popup). */
private val LocalPopupFooter = compositionLocalOf<SnapshotStateList<PinnedButtons>?> { null }

/** One [PopupButtons] block shown in the pinned bar; [content] always holds its latest buttons. */
private class PinnedButtons(val content: State<@Composable ColumnScope.() -> Unit>)

/**
 * The buttons of a popup (Zapisz / Anuluj …): written where they always were, at the end of the popup's content, but
 * shown pinned under it, so they are on screen however long the popup is and right above the keyboard when it is up
 * (Michał, 25.11: no closing the keyboard and scrolling down to save). Outside a popup they stay where they are.
 */
@Composable
fun PopupButtons(content: @Composable ColumnScope.() -> Unit) {
    val sink = LocalPopupFooter.current
    if (sink == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        return
    }
    val latest = rememberUpdatedState(content)
    DisposableEffect(sink) {
        val entry = PinnedButtons(latest)
        sink.add(entry)
        onDispose { sink.remove(entry) }
    }
}

/** A gold arrow, gently moving down, while the popup has more to scroll; it fades out at the bottom. */
@Composable
private fun MoreBelow(visible: Boolean) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, label = "more")
    val bounce = androidx.compose.animation.core.rememberInfiniteTransition(label = "more")
    val shift by bounce.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween<Float>(700), androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "shift",
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().height(14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            pl.ambif.ambify.ui.icons.AppIcons.ExpandMore,
            contentDescription = if (visible) "Niżej jest jeszcze coś" else null,
            tint = Luxury.GoldText,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { this.alpha = alpha; translationY = (shift - 2f) * density },
        )
    }
}

/** The popup window shrinks when the keyboard is up, so the pinned buttons sit right above it. */
@Composable
private fun ResizeWithKeyboard() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

/**
 * Gold popup (lighter graphite, brighter border) over the screen; a tap outside closes it. The content scrolls when
 * tall; buttons put in [PopupButtons] stay pinned at the bottom.
 */
@Composable
fun LuxuryPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LuxuryPopup(onDismiss = onDismiss, header = null, footer = null, content = content)
}

/**
 * The gold popup with [header] pinned at the top and [footer] (the buttons) at the bottom; only the middle
 * [content] scrolls, so the buttons are always visible however long the list is. Buttons the content puts in
 * [PopupButtons] are pinned the same way, under [footer].
 */
@Composable
fun LuxuryPopup(
    onDismiss: () -> Unit,
    header: (@Composable ColumnScope.() -> Unit)?,
    footer: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ResizeWithKeyboard()
        val pinned = remember { mutableStateListOf<PinnedButtons>() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, LuxuryCardShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(LuxuryCardShape)
                .background(Luxury.PopupBrush)
                .border(1.dp, Luxury.PopupBorderBrush, LuxuryCardShape),
        ) {
            header?.let {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = it,
                )
            }
            val scroll = rememberScrollState()
            CompositionLocalProvider(LocalPopupFooter provides pinned) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scroll)
                        .padding(start = 20.dp, end = 20.dp, top = if (header == null) 20.dp else 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
            if (footer != null || pinned.isNotEmpty()) {
                LuxuryDivider()
                // Below the line: a small arrow while there is more above the buttons; gone at the very bottom (Michał, 25.11).
                MoreBelow(scroll.canScrollForward)
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    footer?.invoke(this)
                    pinned.forEach { it.content.value(this) }
                }
            }
        }
    }
}

/**
 * The older screens' dialogs in the gold popup (the messages canvas): the same slots as Material's AlertDialog,
 * so a screen only swaps the name. The buttons are pinned at the bottom, on the right.
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
        PopupButtons {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
