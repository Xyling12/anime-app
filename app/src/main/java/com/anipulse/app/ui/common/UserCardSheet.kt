package com.anipulse.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.FriendActionRequest
import com.anipulse.app.data.UserCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Карточка пользователя (по тапу на аватар): аватар, онлайн, био, статистика,
 * любимый жанр, кнопки «Написать» и «В друзья». Гость видит карточку без кнопок действий.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCardSheet(
    nick: String,
    gateway: GatewayApi,
    token: String?,
    onDismiss: () -> Unit,
    onWrite: (String) -> Unit,
) {
    var card by remember { mutableStateOf<UserCard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var friendState by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(nick) {
        runCatching { gateway.userCard(token?.let { "Bearer $it" }, nick) }
            .onSuccess { card = it; friendState = it.friendState }
            .onFailure { error = "Не удалось загрузить профиль" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val c = card
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c == null -> Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Box {
                        Avatar(c.avatar, 84.dp)
                        if (c.online) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2ECC71)),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(c.nick, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (c.online) "онлайн" else c.lastSeen?.let {
                            "был(а) " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (c.online) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.bio.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(c.bio, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    c.createdAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "В AniPulse с " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("${c.stats?.watchedEpisodes ?: 0}", "серий")
                        StatCell("${(c.stats?.watchMinutes ?: 0) / 60}ч", "просмотра")
                        StatCell("${c.stats?.startedTitles ?: 0}", "тайтлов")
                        StatCell("${c.commentsCount}", "комментов")
                    }
                    c.favoriteGenre?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Любимый жанр: $it",
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (token != null && friendState != "self") {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onWrite(c.nick) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Написать")
                            }
                            OutlinedButton(
                                onClick = {
                                    val action = when (friendState) {
                                        "incoming" -> "accept"
                                        "friends" -> "remove"
                                        "none", null -> "add"
                                        else -> null
                                    }
                                    action?.let { a ->
                                        scope.launch {
                                            runCatching {
                                                gateway.friendAction("Bearer $token", a, FriendActionRequest(c.nick))
                                            }.onSuccess { friendState = it.state }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = friendState != "outgoing",
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (friendState) {
                                        "friends" -> "В друзьях ✓"
                                        "outgoing" -> "Заявка ушла"
                                        "incoming" -> "Принять"
                                        else -> "В друзья"
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
