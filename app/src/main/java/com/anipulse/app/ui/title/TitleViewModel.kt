package com.anipulse.app.ui.title

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.AnimeRepository
import com.anipulse.app.data.db.EpisodeProgress
import com.anipulse.app.data.db.Favorite
import com.anipulse.app.data.db.FavoriteDao
import com.anipulse.app.data.db.ProgressDao
import com.anipulse.app.data.shikimori.ShikiAnimeDetails
import com.anipulse.app.data.shikimori.ShikiRelatedNode
import com.anipulse.app.data.video.Dub
import com.anipulse.app.data.video.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import retrofit2.HttpException
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
    val downloadedEpisodes: Set<Int> = emptySet(),
    val downloadingEpisode: Pair<Int, Int>? = null, // (episode, percent)
    val downloadMessage: String? = null,           // итог загрузки для тоста (null = показывать нечего)
    // Соцчасть
    val ratingAvg: Double? = null,
    val ratingCount: Int = 0,
    val myRating: Int? = null,
    val ratingSending: Boolean = false,        // идёт запрос — игнор повторных нажатий
    val ratingError: String? = null,          // последняя ошибка (null = всё ок)
    val comments: List<com.anipulse.app.data.ChatMessage> = emptyList(),
    val isLoggedIn: Boolean = false,
    val commentSending: Boolean = false,
    val myNick: String? = null,
    val episodeNotifyEnabled: Boolean = false,
    // Связанные тайтлы (sequel/prequel/side_story/…) — порядок просмотра.
    val related: List<ShikiRelatedNode> = emptyList(),
)

