package pl.ambif.ambify.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
import pl.ambif.ambify.core.navigation.AppDestination
import pl.ambif.ambify.features.account.AccountScreen
import pl.ambif.ambify.features.calendar.CalendarScreen
import pl.ambif.ambify.features.home.HomeScreen
import pl.ambif.ambify.modules.splash.SplashScreen
import pl.ambif.ambify.ui.branding.BrandLogo

private val NavigationAccent = Color(0xFFC9A85B)
private val NavigationBarColor = Color(0xFF29251D)
private val NavigationGold = Color(0xFFD6B65F)

private data class NavigationBarDestination(
    val destination: AppDestination,
    val label: String,
    val icon: @Composable () -> Unit,
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
    val barShape = CenterCradleShape(halfWidth = 48.dp, depth = 24.dp)
    val destinations = listOf(
        NavigationBarDestination(AppDestination.Calendar, "Kalendarz") {
            CalendarNavIcon()
        },
        NavigationBarDestination(AppDestination.Account, "Konto") {
            AccountNavIcon()
        },
    )
    val additionalRows = destinations.drop(2).chunked(2)
    val barHeight = 78.dp + (52.dp * additionalRows.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(barHeight),
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
                },
            shape = barShape,
            color = NavigationBarColor,
        ) {}

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
            ) {
                NavigationDestinationItem(
                    modifier = Modifier.weight(1f),
                    destination = destinations[0],
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationSelected,
                )

                DashboardBottomBarItem(
                    modifier = Modifier.weight(1f),
                    selected = currentDestination.isCurrent(AppDestination.Home.route),
                    onClick = { onDestinationSelected(AppDestination.Home.route) },
                )

                destinations.getOrNull(1)?.let { destination ->
                    NavigationDestinationItem(
                        modifier = Modifier.weight(1f),
                        destination = destination,
                        currentDestination = currentDestination,
                        onDestinationSelected = onDestinationSelected,
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))
            }

            additionalRows.forEach { rowDestinations ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    rowDestinations.getOrNull(0)?.let { destination ->
                        NavigationDestinationItem(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            currentDestination = currentDestination,
                            onDestinationSelected = onDestinationSelected,
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))

                    Spacer(modifier = Modifier.weight(1f))

                    rowDestinations.getOrNull(1)?.let { destination ->
                        NavigationDestinationItem(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            currentDestination = currentDestination,
                            onDestinationSelected = onDestinationSelected,
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))
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
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(NavigationBarColor),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = NavigationBarColor,
                            shape = CircleShape,
                        ),
                    animated = false,
                    backgroundColor = NavigationBarColor,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Dashboard",
                color = if (selected) Color.White else NavigationGold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
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
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(iconWidth, iconHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(left, top + iconHeight * 0.3f),
            end = androidx.compose.ui.geometry.Offset(left + iconWidth, top + iconHeight * 0.3f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(left + iconWidth * 0.27f, top - 1.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(left + iconWidth * 0.27f, top + 4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(left + iconWidth * 0.73f, top - 1.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(left + iconWidth * 0.73f, top + 4.dp.toPx()),
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
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.32f),
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

private class CenterCradleShape(
    private val halfWidth: Dp,
    private val depth: Dp,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
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
        size: androidx.compose.ui.geometry.Size,
        density: Density,
    ): Path = with(density) {
        val centerX = size.width / 2f
        val cradleHalfWidth = halfWidth.toPx()
        val cradleDepth = depth.toPx().coerceAtMost(size.height)
        val controlOffset = cradleHalfWidth * 0.52f

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
