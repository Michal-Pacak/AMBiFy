package pl.ambif.ambify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury

/*
 * The one way AMBiFy tells people things (canvas "AMBiFy · Komunikaty", accepted 17.11.2026; docs/06_Luxury_Theme.md
 * → Messages). Every module uses these, never its own coloured text. One cause = one message.
 */

/** Colour, border and fill of each kind; the icon goes with it, the colour is never alone. */
enum class MessageKind(val icon: ImageVector) {
    /** Something failed, or a conflict. Stays until its cause is gone or it is closed. */
    ERROR(AppIcons.Alert),
    /** Works, but needs attention. */
    WARNING(AppIcons.Warning),
    /** How things are now (offline with a copy on the phone). */
    STATUS(AppIcons.Info),
    /** An action worked: only for a moment. */
    SUCCESS(AppIcons.Success),
    /** For information. */
    INFO(AppIcons.Info);

    /** The kind's colour in the current theme (darker on the light Perła). */
    val color: Color get() = when (this) {
        ERROR -> Luxury.Error
        WARNING -> Luxury.Warning
        STATUS -> Luxury.GoldText
        SUCCESS -> Luxury.Ok
        INFO -> Luxury.TextMuted
    }
    val border: Color get() = if (this == INFO) Luxury.Outline else color.copy(alpha = if (this == STATUS) 0.35f else 0.6f)
    val fill: Color get() = if (this == INFO) Luxury.Surface else color.copy(alpha = if (Luxury.palette.light) 0.10f else 0.08f)

    /** Status and information titles are plain text; the others take the kind's colour. */
    val titleColor: Color get() = if (this == STATUS || this == INFO) Luxury.Text else color
}

private val Muted: androidx.compose.ui.graphics.Color get() = Luxury.Quiet
private val BarShape = RoundedCornerShape(16.dp)

/**
 * The bar under a screen's title (a lasting state: offline, could not refresh) or above a form (could not save).
 * At most one per screen. [action]: e.g. "Ponów"; [onClose] adds a cross.
 */
@Composable
fun MessageBar(
    kind: MessageKind,
    title: String,
    text: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector = kind.icon,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BarShape)
            .background(kind.fill)
            .border(1.dp, kind.border, BarShape)
            .padding(start = 14.dp, end = if (onClose != null) 2.dp else 14.dp, top = 12.dp, bottom = 12.dp)
            .semantics { liveRegion = if (kind == MessageKind.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(22.dp))
        MessageTexts(kind, title, text, Modifier.weight(1f))
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, kind.border, RoundedCornerShape(22.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, color = kind.color, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Zamknij komunikat", tint = Muted, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun MessageTexts(kind: MessageKind, title: String, text: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = kind.titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (text.isNotEmpty()) Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** A result of an action, shown in the bubble at the bottom. */
data class Bubble(
    val kind: MessageKind,
    val title: String,
    val text: String = "",
    /** E.g. "Cofnij" (board 27); the bubble closes after it. */
    val action: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/** The bubble of a screen: [show] replaces the one shown. Success and information go away by themselves. */
class Bubbles {
    var current by mutableStateOf<Bubble?>(null)
        private set

    fun show(kind: MessageKind, title: String, text: String = "", action: String? = null, onAction: (() -> Unit)? = null) {
        current = Bubble(kind, title, text, action, onAction)
    }

    fun close() {
        current = null
    }
}

/** Put at the bottom of the screen's root [Box]. An error stays until closed; the rest goes after 4 s. */
@Composable
fun BoxScope.BubbleHost(bubbles: Bubbles, bottomPadding: Int = 16) {
    val bubble = bubbles.current
    LaunchedEffect(bubble?.id) {
        if (bubble != null && bubble.kind != MessageKind.ERROR) {
            delay(if (bubble.action != null) BUBBLE_MS * 2 else BUBBLE_MS)
            if (bubbles.current?.id == bubble.id) bubbles.close()
        }
    }
    AnimatedVisibility(
        visible = bubble != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = bottomPadding.dp),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val shown = bubble ?: return@AnimatedVisibility
        val kind = shown.kind
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, BarShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(BarShape)
                .background(if (kind == MessageKind.ERROR) androidx.compose.ui.graphics.lerp(Luxury.Surface, Luxury.Error, 0.08f) else Luxury.Surface)
                .border(1.dp, kind.border, BarShape)
                .padding(start = 14.dp, end = if (kind == MessageKind.ERROR) 2.dp else 14.dp, top = 12.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(kind.icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(22.dp))
            MessageTexts(kind, shown.title, shown.text, Modifier.weight(1f))
            if (shown.action != null && shown.onAction != null) {
                TextButton(onClick = { bubbles.close(); shown.onAction.invoke() }) {
                    Text(shown.action, color = Luxury.GoldText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (kind == MessageKind.ERROR) {
                IconButton(onClick = bubbles::close) { Icon(AppIcons.Close, contentDescription = "Zamknij komunikat", tint = Muted, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

private const val BUBBLE_MS = 4000L

/** Under a form field that has to be corrected (the field itself gets a red border). */
@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(AppIcons.Alert, contentDescription = null, tint = MessageKind.ERROR.color, modifier = Modifier.size(14.dp))
        Text(text, color = MessageKind.ERROR.color, fontSize = 12.sp)
    }
}

/** A small mark by an item: "Konflikt", "Po terminie", "Czeka na wysłanie", "Do zatwierdzenia". */
@Composable
fun MessageChip(kind: MessageKind, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(if (kind == MessageKind.INFO) kind.fill else kind.color.copy(alpha = 0.12f))
            .border(1.dp, kind.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(13.dp))
        Text(label, color = kind.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont)
    }
}

/** No data to show at all (and no copy on the phone): the whole screen says so, with what to do. */
@Composable
fun FullScreenMessage(
    kind: MessageKind,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = kind.icon,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(kind.fill.copy(alpha = kind.fill.alpha * 0.7f))
            .border(1.dp, kind.border, shape)
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(kind.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(32.dp))
        }
        Text(title, color = kind.titleColor, fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Luxury.GoldFill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, color = Luxury.OnGold, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}