@HiltViewModel
class TitleViewModel @Inject constructor(
    private val repo: AnimeRepository,
    private val session: PlaybackSession,
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
    private val gateway: com.anipulse.app.data.GatewayApi,
    private val settings: com.anipulse.app.data.SettingsStore,
    private val syncRepository: com.anipulse.app.data.SyncRepository,
    private val downloadManager: com.anipulse.app.data.download.DownloadManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val socialGateway: com.anipulse.app.data.GatewayApi get() = gateway
    fun currentToken(): String? = settings.authToken

    val animeId: Long = checkNotNull(savedStateHandle["animeId"])

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
        viewModelScope.launch {
            downloadManager.downloadedList.collect { list ->
                val eps = list.filter { it.animeId == animeId }.map { it.episode }.toSet()
                _state.update { it.copy(downloadedEpisodes = eps) }
            }
        }
        viewModelScope.launch {
            downloadManager.downloadState.collect { dState ->
                when (dState) {
                    is com.anipulse.app.data.download.DownloadState.Downloading -> {
                        if (dState.animeId == animeId) {
                            _state.update { it.copy(downloadingEpisode = dState.episode to dState.progressPercent) }
                        } else {
                            _state.update { it.copy(downloadingEpisode = null) }
                        }
                    }
                    else -> _state.update { it.copy(downloadingEpisode = null) }
                }
            }
        }
        // Раньше ошибка загрузки нигде не показывалась: кружок прогресса просто
        // пропадал, и выглядело так, будто кнопка не работает.
        viewModelScope.launch {
            downloadManager.events.collect { event ->
                val message = when {
                    event is com.anipulse.app.data.download.DownloadState.Error && event.animeId == animeId ->
                        "Серия ${event.episode} не скачалась. ${event.message}"
                    event is com.anipulse.app.data.download.DownloadState.Completed && event.animeId == animeId ->
                        "Серия ${event.episode} скачана — её можно смотреть без интернета"
                    else -> null
                }
                if (message != null) _state.update { it.copy(downloadMessage = message) }
            }
        }
        _state.update { it.copy(isLoggedIn = settings.authToken != null, myNick = settings.authNick, episodeNotifyEnabled = animeId.toString() in settings.episodeNotifyIds) }
        loadSocial()
    }

    private fun bearer(): String? = settings.authToken?.let { "Bearer $it" }

    private fun loadSocial() {
        viewModelScope.launch {
            runCatching { gateway.rating(animeId, bearer()) }.onSuccess { r ->
                _state.update { it.copy(ratingAvg = r.avg, ratingCount = r.count, myRating = r.my) }
            }
            runCatching { gateway.comments(animeId.toString(), bearer()) }.onSuccess { list ->
                _state.update { it.copy(comments = list) }
            }
        }
    }

    fun rate(score: Int) {
        // Не залогинен — покажем почему кнопка не сработала (раньше тихо возвращались).
        val b = bearer()
        if (b == null) {
            _state.update { it.copy(ratingError = "Войдите в Профиле, чтобы оценить") }
            android.util.Log.w("TitleVM", "rate($score) aborted: no auth token")
            return
        }
        // Не спамим запросами, пока предыдущий в полёте.
        if (_state.value.ratingSending) return
        // Запоминаем прежнюю оценку — нужна для отката при ошибке.
        val prevRating = _state.value.myRating
        // Toggle: повторный клик по текущей оценке = удалить.
        val isToggle = prevRating == score
        // Оптимистичное обновление UI: подсветить сразу, до ответа сервера.
        _state.update { it.copy(ratingSending = true, ratingError = null, myRating = if (isToggle) null else score) }
        viewModelScope.launch {
            val result = runCatching {
                if (isToggle) gateway.deleteRating(b, animeId)
                else gateway.sendRating(b, com.anipulse.app.data.RatingRequest(animeId, score))
            }
            result
                .onSuccess { r ->
                    if (!r.error.isNullOrBlank()) {
                        // Сервер вернул 200, но в error — откатываем UI.
                        android.util.Log.w("TitleVM", "rate($score) server error: ${r.error}")
                        _state.update {
                            it.copy(ratingSending = false, ratingError = r.error, myRating = prevRating)
                        }
                    } else {
                        _state.update {
                            it.copy(
                                ratingSending = false,
                                ratingError = null,
                                ratingAvg = r.avg,
                                ratingCount = r.count,
                                myRating = r.my,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("TitleVM", "rate($score) failed", e)
                    // Извлекаем тело ответа сервера ({"error":"Подтвердите почту"}),
                    // а не сухой "HTTP 403" от Retrofit.
                    val serverMsg = (e as? HttpException)?.response()?.errorBody()?.string()
                        ?.let { runCatching { JSONObject(it).optString("error", "") }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }
                    val friendly = serverMsg ?: (e.message ?: "Не удалось поставить оценку")
                    // Откатываем оптимистичное обновление: возвращаем прежнюю оценку.
                    _state.update {
                        it.copy(
                            ratingSending = false,
                            ratingError = friendly,
                            myRating = prevRating,
                        )
                    }
                }
        }
    }

    fun clearRatingError() = _state.update { it.copy(ratingError = null) }

    fun deleteComment(id: Long) {
        val b = bearer() ?: return
        viewModelScope.launch {
            runCatching { gateway.deleteComment(b, animeId.toString(), id) }
                .onSuccess { _state.update { st -> st.copy(comments = st.comments.filterNot { it.id == id }) } }
        }
    }

    fun reportComment(comment: com.anipulse.app.data.ChatMessage) {
        val b = bearer() ?: return
        viewModelScope.launch {
            runCatching {
                gateway.report(
                    b,
                    com.anipulse.app.data.ReportRequest(
                        type = "comment", targetId = comment.id.toString(), targetNick = comment.nick,
                        animeId = animeId.toString(), reason = "Нарушение правил комментариев",
                    ),
                )
            }
        }
    }

    fun blockCommentAuthor(nick: String) {
        val b = bearer() ?: return
        viewModelScope.launch {
            runCatching { gateway.blockUser(b, com.anipulse.app.data.BlockRequest(nick)) }
                .onSuccess { _state.update { state -> state.copy(comments = state.comments.filterNot { it.nick.equals(nick, true) }) } }
        }
    }

    fun toggleEpisodeNotification() {
        val enabled = settings.toggleEpisodeNotify(animeId)
        _state.update { it.copy(episodeNotifyEnabled = enabled) }
        if (enabled && !_state.value.isFavorite) {
            viewModelScope.launch {
                val favorite = makeFavorite("planned")
                favoriteDao.upsert(favorite)
                runCatching { syncRepository.pushFavorite(favorite) }
            }
        }
    }

    fun postComment(text: String) {
        val b = bearer() ?: return
        if (text.isBlank()) return
        _state.update { it.copy(commentSending = true) }
        viewModelScope.launch {
            runCatching { gateway.sendComment(b, com.anipulse.app.data.CommentRequest(animeId.toString(), text.trim())) }
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
                runCatching { syncRepository.deleteFavorite(animeId) }
            } else {
                val favorite = makeFavorite("none")
                favoriteDao.upsert(favorite)
                runCatching { syncRepository.pushFavorite(favorite) }
            }
        }
    }

    /** Выбор статуса добавляет тайтл в «Моё»; повторный тап по тому же статусу снимает его. */
    fun setStatus(status: String) {
        val s = _state.value
        viewModelScope.launch {
            val newStatus = if (s.status == status) "none" else status
            val favorite = makeFavorite(newStatus)
            favoriteDao.upsert(favorite)
            runCatching { syncRepository.pushFavorite(favorite) }
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
            val detailsDeferred = async { runCatching { repo.details(animeId) } }
            val relatedDeferred = async { runCatching { repo.related(animeId) }.getOrDefault(emptyList()) }
            val details = detailsDeferred.await()
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки") } }
                .getOrNull() ?: return@launch

            _state.update { it.copy(details = details, isLoading = false) }

            // Связанные тайтлы могут догрузиться чуть позже — обновим стейт, как только будет готово.
            val related = relatedDeferred.await()
            if (related.isNotEmpty()) {
                _state.update { it.copy(related = related) }
            }

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

    fun startDownload(episode: Int) {
        val dub = _state.value.selectedDub ?: _state.value.dubs.firstOrNull() ?: return
        val details = _state.value.details ?: return
        val title = details.russian?.ifBlank { null } ?: details.name
        viewModelScope.launch {
            val stream = runCatching { repo.episodeStream(dub, episode) }.getOrNull()
            // Только прямой поток. embedUrl — HTML-страница плеера, а не видео:
            // раньше в этом случае сохранялась она под именем .mp4.
            val url = stream?.byQuality?.let { q -> q[720] ?: q[480] ?: q.values.firstOrNull() }
            if (url.isNullOrBlank()) {
                downloadManager.reportError(
                    animeId, episode, "Не удалось получить ссылку на видео. Попробуйте другую озвучку",
                )
                return@launch
            }
            downloadManager.enqueue(
                animeId = animeId,
                episode = episode,
                title = title,
                dubTitle = dub.title,
                streamUrl = url,
                posterId = details.id.toString(),
                image = details.image?.original ?: details.image?.preview,
            )
        }
    }

    fun clearDownloadMessage() = _state.update { it.copy(downloadMessage = null) }

    fun deleteDownload(episode: Int) {
        downloadManager.deleteDownload(animeId, episode)
    }
}
