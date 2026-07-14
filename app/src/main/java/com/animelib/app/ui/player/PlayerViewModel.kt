package com.animelib.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.AnimeRepository
import com.animelib.app.data.db.EpisodeProgress
import com.animelib.app.data.db.ProgressDao
import com.animelib.app.data.video.Dub
import com.animelib.app.data.video.EpisodeStream
import com.animelib.app.data.video.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val episode: Int = 1,
    val totalEpisodes: Int = 0,
    val dubs: List<Dub> = emptyList(),
    val selectedDub: Dub? = null,
    val stream: EpisodeStream? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val resumePositionMs: Long = 0,
    val autoSkipOpening: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val autoNextEpisode: Boolean = true,
    // Комментарии к текущей серии
    val comments: List<com.animelib.app.data.ChatMessage> = emptyList(),
    val isLoggedIn: Boolean = false,
    val commentSending: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: AnimeRepository,
    private val session: PlaybackSession,
    private val progressDao: ProgressDao,
    private val settings: com.animelib.app.data.SettingsStore,
    private val gateway: com.animelib.app.data.GatewayApi,
) : ViewModel() {

    private val animeId = session.animeId

    private val _state = MutableStateFlow(
        PlayerUiState(
            title = session.title,
            episode = session.episode,
            totalEpisodes = session.totalEpisodes,
            dubs = session.dubs,
            selectedDub = session.selectedDub(),
            autoSkipOpening = settings.autoSkipOpening,
            autoSkipRecap = settings.autoSkipRecap,
            autoNextEpisode = settings.autoNextEpisode,
        )
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun setAutoSkipOpening(v: Boolean) { settings.autoSkipOpening = v; _state.update { it.copy(autoSkipOpening = v) } }
    fun setAutoSkipRecap(v: Boolean) { settings.autoSkipRecap = v; _state.update { it.copy(autoSkipRecap = v) } }
    fun setAutoNextEpisode(v: Boolean) { settings.autoNextEpisode = v; _state.update { it.copy(autoNextEpisode = v) } }

    init {
        _state.update { it.copy(isLoggedIn = settings.authToken != null) }
        loadStream()
        loadComments()
    }

    /** Комментарии к текущей серии: ключ "animeId:epN". */
    private fun episodeKey() = "$animeId:ep${_state.value.episode}"

    private fun loadComments() {
        viewModelScope.launch {
            runCatching { gateway.comments(episodeKey()) }.onSuccess { list ->
                _state.update { it.copy(comments = list) }
            }
        }
    }

    fun postComment(text: String) {
        val token = settings.authToken ?: return
        if (text.isBlank()) return
        _state.update { it.copy(commentSending = true) }
        viewModelScope.launch {
            runCatching {
                gateway.sendComment("Bearer $token", com.animelib.app.data.CommentRequest(episodeKey(), text.trim()))
            }.onSuccess { cm ->
                _state.update { it.copy(comments = it.comments + cm, commentSending = false) }
            }.onFailure {
                _state.update { it.copy(commentSending = false) }
            }
        }
    }

    private fun loadStream() {
        val s = _state.value
        val dub = s.selectedDub ?: run {
            _state.update { it.copy(loading = false, error = "Нет доступных озвучек") }
            return
        }
        _state.update { it.copy(loading = true, error = null, stream = null) }
        viewModelScope.launch {
            val startOver = session.startOver && s.episode == session.episode
            val saved = if (startOver) null else runCatching { progressDao.get(animeId, s.episode) }.getOrNull()
            session.startOver = false
            var stream = runCatching { repo.episodeStream(dub, s.episode) }.getOrNull()
            // Точные таймкоды опенинга/эндинга (AniSkip).
            if (stream != null) {
                val (op, ed) = runCatching { repo.skipTimes(animeId, s.episode) }.getOrNull() ?: (null to null)
                stream = stream.copy(
                    opening = stream.opening ?: op,
                    ending = stream.ending ?: ed,
                )
            }
            val finalStream = stream
            _state.update {
                if (finalStream == null) it.copy(loading = false, error = "Не удалось получить видео")
                else it.copy(
                    loading = false,
                    stream = finalStream,
                    resumePositionMs = saved?.takeIf { p -> !p.watched }?.positionMs ?: 0,
                )
            }
        }
    }

    /** Сохранить место остановки (вызывается периодически и при выходе). */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (animeId == 0L || durationMs <= 0) return
        val s = _state.value
        val watched = positionMs >= durationMs * 0.9
        viewModelScope.launch {
            runCatching {
                progressDao.upsert(
                    EpisodeProgress(
                        animeId = animeId,
                        episode = s.episode,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        watched = watched,
                        dubId = s.selectedDub?.id,
                        title = session.title,
                        posterId = session.posterId,
                        totalEpisodes = session.totalEpisodes,
                    )
                )
            }
        }
    }

    fun selectDub(dub: Dub) {
        if (dub.id == _state.value.selectedDub?.id) return
        _state.update { it.copy(selectedDub = dub) }
        loadStream()
    }

    fun setEpisode(episode: Int) {
        if (episode < 1) return
        _state.update { it.copy(episode = episode, comments = emptyList()) }
        loadStream()
        loadComments()
    }

    fun nextEpisode() {
        val s = _state.value
        if (hasNext()) setEpisode(s.episode + 1)
    }

    fun prevEpisode() {
        val s = _state.value
        if (s.episode > 1) setEpisode(s.episode - 1)
    }

    fun hasNext(): Boolean {
        val s = _state.value
        return s.totalEpisodes == 0 || s.episode < s.totalEpisodes
    }

    fun hasPrev(): Boolean = _state.value.episode > 1
}
