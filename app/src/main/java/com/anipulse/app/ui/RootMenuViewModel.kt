package com.anipulse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.BuildConfig
import com.anipulse.app.data.AppVersion
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.ui.common.launchForegroundPolling
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Профиль, тема оформления и проверка OTA-обновлений приложения.
 */
@HiltViewModel
class RootMenuViewModel @Inject constructor(
    private val gateway: GatewayApi,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Шапка профиля и настройки (реактивно: обновится после логина/смены аватара). */
    val nick = MutableStateFlow(settings.authNick)
    val avatarId = MutableStateFlow(settings.avatarId)
    val isDarkTheme = MutableStateFlow(settings.isDarkTheme)

    /** Доступное обновление (versionCode на сервере больше нашего) или null. */
    val update = MutableStateFlow<AppVersion?>(null)

    fun toggleDarkTheme() {
        val newTheme = !isDarkTheme.value
        settings.isDarkTheme = newTheme
        isDarkTheme.value = newTheme
    }

    fun setDarkTheme(dark: Boolean) {
        settings.isDarkTheme = dark
        isDarkTheme.value = dark
    }

    init {
        viewModelScope.launchForegroundPolling(60_000) {
            // Проверяем OTA не только при холодном запуске: серверный манифест
            // мог обновиться, пока приложение было открыто.
            runCatching { gateway.appVersion() }.onSuccess { v ->
                if (v.versionCode > BuildConfig.VERSION_CODE && v.url.isNotBlank()) {
                    update.value = v
                }
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
    }
}
