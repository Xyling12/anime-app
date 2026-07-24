package com.anipulse.app.ui.chat

import com.anipulse.app.ui.common.topSafePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val moderationReports = kotlinx.coroutines.flow.MutableStateFlow<List<com.anipulse.app.data.ModerationReport>>(emptyList())
    val moderationError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

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
                    if (settings.authAdmin) {
                        moderationReports.value = runCatching {
                            gateway.adminReports("Bearer $t")
                        }.getOrDefault(moderationReports.value)
                    }
                }
                kotlinx.coroutines.delay(8000)
            }
        }
    }

    fun resolveReport(id: Long, accepted: Boolean) {
        val token = settings.authToken ?: return
        viewModelScope.launch {
            runCatching {
                gateway.resolveAdminReport(
                    "Bearer $token",
                    com.anipulse.app.data.ResolveReportRequest(
                        id = id,
                        status = if (accepted) "resolved" else "rejected",
                    ),
                )
            }.onSuccess {
                moderationReports.value = moderationReports.value.filterNot { it.id == id }
                moderationError.value = null
            }.onFailure {
                moderationError.value = "Не удалось сохранить решение"
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
    val reports by viewModel.moderationReports.collectAsState()
    val moderationError by viewModel.moderationError.collectAsState()
    var reportQueueOpen by remember { mutableStateOf(false) }

    if (reportQueueOpen) {
        AlertDialog(
            onDismissRequest = { reportQueueOpen = false },
            title = { Text("Очередь жалоб · ${reports.size}") },
            text = {
                if (reports.isEmpty()) {
                    Text(moderationError ?: "Открытых жалоб нет")
                } else {
                    LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        items(reports, key = { it.id }) { report ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    "${report.reason} · ${report.type}",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    listOfNotNull(
                                        report.targetNick.takeIf { it.isNotBlank() },
                                        report.animeId.takeIf { it.isNotBlank() }?.let { "тайтл $it" },
                                        "жалоба от ${report.reporterNick}",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (report.details.isNotBlank()) Text(report.details, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { viewModel.resolveReport(report.id, false) }) { Text("Отклонить") }
                                    TextButton(onClick = { viewModel.resolveReport(report.id, true) }) { Text("Обработано") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reportQueueOpen = false }) { Text("Закрыть") } },
        )
    }

    Column(Modifier.fillMaxSize().topSafePadding()) {
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
        if (viewModel.settings.authAdmin) {
            ChatEntry(
                icon = { Icon(Icons.Filled.Report, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "Очередь жалоб",
                subtitle = "Проверка сообщений, комментариев и профилей",
                onClick = { reportQueueOpen = true },
                badge = reports.size,
            )
        }

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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15151F))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(13.dp))
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
