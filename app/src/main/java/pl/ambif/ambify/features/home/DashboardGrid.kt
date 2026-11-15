package pl.ambif.ambify.features.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pl.ambif.ambify.core.device.GrabVibration
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.Dashboards
import pl.ambif.ambify.shared.WidgetSlot
import pl.ambif.ambify.shared.WidgetType
import pl.ambif.ambify.ui.theme.Luxury

/** A row of the Dashboard: one full widget, or a left and/or a right half (one of them may be empty). */
internal data class GridRow(val widgets: List<DashboardWidget>) {
    val key: String get() = widgets.first().id
    val full: Boolean get() = widgets.any { it.slot == WidgetSlot.FULL }
    fun at(slot: WidgetSlot) = widgets.firstOrNull { it.slot == slot }
}

/** Where a dragged widget lands: a new row before row [Insert.index], or the free half of row [Fill.row]. */
internal sealed interface DropTarget {
    data class Insert(val index: Int, val slot: WidgetSlot) : DropTarget
    data class Fill(val row: Int, val slot: WidgetSlot) : DropTarget
}

internal fun rowsOf(widgets: List<DashboardWidget>): List<GridRow> =
    Dashboards.normalized(Dashboard(widgets)).widgets.groupBy { it.row }.toSortedMap().values.map(::GridRow)

/** [rows] without the dragged [widget], and [widget] put at [target]: the widgets with their new rows. */
internal fun dropped(rows: List<GridRow>, widget: DashboardWidget, target: DropTarget): List<DashboardWidget> {
    val list = rows.map { it.widgets }.toMutableList()
    when (target) {
        is DropTarget.Insert -> list.add(target.index.coerceIn(0, list.size), listOf(widget.copy(slot = target.slot)))
        is DropTarget.Fill -> list[target.row] = list[target.row] + widget.copy(slot = target.slot)
    }
    return list.flatMapIndexed { index, inRow -> inRow.map { it.copy(row = index) } }
}

/**
 * The place under the finger ([pointer], in root coordinates) among [rows] as they are drawn now ([bounds] by
 * row key): the free half of a half row for a half widget, else a new row above the first row whose middle is
 * below the finger. A half widget goes to the side of the screen the finger is on.
 */
internal fun dropTarget(rows: List<GridRow>, bounds: Map<String, Rect>, pointer: Offset, middleX: Float, half: Boolean): DropTarget {
    val side = when {
        !half -> WidgetSlot.FULL
        pointer.x < middleX -> WidgetSlot.LEFT
        else -> WidgetSlot.RIGHT
    }
    rows.forEachIndexed { index, row ->
        val box = bounds[row.key] ?: return@forEachIndexed
        if (half && !row.full && pointer.y >= box.top && pointer.y <= box.bottom && row.at(side) == null) {
            return DropTarget.Fill(index, side)
        }
        if (pointer.y < box.center.y) return DropTarget.Insert(index, side)
    }
    return DropTarget.Insert(rows.size, side)
}

/**
 * The Dashboard's widgets in rows, scrolled as one page with [header] above and [footer] below. Hold a widget
 * and drag it: the others move aside and the gold place under the finger shows where it lands — the whole width,
 * or a half (left or right; the other half may stay empty). Holding is only for moving: the controls of a widget
 * (⋮ = settings, the gold mark) work with a tap. [content] draws a widget; `interactive = false` for the picture
 * that follows the finger.
 */
