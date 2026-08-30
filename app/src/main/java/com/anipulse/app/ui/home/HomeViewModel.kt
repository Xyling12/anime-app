package com.anipulse.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.AnimeRepository
import com.anipulse.app.data.db.ContinueHidden
import com.anipulse.app.data.db.ContinueHiddenDao
import com.anipulse.app.data.db.EpisodeProgress
import com.anipulse.app.data.db.ProgressDao
import com.anipulse.app.data.shikimori.ShikiAnime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val banner: List<ShikiAnime> = emptyList(),    // топ-онгоинги для карусели
    val forYou: List<ShikiAnime> = emptyList(),    // персональные рекомендации
    val popular: List<ShikiAnime> = emptyList(),   // популярное всех времён
    val topRated: List<ShikiAnime> = emptyList(),  // высший рейтинг
    val pulseRatings: Map<Long, Double> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progressDao: ProgressDao,
    private val continueHiddenDao: ContinueHiddenDao,
    private val repo: AnimeRepository,
    private val gateway: com.anipulse.app.data.GatewayApi,
    private val syncRepository: com.anipulse.app.data.SyncRepository,
) : ViewModel() {

    /**
     * Лента «Продолжить просмотр»: недосмотренные тайтлы, свежие сверху, без скрытых.
     *
     * Скрытие сравнивается по времени: тайтл прячется, только пока метка `hiddenAt` свежее
     * последнего прогресса. Открыл серию заново — тайтл вернулся в ленту сам, без настроек.
     * Сравнение идёт по исходному `updatedAt`, до подстановки следующей серии ниже.
     */
    val continueWatching: StateFlow<List<EpisodeProgress>> =
        combine(progressDao.continueWatching(), continueHiddenDao.all()) { list, hidden ->
            val hiddenAt = hidden.associate { it.animeId to it.hiddenAt }
            list.mapNotNull { item ->
                if (item.updatedAt <= (hiddenAt[item.animeId] ?: Long.MIN_VALUE)) return@mapNotNull null
                when {
                    !item.watched && item.positionMs > 1000 -> item
                    item.watched && (item.totalEpisodes == 0 || item.episode < item.totalEpisodes) ->
                        item.copy(episode = item.episode + 1, positionMs = 0, watched = false)
                    else -> null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** «Скрыть из подбора» на карточке ленты. */
    fun hideFromContinue(animeId: Long) {
        viewModelScope.launch {
            continueHiddenDao.hide(ContinueHidden(animeId, System.currentTimeMillis()))
        }
    }

    /** Отмена скрытия — кнопка «Вернуть» в снекбаре. */
    fun unhideFromContinue(animeId: Long) {
        viewModelScope.launch { continueHiddenDao.unhide(animeId) }
    }

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh() = refresh(initial = false)

    private fun refresh(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = initial, isRefreshing = !initial) }
            runCatching { syncRepository.syncAll() }
            val ongoing = async { runCatching { repo.catalog(page = 1, order = "popularity", status = "ongoing") }.getOrDefault(emptyList()) }
            val popular = async { runCatching { repo.catalog(page = 1, order = "popularity") }.getOrDefault(emptyList()) }
            val ranked = async { runCatching { repo.catalog(page = 1, order = "ranked") }.getOrDefault(emptyList()) }
            val ong = ongoing.await()
            val popularItems = popular.await()
            val rankedItems = ranked.await()
            _state.update {
                it.copy(
                    banner = ong.shuffled().take(8),
                    popular = popularItems,
                    topRated = rankedItems,
                    isLoading = false,
                )
            }
            loadPulseRatings((ong + popularItems + rankedItems).map { it.id })
            loadRecommendations()
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    /**
     * «Для вас»: similar-тайтлы Shikimori по последним просмотренным.
     * Кандидат ценнее, если похож сразу на несколько наших тайтлов; просмотренное исключаем.
     */
    private suspend fun loadPulseRatings(ids: List<Long>) {
        runCatching { gateway.ratings(ids.distinct().joinToString(",")) }.onSuccess { map ->
            val ratings = map.mapNotNull { (id, value) ->
                val animeId = id.toLongOrNull() ?: return@mapNotNull null
                val avg = value.avg ?: return@mapNotNull null
                animeId to avg
            }.toMap()
            _state.update { it.copy(pulseRatings = it.pulseRatings + ratings) }
        }
    }

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
        _state.update { it.copy(forYou = recs) }
        loadPulseRatings(recs.map { it.id })
    }
}
