@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.anipulse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import com.anipulse.app.data.shikimori.posterPreviewOf
import kotlinx.coroutines.delay

private val Pulse = Color(0xFFFF4D8D)
private val Panel = Color(0xFF15151F)

enum class HomeSection(val route: String) {
    CONTINUE("continue"),
    FOR_YOU("for_you"),
    POPULAR("popular"),
    TOP_RATED("top_rated");

    companion object {
        fun fromRoute(value: String?): HomeSection? = entries.firstOrNull { it.route == value }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTitleClick: (Long) -> Unit,
    onShowAll: (HomeSection) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueItems by viewModel.continueWatching.collectAsState()
    val state by viewModel.state.collectAsState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      Column(
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (state.banner.isNotEmpty()) {
            val pager = rememberPagerState(pageCount = { state.banner.size })
            LaunchedEffect(state.banner.size) {
                while (true) { delay(9000); pager.animateScrollToPage((pager.currentPage + 1) % state.banner.size) }
            }
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().height(300.dp),
            ) { page ->
                val anime = state.banner[page]
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Panel)
                        .clickable { onTitleClick(anime.id) },
                ) {
                    AsyncImage(
                        model = posterPreviewOf(anime.id, anime.image), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF209090F)), startY = 80f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text("ОНГОИНГ", style = MaterialTheme.typography.labelSmall, color = Pulse, fontWeight = FontWeight.Bold)
                        Text(
                            anime.russian?.ifBlank { null } ?: anime.name,
                            style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            anime.score?.takeIf { it != "0.0" }?.let { RatingText("Shikimori ★ $it", Color(0xFFFFD66B)) }
                            state.pulseRatings[anime.id]?.let { RatingText("AniPulse ♥ %.1f".format(it), Pulse) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onTitleClick(anime.id) }, shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Pulse),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Смотреть")
                            }
                            FilledTonalIconButton(onClick = { onTitleClick(anime.id) }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Filled.Add, "В Моё")
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center) {
                repeat(state.banner.size) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).width(if (i == pager.currentPage) 20.dp else 6.dp).height(5.dp).clip(CircleShape).background(if (i == pager.currentPage) Pulse else Color(0xFF343440)))
                }
            }
        }

        if (continueItems.isNotEmpty()) {
            SectionTitle("Продолжить просмотр") { onShowAll(HomeSection.CONTINUE) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueItems, key = { it.animeId }) { item ->
                    Column(Modifier.width(210.dp).clickable { onTitleClick(item.animeId) }) {
                        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) {
                            AsyncImage(Api.GATEWAY + "poster/${item.posterId}", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD909090F)))))
                            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color(0xD915151F), shape = RoundedCornerShape(7.dp)) {
                                Text("Серия ${item.episode}", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f },
                            Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(CircleShape), color = Pulse,
                            trackColor = Color(0xFF30303A),
                        )
                        Text(item.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (state.forYou.isNotEmpty()) AnimeRail("Для вас", state.forYou, state.pulseRatings, onTitleClick) { onShowAll(HomeSection.FOR_YOU) }
        if (state.popular.isNotEmpty()) AnimeRail("Популярное", state.popular, state.pulseRatings, onTitleClick) { onShowAll(HomeSection.POPULAR) }
        if (state.topRated.isNotEmpty()) AnimeRail("Высший рейтинг", state.topRated, state.pulseRatings, onTitleClick) { onShowAll(HomeSection.TOP_RATED) }
        Spacer(Modifier.height(24.dp))
      }
    }
}

@Composable private fun RatingText(text: String, color: Color) = Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)

@Composable private fun SectionTitle(title: String, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Все ›",
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable private fun AnimeRail(title: String, items: List<ShikiAnime>, ratings: Map<Long, Double>, onClick: (Long) -> Unit, onAll: () -> Unit) {
    SectionTitle(title, onAll)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        items(items, key = { it.id }) { anime ->
            Column(Modifier.width(132.dp).clickable { onClick(anime.id) }) {
                Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) {
                    AsyncImage(posterPreviewOf(anime.id, anime.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    anime.score?.takeIf { it != "0.0" }?.let {
                        RatingBadge("★ $it", Color(0xFFFFD66B), Modifier.align(Alignment.TopEnd))
                    }
                }
                Text(anime.russian?.ifBlank { null } ?: anime.name, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                ratings[anime.id]?.let { Text("AniPulse ♥ %.1f".format(it), style = MaterialTheme.typography.labelSmall, color = Pulse) }
            }
        }
    }
}

@Composable private fun RatingBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier.padding(6.dp), color = Color(0xDD09090F), shape = RoundedCornerShape(7.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
