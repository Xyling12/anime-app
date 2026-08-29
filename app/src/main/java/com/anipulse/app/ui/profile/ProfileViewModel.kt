package com.anipulse.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.LoginRequest
import com.anipulse.app.data.RegisterRequest
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.data.db.FavoriteDao
import com.anipulse.app.data.db.ProgressDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val watchedEpisodes: Int = 0,
    val watchTimeMs: Long = 0,
    val startedTitles: Int = 0,
    val favoritesCount: Int = 0,
    val watchingTitles: Int = 0,
    val plannedTitles: Int = 0,
    val completedTitles: Int = 0,
    val autoSkipOpening: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val autoNextEpisode: Boolean = true,
    // Аккаунт
    val userId: Long = 0,
    val nick: String? = null,
    val email: String? = null,
    val linked: List<String> = emptyList(),
    val authBusy: Boolean = false,
    val authError: String? = null,
    val resetCodeSent: Boolean = false,
    val bugReportBusy: Boolean = false,
    val bugReportError: String? = null,
    val bugReportSent: Boolean = false,
    val avatarId: Int = 0,
    /** Версия кастомной аватарки — для сброса кэша картинки после загрузки новой. */
    val avatarRev: Int = 0,
    val avatarUploadError: String? = null,
    /** false — почта не подтверждена, показываем плашку с кодом. */
    val emailVerified: Boolean = true,
    val verifyMessage: String? = null,
    val admin: Boolean = false,
    val analytics: com.anipulse.app.data.AdminAnalytics? = null,
    val analyticsBusy: Boolean = false,
    val rank: Int = 0,
    val totalParticipants: Int = 0,
    val level: Int = 1,
    val levelTitle: String = "Новичок",
    val badge: String = "🥉",
    val nextTarget: Int? = 10,
    val leaderboard: List<com.anipulse.app.data.LeaderboardEntry> = emptyList(),
    val leaderboardBusy: Boolean = false,
    val leaderboardError: String? = null,
    val showLeaderboardDialog: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
    private val settings: SettingsStore,
    private val gateway: GatewayApi,
    private val syncRepository: com.anipulse.app.data.SyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileState(
            autoSkipOpening = settings.autoSkipOpening,
            autoSkipRecap = settings.autoSkipRecap,
            autoNextEpisode = settings.autoNextEpisode,
            nick = settings.authNick,
            email = settings.authEmail,
            avatarId = settings.avatarId,
            admin = settings.authAdmin,
        )
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { syncRepository.syncAll() }
            refresh()
            syncStatsToServer()
            refreshStatsAndLeaderboard()
        }
        refreshMe()
        if (settings.authAdmin) refreshAnalytics()
    }
    /**
     * Подтянуть аккаунт/привязки с сервера. Дёргается при создании экрана и на каждый
     * RESUME — иначе после возврата из браузера с OAuth-привязкой (Яндекс/VK) кнопки
     * «Привязать сервис» показывали устаревшее состояние до пересоздания экрана.
     */
    fun refreshMe() {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.me("Bearer $token") }.onSuccess { me ->
                if (me.nick != null) {
                    settings.avatarId = me.avatar
                    settings.authAdmin = me.admin
                    _state.update { it.copy(emailVerified = me.emailVerified) }
                    _state.update {
                        it.copy(
                            userId = me.userId,
                            nick = me.nick,
                            email = me.email,
                            linked = me.linked,
                            avatarId = me.avatar,
                            avatarRev = me.avatarRev,
                            admin = me.admin,
                        )
                    }
                    if (me.admin) refreshAnalytics()
                } else {
                    logout() // токен протух
                }
            }
        }
    }

    fun register(nick: String, email: String, password: String, password2: String) {
        if (password != password2) {
            _state.update { it.copy(authError = "Пароли не совпадают") }; return
        }
        authCall {
            gateway.register(
                RegisterRequest(
                    nick = nick.trim(), email = email.trim(), password = password,
                    acceptTerms = true, privacyConsent = true,
                )
            )
        }
    }

    fun login(login: String, password: String) {
        authCall {
            gateway.login(
                LoginRequest(
                    login = login.trim(), password = password,
                    acceptTerms = true, privacyConsent = true,
                )
            )
        }
    }

    private fun authCall(call: suspend () -> com.anipulse.app.data.AuthResponse) {
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            val resp = runCatching { call() }.getOrElse { e ->
                val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                    ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                com.anipulse.app.data.AuthResponse(error = msg ?: "Ошибка сети")
            }
            if (resp.token != null) {
                settings.authToken = resp.token
                settings.authNick = resp.nick
                settings.authEmail = resp.email
                runCatching { syncRepository.syncAll() }
                refresh()
                _state.update { it.copy(nick = resp.nick, email = resp.email, authBusy = false, authError = null) }
                runCatching { gateway.me("Bearer ${resp.token}") }.onSuccess { me ->
                    val resolvedNick = me.nick ?: resp.nick
                    val resolvedEmail = me.email ?: resp.email
                    settings.authNick = resolvedNick
                    settings.authEmail = resolvedEmail
                    settings.avatarId = me.avatar
                    settings.authAdmin = me.admin
                    _state.update {
                        it.copy(
                            userId = me.userId,
                            nick = resolvedNick,
                            email = resolvedEmail,
                            linked = me.linked,
                            avatarId = me.avatar,
                            avatarRev = me.avatarRev,
                            emailVerified = me.emailVerified,
                            authBusy = false,
                            authError = null,
                            admin = me.admin,
                        )
                    }
                    if (me.admin) refreshAnalytics()
                }
            } else {
                _state.update { it.copy(authBusy = false, authError = resp.error ?: "Ошибка") }
            }
        }
    }

    fun logout() {
        settings.authToken = null; settings.authNick = null; settings.authEmail = null; settings.authAdmin = false
        _state.update { it.copy(userId = 0, nick = null, email = null, linked = emptyList(), avatarRev = 0, admin = false, analytics = null) }
    }

    fun refreshAnalytics() {
        val token = settings.authToken ?: return
        if (!settings.authAdmin) return
        _state.update { it.copy(analyticsBusy = true) }
        viewModelScope.launch {
            runCatching { gateway.adminAnalytics("Bearer $token") }
                .onSuccess { value -> _state.update { it.copy(analytics = value, analyticsBusy = false) } }
                .onFailure { _state.update { it.copy(analyticsBusy = false) } }
        }
    }

    fun deleteAccount() {
        val token = settings.authToken ?: return
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            runCatching { gateway.deleteAccount("Bearer $token") }
                .onSuccess { logout() }
                .onFailure { e ->
                    val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                        ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                    _state.update { it.copy(authBusy = false, authError = msg ?: "Не удалось удалить аккаунт") }
                }
        }
    }

    /** Отзыв токенов на всех устройствах (сервер повышает версию токена), затем локальный выход. */
    fun logoutAllDevices() {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.logoutAll("Bearer $token") }
            logout()
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(authError = "Укажите почту") }; return
        }
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            runCatching { gateway.forgotPassword(com.anipulse.app.data.ForgotRequest(email.trim())) }
                .onSuccess { _state.update { it.copy(authBusy = false, resetCodeSent = true) } }
                .onFailure { e ->
                    val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                        ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                    _state.update { it.copy(authBusy = false, authError = msg ?: "Ошибка сети") }
                }
        }
    }

    fun resetPassword(email: String, code: String, password: String) {
        if (code.isBlank() || password.length < 8 || password.length > 128) {
            _state.update { it.copy(authError = "Введите код и пароль (8–128 символов)") }; return
        }
        authCall { gateway.resetPassword(com.anipulse.app.data.ResetRequest(email.trim(), code.trim(), password)) }
    }

    fun clearAuthError() = _state.update { it.copy(authError = null, resetCodeSent = false) }

    fun sendBugReport(text: String, contact: String) {
        if (text.isBlank()) {
            _state.update { it.copy(bugReportError = "Опишите проблему") }; return
        }
        _state.update { it.copy(bugReportBusy = true, bugReportError = null) }
        viewModelScope.launch {
            val body = com.anipulse.app.data.BugReportRequest(
                text = text.trim(),
                contact = contact.trim().ifBlank { null },
                device = android.os.Build.MODEL,
                osVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            )
            runCatching { gateway.sendBugReport(settings.authToken?.let { "Bearer $it" }, body) }
                .onSuccess { _state.update { it.copy(bugReportBusy = false, bugReportSent = true) } }
                .onFailure { e ->
                    val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                        ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                    _state.update { it.copy(bugReportBusy = false, bugReportError = msg ?: "Ошибка сети") }
                }
        }
    }

    fun clearBugReport() = _state.update { it.copy(bugReportError = null, bugReportSent = false) }

    fun currentToken(): String? = settings.authToken

    /** Получает короткоживущий одноразовый код привязки. Bearer-токен не попадает в URL браузера. */
    fun createOAuthLinkCode(onReady: (String) -> Unit) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.createOAuthLinkCode("Bearer $token") }
                .onSuccess { response -> response.code?.let(onReady) }
                .onFailure { _state.update { it.copy(authError = "Не удалось начать привязку сервиса") } }
        }
    }

    /** Подтянуть аккаунт после возврата из OAuth-браузера (токен кладёт MainActivity). */
    fun syncFromSettings() {
        val nick = settings.authNick
        if (nick != _state.value.nick) {
            _state.update { it.copy(nick = nick, email = settings.authEmail) }
            settings.authToken?.let { token ->
                viewModelScope.launch {
                    runCatching { gateway.me("Bearer $token") }.onSuccess { me ->
                        if (me.nick != null) {
                            settings.avatarId = me.avatar
                            _state.update {
                                it.copy(
                                    userId = me.userId,
                                    nick = me.nick,
                                    email = me.email,
                                    linked = me.linked,
                                    avatarId = me.avatar,
                                    avatarRev = me.avatarRev,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Смена аватара: локально всегда, на сервере — если вошли. */
    fun setAvatar(id: Int) {
        settings.avatarId = id
        _state.update { it.copy(avatarId = id) }
        settings.authToken?.let { token ->
            viewModelScope.launch {
                runCatching { gateway.setAvatar("Bearer $token", com.anipulse.app.data.AvatarRequest(id)) }
            }
        }
    }

    /** Загрузка своей аватарки: bytes — уже сжатый JPEG (экран жмёт до 256px). */
    fun uploadAvatar(bytes: ByteArray) {
        val token = settings.authToken ?: run {
            _state.update { it.copy(avatarUploadError = "Войдите в аккаунт") }; return
        }
        _state.update { it.copy(avatarUploadError = null) }
        viewModelScope.launch {
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            runCatching { gateway.uploadAvatar("Bearer $token", com.anipulse.app.data.AvatarUploadRequest(b64)) }
                .onSuccess { r ->
                    if (r.ok) {
                        settings.avatarId = -1
                        _state.update { it.copy(avatarId = -1, avatarRev = r.avatarRev) }
                    } else {
                        _state.update { it.copy(avatarUploadError = r.error ?: "Не удалось загрузить") }
                    }
                }
                .onFailure { e ->
                    val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                        ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                    _state.update { it.copy(avatarUploadError = msg ?: "Не удалось загрузить — проверьте соединение") }
                }
        }
    }

    /**
     * Отправка своей статистики на сервер (для карточки пользователя, которую видят другие).
     * Просмотры хранятся только локально в Room, поэтому сервер узнаёт их отсюда.
     */
    private fun syncStatsToServer() {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            val stats = com.anipulse.app.data.UserStats(
                watchedEpisodes = runCatching { progressDao.watchedEpisodes() }.getOrDefault(0),
                watchMinutes = (runCatching { progressDao.totalWatchTimeMs() }.getOrDefault(0L) / 60000L).toInt(),
                startedTitles = runCatching { progressDao.startedTitles() }.getOrDefault(0),
                favoritesCount = runCatching { favoriteDao.count() }.getOrDefault(0),
            )
            runCatching {
                gateway.updateProfile(
                    "Bearer $token",
                    com.anipulse.app.data.ProfileUpdateRequest(stats = stats),
                )
            }
        }
    }


    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    watchedEpisodes = runCatching { progressDao.watchedEpisodes() }.getOrDefault(0),
                    watchTimeMs = runCatching { progressDao.totalWatchTimeMs() }.getOrDefault(0),
                    startedTitles = runCatching { progressDao.startedTitles() }.getOrDefault(0),
                    favoritesCount = runCatching { favoriteDao.count() }.getOrDefault(0),
                    watchingTitles = runCatching { favoriteDao.countByStatus("watching") }.getOrDefault(0),
                    plannedTitles = runCatching { favoriteDao.countByStatus("planned") }.getOrDefault(0),
                    completedTitles = runCatching { favoriteDao.countByStatus("completed") }.getOrDefault(0),
                )
            }
        }
    }

    fun refreshStatsAndLeaderboard() {
        viewModelScope.launch {
            _state.update { it.copy(leaderboardBusy = true, leaderboardError = null) }
            val token = settings.authToken
            val bearer = token?.let { "Bearer $it" }
            runCatching {
                gateway.leaderboard(bearer)
            }.onSuccess { res ->
                _state.update { state ->
                    val my = res.myRank
                    val localWatched = state.watchedEpisodes
                    val localLevel = when {
                        localWatched >= 500 -> Triple(6, "Легенда", "⚡💎")
                        localWatched >= 300 -> Triple(5, "Сенсей", "👑")
                        localWatched >= 150 -> Triple(4, "Отаку", "⭐")
                        localWatched >= 50 -> Triple(3, "Анимешник", "🔥")
                        localWatched >= 10 -> Triple(2, "Любитель", "🍿")
                        else -> Triple(1, "Новичок", "🌱")
                    }
                    state.copy(
                        leaderboard = res.leaderboard,
                        totalParticipants = res.totalParticipants,
                        rank = my?.rank ?: state.rank,
                        level = my?.level ?: localLevel.first,
                        levelTitle = my?.levelTitle ?: localLevel.second,
                        badge = my?.badge ?: localLevel.third,
                        leaderboardBusy = false
                    )
                }
            }.onFailure { err ->
                _state.update { state ->
                    val localWatched = state.watchedEpisodes
                    val localLevel = when {
                        localWatched >= 500 -> Triple(6, "Легенда", "⚡💎")
                        localWatched >= 300 -> Triple(5, "Сенсей", "👑")
                        localWatched >= 150 -> Triple(4, "Отаку", "⭐")
                        localWatched >= 50 -> Triple(3, "Анимешник", "🔥")
                        localWatched >= 10 -> Triple(2, "Любитель", "🍿")
                        else -> Triple(1, "Новичок", "🌱")
                    }
                    state.copy(
                        level = localLevel.first,
                        levelTitle = localLevel.second,
                        badge = localLevel.third,
                        leaderboardBusy = false,
                        leaderboardError = err.message
                    )
                }
            }
        }
    }

    fun toggleLeaderboard(show: Boolean) {
        _state.update { it.copy(showLeaderboardDialog = show) }
        if (show) refreshStatsAndLeaderboard()
    }

    fun setAutoSkipOpening(v: Boolean) { settings.autoSkipOpening = v; _state.update { it.copy(autoSkipOpening = v) } }
    fun setAutoSkipRecap(v: Boolean) { settings.autoSkipRecap = v; _state.update { it.copy(autoSkipRecap = v) } }
    fun setAutoNextEpisode(v: Boolean) { settings.autoNextEpisode = v; _state.update { it.copy(autoNextEpisode = v) } }
}
