package com.anipulse.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.anipulse.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    val settings: SettingsStore,
    private val gateway: com.anipulse.app.data.GatewayApi,
) : ViewModel() {
    /** Бейджи непрочитанных: ЛС и уведомления. */
    val dmUnread = kotlinx.coroutines.flow.MutableStateFlow(0)
    val notifUnread = kotlinx.coroutines.flow.MutableStateFlow(0)

    init {
        viewModelScope.launch {
            while (true) {
                settings.authToken?.let { t ->
                    dmUnread.value = runCatching {
                        gateway.dmList("Bearer $t").sumOf { it.unread }
                    }.getOrDefault(dmUnread.value)
                    notifUnread.value = runCatching {
                        gateway.notifications("Bearer $t").count { !it.read }
                    }.getOrDefault(notifUnread.value)
                }
                kotlinx.coroutines.delay(8000)
            }
        }
    }
}

/**
 * Хаб «Чаты»: общий чат, ЛС (появятся после серверного обновления), настройка уведомлений.
 * Логика уведомлений: «Все» — любое сообщение чата, «Только @упоминания» (дефолт) — пуш
 * приходит лишь когда тебя тегнули, «Выкл» — тишина.
 */
@Composable
fun ChatsScreen(
    onBack: () -> Unit,
    onOpenGlobalChat: () -> Unit,
    onOpenDms: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFriends: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    var notifyMode by remember { mutableStateOf(viewModel.settings.chatNotifyMode) }
    val dmUnread by viewModel.dmUnread.collectAsState()
    val notifUnread by viewModel.notifUnread.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Чаты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        ChatEntry(
            icon = { Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "Общий чат",
            subtitle = "Все пользователи AniPulse",
            onClick = onOpenGlobalChat,
        )
        ChatEntry(
            icon = { Icon(Icons.Filled.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "Личные сообщения",
            subtitle = "Диалоги с пользователями",
            onClick = onOpenDms,
            badge = dmUnread,
        )
        ChatEntry(
            icon = { Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "Друзья",
            subtitle = "Список друзей и заявки, кто онлайн",
            onClick = onOpenFriends,
        )
        ChatEntry(
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "Уведомления",
            subtitle = "@упоминания и сообщения",
            onClick = onOpenNotifications,
            badge = notifUnread,
        )

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Уведомления чата",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to "Все",
                "mentions" to "Только @упоминания",
                "off" to "Выкл",
            ).forEach { (key, label) ->
                FilterChip(
                    selected = notifyMode == key,
                    onClick = {
                        notifyMode = key
                        viewModel.settings.chatNotifyMode = key
                    },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                )
            }
        }
        Text(
            "«Только @упоминания» — пуш придёт, лишь когда тебя тегнули в чате.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatEntry(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    badge: Int = 0,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge > 0) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 99) "99+" else "$badge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
