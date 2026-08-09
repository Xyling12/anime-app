package com.anipulse.app.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.anipulse.app.data.Api
import com.anipulse.app.ui.catalog.PosterCard
import com.anipulse.app.ui.catalog.CatalogViewModel
import com.anipulse.app.ui.common.topSafePadding

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun HomeAllScreen(
    section: HomeSection?,
    onBack: () -> Unit,
    onTitleClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val catalogState by catalogViewModel.state.collectAsState()
    val continueItems by viewModel.continueWatching.collectAsState()
    LaunchedEffect(section) {
        when (section) {
            HomeSection.POPULAR -> catalogViewModel.setFilter(order = "popularity")
            HomeSection.TOP_RATED -> catalogViewModel.setFilter(order = "ranked")
            else -> Unit
        }
    }
    val title = when (section) {
        HomeSection.CONTINUE -> "Продолжить просмотр"
        HomeSection.FOR_YOU -> "Для вас"
        HomeSection.POPULAR -> "Популярное"
        HomeSection.TOP_RATED -> "Высший рейтинг"
        null -> "Подборка"
    }
    // Экран не вкладка, поэтому корневой Box в AnimeLibRoot не даёт ему отступ от системных
    // панелей — заголовок налезал на часы статус-бара.
    Column(Modifier.fillMaxSize().topSafePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        PullToRefreshBox(
            isRefreshing = if (section == HomeSection.POPULAR || section == HomeSection.TOP_RATED) {
                catalogState.isLoading && catalogState.items.isNotEmpty()
            } else state.isRefreshing,
            onRefresh = {
                if (section == HomeSection.POPULAR || section == HomeSection.TOP_RATED) catalogViewModel.refresh()
                else viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
          if (section == HomeSection.CONTINUE) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(continueItems, key = { it.animeId }) { item ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { onTitleClick(item.animeId) },
                        shape = RoundedCornerShape(16.dp), color = Color(0xFF15151F),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                Api.GATEWAY + "poster/${item.posterId}", null,
                                Modifier.width(128.dp).height(76.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF24242F)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("Серия ${item.episode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFFFF4D8D),
                                )
                            }
                        }
                    }
                }
            }
          } else {
            val anime = when (section) {
                HomeSection.FOR_YOU -> state.forYou
                HomeSection.POPULAR, HomeSection.TOP_RATED -> catalogState.items
                else -> emptyList()
            }
            if ((state.isLoading || catalogState.isLoading) && anime.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(anime, key = { _, item -> item.id }) { index, item ->
                        if ((section == HomeSection.POPULAR || section == HomeSection.TOP_RATED) && index >= anime.lastIndex - 5) {
                            LaunchedEffect(anime.size) { catalogViewModel.loadNextPage() }
                        }
                        val rating = if (section == HomeSection.FOR_YOU) state.pulseRatings[item.id] else catalogState.pulseRatings[item.id]
                        PosterCard(item, onClick = { onTitleClick(item.id) }, pulseRating = rating)
                    }
                }
            }
          }
        }
    }
}
