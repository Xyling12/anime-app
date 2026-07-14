package com.animelib.app.ui.title

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.AnimeRepository
import com.animelib.app.data.db.EpisodeProgress
import com.animelib.app.data.db.Favorite
import com.animelib.app.data.db.FavoriteDao
import com.animelib.app.data.db.ProgressDao
import com.animelib.app.data.shikimori.ShikiAnimeDetails
import com.animelib.app.data.video.Dub
import com.animelib.app.data.video.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TitleState(
    val details: ShikiAnimeDetails? = null,
    val dubs: List<Dub> = emptyList(),
    val selectedDub: Dub? = null,
    val isLoading: Boolean = true,
    val loadingDubs: Boolean = true,
    val error: String? = null,
    val progress: Map<Int, EpisodeProgress> = emptyMap(),
    val isFavorite: Boolean = false,
    val status: String = "none", // watching / planned / completed / none
    // Соцчасть
    val ratingAvg: Double? = null,
    val ratingCount: Int = 0,
    val myRating: Int? = null,
    val comments: List<com.animelib.app.data.ChatMessage> = emptyList(),
    val isLoggedIn: Boolean = false,
    val commentSending: Boolean = false,
)

@HiltViewModel
class TitleViewModel @Inject constructor(
    private val repo: AnimeRepository,
    private val session: PlaybackSession,
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
    private val gateway: com.animelib.app.data.GatewayApi,
    private val settings: com.animelib.app.data.SettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val animeId: Long = checkNotNull(savedStateHandle["animeId"])

    private val _state = MutableStateFlow(TitleState())
    val state: StateFlow<TitleState> = _state.asStateFlow()

    init {
        load()
        // Прогресс просмотра — реактивно (обновляется после выхода из плеера).
        viewModelScope.launch {
            progressDao.forAnimeFlow(animeId).collect { list ->
                _state.update { it.copy(progress = list.associateBy { p -> p.episode }) }
            }
        }
        viewModelScope.launch {
            favoriteDao.get(animeId).collect { fav ->
                _state.update { it.copy(isFavorite = fav != null, status = fav?.status ?: "none") }
            }
        }
        _state.update { it.copy(isLoggedIn = settings.authToken != null) }
        loadSocial()
    }

    private fun bearer(): String? = settings.authToken?.let { "Bearer $it" }

    private fun loadSocial() {
        viewModelScope.launch {
            runCatching { gateway.rating(animeId, bearer()) }.onSuccess { r ->
                _state.update { it.copy(ratingAvg = r.avg, ratingCount = r.count, myRating = r.my) }
            }
            runCatching { gateway.comments(animeId.toString()) }.onSuccess { list ->
                _state.update { it.copy(comments = list) }
            }
        }
    }

    fun rate(score: Int) {
        val b = bearer() ?: return
        viewModelScope.launch {
            runCatching { gateway.sendRating(b, com.animelib.app.data.RatingRequest(animeId, score)) }
                .onSuccess { r ->
                    _state.update { it.copy(ratingAvg = r.avg, ratingCount = r.count, myRating = r.my) }
                }
        }
    }

    fun postComment(text: String) {
        val b = bearer() ?: return
        if (text.isBlank()) return
        _state.update { it.copy(commentSending = true) }
        viewModelScope.launch {
            runCatching { gateway.sendComment(b, com.animelib.app.data.CommentRequest(animeId.toString(), text.trim())) }
                .onSuccess { cm ->
                    _state.update { it.copy(comments = it.comments + cm, commentSending = false) }
                }
                .onFailure { _state.update { it.copy(commentSending = false) } }
        }
    }

    fun toggleFavorite() {
        val s = _state.value
        viewModelScope.launch {
            if (s.isFavorite) {
                favoriteDao.delete(animeId)
            } else {
                favoriteDao.upsert(makeFavorite("none"))
            }
        }
    }

    /** Выбор статуса добавляет тайтл в «Моё»; повторный тап по тому же статусу снимает его. */
    fun setStatus(status: String) {
        val s = _state.value
        viewModelScope.launch {
            val newStatus = if (s.status == status) "none" else status
            favoriteDao.upsert(makeFavorite(newStatus))
        }
    }

    private fun makeFavorite(status: String): Favorite {
        val d = _state.value.details
        return Favorite(
            animeId = animeId,
            title = d?.russian?.ifBlank { null } ?: d?.name ?: "",
            score = d?.score,
            status = status,
        )
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadingDubs = true, error = null) }
        viewModelScope.launch {
            val details = runCatching { repo.details(animeId) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки") } }
                .getOrNull() ?: return@launch

            _state.update { it.copy(details = details, isLoading = false) }

            val dubs = runCatching { repo.dubs(details) }.getOrDefault(emptyList())
            // Озвучка, которой пользователь уже смотрел этот тайтл, — первая (не листать).
            val lastDubId = runCatching {
                progressDao.forAnime(animeId).maxByOrNull { it.updatedAt }?.dubId
            }.getOrNull()
            val ordered = if (lastDubId != null && dubs.any { it.id == lastDubId }) {
                dubs.sortedByDescending { it.id == lastDubId }
            } else dubs
            _state.update {
                it.copy(dubs = ordered, selectedDub = ordered.firstOrNull(), loadingDubs = false)
            }
        }
    }

    fun selectDub(dub: Dub) = _state.update { it.copy(selectedDub = dub) }

    fun episodeCount(): Int {
        val d = _state.value
        val fromDub = d.selectedDub?.episodesCount ?: 0
        if (fromDub > 0) return fromDub
        val det = d.details
        return maxOf(det?.episodesAired ?: 0, det?.episodes ?: 0)
    }

    /**
     * Куда ведёт большая кнопка «Смотреть/Продолжить»:
     * недосмотренная серия с последней позицией, иначе первая непросмотренная, иначе серия 1.
     */
    fun resumeTarget(): Pair<Int, Long> {
        val progress = _state.value.progress
        val unfinished = progress.values
            .filter { !it.watched && it.positionMs > 1000 }
            .maxByOrNull { it.updatedAt }
        if (unfinished != null) return unfinished.episode to unfinished.positionMs
        val nextUnwatched = (1..episodeCount()).firstOrNull { progress[it]?.watched != true } ?: 1
        return nextUnwatched to 0L
    }

    /** Подготовить сессию воспроизведения перед переходом в плеер. */
    fun prepareSession(episode: Int, startOver: Boolean = false) {
        val d = _state.value
        session.start(
            animeId = animeId,
            title = d.details?.russian?.ifBlank { null } ?: d.details?.name ?: "",
            posterId = animeId,
            dubs = d.dubs,
            selectedDubId = d.selectedDub?.id,
            episode = episode,
            totalEpisodes = episodeCount(),
            startOver = startOver,
        )
    }
}
