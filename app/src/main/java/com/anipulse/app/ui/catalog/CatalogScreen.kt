@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.anipulse.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.anipulse.app.ui.common.PillChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.anipulse.app.data.shikimori.ShikiAnime
import com.anipulse.app.data.shikimori.posterPreviewOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                // справа шире — чтобы не залезать под плавающую кнопку меню ☰
                .padding(start = 16.dp, end = 72.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search, 
                    contentDescription = null, 
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearch,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (state.searchQuery.isEmpty()) {
                            Text("Поиск аниме…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                        innerTextField()
                    }
                )
            }
        }

        // Подсказки при поиске: первые совпадения, тап — открыть тайтл
        if (state.searchQuery.isNotBlank() && state.items.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                state.items.take(6).forEach { anime ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTitleClick(anime.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = posterPreviewOf(anime.id, anime.image),
                            contentDescription = null,
                            modifier = Modifier.size(width = 34.dp, height = 48.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = anime.russian?.ifBlank { null } ?: anime.name,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        anime.score?.takeIf { it != "0.0" }?.let {
                            Text("★ $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD54F))
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillChip(
                selected = state.status == null,
                onClick = { viewModel.setFilter(status = null) },
                label = "Все",
            )
            PillChip(
                selected = state.status == "ongoing",
                onClick = { viewModel.setFilter(status = "ongoing") },
                label = "Онгоинги",
            )
            PillChip(
                selected = state.order == "ranked",
                onClick = { viewModel.setFilter(order = if (state.order == "ranked") "popularity" else "ranked") },
                label = "По рейтингу",
            )
            PillChip(
                selected = state.selectedGenres.isNotEmpty(),
                onClick = { viewModel.setGenresSheet(true) },
                label = if (state.selectedGenres.isEmpty()) "Жанры" else "Жанры (${state.selectedGenres.size})"
            )
        }

        if (state.genresSheetOpen) {
            ModalBottomSheet(onDismissRequest = { viewModel.setGenresSheet(false) }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Жанры",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.selectedGenres.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearGenres) { Text("Сбросить") }
                    }
                }
                if (state.allGenres.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.allGenres.forEach { g ->
                            PillChip(
                                selected = g.id in state.selectedGenres,
                                onClick = { viewModel.toggleGenre(g.id) },
                                label = g.russian?.ifBlank { null } ?: g.name,
                            )
                        }
                    }
                }
            }
        }

        when {
            state.error != null && state.items.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry) { Text("Повторить") }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.items, key = { _, a -> a.id }) { index, anime ->
                        if (index >= state.items.size - 6) viewModel.loadNextPage()
                        PosterCard(
                            anime = anime,
                            pulseRating = state.pulseRatings[anime.id],
                            onClick = { onTitleClick(anime.id) },
                        )
                    }
                    if (state.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.animation.ExperimentalSharedTransitionApi
@Composable
fun PosterCard(anime: ShikiAnime, onClick: () -> Unit, pulseRating: Double? = null) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            // Постер с автоподбором из открытых источников (шлюз), под ним — плейсхолдер
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A2A38), Color(0xFF16161C)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = anime.russian?.ifBlank { null } ?: anime.name,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9E9EB0),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            
            val sharedTransitionScope = com.anipulse.app.ui.LocalSharedTransitionScope.current
            val animatedVisibilityScope = com.anipulse.app.ui.LocalAnimatedVisibilityScope.current
            
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(posterPreviewOf(anime.id, anime.image))
                    .memoryCacheKey("poster_prev_${anime.id}")
                    .build(),
                contentDescription = anime.russian ?: anime.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            anime.score?.takeIf { it != "0.0" }?.let { score ->
                Text(
                    text = score,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD54F),
                )
            }
            // Оценка AniPulse (свой рейтинг пользователей) — слева сверху
            pulseRating?.let { pr ->
                Text(
                    text = "♥ %.1f".format(pr),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF4D8D),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                    .padding(top = 24.dp)
            )
        }
        Text(
            text = anime.russian?.ifBlank { null } ?: anime.name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
