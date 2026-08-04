package com.anipulse.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anipulse.app.data.Api
import com.anipulse.app.ui.common.PillChip

@Composable
fun LibraryScreen(onTitleClick: (Long) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val all by viewModel.favorites.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val subscriptionsLoading by viewModel.subscriptionsLoading.collectAsState()
    val filtered = if (filter == "all") all else all.filter { it.status == filter }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshSubscriptions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to "Все",
                "watching" to "Смотрю",
                "planned" to "В планах",
                "completed" to "Просмотрено",
                "subscriptions" to "Подписки",
            ).forEach { (key, label) ->
                PillChip(filter == key, { viewModel.setFilter(key) }, label)
            }
        }
        if (filter == "subscriptions" && subscriptionsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (filter == "subscriptions" && subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нажмите колокольчик у выходящего аниме", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (filter == "subscriptions") {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(subscriptions, key = { "subscription_${it.id}" }) { anime ->
                    LibraryCard(
                        id = anime.id,
                        title = anime.russian?.ifBlank { null } ?: anime.name,
                        subtitle = "Уведомления включены · ${anime.episodesAired}/${anime.episodes.takeIf { it > 0 } ?: "?"} серий",
                        score = anime.score,
                        onClick = { onTitleClick(anime.id) },
                        trailing = {
                            IconButton(onClick = { viewModel.removeSubscription(anime.id) }) {
                                Icon(Icons.Filled.NotificationsActive, "Отключить уведомления", tint = Color(0xFFFF4D8D))
                            }
                        },
                    )
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся сохранённые тайтлы", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.animeId }) { fav ->
                    var menuExpanded by androidx.compose.runtime.remember(fav.animeId) { androidx.compose.runtime.mutableStateOf(false) }
                    val status = when (fav.status) { "watching" -> "Смотрю"; "planned" -> "В планах"; "completed" -> "Просмотрено"; else -> "В Моём" }
                    LibraryCard(
                        id = fav.animeId,
                        title = fav.title,
                        subtitle = status,
                        score = fav.score,
                        onClick = { onTitleClick(fav.animeId) },
                        trailing = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, "Дополнительно", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    listOf("watching" to "Смотрю", "planned" to "В планах", "completed" to "Просмотрено").forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { viewModel.setStatus(fav, key); menuExpanded = false },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Удалить из Моего", color = MaterialTheme.colorScheme.error) },
                                        onClick = { viewModel.removeFavorite(fav.animeId); menuExpanded = false },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    id: Long,
    title: String,
    subtitle: String,
    score: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color(0xFF15151F),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                Api.GATEWAY + "poster/$id",
                title,
                Modifier.width(86.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 15.dp, bottomStart = 15.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF4D8D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                score?.takeIf { it != "0.0" }?.let {
                    Text("Shikimori ★ $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD66B))
                }
            }
            trailing()
        }
    }
}
