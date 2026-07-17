package com.anipulse.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.Notification
import com.anipulse.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val gateway: GatewayApi,
    settings: SettingsStore,
) : ViewModel() {
    private val token = settings.authToken
    private val _items = MutableStateFlow<List<Notification>>(emptyList())
    val items: StateFlow<List<Notification>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            token?.let { t ->
                runCatching { gateway.notifications("Bearer $t") }
                    .onSuccess { list -> _items.value = list.sortedByDescending { it.at } }
                // Открыли список — всё считается прочитанным (точка в меню погаснет).
                runCatching { gateway.markNotificationsRead("Bearer $t") }
            }
        }
    }
}

/** Уведомления: @упоминания, ЛС, позже — заявки в друзья. Тап по ЛС/упоминанию — переход. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenDm: (String) -> Unit,
    onOpenGlobalChat: () -> Unit,
    onOpenFriends: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Уведомления", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (items.isEmpty()) {
            Text(
                "Пока нет уведомлений",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (n.type) {
                                    "dm" -> onOpenDm(n.from)
                                    "friend_request", "friend_accept" -> onOpenFriends()
                                    else -> onOpenGlobalChat()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (n.type == "dm") Icons.Filled.Mail else Icons.Filled.AlternateEmail,
                            contentDescription = null,
                            tint = if (n.read) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (n.type) {
                                    "dm" -> "${n.from} написал(а) вам"
                                    "mention" -> "${n.from} упомянул(а) вас" +
                                        if (n.source?.startsWith("comment") == true) " в комментариях" else " в чате"
                                    "friend_request" -> "${n.from} — заявка в друзья"
                                    "friend_accept" -> "${n.from} принял(а) заявку"
                                    else -> n.from
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold,
                            )
                            Text(
                                n.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Text(
                            SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(n.at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
