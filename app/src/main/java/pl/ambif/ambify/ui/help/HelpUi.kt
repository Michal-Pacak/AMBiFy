package pl.ambif.ambify.ui.help

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.PopupButtons

/** The help's own colour (violet): no theme has it, so the first-run bubble never looks like a button. */
private val HelpViolet = Color(0xFF7C6CFF)

/**
 * The "?" in a screen's title row (0.7.8, canvas boards 3 and 6): small and calm (24 dp ring, 44 dp to touch).
 * [firstRun]: on the Dashboard, the one-time bubble "Pomoc" over a dimmed screen, until "?" is tapped once
 * (once for the whole app, never again — Michał, 25.11).
 */
@Composable
fun HelpButton(help: HelpScreen, firstRun: Boolean = false) {
    val device = LocalAppSession.current.device
    val context = androidx.compose.ui.platform.LocalContext.current
    val canReport = LocalReportBug.current != null
    var open by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(device.helpSeen()) }
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    fun tap() {
        if (!seen) { device.setHelpSeen(); seen = true }
        // 0.7.9: the screen as it is now, before the help covers it — for "Zgłoś błąd" (board 1).
        if (canReport) {
            pl.ambif.ambify.features.reports.captureScreen(context) { shot ->
                pl.ambif.ambify.features.reports.ReportDraft.set(help.title, shot)
                open = true
            }
        } else {
            open = true
        }
    }
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Pomoc: ${help.title}") { tap() }
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .semantics { contentDescription = "Pomoc" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(24.dp).border(1.3.dp, Luxury.GoldBase.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("?", fontSize = 14.sp, color = Luxury.TextMuted, fontWeight = FontWeight.Medium) }
    }
    val at = anchor
    if (firstRun && !seen && at != null) FirstRunBubble(at) { tap() }
    if (open) HelpPopup(help) { open = false }
}

