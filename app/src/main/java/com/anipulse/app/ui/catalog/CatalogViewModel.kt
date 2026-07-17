package com.anipulse.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.AnimeRepository
import com.anipulse.app.data.shikimori.ShikiAnime
import com.anipulse.app.data.shikimori.ShikiGenre
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogState(
    val items: List<ShikiAnime> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val endReached: Boolean = false,
    val order: String = "popularity",
    val status: String? = null,
    val searchQuery: String = "",
    val allGenres: List<ShikiGenre> = emptyList(),
    val selectedGenres: Set<Long> = emptySet(),
    val genresSheetOpen: Boolean = false,
    /** Рейтинги AniPulse по shikimori_id — бейджи «♥» на постерах. */
    val pulseRatings: Map<Long, Double> = emptyMap(),
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repo: AnimeRepository,
    private val gateway: com.anipulse.app.data.GatewayApi,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var searchJob: Job? = null

    // Поколение выдачи: ответ запроса, стартовавшего до смены фильтра/жанра/поиска,
    // не должен приземляться в уже обнулённый список (смешение старой и новой выдачи).
    private var generation = 0

    init {
        loadNextPage()
        loadGenres()
    }

    private fun loadGenres() {
        viewModelScope.launch {
            runCatching { repo.genres() }.onSuccess { list ->
                val animeGenres = list
                    .filter { it.entryType == null || it.entryType == "Anime" }
                    .distinctBy { it.id }
                    .sortedBy { it.russian?.ifBlank { null } ?: it.name }
                _state.update { it.copy(allGenres = animeGenres) }
            }
        }
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.isLoading || s.endReached) return
        val gen = generation
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (s.searchQuery.isBlank()) {
                    repo.catalog(
                        page = s.page,
                        order = s.order,
                        status = s.status,
                        genre = s.selectedGenres.takeIf { it.isNotEmpty() }?.joinToString(","),
                    )
                } else {
                    repo.search(s.searchQuery)
                }
            }.onSuccess { list ->
                if (gen != generation) return@onSuccess // фильтр сменился, пока летел запрос
                _state.update {
                    it.copy(
                        items = it.items + list,
                        page = it.page + 1,
                        isLoading = false,
                        endReached = list.isEmpty() || it.searchQuery.isNotBlank(),
                    )
                }
                loadPulseRatings(list.map { it.id })
            }.onFailure { e ->
                if (gen != generation) return@onFailure
                _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка сети") }
            }
        }
    }

    private fun loadPulseRatings(ids: List<Long>) {
        val missing = ids.filter { it !in _state.value.pulseRatings }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            runCatching { gateway.ratings(missing.joinToString(",")) }.onSuccess { map ->
                val parsed = map.mapNotNull { (k, v) ->
                    val id = k.toLongOrNull() ?: return@mapNotNull null
                    val avg = v.avg ?: return@mapNotNull null
                    id to avg
                }.toMap()
                if (parsed.isNotEmpty()) _state.update { it.copy(pulseRatings = it.pulseRatings + parsed) }
            }
        }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            generation++
            _state.update { it.copy(items = emptyList(), page = 1, endReached = false, isLoading = false) }
            loadNextPage()
        }
    }

    fun setFilter(order: String = _state.value.order, status: String? = _state.value.status) {
        generation++
        _state.update {
            it.copy(order = order, status = status, items = emptyList(), page = 1, endReached = false, isLoading = false)
        }
        loadNextPage()
    }

    fun toggleGenre(id: Long) {
        generation++
        _state.update {
            val sel = if (id in it.selectedGenres) it.selectedGenres - id else it.selectedGenres + id
            it.copy(selectedGenres = sel, items = emptyList(), page = 1, endReached = false, isLoading = false)
        }
        loadNextPage()
    }

    fun clearGenres() {
        if (_state.value.selectedGenres.isEmpty()) return
        generation++
        _state.update { it.copy(selectedGenres = emptySet(), items = emptyList(), page = 1, endReached = false, isLoading = false) }
        loadNextPage()
    }

    fun setGenresSheet(open: Boolean) {
        _state.update { it.copy(genresSheetOpen = open) }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        loadNextPage()
    }
}
