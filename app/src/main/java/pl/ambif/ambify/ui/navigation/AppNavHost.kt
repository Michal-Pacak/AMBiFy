package pl.ambif.ambify.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.features.account.AccountScreen
import pl.ambif.ambify.features.calendar.CalendarScreen
import pl.ambif.ambify.features.common.ModulePlaceholderScreen
import pl.ambif.ambify.features.home.HomeScreen
import pl.ambif.ambify.modules.splash.SplashScreen
import pl.ambif.ambify.ui.branding.BrandLogo

private val NavigationAccent = Color(0xFFC9A85B)
private val NavigationBarColor = Color(0xFF29251D)
private val NavigationGold = Color(0xFFD6B65F)

private val BarRowHeight = 78.dp
private val PanelRowHeight = 64.dp
private const val PanelColumns = 4

// Velocity (px/s) above which a fling opens or closes the panel regardless of position.
private const val PanelFlingVelocity = 600f

private data class NavigationBarDestination(
    val destination: AppDestination,
    val label: String,
    val icon: @Composable () -> Unit,
)

// Two modules on each side of the Dashboard logo; the rest live in the pull-up panel.
private val BarStartDestinations = listOf(
    NavigationBarDestination(AppDestination.Calendar, "Kalendarz") { CalendarNavIcon() },
    NavigationBarDestination(AppDestination.Missions, "Misje") { MissionsNavIcon() },
)
private val BarEndDestinations = listOf(
    NavigationBarDestination(AppDestination.Shopping, "Zakupy") { ShoppingNavIcon() },
    NavigationBarDestination(AppDestination.Account, "Konto") { AccountNavIcon() },
)
private val PanelDestinations = listOf(
    NavigationBarDestination(AppDestination.Communication, "Komunikacja") { CommunicationNavIcon() },
    NavigationBarDestination(AppDestination.Timetable, "Plan lekcji") { TimetableNavIcon() },
    NavigationBarDestination(AppDestination.School, "Szkoła") { SchoolNavIcon() },
)

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigationBar =
        currentDestination != null && currentDestination.route != AppDestination.Splash.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavigationBar) {
                AppBottomBar(
                    currentDestination = currentDestination,
                    onDestinationSelected = { route ->
                        navController.navigateToBottomDestination(route)
                    },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Splash.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(AppDestination.Splash.route) {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(AppDestination.Home.route) {
                            popUpTo(AppDestination.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(AppDestination.Home.route) {
                HomeScreen()
            }

            composable(AppDestination.Calendar.route) {
                CalendarScreen()
            }

            composable(AppDestination.Missions.route) {
                ModulePlaceholderScreen(
                    title = "Misje",
                    description = "Zadania, punkty i nagrody dla członków organizacji.",
                )
            }

            composable(AppDestination.Shopping.route) {
                ModulePlaceholderScreen(
                    title = "Zakupy",
                    description = "Wspólne listy zakupów całej organizacji.",
                )
            }

            composable(AppDestination.Communication.route) {
                ModulePlaceholderScreen(
                    title = "Komunikacja",
                    description = "Wiadomości i dyskusje między członkami organizacji.",
                )
            }

            composable(AppDestination.Timetable.route) {
                ModulePlaceholderScreen(
                    title = "Plan lekcji",
                    description = "Tygodniowy plan lekcji dzieci.",
                )
            }

            composable(AppDestination.School.route) {
                ModulePlaceholderScreen(
                    title = "Szkoła",
                    description = "Oceny, ogłoszenia i sprawy szkolne.",
                )
            }

            composable(AppDestination.Account.route) {
                AccountScreen()
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentDestination: NavDestination?,
    onDestinationSelected: (String) -> Unit,
) {
    val barShape = remember { CenterCradleShape(halfWidth = 64.dp, depth = 24.dp) }
    val panelRows = PanelDestinations.chunked(PanelColumns)
    val panelHeight = PanelRowHeight * panelRows.size
    val panelHeightPx = with(LocalDensity.current) { panelHeight.toPx() }

    // 0 = only the main row is visible, 1 = the bar is pulled up and the panel is open.
    var expansion by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    fun settle(target: Float, velocity: Float = 0f) {
        scope.launch {
            animate(initialValue = expansion, targetValue = target, initialVelocity = velocity) { value, _ ->
                expansion = value
            }
        }
    }
    val dragState = rememberDraggableState { delta ->
        expansion = (expansion - delta / panelHeightPx).coerceIn(0f, 1f)
    }
    // More than four modules: the rest live in the pull-up panel, shown by the grip above the logo.
    val hasPanel = PanelDestinations.isNotEmpty()
    val selectAndClose: (String) -> Unit = { route ->
        settle(0f)
        onDestinationSelected(route)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BarRowHeight + panelHeight * expansion)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = hasPanel,
                onDragStopped = { velocity ->
                    val target = when {
                        velocity < -PanelFlingVelocity -> 1f
                        velocity > PanelFlingVelocity -> 0f
                        expansion > 0.5f -> 1f
                        else -> 0f
                    }
                    settle(target, -velocity / panelHeightPx)
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

        // Content keeps its natural height and is clipped by the bar, so pulling the bar up
        // reveals the panel rows below the main row.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarRowHeight),
            ) {
                BarStartDestinations.forEach { destination ->
                    NavigationDestinationItem(
                        modifier = Modifier.weight(1f),
                        destination = destination,
                        currentDestination = currentDestination,
                        onDestinationSelected = selectAndClose,
                    )
                }

                DashboardBottomBarItem(
                    modifier = Modifier.weight(1f),
                    selected = currentDestination.isCurrent(AppDestination.Home.route),
                    onClick = { selectAndClose(AppDestination.Home.route) },
                )

                BarEndDestinations.forEach { destination ->
                    NavigationDestinationItem(
                        modifier = Modifier.weight(1f),
                        destination = destination,
                        currentDestination = currentDestination,
                        onDestinationSelected = selectAndClose,
                    )
                }
            }

            panelRows.forEach { rowDestinations ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PanelRowHeight)
                        .graphicsLayer { alpha = expansion },
                ) {
                    rowDestinations.forEach { destination ->
                        NavigationDestinationItem(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            currentDestination = currentDestination,
                            onDestinationSelected = selectAndClose,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationDestinationItem(
    modifier: Modifier,
    destination: NavigationBarDestination,
    currentDestination: NavDestination?,
    onDestinationSelected: (String) -> Unit,
) {
    BottomBarItem(
        modifier = modifier,
        label = destination.label,
        selected = currentDestination.isCurrent(destination.destination.route),
        onClick = { onDestinationSelected(destination.destination.route) },
        icon = destination.icon,
    )
}

@Composable
private fun BottomBarItem(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
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
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides NavigationGold,
            ) {
                icon()
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) Color.White else NavigationGold,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun DashboardBottomBarItem(
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
            },
    ) {
        // Same layout as BottomBarItem, so the label sits on the baseline of the other items;
        // the larger gap lifts the logo into the cradle.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            BrandLogo(
                modifier = Modifier.size(width = 40.dp, height = 35.dp),
                animated = false,
                cropToLogo = true,
                backgroundColor = NavigationBarColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Dashboard",
                color = if (selected) Color.White else NavigationGold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
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

@Composable
private fun CalendarNavIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val left = size.width * 0.17f
        val top = size.height * 0.2f
        val iconWidth = size.width * 0.66f
        val iconHeight = size.height * 0.68f

        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(iconWidth, iconHeight),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(left, top + iconHeight * 0.3f),
            end = Offset(left + iconWidth, top + iconHeight * 0.3f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(left + iconWidth * 0.27f, top - 1.dp.toPx()),
            end = Offset(left + iconWidth * 0.27f, top + 4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(left + iconWidth * 0.73f, top - 1.dp.toPx()),
            end = Offset(left + iconWidth * 0.73f, top + 4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun AccountNavIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width / 2f, size.height * 0.32f),
            style = Stroke(width = strokeWidth),
        )
        val shoulders = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.88f)
            cubicTo(
                size.width * 0.2f,
                size.height * 0.61f,
                size.width * 0.8f,
                size.height * 0.61f,
                size.width * 0.82f,
                size.height * 0.88f,
            )
        }
        drawPath(
            path = shoulders,
            color = color,
            style = Stroke(width = strokeWidth),
        )
    }
}

/** Draws stroked paths given in unit coordinates (0..1 of the icon size). */
private class LineIconPainter(
    private val scope: DrawScope,
    private val color: Color,
    private val stroke: Stroke,
) {
    fun line(build: Path.() -> Unit) {
        val path = Path().apply(build)
        path.transform(Matrix().apply { scale(scope.size.width, scope.size.height) })
        scope.drawPath(path = path, color = color, style = stroke)
    }
}

/** Line icon in the style of the Calendar/Account icons. */
@Composable
private fun LineNavIcon(draw: LineIconPainter.() -> Unit) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        LineIconPainter(this, color, stroke).draw()
    }
}

@Composable
private fun MissionsNavIcon() = LineNavIcon {
    // Flag on a pole.
    line { moveTo(0.26f, 0.9f); lineTo(0.26f, 0.12f) }
    line {
        moveTo(0.26f, 0.16f); lineTo(0.82f, 0.16f); lineTo(0.68f, 0.35f)
        lineTo(0.82f, 0.54f); lineTo(0.26f, 0.54f)
    }
}

@Composable
private fun ShoppingNavIcon() = LineNavIcon {
    // Shopping cart.
    line {
        moveTo(0.08f, 0.2f); lineTo(0.22f, 0.2f); lineTo(0.33f, 0.64f)
        lineTo(0.8f, 0.64f); lineTo(0.9f, 0.32f); lineTo(0.26f, 0.32f)
    }
    line { addOval(androidx.compose.ui.geometry.Rect(0.33f, 0.76f, 0.45f, 0.88f)) }
    line { addOval(androidx.compose.ui.geometry.Rect(0.68f, 0.76f, 0.8f, 0.88f)) }
}

@Composable
private fun CommunicationNavIcon() = LineNavIcon {
    // Speech bubble with a tail.
    line {
        moveTo(0.24f, 0.16f); lineTo(0.76f, 0.16f)
        quadraticTo(0.9f, 0.16f, 0.9f, 0.3f); lineTo(0.9f, 0.56f)
        quadraticTo(0.9f, 0.7f, 0.76f, 0.7f); lineTo(0.46f, 0.7f)
        lineTo(0.26f, 0.88f); lineTo(0.28f, 0.7f); lineTo(0.24f, 0.7f)
        quadraticTo(0.1f, 0.7f, 0.1f, 0.56f); lineTo(0.1f, 0.3f)
        quadraticTo(0.1f, 0.16f, 0.24f, 0.16f); close()
    }
}

@Composable
private fun TimetableNavIcon() = LineNavIcon {
    // Timetable grid: a header row and a column of lesson numbers.
    line { addRoundRect(androidx.compose.ui.geometry.RoundRect(0.14f, 0.16f, 0.86f, 0.86f, 0.1f, 0.1f)) }
    line { moveTo(0.14f, 0.38f); lineTo(0.86f, 0.38f) }
    line { moveTo(0.14f, 0.62f); lineTo(0.86f, 0.62f) }
    line { moveTo(0.38f, 0.16f); lineTo(0.38f, 0.86f) }
}

@Composable
private fun SchoolNavIcon() = LineNavIcon {
    // Graduation cap with a tassel.
    line {
        moveTo(0.5f, 0.2f); lineTo(0.94f, 0.4f); lineTo(0.5f, 0.6f); lineTo(0.06f, 0.4f); close()
    }
    line {
        moveTo(0.26f, 0.51f); lineTo(0.26f, 0.72f)
        quadraticTo(0.5f, 0.88f, 0.74f, 0.72f); lineTo(0.74f, 0.51f)
    }
    line { moveTo(0.94f, 0.4f); lineTo(0.94f, 0.66f) }
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

private fun NavHostController.navigateToBottomDestination(route: String) {
    navigate(route) {
        popUpTo(AppDestination.Home.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
