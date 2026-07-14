package com.animelib.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.FriendActionRequest
import com.animelib.app.data.FriendsResponse
import com.animelib.app.data.GatewayApi
import com.animelib.app.data.SettingsStore
import com.animelib.app.data.UserCard
import com.animelib.app.ui.common.Avatar
import com.animelib.app.ui.common.UserCardSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsState(
    val data: FriendsResponse = FriendsResponse(),
    val isLoggedIn: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val gateway: GatewayApi,
    private val settings: SettingsStore,
) : ViewModel() {
    val gatewayApi: GatewayApi get() = gateway
    fun token(): String? = settings.authToken

    private val _state = MutableStateFlow(FriendsState(isLoggedIn = settings.authToken != null))
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(10_000)
            }
        }
    }

    fun refresh() {
        val t = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.friends("Bearer $t") }.onSuccess { r ->
                _state.value = FriendsState(data = r, isLoggedIn = true, loading = false)
            }
        }
    }

    fun act(action: String, nick: String) {
        val t = settings.authToken ?: return
        viewModelScope.launch {
            runCatching { gateway.friendAction("Bearer $t", action, FriendActionRequest(nick)) }
            refresh()
        }
    }
}

/** Друзья: входящие заявки сверху (принять/отклонить), затем друзья — онлайн первыми. */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onWrite: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var cardNick by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Друзья", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when {
            !state.isLoggedIn -> Text(
                "Войдите в Профиле, чтобы добавлять друзей",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.data.friends.isEmpty() && state.data.incoming.isEmpty() && !state.loading -> Text(
                "Пока пусто. Добавить в друзья можно из карточки пользователя — тапни по аватару в чате.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (state.data.incoming.isNotEmpty()) {
                    item {
                        Text(
                            "Заявки в друзья",
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(state.data.incoming, key = { "in-" + it.nick }) { u ->
                        FriendRow(u, onOpen = { cardNick = u.nick }) {
                            IconButton(onClick = { viewModel.act("accept", u.nick) }) {
                                Icon(Icons.Filled.Check, contentDescription = "Принять", tint = Color(0xFF2ECC71))
                            }
                            IconButton(onClick = { viewModel.act("decline", u.nick) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Отклонить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.data.friends.isNotEmpty()) {
                    item {
                        Text(
                            "Друзья · ${state.data.friends.size}",
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(state.data.friends, key = { it.nick }) { u ->
                        FriendRow(u, onOpen = { cardNick = u.nick }) {
                            Text(
                                if (u.online) "онлайн" else "офлайн",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (u.online) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    cardNick?.let { nick ->
        UserCardSheet(
            nick = nick,
            gateway = viewModel.gatewayApi,
            token = viewModel.token(),
            onDismiss = { cardNick = null; viewModel.refresh() },
            onWrite = { n -> cardNick = null; onWrite(n) },
        )
    }
}

@Composable
private fun FriendRow(u: UserCard, onOpen: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(u.avatar, 42.dp)
            if (u.online) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2ECC71)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(u.nick, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (u.bio.isNotBlank()) {
                Text(
                    u.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        trailing()
    }
}