/** Dimmed screen, the "?" pulsing in the help's violet, and the bubble under it (board 3). */
@Composable
private fun FirstRunBubble(anchor: androidx.compose.ui.geometry.Rect, onTap: () -> Unit) {
    val density = LocalDensity.current
    val pulse = rememberInfiniteTransition(label = "help").animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "help").value
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
        },
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xBB040406))) {
            val left = with(density) { anchor.left.toDp() }
            val top = with(density) { anchor.top.toDp() }
            val side = with(density) { anchor.width.toDp() }
            // The "?" again, over the dimmed screen, with a pulsing ring.
            Box(
                modifier = Modifier.offset(left, top).size(side)
                    .drawBehind {
                        drawCircle(HelpViolet.copy(alpha = 1f - pulse), radius = size.minDimension / 2 + 16.dp.toPx() * pulse, style = Stroke(3.dp.toPx()))
                        drawCircle(HelpViolet, radius = 14.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                    .clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Pomoc", onClick = onTap),
                contentAlignment = Alignment.Center,
            ) { Text("?", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            // The bubble: not a button (no shape of one), the help's own colour, a tail pointing up at "?".
            Column(
                modifier = Modifier.offset(x = (left + side - 262.dp).coerceAtLeast(12.dp), y = top + side + 10.dp).width(250.dp)
                    .drawBehind {
                        val tail = androidx.compose.ui.graphics.Path().apply {
                            val x = size.width - 26.dp.toPx()
                            moveTo(x - 9.dp.toPx(), 0f); lineTo(x, -9.dp.toPx()); lineTo(x + 9.dp.toPx(), 0f); close()
                        }
                        drawPath(tail, HelpViolet)
                    }
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(HelpViolet).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("Pomoc", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Do każdego ekranu jest instrukcja. Stuknij ?, żeby ją otworzyć.", fontSize = 14.sp, color = Color.White, lineHeight = 19.sp)
            }
        }
    }
}

/** The help window (board 6): title, 1–3 sentences, topics; a topic opens its steps or its intro and subtopics. */
@Composable
fun HelpPopup(help: HelpScreen, onClose: () -> Unit) {
    var openTopic by remember { mutableStateOf<Int?>(null) }
    var openSub by remember { mutableStateOf<Int?>(null) }
    LuxuryPopup(onDismiss = onClose) {
        Text("Pomoc · ${help.title}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Luxury.HeadingFont)
        Text(help.intro, fontSize = 14.sp, color = Luxury.TextMuted, lineHeight = 20.sp)
        Column {
            help.topics.forEachIndexed { i, topic ->
                val open = openTopic == i
                TopicRow(topic.name, topic.premium, open, sub = false) { openTopic = if (open) null else i; openSub = null }
                if (open) Column(modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topic.intro?.let {
                        Text(it, fontSize = 13.5.sp, color = Luxury.TextMuted, lineHeight = 19.sp,
                            modifier = Modifier.drawBehind { drawLine(Luxury.GoldBase, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx()) }.padding(start = 10.dp))
                    }
                    topic.steps.forEachIndexed { n, step -> StepRow(n + 1, step) }
                    if (topic.subs.isNotEmpty()) Column(modifier = Modifier.padding(start = 6.dp)) {
                        topic.subs.forEachIndexed { j, sub ->
                            val subOpen = openSub == j
                            TopicRow(sub.name, sub.premium, subOpen, sub = true) { openSub = if (subOpen) null else j }
                            if (subOpen) Column(modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sub.steps.forEachIndexed { n, step -> StepRow(n + 1, step) }
                                sub.who?.let { Text(it, fontSize = 13.sp, color = Luxury.TextMuted) }
                            }
                        }
                    }
                    topic.who?.let { Text(it, fontSize = 13.sp, color = Luxury.TextMuted) }
                }
            }
        }
        val report = LocalReportBug.current
        PopupButtons {
            if (report == null) {
                pl.ambif.ambify.features.timetable.GoldButton("Zamknij", onClick = onClose)
            } else {
                // 0.7.9 (board 1 A): "Zgłoś błąd" next to "Zamknij".
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onClose(); report(help.title) }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
                        Text("Zgłoś błąd", fontSize = 15.sp, color = Luxury.GoldText)
                    }
                    Box(Modifier.weight(1f)) { pl.ambif.ambify.features.timetable.GoldButton("Zamknij", onClick = onClose) }
                }
            }
        }
    }
}

/**
 * 0.7.9: opens "Zgłoś błąd" for the screen named by its help title; null where nobody is signed in (no report
 * possible) — then the help has "Zamknij" only.
 */
val LocalReportBug = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun TopicRow(name: String, premium: Boolean, open: Boolean, sub: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (sub) 44.dp else 48.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "Zwiń" else "Rozwiń") { onClick() }
            .drawBehind {
                drawLine(
                    Luxury.Outline.copy(alpha = 0.6f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                    pathEffect = if (sub) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
                )
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, fontSize = if (sub) 14.sp else 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (premium) Text(
            "PREMIUM", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Luxury.OnGold,
            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Luxury.GoldFill).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Icon(AppIcons.Forward, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(if (sub) 16.dp else 18.dp).rotate(if (open) 90f else 0f))
    }
}

@Composable
private fun StepRow(n: Int, step: HelpStep) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepMock(step.mock, step.label, Modifier.width(100.dp).height(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldText, letterSpacing = 1.2.sp)
            Text(step.text, fontSize = 13.5.sp, lineHeight = 19.sp)
        }
    }
}

