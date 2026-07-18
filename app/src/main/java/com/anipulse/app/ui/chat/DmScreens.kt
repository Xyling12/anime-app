package com.anipulse.app.ui.chat

import com.anipulse.app.ui.common.topSafePadding
import android.content.Context
import com.anipulse.app.notify.SoundPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.DmMessage
import com.anipulse.app.data.DmSendRequest
import com.anipulse.app.data.DmThread
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.ui.common.Avatar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- Список диалогов ----------

data class DmListState(
    val threads: List<DmThread> = emptyList(),
    val isLoggedIn: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class DmListViewModel @Inject constructor(
    private val gateway: GatewayApi,
    settings: SettingsStore,
) : ViewModel() {
    private val token = settings.authToken
    private val _state = MutableStateFlow(DmListState(isLoggedIn = token != null))
    val state: StateFlow<DmListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                token?.let { t ->
                    runCatching { gateway.dmList("Bearer $t") }.onSuccess { list ->
                        _state.update { it.copy(threads = list, loading = false) }
                    }.onFailure { _state.update { it.copy(loading = false) } }
                }
                delay(6000)
            }
        }
    }
}

@Composable
fun DmListScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    viewModel: DmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().topSafePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Личные сообщения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when {
            !state.isLoggedIn -> Text(
                "Войдите в Профиле, чтобы писать ЛС",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.threads.isEmpty() && !state.loading -> Text(
                "Пока нет диалогов. Написать можно из чата — тапни по нику собеседника.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.threads, key = { it.withNick }) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(t.withNick) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(t.withAvatar, 42.dp, nick = t.withNick)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.withNick, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                t.lastText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.lastAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (t.unread > 0) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${t.unread}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Переписка ----------

data class DmChatState(
    val withNick: String = "",
    val messages: List<DmMessage> = emptyList(),
    val myNick: String? = null,
    val sending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DmChatViewModel @Inject constructor(
    private val gateway: GatewayApi,
    settings: SettingsStore,
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val token = settings.authToken
    private val withNick: String = savedState.get<String>("nick") ?: ""
    private val _state = MutableStateFlow(DmChatState(withNick = withNick, myNick = settings.authNick))
    val state: StateFlow<DmChatState> = _state.asStateFlow()
    private var firstLoadDone = false

    init {
        viewModelScope.launch {
            while (true) {
                token?.let { t ->
                    val after = _state.value.messages.lastOrNull()?.id ?: 0
                    runCatching { gateway.dmThread("Bearer $t", withNick, after) }.onSuccess { fresh ->
                        if (fresh.isNotEmpty()) {
                            _state.update { it.copy(messages = (it.messages + fresh).takeLast(200)) }
                            // Собственные отправленные сообщения добавляются локально в send(), сюда не попадают —
                            // всё, что пришло пулингом, от собеседника.
                            if (firstLoadDone) SoundPlayer.playMessageSound(context)
                        }
                        firstLoadDone = true
                    }
                }
                delay(4000)
            }
        }
    }

    fun send(text: String) {
        val t = token
        if (t == null) {
            _state.update { it.copy(error = "Вы не вошли в аккаунт") }
            return
        }
        if (text.isBlank()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { gateway.dmSend("Bearer $t", DmSendRequest(withNick, text.trim())) }
                .onSuccess { msg -> _state.update { it.copy(messages = it.messages + msg, sending = false) } }
                .onFailure { e ->
                    val msg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                        ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                    _state.update { it.copy(sending = false, error = msg ?: "Не удалось отправить — проверьте соединение") }
                }
        }
    }
}

@Composable
fun DmChatScreen(
    onBack: () -> Unit,
    viewModel: DmChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().topSafePadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(state.withNick, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { m ->
                val mine = m.from == state.myNick
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = if (mine) 14.dp else 4.dp,
                                    topEnd = if (mine) 4.dp else 14.dp,
                                    bottomStart = 14.dp, bottomEnd = 14.dp,
                                )
                            )
                            .background(
                                if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .widthIn(max = 280.dp),
                    ) {
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
            )
            IconButton(
                onClick = { viewModel.send(input); input = "" },
                enabled = !state.sending && input.isNotBlank(),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        state.error?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
