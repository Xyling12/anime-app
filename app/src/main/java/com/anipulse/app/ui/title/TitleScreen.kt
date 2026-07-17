@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.anipulse.app.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun TitleScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: TitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Диалог «Сначала / Продолжить» для начатой серии
    var resumeDialogEp by remember { mutableStateOf<Int?>(null) }
    resumeDialogEp?.let { ep ->
        val prog = state.progress[ep]
        val sec = (prog?.positionMs ?: 0) / 1000
        AlertDialog(
            onDismissRequest = { resumeDialogEp = null },
            title = { Text("Серия $ep") },
            text = { Text("Продолжить с %d:%02d или смотреть сначала?".format(sec / 60, sec % 60)) },
            confirmButton = {
                TextButton(onClick = { viewModel.prepareSession(ep, startOver = false); resumeDialogEp = null; onPlay() }) {
                    Text("Продолжить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prepareSession(ep, startOver = true); resumeDialogEp = null; onPlay() }) {
                    Text("Сначала")
                }
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

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(posterOf(animeId, d?.image))
                                .memoryCacheKey("poster_$animeId")
                                .build(),
                            contentDescription = displayTitle,
                            modifier = Modifier.fillMaxSize().then(
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                                        Modifier.sharedElement(
                                            rememberSharedContentState(key = "poster_$animeId"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }
                                } else Modifier
                            ),
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
                                        d.score?.takeIf { it != "0.0" }?.let { "★ $it" },
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
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Моя оценка",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        state.ratingAvg?.let { avg ->
                            Text(
                                "♥ $avg AniPulse · ${state.ratingCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
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
                                        .clickable { viewModel.rate(score) },
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

                if (state.dubs.isNotEmpty()) {
                    item {
                        Text(
                            "Серии",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // Компактная сетка номеров серий: зелёная галочка = просмотрено,
                    // розовая рамка = начата (недосмотрена), меньше листать.
                    items((1..viewModel.episodeCount()).chunked(5)) { rowEps ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowEps.forEach { ep ->
                                val prog = state.progress[ep]
                                val watched = prog?.watched == true
                                val started = prog?.takeIf { !it.watched && it.positionMs > 1000 } != null
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                started -> Color(0x33FF4D8D)
                                                watched -> Color(0x2266BB6A)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .clickable {
                                            if (started) resumeDialogEp = ep
                                            else { viewModel.prepareSession(ep, startOver = true); onPlay() }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$ep",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = when {
                                                started -> Color(0xFFFF4D8D)
                                                watched -> Color(0xFF66BB6A)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        if (watched) {
                                            Spacer(Modifier.width(3.dp))
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = Color(0xFF66BB6A),
                                            )
                                        }
                                    }
                                }
                            }
                            // добивка пустыми ячейками до 5 колонок
                            repeat(5 - rowEps.size) { Spacer(Modifier.weight(1f)) }
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
                        com.anipulse.app.ui.common.Avatar(cm.avatar, 30.dp)
                        Column(
                            Modifier
                                .padding(start = 10.dp)
                                .clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .weight(1f),
                        ) {
                            Text(
                                cm.nick,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
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
                        }
                    }
                }
                item {
                    if (state.isLoggedIn) {
                        var commentInput by remember { mutableStateOf("") }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Написать комментарий…") },
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
