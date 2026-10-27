package pl.ambif.ambify.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.device.GrabVibration
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.Tier
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.settings.ModuleOrderStore
import pl.ambif.ambify.ui.branding.BrandLogo

private val NavigationAccent = Color(0xFFC9A85B)
private val NavigationBarColor = Color(0xFF29251D)
private val NavigationGold = Color(0xFFD6B65F)

private val BarRowHeight = 75.dp
private val PanelRowHeight = 64.dp
private val CradleDepth = 15.dp
private const val PanelColumns = 4

// Icons sit on one line; labels start below them and may take two lines.
private val BarIconTop = 19.dp
private val PanelIconTop = 10.dp
private val LabelGap = 3.dp
private val LabelFontSize = 11.sp
private val LabelLineHeight = 12.65.dp // LabelFontSize * 1.15
// Dashboard logo (outlined logo bounds, 408:356) without a label; its bottom is level with the labels.
private val DashboardLogoWidth = 55.dp
private val DashboardLogoHeight = 48.dp
private const val DashboardLabel = "Pulpit"

// Velocity (px/s) above which a fling decides the gesture regardless of how far the bar moved.
private const val FlingVelocity = 600f

// How far (in seconds of the release velocity) a sideways fling carries the bar before it snaps.
private const val FlingCarry = 0.15f

internal class BarModule(
    val destination: AppDestination,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    val route: String get() = destination.route
}

/** A module's bar entry: its name comes from the module catalog, the same as on every other screen. */
private fun BarModule(module: ModuleId, icon: @Composable () -> Unit): BarModule {
    val destination = BarDestinations.getValue(module)
    check(destination.route == module.route) { "Route of $module differs from its screen" }
    return BarModule(destination, module.label, icon)
}

private val BarDestinations = mapOf(
    ModuleId.CALENDAR to AppDestination.Calendar,
    ModuleId.MISSIONS to AppDestination.Missions,
    ModuleId.LISTS to AppDestination.Lists,
    ModuleId.COMMUNICATION to AppDestination.Communication,
    ModuleId.TIMETABLE to AppDestination.Timetable,
    ModuleId.SCHOOL to AppDestination.School,
    ModuleId.ASSISTANT to AppDestination.Assistant,
)

/** All bar modules in their default order. */
internal val BarModules = listOf(
    BarModule(ModuleId.CALENDAR) { CalendarNavIcon() },
    BarModule(ModuleId.MISSIONS) { MissionsNavIcon() },
    BarModule(ModuleId.LISTS) { ShoppingNavIcon() },
    BarModule(AppDestination.Settings, "Ustawienia") { SettingsNavIcon() },
    BarModule(AppDestination.Platform, "Platform Admin") { PlatformNavIcon() },
    BarModule(ModuleId.COMMUNICATION) { CommunicationNavIcon() },
    BarModule(ModuleId.TIMETABLE) { TimetableNavIcon() },
    BarModule(ModuleId.SCHOOL) { SchoolNavIcon() },
    BarModule(ModuleId.ASSISTANT) { AssistantNavIcon() },
)

/**
 * Routes on the bar: the modules switched on in the current community (the basic ones while that is
 * not known yet), Ustawienia, which is not a module and is always there, and the Platform Admin panel,
 * only for the Platform Admin.
 */
internal fun barRoutes(enabled: List<ModuleId>?, platformAdmin: Boolean = false): List<String> {
    val modules = (enabled ?: ModuleId.entries.filter { it.tier == Tier.BASIC }).map { it.route }
    return BarModules.map { it.route }.filter {
        it == AppDestination.Settings.route || (it == AppDestination.Platform.route && platformAdmin) || it in modules
    }
}

/**
 * Navigation bar with the Dashboard logo fixed in the middle.
 *
 * Modules form one circular list. The collapsed bar shows a window of four of them; swiping it
 * sideways shifts the window by one module (back to the start after an app restart). Pulling the
 * bar up reveals the panel with the remaining modules; there a long press drags a module to a new
 * place in the saved order. Choosing a module from the panel starts the window at that module.
 */
