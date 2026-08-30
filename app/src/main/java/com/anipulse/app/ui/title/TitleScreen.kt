@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.anipulse.app.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anipulse.app.data.shikimori.posterOf
import com.anipulse.app.data.shikimori.posterPreviewOf

/** Состояние фильтра списка серий. */
private enum class EpisodeFilter(val label: String) {
    ALL("Все"),
    UNWATCHED("Не просм."),
    STARTED("Начатые"),
    WATCHED("Просм."),
}

/**
 * Разбирает запрос пользователя в набор номеров серий.
 * Поддерживает: «12» (одна серия), «12-15» или «12..15» (диапазон),
 * «12, 15, 20» (список), пустую строку (все серии).
 * Невалидные токены игнорируются.
 */
private fun parseEpisodeQuery(
    raw: String,
    maxEp: Int,
): List<Int> {
    val text = raw.trim()
    if (text.isEmpty()) return (1..maxEp).toList()
    val out = sortedSetOf<Int>()
    text.split(',', ' ', '\n', '\t').forEach { token ->
        val t = token.trim()
        if (t.isEmpty()) return@forEach
        val dash = t.indexOfAny(charArrayOf('-', '—'))
        if (dash > 0) {
            // Диапазон: «12-15» или «12..15» (вторая '.' съедается removePrefix).
            val a = t.substring(0, dash).trim().toIntOrNull()
            val b = t.substring(dash + 1).trim().removePrefix(".").trim().toIntOrNull()
            if (a != null && b != null) {
                val (lo, hi) = if (a <= b) a to b else b to a
                for (n in lo..hi) if (n in 1..maxEp) out += n
            }
        } else if (dash == 0) {
            // «-5» → от 1 до 5
            val b = t.substring(1).trim().toIntOrNull()
            if (b != null) for (n in 1..b) if (n in 1..maxEp) out += n
        } else {
            val n = t.toIntOrNull()
            if (n != null && n in 1..maxEp) out += n
        }
    }
    return out.toList()
}

