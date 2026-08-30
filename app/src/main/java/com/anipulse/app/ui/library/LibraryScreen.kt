package com.anipulse.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.anipulse.app.data.download.DownloadedItem
import com.anipulse.app.ui.common.PillChip

@Composable
fun LibraryScreen(
    onTitleClick: (Long) -> Unit,
    initialFilter: String? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val all by viewModel.favorites.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val subscriptionsLoading by viewModel.subscriptionsLoading.collectAsState()
    androidx.compose.runtime.LaunchedEffect(initialFilter) {
        initialFilter?.let(viewModel::setFilter)
    }
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
                "downloads" to "Загрузки",
                "subscriptions" to "Подписки",
            ).forEach { (key, label) ->
                val displayLabel = if (key == "downloads" && downloads.isNotEmpty()) "$label (${downloads.size})" else label
                PillChip(filter == key, { viewModel.setFilter(key) }, displayLabel)
            }
        }
        if (filter == "downloads") {
            if (downloads.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Нет скачанных серий.\nВы можете скачать любую серию на странице аниме для просмотра без интернета.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(downloads, key = { "download_${it.animeId}_${it.episode}" }) { item ->
                        DownloadedCard(
                            item = item,
                            onPlay = { 
                                viewModel.playDownloaded(item)
                                onTitleClick(item.animeId) 
                            },
                            onDelete = { viewModel.deleteDownload(item) },
                        )
                    }
                }
            }
        } else if (filter == "subscriptions" && subscriptionsLoading) {
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
private fun DownloadedCard(
    item: DownloadedItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                Api.GATEWAY + "poster/${item.animeId}",
                item.title,
                Modifier.width(86.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 15.dp, bottomStart = 15.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("Серия ${item.episode} · ${item.dubTitle.ifBlank { "Скачано" }}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF0288D1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sizeMb = if (item.sizeBytes > 0) "%.1f МБ".format(item.sizeBytes / (1024f * 1024f)) else "Офлайн"
                Text(sizeMb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
