package com.animelib.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.animelib.app.ui.catalog.CatalogScreen
import com.animelib.app.ui.player.PlayerScreen
import com.animelib.app.ui.theme.AnimeLibTheme
import com.animelib.app.ui.title.TitleScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("catalog", "Каталог", Icons.Filled.GridView),
    Tab("schedule", "Календарь", Icons.Filled.CalendarMonth),
    Tab("library", "Моё", Icons.Filled.CollectionsBookmark),
    Tab("chats", "Чаты", Icons.Filled.Forum),
    Tab("friends", "Друзья", Icons.Filled.People),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

@Composable
fun AnimeLibRoot(menuViewModel: RootMenuViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    AnimeLibTheme {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentDestination = backStack?.destination
        val isFullscreen = currentDestination?.route?.startsWith("player") == true ||
            currentDestination?.route?.startsWith("title") == true
        val hasUnread by menuViewModel.hasUnread.collectAsState()
        val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        // Шторка-навигация (решение владельца 07-10): шапка с профилем + разделы.
        androidx.compose.material3.ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isFullscreen,
            drawerContent = {
                androidx.compose.material3.ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                ) {
                    // Шапка: аватар + ник (тап → Профиль)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                navController.navigate("profile") { launchSingleTop = true }
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val nick by menuViewModel.nick.collectAsState()
                        val avatarId by menuViewModel.avatarId.collectAsState()
                        com.animelib.app.ui.common.Avatar(avatarId, 56.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(
                                nick ?: "Гость",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (nick != null) "Открыть профиль" else "Войти в аккаунт",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.size(8.dp))
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        androidx.compose.material3.NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tab.label)
                                    if (tab.route == "chats" && hasUnread) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF3B30)),
                                        )
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            selected = selected,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                navController.popBackStack(tab.route, false)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
        ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) {
                composable("home") {
                    com.animelib.app.ui.home.HomeScreen(
                        onTitleClick = { id -> navController.navigate("title/$id") },
                    )
                }
                composable("chats") {
                    com.animelib.app.ui.chat.ChatsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenGlobalChat = { navController.navigate("chat") },
                        onOpenDms = { navController.navigate("dms") },
                        onOpenNotifications = { navController.navigate("notifications") },
                        onOpenFriends = { navController.navigate("friends") },
                    )
                }
                composable("friends") {
                    com.animelib.app.ui.chat.FriendsScreen(
                        onBack = { navController.popBackStack() },
                        onWrite = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable("notifications") {
                    com.animelib.app.ui.chat.NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDm = { nick -> navController.navigate("dm/$nick") },
                        onOpenGlobalChat = { navController.navigate("chat") },
                        onOpenFriends = { navController.navigate("friends") },
                    )
                }
                composable("chat") {
                    com.animelib.app.ui.chat.ChatScreen(
                        onBack = { navController.popBackStack() },
                        onGoProfile = { navController.navigate("profile") },
                        onOpenDm = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable("dms") {
                    com.animelib.app.ui.chat.DmListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenThread = { nick -> navController.navigate("dm/$nick") },
                    )
                }
                composable(
                    "dm/{nick}",
                    arguments = listOf(navArgument("nick") { type = NavType.StringType }),
                ) {
                    com.animelib.app.ui.chat.DmChatScreen(onBack = { navController.popBackStack() })
                }
                composable("catalog") {
                    CatalogScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("schedule") {
                    com.animelib.app.ui.schedule.ScheduleScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("library") {
                    com.animelib.app.ui.library.LibraryScreen(onTitleClick = { id -> navController.navigate("title/$id") })
                }
                composable("profile") { com.animelib.app.ui.profile.ProfileScreen() }

                composable(
                    "title/{animeId}",
                    arguments = listOf(navArgument("animeId") { type = NavType.LongType }),
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

            // Кнопка ☰ открывает шторку; красная точка — только при непрочитанном.
            if (!isFullscreen) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 10.dp),
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню")
                    }
                    if (hasUnread) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3B30)),
                        )
                    }
                }
            }
        }
        }
    }
}
