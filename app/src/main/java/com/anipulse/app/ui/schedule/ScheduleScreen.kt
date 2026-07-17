package com.anipulse.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.anipulse.app.data.Api
import com.anipulse.app.data.shikimori.posterOf

@Composable
fun ScheduleScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        // Заголовок теперь в общей шапке (AnimeLibRoot) — здесь не дублируем.

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.tab == 0,
                onClick = { viewModel.setTab(0) },
                label = { Text("Расписание", maxLines = 1, softWrap = false) },
            )
            FilterChip(
                selected = state.tab == 1,
                onClick = { viewModel.setTab(1) },
                label = { Text("Обновления", maxLines = 1, softWrap = false) },
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Повторить") }
            }
            state.tab == 0 -> ScheduleList(viewModel, onTitleClick)
            else -> UpdatesList(viewModel, onTitleClick)
        }
    }
}

@Composable
private fun ScheduleList(viewModel: ScheduleViewModel, onTitleClick: (Long) -> Unit) {
    val state by viewModel.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Text(
                    day.label,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(day.entries, key = { "e-${day.date}-${it.anime.id}" }) { e ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTitleClick(e.anime.id) },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = posterOf(e.anime.id, e.anime.image),
                            contentDescription = null,
                            modifier = Modifier.size(width = 52.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                e.anime.russian?.ifBlank { null } ?: e.anime.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOfNotNull("Серия ${e.nextEpisode}", viewModel.timeOf(e)).joinToString(" — "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun UpdatesList(viewModel: ScheduleViewModel, onTitleClick: (Long) -> Unit) {
    val state by viewModel.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.updates) { u ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.openUpdate(u, onTitleClick) },
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = u.poster?.let { Api.GATEWAY.removeSuffix("/alapi/") + it },
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 52.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A38)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            u.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                u.episode?.let { "Серия $it" },
                                "AniLibria",
                                viewModel.freshLabel(u),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
