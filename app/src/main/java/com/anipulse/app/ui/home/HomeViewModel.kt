package com.anipulse.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.AnimeRepository
import com.anipulse.app.data.db.EpisodeProgress
import com.anipulse.app.data.db.ProgressDao
import com.anipulse.app.data.shikimori.ShikiAnime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val banner: List<ShikiAnime> = emptyList(),    // топ-онгоинги для карусели
    val forYou: List<ShikiAnime> = emptyList(),    // персональные рекомендации
    val popular: List<ShikiAnime> = emptyList(),   // популярное всех времён
    val topRated: List<ShikiAnime> = emptyList(),  // высший рейтинг
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progressDao: ProgressDao,
    private val repo: AnimeRepository,
) : ViewModel() {

    /** Лента «Продолжить просмотр»: недосмотренные тайтлы, свежие сверху. */
    val continueWatching: StateFlow<List<EpisodeProgress>> =
        progressDao.continueWatching()
            .map { list -> list.filter { !it.watched && it.positionMs > 1000 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ongoing = async { runCatching { repo.catalog(page = 1, order = "popularity", status = "ongoing") }.getOrDefault(emptyList()) }
            val popular = async { runCatching { repo.catalog(page = 1, order = "popularity") }.getOrDefault(emptyList()) }
            val ranked = async { runCatching { repo.catalog(page = 1, order = "ranked") }.getOrDefault(emptyList()) }
            val ong = ongoing.await()
            _state.update {
                it.copy(
                    banner = ong.take(6),
                    popular = popular.await(),
                    topRated = ranked.await(),
                    isLoading = false,
                )
            }
            loadRecommendations()
        }
    }

    /**
     * «Для вас»: similar-тайтлы Shikimori по последним просмотренным.
     * Кандидат ценнее, если похож сразу на несколько наших тайтлов; просмотренное исключаем.
     */
    private suspend fun loadRecommendations() {
        val seeds = runCatching { progressDao.recentAnimeIds(3) }.getOrDefault(emptyList())
        if (seeds.isEmpty()) return
        val seen = runCatching { progressDao.allAnimeIds() }.getOrDefault(emptyList()).toSet()
        val candidates = mutableMapOf<Long, Pair<ShikiAnime, Int>>() // id -> (тайтл, сколько сидов на него указало)
        for (seed in seeds) {
            val similar = runCatching { repo.similar(seed) }.getOrDefault(emptyList())
            for (a in similar) {
                if (a.id in seen) continue
                val prev = candidates[a.id]
                candidates[a.id] = a to ((prev?.second ?: 0) + 1)
            }
        }
        val recs = candidates.values
            .sortedWith(
                compareByDescending<Pair<ShikiAnime, Int>> { it.second }
                    .thenByDescending { it.first.score?.toFloatOrNull() ?: 0f }
            )
            .map { it.first }
            .take(20)
        _state.update { it.copy(forYou = recs) }
    }
}
