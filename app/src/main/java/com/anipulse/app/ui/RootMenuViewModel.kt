package com.anipulse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Красная точка на кнопке меню: есть ли непрочитанные ЛС или уведомления.
 * Лёгкий пулинг раз в 30с, только для вошедших.
 */
@HiltViewModel
class RootMenuViewModel @Inject constructor(
    private val gateway: GatewayApi,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Непрочитанные ЛС — бейдж на иконке чата в шапке. */
    private val _dmUnread = MutableStateFlow(false)
    val dmUnread: StateFlow<Boolean> = _dmUnread.asStateFlow()

    /** Непрочитанные уведомления (упоминания/друзья/эпизоды) — бейдж на колокольчике. */
    private val _notifUnread = MutableStateFlow(false)
    val notifUnread: StateFlow<Boolean> = _notifUnread.asStateFlow()

    /** Шапка шторки (реактивно: обновится после логина/смены аватара). */
    val nick = MutableStateFlow(settings.authNick)
    val avatarId = MutableStateFlow(settings.avatarId)
    val isDarkTheme = MutableStateFlow(settings.isDarkTheme)
    
    fun toggleDarkTheme() {
        val newTheme = !isDarkTheme.value
        settings.isDarkTheme = newTheme
        isDarkTheme.value = newTheme
    }

    /** Доступное обновление (versionCode на сервере больше нашего) или null. */
    val update = kotlinx.coroutines.flow.MutableStateFlow<com.anipulse.app.data.AppVersion?>(null)

    init {
        viewModelScope.launch {
            runCatching { gateway.appVersion() }.onSuccess { v ->
                if (v.versionCode > com.anipulse.app.BuildConfig.VERSION_CODE && v.url.isNotBlank()) update.value = v
            }
        }

        // Актуализируем ник/аватар с сервера (prefs может не знать ник после OAuth-входа)
        viewModelScope.launch {
            settings.authToken?.let { t ->
                runCatching { gateway.me("Bearer $t") }.onSuccess { me ->
                    if (me.nick != null) {
                        settings.authNick = me.nick
                        settings.avatarId = me.avatar
                        settings.authAdmin = me.admin
                        nick.value = me.nick
                        avatarId.value = me.avatar
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                settings.authToken?.let { t ->
                    val dm = runCatching {
                        gateway.dmList("Bearer $t").sumOf { it.unread }
                    }.getOrDefault(0)
                    val notif = runCatching {
                        gateway.notifications("Bearer $t").count { !it.read }
                    }.getOrDefault(0)
                    _dmUnread.value = dm > 0
                    _notifUnread.value = notif > 0
                } ?: run { _dmUnread.value = false; _notifUnread.value = false }
                delay(10_000) // точка у колокольчика/чата не позже 10с (фидбек: 30с — долго)
            }
        }
    }
}
