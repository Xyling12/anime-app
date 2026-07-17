package com.anipulse.app.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anipulse.app.ui.common.AVATAR_PRESETS
import com.anipulse.app.ui.common.Avatar

private val PulseGradient = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFFF4D8D)))

@Composable
fun ProfileScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // После возврата из OAuth-браузера подтягиваем аккаунт
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.syncFromSettings()
                viewModel.refreshMe() // привязки Яндекс/VK обновляются после возврата из браузера
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp),
    ) {
        // Заголовок теперь в общей шапке (AnimeLibRoot) — здесь не дублируем.

        // Диалог входа/регистрации
        var authDialog by remember { mutableStateOf<String?>(null) } // "login" | "register" | null
        authDialog?.let { mode ->
            AuthDialog(
                mode = mode,
                busy = state.authBusy,
                error = state.authError,
                resetCodeSent = state.resetCodeSent,
                onDismiss = { authDialog = null; viewModel.clearAuthError() },
                onLogin = viewModel::login,
                onRegister = viewModel::register,
                onForgot = viewModel::forgotPassword,
                onReset = viewModel::resetPassword,
            )
        }
        // Закрыть диалог после успешного входа
        LaunchedEffect(state.nick) { if (state.nick != null) authDialog = null }

        // Выбор аватара
        var avatarDialog by remember { mutableStateOf(false) }
        if (avatarDialog) {
            AlertDialog(
                onDismissRequest = { avatarDialog = false },
                title = { Text("Выбери аватар") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AVATAR_PRESETS.indices.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { id ->
                                    Box(Modifier.clickable { viewModel.setAvatar(id); avatarDialog = false }) {
                                        Avatar(id, 56.dp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { avatarDialog = false }) { Text("Закрыть") } },
            )
        }

        // Шапка: гость или аккаунт
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable { avatarDialog = true }) {
                Avatar(state.avatarId, 52.dp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(state.nick ?: "Гость", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    state.email ?: "Просмотр и «Моё» работают без входа",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.nick != null) {
                TextButton(onClick = viewModel::logout) { Text("Выйти") }
            }
        }

        // Статистика 2×2
        val hours = state.watchTimeMs / 3_600_000
        val minutes = state.watchTimeMs % 3_600_000 / 60_000
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f).height(110.dp), "${state.watchedEpisodes}", "Серий просмотрено", Icons.Outlined.Visibility)
            StatCard(Modifier.weight(1f).height(110.dp), "$hours ч $minutes м", "Времени в аниме", Icons.Outlined.Schedule)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f).height(110.dp), "${state.startedTitles}", "Тайтлов начато", Icons.Outlined.PlayCircleOutline)
            StatCard(Modifier.weight(1f).height(110.dp), "${state.favoritesCount}", "В списке «Моё»", Icons.Outlined.FavoriteBorder)
        }

        // Плашка подтверждения почты (без него закрыты чат/комменты/ЛС)
        if (state.nick != null && !state.emailVerified) {
            var code by remember { mutableStateOf("") }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                    .padding(14.dp),
            ) {
                Text(
                    "Подтвердите почту",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Мы отправили 6-значный код на ${state.email ?: "вашу почту"}. Без подтверждения закрыты чат, комментарии и ЛС.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Код из письма") },
                        singleLine = true,
                    )
                    TextButton(onClick = { viewModel.verifyEmail(code) }, enabled = code.length == 6) {
                        Text("Подтвердить")
                    }
                }
                Row {
                    TextButton(onClick = viewModel::resendCode) { Text("Отправить код снова") }
                }
                state.verifyMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // «О себе» — виден другим в карточке пользователя
        if (state.nick != null) {
            var bio by remember { mutableStateOf("") }
            var bioSaved by remember { mutableStateOf(false) }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(200); bioSaved = false },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("О себе (видно другим в вашей карточке)") },
                maxLines = 3,
                trailingIcon = {
                    TextButton(onClick = { viewModel.saveBio(bio); bioSaved = true }, enabled = bio.isNotBlank() && !bioSaved) {
                        Text(if (bioSaved) "✓" else "Сохранить")
                    }
                },
            )
        }

        if (state.nick == null) {
            // Аккаунт: регистрация / вход
            Text(
                "Аккаунт",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFFFF4D8D))
                    .clip(RoundedCornerShape(16.dp))
                    .background(PulseGradient)
                    .clickable { authDialog = "register" }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Создать аккаунт", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = { authDialog = "login" },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("У меня уже есть аккаунт — войти") }
            Text(
                "Аккаунт откроет чат, комментарии и свой рейтинг",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Или войти через сервис",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton(Modifier.weight(1f).clickable { openOAuth(ctx, "yandex", null) }, "Яндекс", badge = "Я", badgeColor = Color(0xFFFC3F1D))
                SocialButton(Modifier.weight(1f).clickable { openOAuth(ctx, "vk", null) }, "VK", badge = "VK", badgeColor = Color(0xFF0077FF))
            }
        } else {
            // Привязка соцсервисов
            Text(
                "Привязать сервис",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val yandexLinked = "yandex" in state.linked
                val vkLinked = "vk" in state.linked
                SocialButton(
                    Modifier.weight(1f).then(
                        if (yandexLinked) Modifier // уже привязан — повторная привязка не нужна
                        else Modifier.clickable { viewModel.currentToken()?.let { openOAuth(ctx, "yandex", it) } }
                    ),
                    "Яндекс", badge = "Я", badgeColor = Color(0xFFFC3F1D), linked = yandexLinked,
                )
                SocialButton(
                    Modifier.weight(1f).then(
                        if (vkLinked) Modifier
                        else Modifier.clickable { viewModel.currentToken()?.let { openOAuth(ctx, "vk", it) } }
                    ),
                    "VK", badge = "VK", badgeColor = Color(0xFF0077FF), linked = vkLinked,
                )
            }
            TextButton(
                onClick = viewModel::logoutAllDevices,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Выйти на всех устройствах", color = MaterialTheme.colorScheme.error) }
        }

        // Настройки
        Text(
            "Настройки",
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        SettingRow("Тёмная тема", isDarkTheme, { onThemeToggle() })
        
        Text(
            "Настройки плеера",
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        SettingRow("Автопропуск опенинга", state.autoSkipOpening, viewModel::setAutoSkipOpening)
        SettingRow("Автопропуск повтора", state.autoSkipRecap, viewModel::setAutoSkipRecap)
        SettingRow("Автопереход к след. серии", state.autoNextEpisode, viewModel::setAutoNextEpisode)

        var bugDialog by remember { mutableStateOf(false) }
        TextButton(
            onClick = { bugDialog = true },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) { Text("Сообщить о баге") }
        if (bugDialog) {
            BugReportDialog(
                busy = state.bugReportBusy,
                error = state.bugReportError,
                sent = state.bugReportSent,
                defaultContact = state.email.orEmpty(),
                onDismiss = { bugDialog = false; viewModel.clearBugReport() },
                onSend = viewModel::sendBugReport,
            )
        }
        LaunchedEffect(state.bugReportSent) {
            if (state.bugReportSent) bugDialog = false
        }

        val legalCtx = androidx.compose.ui.platform.LocalContext.current
        Row {
            TextButton(
                onClick = { openUrl(legalCtx, "https://5-42-99-195.sslip.io/privacy") },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Конфиденциальность", style = MaterialTheme.typography.labelSmall) }
            TextButton(
                onClick = { openUrl(legalCtx, "https://5-42-99-195.sslip.io/for-right-holders") },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Правообладателям", style = MaterialTheme.typography.labelSmall) }
        }

        Text(
            "Версия ${com.anipulse.app.BuildConfig.VERSION_NAME}",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BugReportDialog(
    busy: Boolean,
    error: String?,
    sent: Boolean,
    defaultContact: String,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf(defaultContact) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сообщить о баге") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sent) {
                    Text("Спасибо! Отчёт отправлен.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(2000) },
                        label = { Text("Что пошло не так?") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        label = { Text("Почта для ответа (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Text("Отправляем…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!sent) {
                TextButton(enabled = !busy, onClick = { onSend(text, contact) }) { Text("Отправить") }
            } else {
                TextButton(onClick = onDismiss) { Text("Готово") }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Открыть OAuth-вход в браузере; linkToken != null → режим привязки к текущему аккаунту. */
private fun openUrl(ctx: android.content.Context, url: String) {
    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
}

private fun openOAuth(ctx: android.content.Context, provider: String, linkToken: String?) {
    val state = if (linkToken != null) "link.$linkToken" else ""
    val uri = android.net.Uri.parse(
        com.anipulse.app.data.Api.GATEWAY + "auth/$provider" + if (state.isNotEmpty()) "?state=" + android.net.Uri.encode(state) else ""
    )
    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
}

@Composable
private fun AuthDialog(
    mode: String,
    busy: Boolean,
    error: String?,
    resetCodeSent: Boolean,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onForgot: (String) -> Unit,
    onReset: (String, String, String) -> Unit,
) {
    var nick by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(mode) } // "login" | "register" | "forgot"
    val isRegister = step == "register"
    val isForgot = step == "forgot"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRegister) "Создать аккаунт" else if (isForgot) "Восстановление пароля" else "Вход") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isForgot) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Почта аккаунта") }, singleLine = true, enabled = !resetCodeSent)
                    if (resetCodeSent) {
                        Text("Код отправлен на почту (проверьте и папку «Спам»).", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Код из письма") }, singleLine = true)
                        OutlinedTextField(
                            value = pw, onValueChange = { pw = it }, label = { Text("Новый пароль") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                } else if (isRegister) {
                    OutlinedTextField(value = nick, onValueChange = { nick = it }, label = { Text("Ник") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Почта") }, singleLine = true)
                } else {
                    OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text("Ник или почта") }, singleLine = true)
                }
                if (!isForgot) {
                    OutlinedTextField(
                        value = pw, onValueChange = { pw = it }, label = { Text("Пароль") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (isRegister) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text("Подтверждение пароля") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (step == "login") {
                    Text(
                        "Забыли пароль?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { step = "forgot"; pw = "" },
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Text("Подождите…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when {
                        isForgot && !resetCodeSent -> onForgot(email)
                        isForgot -> onReset(email, code, pw)
                        isRegister -> onRegister(nick, email, pw, pw2)
                        else -> onLogin(login, pw)
                    }
                },
            ) { Text(if (isRegister) "Зарегистрироваться" else if (isForgot) { if (resetCodeSent) "Сменить пароль" else "Отправить код" } else "Войти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.padding(16.dp)) {
            Column(Modifier.padding(end = 28.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun SocialButton(
    modifier: Modifier,
    label: String,
    badge: String? = null,       // текст иконки-бейджа: "VK" / "Я"
    badgeColor: Color = Color.Transparent,
    linked: Boolean = false,
) {
    Surface(modifier = modifier.clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge != null) {
                Box(
                    Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (linked) "$label ✓" else label,
                style = MaterialTheme.typography.labelLarge,
                color = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
