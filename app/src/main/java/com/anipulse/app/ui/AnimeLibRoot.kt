package com.anipulse.app.ui

import com.anipulse.app.ui.common.topSafePadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anipulse.app.ui.catalog.CatalogScreen
import com.anipulse.app.ui.player.PlayerScreen
import com.anipulse.app.ui.theme.AnimeLibTheme
import com.anipulse.app.ui.title.TitleScreen

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private data class Tab(val route: String, val label: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector)

// Переход «вглубь» (тайтл, чаты, ЛС, друзья, уведомления) — красивый "всплывающий" эффект
// (в стиле iOS: масштаб + появление)
private val pushEnter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.85f)
private val pushExit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 1.05f)
private val pushPopEnter = fadeIn(tween(350, easing = FastOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 1.05f)
private val pushPopExit = fadeOut(tween(300, easing = FastOutSlowInEasing)) + scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.85f)

/** Нижняя навигация (редизайн 07-16): только основные разделы контента, максимум 5. */
private val tabs = listOf(
    Tab("home", "Главная", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("catalog", "Каталог", Icons.Rounded.Explore, Icons.Outlined.Explore),
    Tab("schedule", "Эфир", Icons.Rounded.CalendarToday, Icons.Outlined.CalendarToday),
    Tab("library", "Моё", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Tab("profile", "Профиль", Icons.Rounded.Person, Icons.Outlined.PersonOutline),
)

@Composable
fun AnimeLibRoot(menuViewModel: RootMenuViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val isDarkTheme by menuViewModel.isDarkTheme.collectAsState()
    AnimeLibTheme(darkTheme = isDarkTheme) {
        // Цвет иконок статус-бара (часы/батарея) должен следовать теме приложения,
        // а не системной: в светлой теме без этого иконки оставались белыми на белом.
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.context as? android.app.Activity)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !isDarkTheme
            }
        }

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentDestination = backStack?.destination
        // Нижняя навигация + общая шапка видны только на 5 основных вкладках;
        // страница тайтла, плеер и второстепенные экраны (чаты/ЛС/друзья/уведомления) — во весь экран.
        val currentTab = tabs.firstOrNull { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
        val dmUnread by menuViewModel.dmUnread.collectAsState()
        val notifUnread by menuViewModel.notifUnread.collectAsState()

        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (currentTab != null) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .topSafePadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Text(
                            if (currentTab.route == "home") "AniPulse" else currentTab.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgedIconButton(
                                icon = Icons.Filled.Forum,
                                contentDescription = "Чаты",
                                showBadge = dmUnread,
                                onClick = { navController.navigate("chats") { launchSingleTop = true } },
                            )
                            BadgedIconButton(
                                icon = Icons.Filled.Notifications,
                                contentDescription = "Уведомления",
                                showBadge = notifUnread,
                                onClick = { navController.navigate("notifications") { launchSingleTop = true } },
                            )
                        }
                    }
                    }
                }
            },
            bottomBar = {
                if (currentTab != null) {
                    NavigationBar(
                        modifier = Modifier.height(60.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                    ) {
                        tabs.forEach { tab ->
                            val selected = tab.route == currentTab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.activeIcon else tab.inactiveIcon, contentDescription = tab.label) },
                                alwaysShowLabel = false,
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            },
        ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (currentTab != null) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))) {
            @OptIn(ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier,
                        enterTransition = {
                            fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.98f)
                        },
                        exitTransition = {
                            fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.02f)
                        },
                        popEnterTransition = { fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.98f) },
                        popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.02f) },
                    ) {
                        composable("home") {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                com.anipulse.app.ui.home.HomeScreen(
                                    onTitleClick = { id -> navController.navigate("title/$id") },
                                )
                            }
                        }
                composable(
                    "chats",
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.ChatsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenGlobalChat = { navController.navigate("chat") },
                        onOpenDms = { navController.navigate("dms") },
                        onOpenNotifications = { navController.navigate("notifications") },
                        onOpenFriends = { navController.navigate("friends") },
                    )
                }
                composable(
                    "friends",
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.FriendsScreen(
                        onBack = { navController.popBackStack() },
                        onWrite = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable(
                    "notifications",
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDm = { nick -> navController.navigate("dm/$nick") },
                        onOpenGlobalChat = { navController.navigate("chat") },
                        onOpenFriends = { navController.navigate("friends") },
                    )
                }
                composable(
                    "chat",
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.ChatScreen(
                        onBack = { navController.popBackStack() },
                        onGoProfile = { navController.navigate("profile") },
                        onOpenDm = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable(
                    "dms",
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.DmListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenThread = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable(
                    "dm/{nick}",
                    arguments = listOf(navArgument("nick") { type = NavType.StringType }),
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    com.anipulse.app.ui.chat.DmChatScreen(onBack = { navController.popBackStack() })
                }
                composable("catalog") {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        CatalogScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                    }
                }
                composable("schedule") {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        com.anipulse.app.ui.schedule.ScheduleScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                    }
                }
                composable("library") {
                    com.anipulse.app.ui.library.LibraryScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("profile") { 
                    com.anipulse.app.ui.profile.ProfileScreen(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { menuViewModel.toggleDarkTheme() }
                    ) 
                }

                composable(
                    "title/{animeId}",
                    arguments = listOf(navArgument("animeId") { type = NavType.LongType }),
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        TitleScreen(
                            onBack = { navController.popBackStack() },
                            onPlay = { navController.navigate("player") },
                        )
                    }
                }

                composable(
                    "player",
                    // Плеер открывается/закрывается мгновенно — без остаточной рамки при повороте.
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None },
                ) {
                    PlayerScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        }
        }
    }
    }
}
}

/** Иконка в шапке с розовой точкой-бейджем непрочитанного в углу. */
@Composable
private fun BadgedIconButton(
    icon: ImageVector,
    contentDescription: String,
    showBadge: Boolean,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
        if (showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4D8D)),
            )
        }
    }
}
