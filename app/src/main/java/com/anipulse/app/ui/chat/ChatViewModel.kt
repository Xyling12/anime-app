package com.anipulse.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.ChatMessage
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.data.TextRequest
import com.anipulse.app.notify.SoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoggedIn: Boolean = false,
    val myNick: String? = null,
    val sending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gateway: GatewayApi,
    private val settings: SettingsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Для карточки пользователя (UserCardSheet) — доступ к API и токену. */
    val gatewayApi: GatewayApi get() = gateway
    fun token(): String? = settings.authToken
    fun isAdmin(): Boolean = settings.authAdmin

    /** Админ: удалить сообщение из общего чата (сервер + локально). */
    fun deleteMessage(id: Long) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.adminDeleteChat("Bearer $token", com.anipulse.app.data.AdminDeleteChatRequest(id)) }
                .onSuccess { _state.update { st -> st.copy(messages = st.messages.filter { it.id != id }) } }
        }
    }

    /** Админ: бан пользователя на N часов (0 — снять бан). */
    fun banUser(nick: String, hours: Int) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.adminBan("Bearer $token", com.anipulse.app.data.AdminBanRequest(nick, hours)) }
        }
    }

    private val _state = MutableStateFlow(
        ChatState(isLoggedIn = settings.authToken != null, myNick = settings.authNick)
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var firstLoadDone = false

    init {
        // Пулинг новых сообщений каждые 4 секунды
        viewModelScope.launch {
            while (true) {
                val after = _state.value.messages.lastOrNull()?.id ?: 0
                runCatching { gateway.chat(after) }.onSuccess { fresh ->
                    if (fresh.isNotEmpty()) {
                        _state.update { it.copy(messages = (it.messages + fresh).takeLast(200)) }
                        // Звук — только на реально новые сообщения (не на подгрузку истории при входе) и не на свои.
                        if (firstLoadDone && fresh.any { it.nick != _state.value.myNick }) {
                            SoundPlayer.playMessageSound(context)
                        }
                    }
                    firstLoadDone = true
                }
                delay(4000)
            }
        }
    }

    fun send(text: String, replyTo: Long? = null) {
        val token = settings.authToken ?: return
        if (text.isBlank()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { gateway.sendChat("Bearer $token", TextRequest(text.trim(), replyTo)) }
                .onSuccess { msg ->
                    _state.update { it.copy(messages = it.messages + msg, sending = false) }
                }
                .onFailure {
                    _state.update { it.copy(sending = false, error = "Не удалось отправить") }
                }
        }
    }
}
