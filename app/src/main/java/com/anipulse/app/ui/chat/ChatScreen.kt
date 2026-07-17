package com.anipulse.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import com.anipulse.app.ui.common.Avatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Регексп упоминаний: @ник (буквы/цифры/._-, 2–24 символа). */
private val MENTION_RE = Regex("@[\\w.-]{2,24}")

/** Подсвечивает @упоминания; своё — жирным на розовом фоне. */
fun highlightMentions(text: String, myNick: String?): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in MENTION_RE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val isMe = myNick != null && m.value.equals("@$myNick", ignoreCase = true)
        addStyle(
            SpanStyle(
                color = Color(0xFFFF4D8D),
                fontWeight = FontWeight.Bold,
                background = if (isMe) Color(0x33FF4D8D) else Color.Unspecified,
            ),
            start = length,
            end = length + m.value.length,
        )
        append(m.value)
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onGoProfile: () -> Unit,
    onOpenDm: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var cardNick by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<com.anipulse.app.data.ChatMessage?>(null) }
    var menuForId by remember { mutableStateOf<Long?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Автопрокрутка к новым сообщениям
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Чат AniPulse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "Пока тихо… Напиши первым!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.messages, key = { it.id }) { m ->
                val mine = m.nick == state.myNick
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    if (!mine) {
                        // Тап по аватару — карточка пользователя
                        Box(Modifier.clickable { cardNick = m.nick }) { Avatar(m.avatar, 30.dp) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box {
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
                            .combinedClickable(onClick = {}, onLongClick = { menuForId = m.id })
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .widthIn(max = 280.dp),
                    ) {
                        // Цитата, на которую отвечает сообщение
                        m.replyTo?.let { r ->
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth(),
                            ) {
                                Text(
                                    r.nick,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    r.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!mine) {
                            // Тап по нику — вставить обращение @ник в поле ввода
                            Text(
                                m.nick,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    val tag = "@${m.nick} "
                                    if (!input.contains(tag)) input = tag + input
                                },
                            )
                        }
                        Text(highlightMentions(m.text, state.myNick), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                    // Меню по длинному тапу
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuForId == m.id,
                        onDismissRequest = { menuForId = null },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Ответить") },
                            onClick = { replyTarget = m; menuForId = null },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Упомянуть") },
                            onClick = {
                                val tag = "@${m.nick} "
                                if (!input.contains(tag)) input = tag + input
                                menuForId = null
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Копировать") },
                            onClick = {
                                clipboard.setText(AnnotatedString(m.text))
                                menuForId = null
                            },
                        )
                        if (!mine) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Профиль") },
                                onClick = { cardNick = m.nick; menuForId = null },
                            )
                        }
                        if (viewModel.isAdmin()) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Удалить 🛡", color = MaterialTheme.colorScheme.error) },
                                onClick = { viewModel.deleteMessage(m.id); menuForId = null },
                            )
                            if (!mine) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Бан 24ч 🛡", color = MaterialTheme.colorScheme.error) },
                                    onClick = { viewModel.banUser(m.nick, 24); menuForId = null },
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        if (state.isLoggedIn) {
            // Плашка «Ответ на …» над полем ввода
            replyTarget?.let { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Ответ ${r.nick}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            r.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        "✕",
                        Modifier.clickable { replyTarget = null }.padding(6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // @автодополнение: набрал "@пре" — чипы с никами из чата; тап вставляет "@ник "
            val atPos = input.lastIndexOf('@')
            val mentionQuery = if (atPos >= 0) input.substring(atPos + 1) else null
            if (mentionQuery != null && !mentionQuery.contains(' ')) {
                val suggestions = state.messages.map { it.nick }.distinct()
                    .filter { it != state.myNick && it.startsWith(mentionQuery, ignoreCase = true) }
                    .take(5)
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { nick ->
                            Text(
                                "@$nick",
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { input = input.substring(0, atPos) + "@$nick " }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
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
                    onClick = { viewModel.send(input, replyTarget?.id); input = ""; replyTarget = null },
                    enabled = !state.sending && input.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Войдите в аккаунт, чтобы писать →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp).clickable(onClick = onGoProfile),
                )
            }
        }
    }

    cardNick?.let { nick ->
        com.anipulse.app.ui.common.UserCardSheet(
            nick = nick,
            gateway = viewModel.gatewayApi,
            token = viewModel.token(),
            onDismiss = { cardNick = null },
            onWrite = { n -> cardNick = null; onOpenDm(n) },
        )
    }
}

