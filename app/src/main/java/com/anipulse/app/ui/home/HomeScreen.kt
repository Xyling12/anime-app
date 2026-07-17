package com.anipulse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.anipulse.app.data.Api
import com.anipulse.app.data.shikimori.ShikiAnime
import com.anipulse.app.data.shikimori.posterOf

private val PulseGradient = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFFF4D8D)))

@Composable
fun HomeScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueItems by viewModel.continueWatching.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp),
    ) {
        // Заголовок «AniPulse» теперь в общей шапке (AnimeLibRoot) — здесь не дублируем.

        // Баннер-карусель топ-онгоингов
        if (state.banner.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { state.banner.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(200.dp),
                pageSpacing = 12.dp,
            ) { page ->
                val anime = state.banner[page]
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTitleClick(anime.id) },
                ) {
                    AsyncImage(
                        model = posterOf(anime.id, anime.image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(Color(0xE6101018), Color(0x33101018)))
                        )
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxWidth(0.62f)) {
                        Text(
                            "СЕЙЧАС ВЫХОДИТ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4D8D),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            anime.russian?.ifBlank { null } ?: anime.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        anime.score?.takeIf { it != "0.0" }?.let {
                            Text("★ $it", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD54F))
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onTitleClick(anime.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PulseGradient)
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Смотреть", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            // Индикатор страниц
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(state.banner.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Color(0xFFFF4D8D)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }

        // Продолжить просмотр
        if (continueItems.isNotEmpty()) {
            SectionHeader("Продолжить просмотр")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(continueItems, key = { it.animeId }) { p ->
                    Column(Modifier.width(130.dp).clickable { onTitleClick(p.animeId) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(
                                model = Api.GATEWAY + "poster/${p.posterId}",
                                contentDescription = p.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Box(
                                Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                Text("Серия ${p.episode}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (p.durationMs > 0) {
                            LinearProgressIndicator(
                                progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = Color(0xFFFF4D8D),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Text(
                            p.title,
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (state.forYou.isNotEmpty()) {
            SectionHeader("Для вас")
            PosterRow(state.forYou, onTitleClick)
        }
        if (state.popular.isNotEmpty()) {
            SectionHeader("Популярное")
            PosterRow(state.popular, onTitleClick)
        }
        if (state.topRated.isNotEmpty()) {
            SectionHeader("Высший рейтинг")
            PosterRow(state.topRated, onTitleClick)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PosterRow(items: List<ShikiAnime>, onTitleClick: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { anime ->
            Column(Modifier.width(110.dp).clickable { onTitleClick(anime.id) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp))) {
                    AsyncImage(
                        model = posterOf(anime.id, anime.image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    anime.score?.takeIf { it != "0.0" }?.let { score ->
                        Text(
                            score,
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
                    anime.russian?.ifBlank { null } ?: anime.name,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
