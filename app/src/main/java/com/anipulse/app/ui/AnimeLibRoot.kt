package com.anipulse.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// Переход «вглубь» (тайтл, чаты, ЛС, друзья, уведомления) — направленный slide+fade,
// в отличие от плоского кросс-фейда между соседними вкладками (задаётся на уровне NavHost).
private val pushEnter = slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))
private val pushExit = slideOutHorizontally(tween(280)) { -it / 6 } + fadeOut(tween(180))
private val pushPopEnter = slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(280))
private val pushPopExit = slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(180))

/** Нижняя навигация (редизайн 07-16): только основные разделы контента, максимум 5. */
private val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("catalog", "Каталог", Icons.Filled.GridView),
    Tab("library", "Моё", Icons.Filled.CollectionsBookmark),
    Tab("schedule", "Эфир", Icons.Filled.CalendarMonth),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

@Composable
fun AnimeLibRoot(menuViewModel: RootMenuViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    AnimeLibTheme {
        var showSplash by remember { mutableStateOf(true) }
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            },
            bottomBar = {
                if (currentTab != null) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.route == currentTab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (currentTab != null) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier,
                // Кросс-фейд между соседними вкладками (fade through: лёгкий scale добавляет глубину без направленности).
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 1.03f) },
                popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                popExitTransition = { fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 1.03f) },
            ) {
                composable("home") {
                    com.anipulse.app.ui.home.HomeScreen(
                        onTitleClick = { id -> navController.navigate("title/$id") },
                    )
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
                    CatalogScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("schedule") {
                    com.anipulse.app.ui.schedule.ScheduleScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("library") {
                    com.anipulse.app.ui.library.LibraryScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("profile") { com.anipulse.app.ui.profile.ProfileScreen() }

                composable(
                    "title/{animeId}",
                    arguments = listOf(navArgument("animeId") { type = NavType.LongType }),
                    enterTransition = { pushEnter }, exitTransition = { pushExit },
                    popEnterTransition = { pushPopEnter }, popExitTransition = { pushPopExit },
                ) {
                    TitleScreen(
                        onBack = { navController.popBackStack() },
                        onPlay = { navController.navigate("player") },
                    )
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
        if (showSplash) SplashScreen(onFinished = { showSplash = false })
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
