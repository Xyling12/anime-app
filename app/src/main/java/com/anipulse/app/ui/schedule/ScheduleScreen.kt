@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.anipulse.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.anipulse.app.ui.common.PillChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anipulse.app.data.Api
import com.anipulse.app.data.shikimori.posterPreviewOf

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
            PillChip(
                selected = state.tab == 0,
                onClick = { viewModel.setTab(0) },
                label = "Расписание",
            )
            PillChip(
                selected = state.tab == 1,
                onClick = { viewModel.setTab(1) },
                label = "Обновления",
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
    
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.days) { day ->
                val dayOfWeekStr = when (day.date.dayOfWeek.value) {
                    1 -> "ПН" 2 -> "ВТ" 3 -> "СР" 4 -> "ЧТ" 5 -> "ПТ" 6 -> "СБ" else -> "ВС"
                }
                val isSelected = state.selectedDayDate == day.date
                PillChip(
                    selected = isSelected,
                    onClick = { viewModel.selectDay(day.date) },
                    label = "$dayOfWeekStr - ${day.date.dayOfMonth}"
                )
            }
        }

        val selectedDay = state.days.find { it.date == state.selectedDayDate }
        if (selectedDay != null) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "day-${selectedDay.date}") {
                    Text(
                        selectedDay.label,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(selectedDay.entries, key = { "e-${selectedDay.date}-${it.anime.id}" }) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.timeOf(e) ?: "--:--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTitleClick(e.anime.id) },
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val sharedTransitionScope = com.anipulse.app.ui.LocalSharedTransitionScope.current
                                val animatedVisibilityScope = com.anipulse.app.ui.LocalAnimatedVisibilityScope.current
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(posterPreviewOf(e.anime.id, e.anime.image))
                                        .memoryCacheKey("poster_prev_${e.anime.id}")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 80.dp)
                                        
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(
                                        e.anime.russian?.ifBlank { null } ?: e.anime.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Серия ${e.nextEpisode}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun UpdatesList(viewModel: ScheduleViewModel, onTitleClick: (Long) -> Unit) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.updates) { u ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.openUpdate(u, onTitleClick) },
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = u.poster?.let { Api.GATEWAY.removeSuffix("/alapi/") + it },
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 60.dp, height = 80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A38)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                u.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                u.episode?.let { ep ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Серия $ep",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                viewModel.freshLabel(u)?.let { label ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
