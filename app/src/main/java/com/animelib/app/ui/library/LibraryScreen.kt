package com.animelib.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.animelib.app.data.Api

@Composable
fun LibraryScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val all by viewModel.favorites.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val items = if (filter == "all") all else all.filter { it.status == filter }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        // Заголовок теперь в общей шапке (AnimeLibRoot) — здесь не дублируем.

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to "Все",
                "watching" to "Смотрю",
                "planned" to "В планах",
                "completed" to "Просмотрено",
            ).forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { viewModel.setFilter(key) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Добавляйте тайтлы сердечком ♡ на странице аниме",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.animeId }) { fav ->
                    Column(Modifier.clickable { onTitleClick(fav.animeId) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = Api.GATEWAY + "poster/${fav.animeId}",
                                contentDescription = fav.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            val statusLabel = when (fav.status) {
                                "watching" -> "Смотрю"
                                "planned" -> "В планах"
                                "completed" -> "Просмотрено ✓"
                                else -> null
                            }
                            statusLabel?.let { st ->
                                Text(
                                    st,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (fav.status) {
                                        "watching" -> Color(0xFFFF4D8D)
                                        "completed" -> Color(0xFF66BB6A)
                                        else -> Color.White
                                    },
                                )
                            }
                            fav.score?.takeIf { it != "0.0" }?.let { score ->
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
                        }
                        Text(
                            fav.title,
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