@Composable
internal fun DashboardGrid(
    widgets: List<DashboardWidget>,
    scroll: ScrollState,
    onMove: (List<DashboardWidget>) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable (widget: DashboardWidget, interactive: Boolean, modifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val vibration = remember { GrabVibration(context) }
    val current by rememberUpdatedState(widgets)
    val move by rememberUpdatedState(onMove)

    val widgetBounds = remember { mutableMapOf<String, Rect>() }
    val rowBounds = remember { mutableMapOf<String, Rect>() }
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var dragged by remember { mutableStateOf<DashboardWidget?>(null) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var draggedSize by remember { mutableStateOf(Rect.Zero) }
    var target by remember { mutableStateOf<DropTarget?>(null) }

    val rows = rowsOf(widgets)
    val base = dragged?.let { held -> rowsOf(widgets.filter { it.id != held.id }) } ?: rows

    fun aim() {
        val held = dragged ?: return
        val coordinates = box ?: return
        val root = coordinates.localToRoot(pointer)
        val middle = coordinates.localToRoot(Offset(coordinates.size.width / 2f, 0f)).x
        val half = WidgetType.of(held.type)?.half == true
        target = dropTarget(rowsOf(current.filter { it.id != held.id }), rowBounds, root, middle, half)
    }

    // Near the top or bottom edge while dragging: the page scrolls by itself.
    LaunchedEffect(dragged != null) {
        if (dragged == null) return@LaunchedEffect
        val edge = with(density) { 80.dp.toPx() }
        while (dragged != null) {
            withFrameNanos { }
            val height = box?.size?.height?.toFloat() ?: continue
            val step = when {
                pointer.y < edge -> -(edge - pointer.y) / edge * MaxScrollStep
                pointer.y > height - edge -> (pointer.y - (height - edge)) / edge * MaxScrollStep
                else -> 0f
            }
            if (step != 0f) {
                scroll.scrollBy(step)
                aim()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { box = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // The first pass, before the page scroll and the widget's own controls see the touch.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val coordinates = box ?: return@awaitEachGesture
                    val rootDown = coordinates.localToRoot(down.position)
                    val held = current.firstOrNull { widgetBounds[it.id]?.contains(rootDown) == true } ?: return@awaitEachGesture
                    val gaveUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                            if (!change.pressed || event.changes.size > 1) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE")
                        true
                    }
                    if (gaveUp != null) return@awaitEachGesture

                    // Held long enough: the widget is picked up.
                    vibration.play()
                    val bounds = widgetBounds.getValue(held.id)
                    grab = rootDown - bounds.topLeft
                    draggedSize = bounds
                    pointer = down.position
                    dragged = held
                    aim()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        event.changes.forEach { it.consume() }
                        if (change == null || !change.pressed) break
                        pointer = change.position
                        aim()
                    }
                    val landing = target
                    val stillThere = current.firstOrNull { it.id == held.id }
                    dragged = null
                    target = null
                    if (landing != null && stillThere != null) {
                        move(dropped(rowsOf(current.filter { it.id != held.id }), stillThere, landing))
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header()
            val placeHeight = with(density) { draggedSize.height.toDp() }
            base.forEachIndexed { index, row ->
                (target as? DropTarget.Insert)?.takeIf { it.index == index }?.let { NewRowPlace(it.slot, placeHeight) }
                val fill = (target as? DropTarget.Fill)?.takeIf { it.row == index }
                Box(Modifier.fillMaxWidth().onGloballyPositioned { rowBounds[row.key] = it.boundsInRoot() }) {
                    if (row.full) {
                        val widget = row.widgets.first()
                        content(widget, true, Modifier.fillMaxWidth().onGloballyPositioned { widgetBounds[widget.id] = it.boundsInRoot() })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (slot in listOf(WidgetSlot.LEFT, WidgetSlot.RIGHT)) {
                                Box(Modifier.weight(1f)) {
                                    val widget = row.at(slot)
                                    when {
                                        widget != null -> content(widget, true, Modifier.fillMaxWidth().onGloballyPositioned { widgetBounds[widget.id] = it.boundsInRoot() })
                                        fill?.slot == slot -> Place(Modifier.fillMaxWidth(), placeHeight)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            (target as? DropTarget.Insert)?.takeIf { it.index == base.size }?.let { NewRowPlace(it.slot, placeHeight) }
            footer()
        }

        // The picked-up widget follows the finger, a little bigger, above everything.
        dragged?.let { held ->
            val width = with(density) { draggedSize.width.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((pointer.x - grab.x).roundToInt(), (pointer.y - grab.y).roundToInt()) }
                    .width(width)
                    .graphicsLayer {
                        scaleX = 1.03f
                        scaleY = 1.03f
                        alpha = 0.95f
                        shadowElevation = 28.dp.toPx()
                        shape = RoundedCornerShape(20.dp)
                    },
            ) { content(held, false, Modifier.fillMaxWidth()) }
        }
    }
}

/** The gold place of a new row: the whole width, or a half on its side with the other half empty. */
@Composable
private fun NewRowPlace(slot: WidgetSlot, height: Dp) {
    when (slot) {
        WidgetSlot.FULL -> Place(Modifier.fillMaxWidth(), height)
        else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (slot == WidgetSlot.RIGHT) Spacer(Modifier.weight(1f))
            Place(Modifier.weight(1f), height)
            if (slot == WidgetSlot.LEFT) Spacer(Modifier.weight(1f))
        }
    }
}

/** Where the widget will land: a dashed gold frame on a faint gold fill, as tall as the widget. */
@Composable
private fun Place(modifier: Modifier, height: Dp) {
    Box(
        modifier = modifier
            .height(height.coerceAtLeast(64.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Luxury.GoldBase.copy(alpha = 0.12f))
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = Luxury.GoldBase,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
    )
}

private const val MaxScrollStep = 22f
