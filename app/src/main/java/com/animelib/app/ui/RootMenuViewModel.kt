package com.animelib.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.GatewayApi
import com.animelib.app.data.SettingsStore
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

    private val _hasUnread = MutableStateFlow(false)
    val hasUnread: StateFlow<Boolean> = _hasUnread.asStateFlow()

    /** Шапка шторки (реактивно: обновится после логина/смены аватара). */
    val nick = MutableStateFlow(settings.authNick)
    val avatarId = MutableStateFlow(settings.avatarId)

    init {
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
                    val dmUnread = runCatching {
                        gateway.dmList("Bearer $t").sumOf { it.unread }
                    }.getOrDefault(0)
                    val notifUnread = runCatching {
                        gateway.notifications("Bearer $t").count { !it.read }
                    }.getOrDefault(0)
                    _hasUnread.value = dmUnread + notifUnread > 0
                } ?: run { _hasUnread.value = false }
                delay(30_000)
            }
        }
    }
}
