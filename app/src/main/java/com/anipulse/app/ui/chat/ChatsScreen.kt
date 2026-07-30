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
import com.anipulse.app.ui.common.launchForegroundPolling
import com.anipulse.app.ui.common.ScreenPollingEffect
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
    val moderationBusyIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
    private val pollingActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun setPollingActive(active: Boolean) { pollingActive.value = active }

    init {
        viewModelScope.launchForegroundPolling(8_000, pollingActive) {
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
        }
    }

    fun actOnReport(report: com.anipulse.app.data.ModerationReport, action: String) {
        val token = settings.authToken ?: return
        if (report.id in moderationBusyIds.value) return
        moderationBusyIds.value = moderationBusyIds.value + report.id
        viewModelScope.launch {
            runCatching {
                gateway.actOnAdminReport(
                    "Bearer $token",
                    com.anipulse.app.data.ModerationActionRequest(
                        id = report.id,
                        action = action,
                        resolution = when (action) {
                            "reject" -> "Нарушение не подтверждено"
                            "remove" -> "Контент удалён"
                            "ban_24h" -> "Пользователь заблокирован на 24 часа"
                            "remove_ban_24h" -> "Контент удалён, пользователь заблокирован на 24 часа"
                            else -> "Проверено модератором"
                        },
                    ),
                )
            }.onSuccess { response ->
                moderationReports.value = moderationReports.value.filterNot { it.id == report.id }
                moderationError.value = if (action.contains("remove") && response.removed == 0) {
                    "Жалоба закрыта, но спорный контент уже отсутствовал"
                } else {
                    null
                }
            }.onFailure {
                val serverMessage = (it as? retrofit2.HttpException)
                    ?.response()?.errorBody()?.string()
                    ?.let { body -> Regex("\"error\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) }
                moderationError.value = serverMessage ?: "Не удалось выполнить решение"
            }.also {
                moderationBusyIds.value = moderationBusyIds.value - report.id
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
    ScreenPollingEffect(viewModel::setPollingActive)
    var notifyMode by remember { mutableStateOf(viewModel.settings.chatNotifyMode) }
    val dmUnread by viewModel.dmUnread.collectAsState()
    val notifUnread by viewModel.notifUnread.collectAsState()
    val reports by viewModel.moderationReports.collectAsState()
    val moderationError by viewModel.moderationError.collectAsState()
    val moderationBusyIds by viewModel.moderationBusyIds.collectAsState()
    var reportQueueOpen by remember { mutableStateOf(false) }
    var pendingModeration by remember {
        mutableStateOf<Pair<com.anipulse.app.data.ModerationReport, String>?>(null)
    }

    pendingModeration?.let { (report, action) ->
        val actionText = when (action) {
            "remove" -> "удалить спорный контент"
            "ban_24h" -> "заблокировать пользователя на 24 часа"
            "remove_ban_24h" -> "удалить контент и заблокировать пользователя на 24 часа"
            else -> "выполнить действие"
        }
        AlertDialog(
            onDismissRequest = { pendingModeration = null },
            title = { Text("Подтвердите решение") },
            text = {
                Text(
                    "Вы действительно хотите $actionText? " +
                        "Жалоба №${report.id}, пользователь: ${report.targetNick.ifBlank { "не указан" }}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.actOnReport(report, action)
                        pendingModeration = null
                    },
                ) {
                    Text("Подтвердить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModeration = null }) { Text("Отмена") }
            },
        )
    }

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
                            val moderationBusy = report.id in moderationBusyIds
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
                                report.snapshot?.let { snapshot ->
                                    Spacer(Modifier.height(6.dp))
                                    val author = snapshot.nick.ifBlank { snapshot.from }
                                    if (author.isNotBlank()) {
                                        Text(
                                            listOfNotNull(
                                                "Автор: $author",
                                                snapshot.to.takeIf { it.isNotBlank() }?.let { "кому: $it" },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    if (snapshot.text.isNotBlank()) {
                                        Text(
                                            "«${snapshot.text}»",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .padding(8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = { viewModel.actOnReport(report, "reject") },
                                        enabled = !moderationBusy,
                                    ) {
                                        Text("Отклонить")
                                    }
                                    TextButton(
                                        onClick = { pendingModeration = report to "remove" },
                                        enabled = !moderationBusy,
                                    ) {
                                        Text("Удалить")
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = { pendingModeration = report to "ban_24h" },
                                        enabled = !moderationBusy,
                                    ) {
                                        Text("Бан 24ч")
                                    }
                                    TextButton(
                                        onClick = { pendingModeration = report to "remove_ban_24h" },
                                        enabled = !moderationBusy,
                                    ) {
                                        Text("Удалить + бан", color = MaterialTheme.colorScheme.error)
                                    }
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
