package com.anipulse.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    val authContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(authContext) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                viewModel.syncFromSettings()
                viewModel.refreshMe()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            authContext,
            receiver,
            android.content.IntentFilter(com.anipulse.app.MainActivity.ACTION_AUTH_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { authContext.unregisterReceiver(receiver) }
    }

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

    ProfileRedesign(
        state = state,
        isDarkTheme = isDarkTheme,
        onThemeToggle = onThemeToggle,
        viewModel = viewModel,
    )
    return

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

        // Выбор аватара: 12 пресетов + загрузка своей картинки (жмём до 256px JPEG перед отправкой)
        var avatarDialog by remember { mutableStateOf(false) }
        val pickCtx = androidx.compose.ui.platform.LocalContext.current
        val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    val src = pickCtx.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: return@runCatching
                    val side = minOf(src.width, src.height)
                    // центр-кроп в квадрат + даунскейл до 256
                    val square = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(square, 256, 256, true)
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    viewModel.uploadAvatar(out.toByteArray())
                }
                avatarDialog = false
            }
        }
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
                        if (state.nick != null) {
                            TextButton(onClick = {
                                photoPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }) { Text("📷 Загрузить свою…") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { avatarDialog = false }) { Text("Закрыть") } },
            )
        }
        state.avatarUploadError?.let {
            Text(
                it,
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Шапка: гость или аккаунт
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable { avatarDialog = true }) {
                Avatar(state.avatarId, 76.dp, nick = state.nick, rev = state.avatarRev, accountId = state.userId)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(state.nick ?: "Гость", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

        // Компактная строка статистики как в утверждённом макете.
        val hours = state.watchTimeMs / 3_600_000
        val minutes = state.watchTimeMs % 3_600_000 / 60_000
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFF15151F), shape = RoundedCornerShape(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileStat(Modifier.weight(1f), "${state.watchedEpisodes}", "Досмотрено")
                ProfileStat(Modifier.weight(1f), "$hours ч", "Просмотр")
                ProfileStat(Modifier.weight(1f), "${state.startedTitles}", "Начато")
                ProfileStat(Modifier.weight(1f), "${state.favoritesCount}", "В Моём")
            }
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
                        else Modifier.clickable { viewModel.createOAuthLinkCode { openOAuth(ctx, "yandex", it) } }
                    ),
                    "Яндекс", badge = "Я", badgeColor = Color(0xFFFC3F1D), linked = yandexLinked,
                )
                SocialButton(
                    Modifier.weight(1f).then(
                        if (vkLinked) Modifier
                        else Modifier.clickable { viewModel.createOAuthLinkCode { openOAuth(ctx, "vk", it) } }
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
private fun ProfileRedesign(
    state: ProfileState,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: ProfileViewModel,
) {
    var authMode by remember { mutableStateOf<String?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf<String?>(null) }
    var deleteAccountConfirm by remember { mutableStateOf(false) }
    var avatarDialog by remember { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            cropBitmap = context.contentResolver.openInputStream(it)?.use(android.graphics.BitmapFactory::decodeStream)
            avatarDialog = false
        }
    }
    authMode?.let { mode ->
        AuthDialog(
            mode = mode,
            busy = state.authBusy,
            error = state.authError,
            resetCodeSent = state.resetCodeSent,
            onDismiss = { authMode = null; viewModel.clearAuthError() },
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onForgot = viewModel::forgotPassword,
            onReset = viewModel::resetPassword,
        )
    }
    LaunchedEffect(state.nick) { if (state.nick != null) authMode = null }
    if (avatarDialog) {
        AlertDialog(
            onDismissRequest = { avatarDialog = false },
            title = { Text("Выберите аватар") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AVATAR_PRESETS.indices.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { id ->
                                Box(Modifier.clickable { viewModel.setAvatar(id); avatarDialog = false }) { Avatar(id, 54.dp) }
                            }
                        }
                    }
                    if (state.nick != null) TextButton(onClick = {
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Выбрать своё изображение") }
                }
            },
            confirmButton = { TextButton(onClick = { avatarDialog = false }) { Text("Закрыть") } },
        )
    }
    cropBitmap?.let { bitmap ->
        AvatarCropDialog(bitmap, onDismiss = { cropBitmap = null }) { bytes ->
            viewModel.uploadAvatar(bytes)
            cropBitmap = null
        }
    }
    if (profileDialog == "account") {
        Dialog(onDismissRequest = { profileDialog = null }) {
            Surface(color = Color(0xFF15151F), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(state.avatarId, 72.dp, nick = state.nick, rev = state.avatarRev, accountId = state.userId)
                    Spacer(Modifier.height(12.dp))
                    Text(state.nick.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(22.dp))
                    OutlinedButton(
                        onClick = { viewModel.logout(); profileDialog = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Выйти из аккаунта") }
                    TextButton(onClick = { profileDialog = null; deleteAccountConfirm = true }) {
                        Text("Удалить аккаунт и данные", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { profileDialog = null }) { Text("Отмена") }
                }
            }
        }
    }
    if (deleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { deleteAccountConfirm = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Будут удалены профиль, аватар, сообщения, комментарии, оценки и связи с друзьями. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAccount(); deleteAccountConfirm = false }) {
                    Text("Удалить навсегда", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteAccountConfirm = false }) { Text("Отмена") } },
        )
    }
    profileDialog?.takeIf { it != "account" }?.let { dialog ->
        AlertDialog(
            onDismissRequest = { profileDialog = null },
            title = { Text(when (dialog) { "account" -> "Аккаунт"; "privacy" -> "Конфиденциальность"; else -> "Справка и поддержка" }) },
            text = {
                Text(when (dialog) {
                    "account" -> "${state.nick.orEmpty()}\n${state.email.orEmpty()}"
                    "privacy" -> "Данные аккаунта используются только для синхронизации профиля, списка, оценок и комментариев."
                    else -> "Если возникла проблема, отправьте сообщение через пункт «Сообщить о баге» в настройках приложения."
                })
            },
            confirmButton = {
                if (dialog == "account" && state.nick != null) {
                    TextButton(onClick = { viewModel.logout(); profileDialog = null }) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
                } else TextButton(onClick = { profileDialog = null }) { Text("Понятно") }
            },
            dismissButton = { if (dialog == "account") TextButton(onClick = { profileDialog = null }) { Text("Отмена") } },
        )
    }
    val hours = state.watchTimeMs / 3_600_000
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { avatarDialog = true }) {
                Avatar(state.avatarId, 78.dp, nick = state.nick, rev = state.avatarRev, accountId = state.userId)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(state.nick ?: "Гость", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.nick != null) {
                    Text(state.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Просмотр и «Моё» работают без входа", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = Color(0x33FF4D8D), shape = RoundedCornerShape(6.dp)) {
                    Text("BETA · В разработке", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFFFF4D8D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Surface(color = Color(0xFF15151F), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                ReferenceStat(Modifier.weight(1f), "${state.watchingTitles}", "Смотрю")
                ReferenceStat(Modifier.weight(1f), "${state.plannedTitles}", "В планах")
                ReferenceStat(Modifier.weight(1f), "${state.completedTitles}", "Просмотрено")
                ReferenceStat(Modifier.weight(1f), if (hours > 999) "${hours / 1000}.${(hours % 1000) / 100}K" else "$hours", "Часов")
            }
        }

        ReferenceGroup {
            ReferenceRow(Icons.Outlined.Settings, "Настройки плеера", onClick = { playerExpanded = !playerExpanded })
            if (playerExpanded) {
                CompactToggle("Автопропуск опенинга", state.autoSkipOpening, viewModel::setAutoSkipOpening)
                CompactToggle("Автопропуск повтора", state.autoSkipRecap, viewModel::setAutoSkipRecap)
                CompactToggle("Следующая серия автоматически", state.autoNextEpisode, viewModel::setAutoNextEpisode)
            }
            ReferenceRow(Icons.Outlined.Notifications, "Уведомления", onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            })
            ReferenceRow(Icons.Outlined.DarkMode, "Светлая тема", "В разработке")
        }
        ReferenceGroup {
            ReferenceRow(Icons.Outlined.Person, "Аккаунт", onClick = { if (state.nick == null) authMode = "login" else profileDialog = "account" })
            if (state.nick != null) {
                if ("yandex" !in state.linked) ReferenceRow(Icons.Outlined.Person, "Привязать Яндекс", onClick = {
                    viewModel.createOAuthLinkCode { openOAuth(context, "yandex", it) }
                })
                if ("vk" !in state.linked) ReferenceRow(Icons.Outlined.Person, "Привязать VK", onClick = {
                    viewModel.createOAuthLinkCode { openOAuth(context, "vk", it) }
                })
            }
            ReferenceRow(Icons.Outlined.Security, "Конфиденциальность", onClick = {
                openUrl(context, "https://anipulsetv.ru/privacy")
            })
            ReferenceRow(Icons.Outlined.Info, "Пользовательское соглашение", onClick = {
                openUrl(context, "https://anipulsetv.ru/terms")
            })
            ReferenceRow(Icons.Outlined.Security, "Правила сообщества", onClick = {
                openUrl(context, "https://anipulsetv.ru/community-rules")
            })
            ReferenceRow(Icons.Outlined.HelpOutline, "Справка и поддержка", onClick = { profileDialog = "help" })
            ReferenceRow(Icons.Outlined.Security, "Правообладателям", onClick = {
                openUrl(context, "https://anipulsetv.ru/for-right-holders")
            })
        }
        ReferenceGroup {
            ReferenceRow(Icons.Outlined.Info, "Версия приложения", com.anipulse.app.BuildConfig.VERSION_NAME)
            Text(
                "Приложение находится на стадии разработки",
                modifier = Modifier.padding(start = 40.dp, end = 12.dp, bottom = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.nick == null) {
            Button(
                onClick = { authMode = "register" },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Создать аккаунт", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AvatarCropDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onCrop: (ByteArray) -> Unit,
) {
    var horizontal by remember { mutableStateOf(.5f) }
    var vertical by remember { mutableStateOf(.5f) }
    var zoom by remember { mutableStateOf(1f) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF15151F), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Область аватара", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Перемещайте фото и масштабируйте двумя пальцами", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                horizontal = (horizontal - pan.x / (size.width * zoom)).coerceIn(0f, 1f)
                                vertical = (vertical - pan.y / (size.height * zoom)).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = (.5f - horizontal) * 180f * zoom
                            translationY = (.5f - vertical) * 180f * zoom
                        },
                    )
                    Box(Modifier.size(250.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = {
                            val side = (minOf(bitmap.width, bitmap.height) / zoom).toInt().coerceAtLeast(1)
                            val left = ((bitmap.width - side) * horizontal).toInt().coerceIn(0, bitmap.width - side)
                            val top = ((bitmap.height - side) * vertical).toInt().coerceIn(0, bitmap.height - side)
                            val cropped = android.graphics.Bitmap.createBitmap(bitmap, left, top, side, side)
                            val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 256, 256, true)
                            val output = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)
                            onCrop(output.toByteArray())
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable private fun ReferenceStat(modifier: Modifier, value: String, label: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable private fun ReferenceGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color(0xFF15151F), shape = RoundedCornerShape(14.dp)) {
        Column(content = content)
    }
}

@Composable private fun ReferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (trailing == null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun CompactToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 40.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked, onChange, modifier = Modifier.size(width = 44.dp, height = 28.dp))
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

/** Открыть OAuth-вход; linkCode — одноразовый код, bearer-токен в URL не передаётся. */
private fun openUrl(ctx: android.content.Context, url: String) {
    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
}

private fun openOAuth(
    ctx: android.content.Context,
    provider: String,
    linkCode: String?,
    legalAccepted: Boolean = false,
) {
    val state = when {
        linkCode != null -> "link.$linkCode"
        legalAccepted -> "consent.2026-07-22"
        else -> ""
    }
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
    var acceptedTerms by remember { mutableStateOf(false) }
    var privacyConsent by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(mode) } // "login" | "register" | "forgot"
    val isRegister = step == "register"
    val isForgot = step == "forgot"

    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF09090F))) {
            Text(
                "‹",
                modifier = Modifier.padding(start = 20.dp, top = 54.dp).clickable(onClick = onDismiss).padding(8.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF15151F),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0B0B11)), contentAlignment = Alignment.Center) {
                    Text("A", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
                    Text("⌁", modifier = Modifier.align(Alignment.BottomCenter), color = Color(0xFFFF4D8D), fontWeight = FontWeight.Bold)
                }
                Text(
                    if (isRegister) "Создать аккаунт" else if (isForgot) "Восстановление пароля" else "Вход",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (isForgot) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Почта аккаунта") }, singleLine = true, enabled = !resetCodeSent, shape = RoundedCornerShape(12.dp))
                    if (resetCodeSent) {
                        Text("Код отправлен на почту (проверьте и папку «Спам»).", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Код из письма") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(
                            value = pw, onValueChange = { pw = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Новый пароль") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                } else if (isRegister) {
                    OutlinedTextField(value = nick, onValueChange = { nick = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ник") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Почта") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                } else {
                    OutlinedTextField(value = login, onValueChange = { login = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ник или почта") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                }
                if (!isForgot) {
                    OutlinedTextField(
                        value = pw, onValueChange = { pw = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (isRegister) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Подтверждение пароля") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = acceptedTerms,
                            onCheckedChange = { acceptedTerms = it },
                        )
                        Text(
                            "Принимаю пользовательское соглашение и правила сообщества",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { openUrl(context, "https://anipulsetv.ru/terms") },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = privacyConsent,
                            onCheckedChange = { privacyConsent = it },
                        )
                        Text(
                            "Отдельно соглашаюсь на обработку персональных данных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { openUrl(context, "https://anipulsetv.ru/personal-data-consent") },
                        )
                    }
                }
                if (step == "login") {
                    Text(
                        "Забыли пароль?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { step = "forgot"; pw = "" },
                    )
                    Text(
                        "Для входа через VK/Яндекс подтвердите документы ниже",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                        Text(
                            "Принимаю пользовательское соглашение",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { openUrl(context, "https://anipulsetv.ru/terms") },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = privacyConsent, onCheckedChange = { privacyConsent = it })
                        Text(
                            "Соглашаюсь на обработку персональных данных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { openUrl(context, "https://anipulsetv.ru/personal-data-consent") },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Text("Подождите…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                enabled = !busy && (isForgot || (acceptedTerms && privacyConsent)),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
                onClick = {
                    when {
                        isForgot && !resetCodeSent -> onForgot(email)
                        isForgot -> onReset(email, code, pw)
                        isRegister -> onRegister(nick, email, pw, pw2)
                        else -> onLogin(login, pw)
                    }
                },
                ) { Text(if (isRegister) "Зарегистрироваться" else if (isForgot) { if (resetCodeSent) "Сменить пароль" else "Отправить код" } else "Войти", fontWeight = FontWeight.Bold) }
                if (!isForgot) {
                    Text("или войти через", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val socialEnabled = acceptedTerms && privacyConsent
                        SocialButton(
                            Modifier.weight(1f).clickable(enabled = socialEnabled) {
                                openOAuth(context, "yandex", null, legalAccepted = acceptedTerms && privacyConsent)
                            }, "Яндекс", "Я", Color(0xFFFC3F1D)
                        )
                        SocialButton(
                            Modifier.weight(1f).clickable(enabled = socialEnabled) {
                                openOAuth(context, "vk", null, legalAccepted = acceptedTerms && privacyConsent)
                            }, "VK", "VK", Color(0xFF0077FF)
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
        }
    }
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
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
