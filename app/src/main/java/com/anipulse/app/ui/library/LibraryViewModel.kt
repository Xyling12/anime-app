package com.anipulse.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.AnimeRepository
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.data.db.Favorite
import com.anipulse.app.data.db.FavoriteDao
import com.anipulse.app.data.download.DownloadManager
import com.anipulse.app.data.download.DownloadedItem
import com.anipulse.app.data.shikimori.ShikiAnimeDetails
import com.anipulse.app.data.video.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val settings: SettingsStore,
    private val repository: AnimeRepository,
    private val syncRepository: com.anipulse.app.data.SyncRepository,
    private val downloadManager: DownloadManager,
    private val session: PlaybackSession,
) : ViewModel() {

    val favorites: StateFlow<List<Favorite>> = favoriteDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadedItem>> = downloadManager.downloadedList

    private val _filter = MutableStateFlow("all") // all / watching / planned / completed / downloads / subscriptions
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<ShikiAnimeDetails>>(emptyList())
    val subscriptions: StateFlow<List<ShikiAnimeDetails>> = _subscriptions.asStateFlow()

    private val _subscriptionsLoading = MutableStateFlow(false)
    val subscriptionsLoading: StateFlow<Boolean> = _subscriptionsLoading.asStateFlow()
    private var subscriptionsJob: Job? = null

    init { refreshSubscriptions() }

    fun setFilter(f: String) { _filter.value = f }

    fun setStatus(favorite: Favorite, status: String) {
        viewModelScope.launch {
            val updated = favorite.copy(status = status, addedAt = System.currentTimeMillis())
            favoriteDao.upsert(updated)
            runCatching { syncRepository.pushFavorite(updated) }
        }
    }

    fun removeFavorite(animeId: Long) {
        viewModelScope.launch {
            favoriteDao.delete(animeId)
            runCatching { syncRepository.deleteFavorite(animeId) }
        }
    }

    fun deleteDownload(item: DownloadedItem) {
        downloadManager.deleteDownload(item.animeId, item.episode)
    }

    fun playDownloaded(item: DownloadedItem) {
        session.start(
            animeId = item.animeId,
            title = item.title,
            posterId = item.animeId,
            dubs = emptyList(),
            selectedDubId = null,
            episode = item.episode,
            totalEpisodes = item.episode,
            startOver = false,
        )
    }

    fun refreshSubscriptions() {
        val ids = settings.episodeNotifyIds.mapNotNull(String::toLongOrNull).distinct().take(30)
        if (ids.isEmpty()) {
            _subscriptions.value = emptyList()
            _subscriptionsLoading.value = false
            return
        }
        subscriptionsJob?.cancel()
        subscriptionsJob = viewModelScope.launch {
            _subscriptionsLoading.value = true
            _subscriptions.value = ids.mapNotNull { id -> runCatching { repository.details(id) }.getOrNull() }
                .sortedBy { it.russian?.ifBlank { null } ?: it.name }
            _subscriptionsLoading.value = false
        }
    }

    fun removeSubscription(animeId: Long) {
        val ids = settings.episodeNotifyIds.toMutableSet()
        ids.remove(animeId.toString())
        settings.episodeNotifyIds = ids
        _subscriptions.value = _subscriptions.value.filterNot { it.id == animeId }
    }
}