/** A step's little picture in the theme's colours: grey bars for content, a gold dot = the finger (board 6 D). */
@Composable
private fun StepMock(mock: Mock, label: String, modifier: Modifier) {
    val bar = Luxury.TextMuted.copy(alpha = 0.35f)
    val gold = Luxury.GoldBase
    val finger = Luxury.GoldLight
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Luxury.Background).border(1.dp, Luxury.Outline, RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = 64.dp.toPx()
            val r = CornerRadius(5.dp.toPx())
            fun row(y: Float, x0: Float = 10.dp.toPx(), x1: Float = w - 10.dp.toPx(), color: Color = bar) =
                drawRoundRect(color, Offset(x0, y), Size(x1 - x0, 14.dp.toPx()), r)
            fun dot(x: Float, y: Float) {
                drawCircle(finger.copy(alpha = 0.25f), 12.dp.toPx(), Offset(x, y))
                drawCircle(finger.copy(alpha = 0.7f), 7.dp.toPx(), Offset(x, y))
            }
            when (mock) {
                Mock.TAP -> {
                    row(8.dp.toPx())
                    dot(w * 0.45f, h - 16.dp.toPx())
                }
                Mock.HOLD -> {
                    row(8.dp.toPx()); row(32.dp.toPx(), color = gold.copy(alpha = 0.35f))
                    drawCircle(finger.copy(alpha = 0.8f), 13.dp.toPx(), Offset(w * 0.4f, 39.dp.toPx()), style = Stroke(2.dp.toPx()))
                    dot(w * 0.4f, 39.dp.toPx())
                }
                Mock.DRAG -> {
                    row(40.dp.toPx()); row(8.dp.toPx(), 16.dp.toPx(), w - 4.dp.toPx(), gold.copy(alpha = 0.35f))
                    dot(w * 0.45f, 15.dp.toPx())
                    drawLine(finger, Offset(w - 16.dp.toPx(), 52.dp.toPx()), Offset(w - 16.dp.toPx(), 24.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(finger, Offset(w - 22.dp.toPx(), 30.dp.toPx()), Offset(w - 16.dp.toPx(), 24.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(finger, Offset(w - 10.dp.toPx(), 30.dp.toPx()), Offset(w - 16.dp.toPx(), 24.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
                Mock.DROP -> {
                    row(8.dp.toPx(), color = gold.copy(alpha = 0.35f)); row(36.dp.toPx())
                    drawRoundRect(gold, Offset(8.dp.toPx(), 6.dp.toPx()), Size(w - 16.dp.toPx(), 18.dp.toPx()), r,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                }
                Mock.SWIPE -> {
                    row(25.dp.toPx())
                    dot(w * 0.6f, 32.dp.toPx())
                    drawLine(finger, Offset(w * 0.6f, 50.dp.toPx()), Offset(w * 0.25f, 50.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(finger, Offset(w * 0.25f, 50.dp.toPx()), Offset(w * 0.25f + 6.dp.toPx(), 44.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
                Mock.MENU -> {
                    row(10.dp.toPx(), x1 = w - 70.dp.toPx()); row(36.dp.toPx(), x1 = w - 70.dp.toPx())
                    drawRoundRect(gold.copy(alpha = 0.2f), Offset(w - 62.dp.toPx(), 6.dp.toPx()), Size(54.dp.toPx(), 40.dp.toPx()), r)
                    drawRoundRect(gold, Offset(w - 62.dp.toPx(), 6.dp.toPx()), Size(54.dp.toPx(), 40.dp.toPx()), r, style = Stroke(1.dp.toPx()))
                    listOf(12f, 22f, 32f).forEachIndexed { i, y ->
                        drawRoundRect(if (i == 1) finger else gold.copy(alpha = 0.6f), Offset(w - 56.dp.toPx(), y.dp.toPx()), Size(42.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                    dot(w - 30.dp.toPx(), 25.dp.toPx())
                }
                Mock.CHECK -> {
                    listOf(8f, 26f, 44f).forEachIndexed { i, y ->
                        val box = Offset(10.dp.toPx(), y.dp.toPx())
                        if (i < 2) drawRoundRect(gold, box, Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                        else drawRoundRect(gold, box, Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        drawRoundRect(bar, Offset(28.dp.toPx(), y.dp.toPx()), Size(w - 38.dp.toPx(), 12.dp.toPx()), r)
                    }
                    dot(16.dp.toPx(), 32.dp.toPx())
                }
                Mock.TYPE -> {
                    drawRoundRect(gold, Offset(10.dp.toPx(), 22.dp.toPx()), Size(w - 20.dp.toPx(), 22.dp.toPx()), r, style = Stroke(1.dp.toPx()))
                }
                Mock.TOGGLE -> {
                    row(25.dp.toPx(), x1 = w - 50.dp.toPx())
                    drawRoundRect(gold, Offset(w - 42.dp.toPx(), 22.dp.toPx()), Size(32.dp.toPx(), 20.dp.toPx()), CornerRadius(10.dp.toPx()))
                    drawCircle(Luxury.Background, 7.dp.toPx(), Offset(w - 20.dp.toPx(), 32.dp.toPx()))
                    dot(w - 26.dp.toPx(), 32.dp.toPx())
                }
            }
        }
        // Words on the picture (a button's or the typed text), in the theme's gold.
        if (label.isNotEmpty()) {
            val type = mock == Mock.TYPE
            Text(
                label, fontSize = 10.sp, maxLines = 1, color = if (type) Luxury.GoldText else Luxury.OnGold,
                modifier = Modifier.align(if (type) Alignment.CenterStart else Alignment.BottomCenter)
                    .padding(start = if (type) 16.dp else 0.dp, bottom = if (type) 0.dp else 8.dp)
                    .then(if (type) Modifier else Modifier.clip(RoundedCornerShape(8.dp)).background(Luxury.GoldFill).padding(horizontal = 7.dp, vertical = 2.dp)),
            )
        }
    }
}
