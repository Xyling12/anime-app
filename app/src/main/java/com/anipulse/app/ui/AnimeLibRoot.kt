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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
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
    Tab("catalog", "Поиск", Icons.Rounded.Search, Icons.Outlined.Search),
    Tab("schedule", "Расписание", Icons.Rounded.CalendarToday, Icons.Outlined.CalendarToday),
    Tab("library", "Моё", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Tab("profile", "Профиль", Icons.Rounded.Person, Icons.Outlined.PersonOutline),
)

@Composable
fun AnimeLibRoot(menuViewModel: RootMenuViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    // Светлая палитра ещё не прошла визуальную и accessibility-проверку.
    // До её готовности приложение намеренно работает только в тёмной теме.
    val isDarkTheme = true
    AnimeLibTheme(darkTheme = true) {
        // Цвет иконок статус-бара (часы/батарея) должен следовать теме приложения,
        // а не системной: в светлой теме без этого иконки оставались белыми на белом.
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.context as? android.app.Activity)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = false
            }
        }

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentDestination = backStack?.destination
        // Нижняя навигация + общая шапка видны только на 5 основных вкладках;
        // страница тайтла, плеер и второстепенные экраны (чаты/ЛС/друзья/уведомления) — во весь экран.
        val currentTab = tabs.firstOrNull { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
        // OTA: диалог «Вышло обновление» (сервер /alapi/app-version)
        val updateInfo by menuViewModel.update.collectAsState()
        var updateDismissed by remember { mutableStateOf(false) }
        val updCtx = androidx.compose.ui.platform.LocalContext.current
        updateInfo?.takeIf { !updateDismissed }?.let { u ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { updateDismissed = true },
                icon = {
                    Box(
                        Modifier.background(Color(0x33FF4D8D), CircleShape).padding(14.dp),
                    ) { Icon(Icons.Filled.Download, null, tint = Color(0xFFFF4D8D)) }
                },
                title = { Text("Новая версия ${u.versionName}", fontWeight = FontWeight.Bold) },
                text = { Text("Мы подготовили обновление AniPulse. APK скачается через браузер — откройте файл после загрузки, чтобы установить новую версию.") },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                        runCatching {
                            val updateUri = android.net.Uri.parse(u.url)
                            require(updateUri.scheme == "https" && updateUri.host == "anipulsetv.ru" && updateUri.path == "/alapi/apk")
                            updCtx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, updateUri)
                            )
                        }
                        updateDismissed = true
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
                    ) { Text("Обновить") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { updateDismissed = true }) { Text("Позже") }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                containerColor = Color(0xFF15151F),
                tonalElevation = 0.dp,
            )
        }

        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (currentTab != null) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .topSafePadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Text(
                            when (currentTab.route) {
                                "home" -> "AniPulse"
                                "profile" -> ""
                                else -> currentTab.label
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    }
                }
            },
            bottomBar = {
                if (currentTab != null) {
                    androidx.compose.material3.Surface(color = Color(0xFF101017), tonalElevation = 0.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEach { tab ->
                            val selected = tab.route == currentTab.route
                            Column(
                                Modifier.weight(1f).fillMaxSize().clickable {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(if (selected) tab.activeIcon else tab.inactiveIcon, tab.label, Modifier.size(23.dp), tint = tint)
                                Spacer(Modifier.height(3.dp))
                                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
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
                                    onShowAll = { section ->
                                        if (section == com.anipulse.app.ui.home.HomeSection.CONTINUE) {
                                            navController.navigate("library") { launchSingleTop = true }
                                            navController.getBackStackEntry("library").savedStateHandle["initialFilter"] = "watching"
                                        } else {
                                            navController.navigate("home_all/${section.route}")
                                        }
                                    },
                                )
                            }
                        }
                        composable("home_all/{section}") { entry ->
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                com.anipulse.app.ui.home.HomeAllScreen(
                                    section = com.anipulse.app.ui.home.HomeSection.fromRoute(entry.arguments?.getString("section")),
                                    onBack = { navController.popBackStack() },
                                    onTitleClick = { id -> navController.navigate("title/$id") },
                                )
                            }
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
                composable("library") { entry ->
                    com.anipulse.app.ui.library.LibraryScreen(
                        onTitleClick = { id -> navController.navigate("title/$id") },
                        initialFilter = entry.savedStateHandle.get<String>("initialFilter"),
                    )
                }
                composable("profile") { 
                    com.anipulse.app.ui.profile.ProfileScreen(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { }
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
                            onOpenDm = { },
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