@Composable
fun ModuleBar(
    currentDestination: NavDestination?,
    onDestinationSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val grabVibration = remember(context) { GrabVibration(context.applicationContext) }
    val store = remember(context) { ModuleOrderStore(context.applicationContext) }
    val session = LocalAppSession.current
    // The order also lives on the server; a newer copy from there reloads the bar.
    val moduleOrderVersion by session.moduleOrderVersion.collectAsState()
    val communityModules by session.modules.collectAsState()
    val sessionState by session.state.collectAsState()
    val platformAdmin = (sessionState as? SessionState.SignedIn)?.me?.platformAdmin == true
    val modulesByRoute = remember { BarModules.associateBy { it.route } }
    val shownRoutes = remember(communityModules, platformAdmin) { barRoutes(communityModules?.enabled, platformAdmin) }

    var order by remember(moduleOrderVersion, shownRoutes) { mutableStateOf(resolveModuleOrder(store.load(), shownRoutes)) }
    // Index in `order` of the first module on the collapsed bar.
    var windowStart by rememberSaveable { mutableIntStateOf(0) }

    val count = order.size
    val panelCount = (count - BarSlots).coerceAtLeast(0)
    val hasPanel = panelCount > 0
    val panelRows = (panelCount + PanelColumns - 1) / PanelColumns
    val panelHeight = PanelRowHeight * panelRows

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val barRowPx = with(density) { BarRowHeight.toPx() }
    val panelRowPx = with(density) { PanelRowHeight.toPx() }
    val panelHeightPx = panelRowPx * panelRows
    val columnPx = barWidthPx / 5f
    // Sideways drag distance per module: four slots spread over five columns (the logo takes one),
    // so a module dragged from the last slot to the first ends up there.
    val slotDragPx = columnPx * 5f / BarSlots

    // 0 = collapsed, 1 = pulled up with the panel open.
    var expansion by remember { mutableFloatStateOf(0f) }
    // Sideways drag of the collapsed bar, in slots (any distance, the list is circular);
    // positive moves towards the next modules.
    var shift by remember { mutableFloatStateOf(0f) }
    // Long-press reordering in the open panel.
    var draggedRoute by remember { mutableStateOf<String?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var dragTarget by remember { mutableIntStateOf(-1) }

    fun animateExpansion(target: Float, velocity: Float = 0f) {
        scope.launch {
            animate(initialValue = expansion, targetValue = target, initialVelocity = velocity) { value, _ ->
                expansion = value
            }
        }
    }

    fun slotRect(index: Int): Rect =
        if (index < BarSlots) {
            Rect(Offset(carouselColumn(index.toFloat()) * columnPx, 0f), Size(columnPx, barRowPx))
        } else {
            val panelIndex = index - BarSlots
            val row = panelIndex / PanelColumns
            val inRow = minOf(PanelColumns, panelCount - row * PanelColumns)
            val width = barWidthPx / inRow
            Rect(Offset((panelIndex % PanelColumns) * width, barRowPx + row * panelRowPx), Size(width, panelRowPx))
        }

    fun select(route: String) {
        val visible = order.rotatedFrom(windowStart).take(BarSlots)
        if (route !in visible) windowStart = order.indexOf(route)
        animateExpansion(0f)
        onDestinationSelected(route)
    }

    fun endReorder() {
        val dragged = draggedRoute
        if (dragged != null && dragTarget >= 0) {
            // The arrangement seen while reordering becomes the saved order, so the bar
            // looks the same after a restart.
            val visible = order.rotatedFrom(windowStart)
            order = visible.moved(visible.indexOf(dragged), dragTarget)
            windowStart = 0
            session.saveModuleOrder(order)
        }
        draggedRoute = null
        dragTarget = -1
    }

    val verticalDrag = rememberDraggableState { delta ->
        expansion = (expansion - delta / panelHeightPx).coerceIn(0f, 1f)
    }
    val sidewaysDrag = rememberDraggableState { delta ->
        if (slotDragPx > 0f) shift -= delta / slotDragPx
    }
    val barShape = remember { CenterCradleShape(halfWidth = 64.dp, depth = CradleDepth) }
    val reorderEnabled = hasPanel && expansion >= 0.99f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BarRowHeight + panelHeight * expansion)
            .onSizeChanged { barWidthPx = it.width.toFloat() }
            .draggable(
                state = verticalDrag,
                orientation = Orientation.Vertical,
                enabled = hasPanel && draggedRoute == null,
                onDragStopped = { velocity ->
                    val target = when {
                        velocity < -FlingVelocity -> 1f
                        velocity > FlingVelocity -> 0f
                        expansion > 0.5f -> 1f
                        else -> 0f
                    }
                    animateExpansion(target, -velocity / panelHeightPx)
                },
            )
            .draggable(
                state = sidewaysDrag,
                orientation = Orientation.Horizontal,
                enabled = hasPanel && expansion == 0f && draggedRoute == null,
                onDragStopped = { velocity ->
                    // Snap to the nearest module; a fling carries the bar at most one module further,
                    // so a fast flick never spins it around the whole circular list.
                    val carry = (-velocity / slotDragPx * FlingCarry).coerceIn(-1f, 1f)
                    val step = (shift + carry).roundToInt()
                    animate(initialValue = shift, targetValue = step.toFloat()) { value, _ -> shift = value }
                    // Same positions after the swap, so the bar does not jump.
                    windowStart = (windowStart + step).mod(count)
                    shift = 0f
                },
            )
            .clipToBounds(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawPath(
                        path = barShape.topEdgePath(size, this),
                        color = NavigationAccent,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    // Grip above the logo: hints that the bar can be pulled up.
                    if (hasPanel) {
                        val gripWidth = 22.dp.toPx()
                        drawRoundRect(
                            color = NavigationAccent.copy(alpha = 0.6f),
                            topLeft = Offset((size.width - gripWidth) / 2f, 5.dp.toPx()),
                            size = Size(gripWidth, 3.dp.toPx()),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                },
            shape = barShape,
            color = NavigationBarColor,
        ) {}

        if (barWidthPx > 0f) {
            val visible = order.rotatedFrom(windowStart)
            val dragged = draggedRoute
            val shown = if (dragged != null && dragTarget >= 0) {
                visible.moved(visible.indexOf(dragged), dragTarget)
            } else {
                visible
            }

            order.forEach { route ->
                key(route) {
                    val module = modulesByRoute.getValue(route)
                    val index = shown.indexOf(route)
                    val isDragged = route == dragged
                    // Carousel position on the bar row (0..3 visible). While the bar is dragged sideways,
                    // every module moves along the circular list and enters from either edge.
                    val barPosition = if (shift == 0f) {
                        index.toFloat().takeIf { index < BarSlots }
                    } else {
                        ((index - shift + 1f).mod(count.toFloat()) - 1f).takeIf { it > -1f && it < BarSlots }
                    }
                    val slot = slotRect(index)
                    val rect = when {
                        isDragged -> Rect(dragFinger - dragGrab, slot.size)
                        barPosition != null -> Rect(Offset(carouselColumn(barPosition) * columnPx, 0f), Size(columnPx, barRowPx))
                        else -> slot
                    }
                    val position by animateIntOffsetAsState(
                        targetValue = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        animationSpec = if (dragged != null && !isDragged) {
                            spring(stiffness = Spring.StiffnessMediumLow)
                        } else {
                            snap()
                        },
                        label = "module position",
                    )
                    val slotOrigin by rememberUpdatedState(rect.topLeft)

                    ModuleItem(
                        module = module,
                        selected = currentDestination.isCurrent(route),
                        iconTop = if (rect.height < barRowPx - 1f) PanelIconTop else BarIconTop,
                        onClick = { select(route) },
                        modifier = Modifier
                            .offset { position }
                            .requiredSize(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                            .zIndex(if (isDragged) 3f else 0f)
                            .graphicsLayer {
                                // Near the logo a module turns away as if around a sphere: it shrinks and
                                // fades out, then grows back on the other side.
                                val sphere = barPosition?.let { abs(carouselColumn(it) - 2f).coerceIn(0f, 1f) } ?: 1f
                                alpha = (if (barPosition != null || isDragged) 1f else expansion) * sphere * sphere
                                val scale = (if (isDragged) 1.12f else 1f) * (0.4f + 0.6f * sphere)
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(route, reorderEnabled) {
                                if (!reorderEnabled) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        // The icon is small and covered by the finger: confirm the grab by touch.
                                        grabVibration.play()
                                        draggedRoute = route
                                        dragGrab = offset
                                        dragFinger = slotOrigin + offset
                                        dragTarget = order.rotatedFrom(windowStart).indexOf(route)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragFinger += amount
                                        dragTarget = (0 until count).minBy { (slotRect(it).center - dragFinger).getDistanceSquared() }
                                    },
                                    onDragEnd = { endReorder() },
                                    onDragCancel = { endReorder() },
                                )
                            },
                    )
                }
            }

            DashboardItem(
                selected = currentDestination.isCurrent(AppDestination.Home.route),
                onClick = {
                    animateExpansion(0f)
                    onDestinationSelected(AppDestination.Home.route)
                },
                modifier = Modifier
                    .offset { IntOffset((2 * columnPx).roundToInt(), 0) }
                    .requiredSize(with(density) { columnPx.toDp() }, BarRowHeight)
                    .zIndex(2f),
            )
        }
    }
}

@Composable
private fun ModuleItem(
    module: BarModule,
    selected: Boolean,
    iconTop: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(iconTop))
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides NavigationGold) {
                module.icon()
            }
        }
        Spacer(modifier = Modifier.height(LabelGap))
        Text(
            text = module.label,
            color = if (selected) Color.White else NavigationGold,
            fontSize = LabelFontSize,
            lineHeight = LabelFontSize * 1.15f,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun DashboardItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.selected = selected
            role = Role.Tab
            contentDescription = DashboardLabel
        },
    ) {
        // The logo has no label (screen readers get "Pulpit"); it ends level with the labels
        // of the other items and rises into the cradle.
        BrandLogo(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = BarIconTop + 24.dp + LabelGap + LabelLineHeight - DashboardLogoHeight)
                .size(width = DashboardLogoWidth, height = DashboardLogoHeight),
            animated = false,
            cropToLogo = true,
            // Transparent around the symbol, so modules sliding along the bar pass behind the symbol only.
            outlineColor = NavigationBarColor,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        )
    }
}

private class CenterCradleShape(
    private val halfWidth: Dp,
    private val depth: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val edge = topEdgePath(size, density)
        val outline = Path().apply {
            addPath(edge)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(outline)
    }

    fun topEdgePath(
        size: Size,
        density: Density,
    ): Path = with(density) {
        val centerX = size.width / 2f
        val cradleHalfWidth = halfWidth.toPx()
        val cradleDepth = depth.toPx().coerceAtMost(size.height)
        // Control points halfway along each side give a smooth bell with flat ends and top.
        val controlOffset = cradleHalfWidth * 0.5f

        Path().apply {
            moveTo(0f, cradleDepth)
            lineTo(centerX - cradleHalfWidth, cradleDepth)
            cubicTo(
                centerX - controlOffset,
                cradleDepth,
                centerX - controlOffset,
                0f,
                centerX,
                0f,
            )
            cubicTo(
                centerX + controlOffset,
                0f,
                centerX + controlOffset,
                cradleDepth,
                centerX + cradleHalfWidth,
                cradleDepth,
            )
            lineTo(size.width, cradleDepth)
        }
    }
}

private fun NavDestination?.isCurrent(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true
