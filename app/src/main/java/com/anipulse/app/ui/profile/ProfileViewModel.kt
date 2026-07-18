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
    val autoSkipOpening: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val autoNextEpisode: Boolean = true,
    // Аккаунт
    val nick: String? = null,
    val email: String? = null,
    val linked: List<String> = emptyList(),
    val authBusy: Boolean = false,
    val authError: String? = null,
    /** true — код восстановления отправлен, диалог показывает поля кода и нового пароля. */
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
    private val settings: SettingsStore,
    private val gateway: GatewayApi,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileState(
            autoSkipOpening = settings.autoSkipOpening,
            autoSkipRecap = settings.autoSkipRecap,
            autoNextEpisode = settings.autoNextEpisode,
            nick = settings.authNick,
            email = settings.authEmail,
            avatarId = settings.avatarId,
        )
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        refresh()
        syncStatsToServer()
        refreshMe()
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
                    _state.update { it.copy(nick = me.nick, email = me.email, linked = me.linked, avatarId = me.avatar) }
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
        authCall { gateway.register(RegisterRequest(nick.trim(), email.trim(), password)) }
    }

    fun login(login: String, password: String) {
        authCall { gateway.login(LoginRequest(login.trim(), password)) }
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
                _state.update { it.copy(nick = resp.nick, email = resp.email, authBusy = false, authError = null) }
            } else {
                _state.update { it.copy(authBusy = false, authError = resp.error ?: "Ошибка") }
            }
        }
    }

    fun logout() {
        settings.authToken = null; settings.authNick = null; settings.authEmail = null
        _state.update { it.copy(nick = null, email = null, linked = emptyList()) }
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
        if (code.isBlank() || password.length < 6) {
            _state.update { it.copy(authError = "Введите код и пароль (мин. 6 символов)") }; return
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
                            _state.update { it.copy(nick = me.nick, email = me.email, linked = me.linked, avatarId = me.avatar) }
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

    /** Подтверждение почты: отправить код с плашки. */
    fun verifyEmail(code: String) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            val ok = runCatching {
                gateway.verifyEmail("Bearer $token", com.anipulse.app.data.VerifyRequest(code.trim()))
            }.isSuccess
            _state.update {
                it.copy(
                    emailVerified = if (ok) true else it.emailVerified,
                    verifyMessage = if (ok) "Почта подтверждена!" else "Неверный или просроченный код",
                )
            }
        }
    }

    /** Прислать код повторно (сервер: не чаще 1/мин). */
    fun resendCode() {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.resendCode("Bearer $token") }
            _state.update { it.copy(verifyMessage = "Код отправлен на почту") }
        }
    }

    /** Сохранить «О себе» (до 200 символов) на сервере. */
    fun saveBio(bio: String) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching {
                gateway.updateProfile("Bearer $token", com.anipulse.app.data.ProfileUpdateRequest(bio = bio.take(200)))
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
                )
            }
        }
    }

    fun setAutoSkipOpening(v: Boolean) { settings.autoSkipOpening = v; _state.update { it.copy(autoSkipOpening = v) } }
    fun setAutoSkipRecap(v: Boolean) { settings.autoSkipRecap = v; _state.update { it.copy(autoSkipRecap = v) } }
    fun setAutoNextEpisode(v: Boolean) { settings.autoNextEpisode = v; _state.update { it.copy(autoNextEpisode = v) } }
}