@Composable
fun TitleScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenTitle: (Long) -> Unit = {},
    viewModel: TitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var commentInput by remember { mutableStateOf("") }
    var commentProfileNick by remember { mutableStateOf<String?>(null) }
    // Поиск/фильтр серий. Запрос («12», «12-15», «12..24», «12, 20») и фильтр по статусу суммируются.
    var episodeQuery by remember { mutableStateOf("") }
    var episodeFilter by remember { mutableStateOf(EpisodeFilter.ALL) }
    commentProfileNick?.let { nick ->
        com.anipulse.app.ui.common.UserCardSheet(
            nick = nick,
            gateway = viewModel.socialGateway,
            token = viewModel.currentToken(),
            onDismiss = { commentProfileNick = null },
        )
    }

    // Меню действий по серии (запуск, скачивание офлайн, удаление)
    var selectedEpisodeSheet by remember { mutableStateOf<Int?>(null) }
    selectedEpisodeSheet?.let { ep ->
        val d = state.details
        val displayTitle = d?.russian?.ifBlank { null } ?: d?.name ?: ""
        val prog = state.progress[ep]
        val sec = (prog?.positionMs ?: 0) / 1000
        val isDownloaded = ep in state.downloadedEpisodes
        val isDownloading = state.downloadingEpisode?.first == ep
        val downloadPercent = state.downloadingEpisode?.second ?: 0
        
        EpisodeBottomSheet(
            ep = ep,
            titleName = displayTitle,
            dubName = state.selectedDub?.title,
            progSec = sec,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadPercent = downloadPercent,
            onDismiss = { selectedEpisodeSheet = null },
            onPlayResume = {
                selectedEpisodeSheet = null
                viewModel.prepareSession(ep, startOver = false)
                onPlay()
            },
            onPlayStartOver = {
                selectedEpisodeSheet = null
                viewModel.prepareSession(ep, startOver = true)
                onPlay()
            },
            onDownload = {
                selectedEpisodeSheet = null
                viewModel.startDownload(ep)
            },
            onDeleteDownload = {
                selectedEpisodeSheet = null
                viewModel.deleteDownload(ep)
            },
        )
    }

    val sharedTransitionScope = com.anipulse.app.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.anipulse.app.ui.LocalAnimatedVisibilityScope.current
    val animeId = viewModel.animeId
    
    when {
        state.error != null -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("Повторить") }
        }
        else -> {
            val d = state.details
            val displayTitle = d?.russian?.ifBlank { null } ?: d?.name

            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(posterOf(animeId, d?.image))
                                .memoryCacheKey("poster_$animeId")
                                .placeholderMemoryCacheKey("poster_prev_$animeId")
                                .build(),
                            contentDescription = displayTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                    startY = 300f,
                                )
                            )
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(top = 36.dp, start = 8.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000)),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                        if (d != null) {
                            com.anipulse.app.ui.common.AgeRatingBadge(
                                Modifier.align(Alignment.TopStart).padding(top = 92.dp, start = 12.dp),
                            )
                            if (d.status == "ongoing") {
                                IconButton(
                                    onClick = viewModel::toggleEpisodeNotification,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 60.dp).clip(CircleShape).background(Color(0x66000000)),
                                ) {
                                    Icon(
                                        if (state.episodeNotifyEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                        contentDescription = "Уведомлять о новых сериях",
                                        tint = if (state.episodeNotifyEnabled) Color(0xFFFF4D8D) else Color.White,
                                    )
                                }
                            }
                            IconButton(
                                onClick = viewModel::toggleFavorite,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 36.dp, end = 8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66000000)),
                            ) {
                                Icon(
                                    if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (state.isFavorite) "Убрать из «Моё»" else "В «Моё»",
                                    tint = if (state.isFavorite) Color(0xFFEF5350) else Color.White,
                                )
                            }
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text(displayTitle ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(
                                        d.score?.takeIf { it != "0.0" }?.let { "Shikimori ★ $it" },
                                        d.airedOn?.take(4),
                                        d.episodes?.let { "Эп: $it" },
                                        d.kind?.uppercase(),
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (state.isLoading || d == null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (!state.isLoading && d != null) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(d.genres) { g -> AssistChip(onClick = {}, label = { Text(g.russian ?: g.name) }) }
                        }
                    }

                    d.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        item {
                            // Свёрнутое описание: 4 строки + «Развернуть»/«Свернуть»
                            var expanded by remember { mutableStateOf(false) }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    desc.replace(Regex("\\[[^]]*]"), ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (expanded) "Свернуть" else "Развернуть",
                                    Modifier
                                        .clickable { expanded = !expanded }
                                        .padding(top = 4.dp, bottom = 2.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Большая кнопка «Смотреть/Продолжить» — без поиска серии в списке
                    if (state.dubs.isNotEmpty()) {
                        item {
                            val (ep, posMs) = viewModel.resumeTarget()
                            val sec = posMs / 1000
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFFF4D8D))))
                                    .clickable { viewModel.prepareSession(ep, startOver = false); onPlay() }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (sec > 0) "Продолжить · Серия $ep (%d:%02d)".format(sec / 60, sec % 60)
                                    else "Смотреть · Серия $ep",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                // Статус в «Моё»: Смотрю / В планах / Просмотрено
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("watching" to "Смотрю", "planned" to "В планах", "completed" to "Просмотрено").forEach { (key, label) ->
                            FilterChip(
                                selected = state.status == key,
                                onClick = { viewModel.setStatus(key) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }

                // Моя оценка (1–10) + рейтинг AniPulse
                item {
                    Row(
                        Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            Modifier.weight(1f).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("Рейтинг Shikimori", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("★ ${d.score?.takeIf { it.toDoubleOrNull()?.let { value -> value > 0.0 } == true } ?: "—"}", maxLines = 1, style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFD66B), fontWeight = FontWeight.Bold)
                                Text("Оценка каталога", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            Modifier.weight(1f).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("Рейтинг AniPulse", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("♥ ${state.ratingAvg ?: "—"}", maxLines = 1, style = MaterialTheme.typography.titleLarge, color = Color(0xFFFF4D8D), fontWeight = FontWeight.Bold)
                                Text("${state.ratingCount} оценок", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("Моя оценка", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.isLoggedIn) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            (1..10).forEach { score ->
                                val selected = state.myRating != null && score <= state.myRating!!
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) Color(0x55FF4D8D)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable(enabled = !state.ratingSending) { viewModel.rate(score) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$score",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        // Сообщение об ошибке оценки (если есть). Покажем сразу под кнопками.
                        state.ratingError?.let { err ->
                            Text(
                                err,
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { viewModel.clearRatingError() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            "Войдите в Профиле, чтобы поставить оценку",
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Озвучки
                item {
                    Text(
                        "Озвучка",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    when {
                        state.loadingDubs -> Row(Modifier.padding(16.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text("Ищем озвучки…", Modifier.padding(start = 12.dp))
                        }
                        state.dubs.isEmpty() -> Text(
                            "Видео не найдено",
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.dubs) { dub ->
                                FilterChip(
                                    selected = state.selectedDub?.id == dub.id,
                                    onClick = { viewModel.selectDub(dub) },
                                    label = { Text(dub.title) },
                                )
                            }
                        }
                    }
                }

                // «Порядок просмотра» — если у тайтла есть связанные (sequel/prequel/side_story/…).
                if (state.related.isNotEmpty()) {
                    item {
                        Text(
                            "Порядок просмотра",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        val currentId = viewModel.animeId
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(state.related, key = { _, n -> n.anime?.id ?: 0 }) { idx, node ->
                                val a = node.anime ?: return@itemsIndexed
                                val isCurrent = a.id == currentId
                                Column(
                                    Modifier
                                        .width(118.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) Color(0x33FF4D8D)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable(enabled = !isCurrent) { onOpenTitle(a.id) }
                                        .padding(8.dp),
                                ) {
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF15151F)),
                                    ) {
                                        val url = posterPreviewOf(a.id, a.image)
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(ctx)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        // Порядковый номер (по году выхода)
                                        Box(
                                            Modifier
                                                .padding(4.dp)
                                                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                "#${idx + 1}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        a.russian?.ifBlank { null } ?: a.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(
                                            a.airedOn?.take(4),
                                            node.relationLabel().takeIf { it.isNotEmpty() && !isCurrent },
                                        ).joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isCurrent) {
                                        Text(
                                            "Этот тайтл",
                                            color = Color(0xFFFF4D8D),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.dubs.isNotEmpty()) {
                    item {
                        Text(
                            "Серии",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Поиск по сериям. Примеры: «12», «12-15», «12, 20», «1..24».
                        // Пусто — показываем все. Запрос и фильтр по статусу суммируются.
                        OutlinedTextField(
                            value = episodeQuery,
                            onValueChange = { episodeQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            placeholder = { Text("Поиск серии: 12, 12-15, 1..24") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (episodeQuery.isNotEmpty()) {
                                    IconButton(onClick = { episodeQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Очистить")
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF343442),
                            ),
                        )
                        // Чипы фильтра по статусу
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EpisodeFilter.values().forEach { f ->
                                FilterChip(
                                    selected = episodeFilter == f,
                                    onClick = { episodeFilter = if (episodeFilter == f) EpisodeFilter.ALL else f },
                                    label = { Text(f.label, maxLines = 1, softWrap = false) },
                                )
                            }
                        }
                    }
                    // Сначала фильтруем по статусу, потом по поисковому запросу.
                    val total = viewModel.episodeCount()
                    val byStatus: List<Int> = when (episodeFilter) {
                        EpisodeFilter.ALL -> (1..total).toList()
                        EpisodeFilter.WATCHED -> state.progress.filterValues { it.watched }.keys.sorted()
                        EpisodeFilter.UNWATCHED -> (1..total).filter { state.progress[it]?.watched != true }
                        EpisodeFilter.STARTED -> state.progress
                            .filterValues { !it.watched && it.positionMs > 1000 }
                            .keys.sorted()
                    }
                    val visible: List<Int> = if (episodeQuery.isBlank()) byStatus else {
                        val wanted = parseEpisodeQuery(episodeQuery, total).toSet()
                        byStatus.filter { it in wanted }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                "Серий не найдено",
                                Modifier.fillMaxWidth().padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        item {
                            Text(
                                "Показано ${visible.size} из $total",
                                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(visible.chunked(5)) { rowEps ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowEps.forEach { ep ->
                                    val prog = state.progress[ep]
                                    val watched = prog?.watched == true
                                    val started = prog?.takeIf { !it.watched && it.positionMs > 1000 } != null
                                    val isDownloaded = ep in state.downloadedEpisodes
                                    val isDownloading = state.downloadingEpisode?.first == ep
                                    val downloadPercent = state.downloadingEpisode?.second ?: 0
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    started -> Color(0x33FF4D8D)
                                                    watched -> Color(0x2266BB6A)
                                                    isDownloaded -> Color(0x220288D1)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (started) selectedEpisodeSheet = ep
                                                    else { viewModel.prepareSession(ep, startOver = true); onPlay() }
                                                },
                                                onLongClick = { selectedEpisodeSheet = ep },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                progress = { downloadPercent / 100f },
                                                modifier = Modifier.size(36.dp),
                                                color = Color(0xFFFF4D8D),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "$ep",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = when {
                                                    started -> Color(0xFFFF4D8D)
                                                    watched -> Color(0xFF66BB6A)
                                                    isDownloaded -> Color(0xFF0288D1)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            if (watched) {
                                                Spacer(Modifier.width(2.dp))
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color(0xFF66BB6A),
                                                )
                                            } else if (isDownloaded) {
                                                Spacer(Modifier.width(2.dp))
                                                Icon(
                                                    Icons.Filled.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color(0xFF0288D1),
                                                )
                                            }
                                        }
                                    }
                                }
                                // добивка пустыми ячейками до 5 колонок
                                repeat(5 - rowEps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } // End of if (state.dubs.isNotEmpty())

                // Комментарии
                item {
                    Text(
                        "Комментарии · ${state.comments.size}",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.comments, key = { "cm-${it.id}" }) { cm ->
                    // Спойлеры скрыты до тапа
                    var revealed by remember(cm.id) { mutableStateOf(false) }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Box(Modifier.clickable { commentProfileNick = cm.nick }) {
                            com.anipulse.app.ui.common.Avatar(
                                cm.avatar,
                                30.dp,
                                nick = cm.nick,
                                rev = cm.avatarRev,
                                accountId = cm.userId,
                            )
                        }
                        Column(
                            Modifier
                                .padding(start = 10.dp)
                                .clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .widthIn(max = 290.dp),
                        ) {
                            Text(
                                cm.nick,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    val tag = "@${cm.nick} "
                                    if (!commentInput.contains(tag)) commentInput = tag + commentInput
                                },
                            )
                            if (cm.spoiler && !revealed) {
                                Text(
                                    "⚠ Спойлер — нажми, чтобы открыть",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { revealed = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            } else {
                                Text(cm.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(Modifier.align(Alignment.End).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Ответить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable {
                                    val tag = "@${cm.nick} "
                                    if (!commentInput.contains(tag)) commentInput = tag + commentInput
                                })
                                if (!cm.nick.equals(state.myNick, ignoreCase = true)) {
                                    Text("Профиль", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { commentProfileNick = cm.nick })
                                    Text("Жалоба", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { viewModel.reportComment(cm) })
                                    Text("Блок", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { viewModel.blockCommentAuthor(cm.nick) })
                                } else {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { viewModel.deleteComment(cm.id) })
                                }
                            }
                        }
                    }
                }
                item {
                    if (state.isLoggedIn) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Написать комментарий…") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = Color(0xFFFF4D8D),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = Color(0xFFFF4D8D),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                ),
                                shape = RoundedCornerShape(20.dp),
                                maxLines = 3,
                            )
                            IconButton(
                                onClick = { viewModel.postComment(commentInput); commentInput = "" },
                                enabled = !state.commentSending && commentInput.isNotBlank(),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    } else {
                        Text(
                            "Войдите, чтобы комментировать",
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
                } // End of if (!state.isLoading && d != null)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeBottomSheet(
    ep: Int,
    titleName: String,
    dubName: String?,
    progSec: Long,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadPercent: Int,
    onDismiss: () -> Unit,
    onPlayResume: () -> Unit,
    onPlayStartOver: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Серия $ep",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (titleName.isNotBlank() || !dubName.isNullOrBlank()) {
                        Text(
                            text = buildString {
                                if (titleName.isNotBlank()) append(titleName)
                                if (!dubName.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(dubName)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (isDownloaded) {
                    Surface(
                        color = Color(0xFF2ECC71).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF2ECC71),
                            )
                            Text(
                                "Офлайн",
                                color = Color(0xFF2ECC71),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Progress info if started
            if (progSec > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Остановлено на %d:%02d".format(progSec / 60, progSec % 60),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Action 1: Main Play / Resume button
            Button(
                onClick = onPlayResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (progSec > 0) "Продолжить просмотр" else "Смотреть серию",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            // Action 2: Start over (if started)
            if (progSec > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPlayStartOver() },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Смотреть сначала",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Action 3: Download / Delete download
            when {
                isDownloaded -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDeleteDownload() },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Удалить скачанную серию",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Освободить память устройства",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                isDownloading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadPercent / 100f },
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFFFF4D8D),
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Идёт скачивание ($downloadPercent%)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Пожалуйста, подождите завершения",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                else -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDownload() },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                tint = Color(0xFFFF4D8D),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Скачать для просмотра офлайн",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Серия будет доступна без подключения к сети",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
