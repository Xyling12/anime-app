package com.anipulse.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anipulse.app.ui.common.AVATAR_PRESETS
import com.anipulse.app.ui.common.Avatar

@Composable
fun ProfileScreen(
    isDarkTheme: Boolean = true,
    onThemeToggle: () -> Unit = {},
    viewModel: ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.syncFromSettings()
                viewModel.refreshMe()
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
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

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                ReferenceStat(Modifier.weight(1f), "${state.watchingTitles}", "Смотрю")
                ReferenceStat(Modifier.weight(1f), "${state.plannedTitles}", "В планах")
                ReferenceStat(Modifier.weight(1f), "${state.completedTitles}", "Просмотрено")
                ReferenceStat(Modifier.weight(1f), if (hours > 999) "${hours / 1000}.${(hours % 1000) / 100}K" else "$hours", "Часов")
            }
        }

        // Карточка Ранга и Таблицы лидеров
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFF4D8D).copy(alpha = 0.35f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.badge, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ранг: ${state.levelTitle}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val rankText = if (state.rank > 0) "#${state.rank} в общем топе" else "${state.watchedEpisodes} серий просмотрено"
                        Text(
                            text = rankText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB300),
                        )
                    }
                }
                Button(
                    onClick = { viewModel.toggleLeaderboard(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D8D)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("👑 Топ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.showLeaderboardDialog) {
            LeaderboardDialog(state, onDismiss = { viewModel.toggleLeaderboard(false) })
        }

        // Блок сообщества Telegram
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF0288D1).copy(alpha = 0.4f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✈", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Telegram сообщество AniPulse",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Анонсы серий, обсуждения аниме, общение и связь с создателями:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { openUrl(context, "https://t.me/+D92r3ttCfNtkYWFi") },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Вступить в Telegram группу", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.admin) {
            AdminAnalyticsCard(
                value = state.analytics,
                loading = state.analyticsBusy,
                onRefresh = viewModel::refreshAnalytics,
            )
        }

        ThemeSelectorCard(
            isDarkTheme = isDarkTheme,
            onThemeToggle = onThemeToggle,
        )

        ReferenceGroup {
            ReferenceRow(Icons.Outlined.Settings, "Настройки плеера", onClick = { playerExpanded = !playerExpanded })
            if (playerExpanded) {
                CompactToggle("Автопропуск опенинга", state.autoSkipOpening, viewModel::setAutoSkipOpening)
                CompactToggle("Автопропуск повтора", state.autoSkipRecap, viewModel::setAutoSkipRecap)
                CompactToggle("Следующая серия автоматически", state.autoNextEpisode, viewModel::setAutoNextEpisode)
            }
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
            ReferenceRow(Icons.Outlined.Security, "Согласие на обработку персональных данных", onClick = {
                openUrl(context, "https://anipulsetv.ru/personal-data-consent")
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
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
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
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
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

@Composable private fun CompactToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
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
private fun ThemeSelectorCard(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    contentDescription = null,
                    tint = Color(0xFFFF4D8D),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Тема оформления",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Светлая
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (!isDarkTheme) Color(0xFFFF4D8D) else Color.Transparent
                        )
                        .clickable { if (isDarkTheme) onThemeToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.LightMode,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Светлая",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!isDarkTheme) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Тёмная
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isDarkTheme) Color(0xFFFF4D8D) else Color.Transparent
                        )
                        .clickable { if (!isDarkTheme) onThemeToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.DarkMode,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Тёмная",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isDarkTheme) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAnalyticsCard(
    value: com.anipulse.app.data.AdminAnalytics?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BarChart, null, tint = Color(0xFFFF4D8D))
                Text(
                    "Статистика AniPulse",
                    Modifier.padding(start = 9.dp).weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Обновить") }
            }
            if (loading && value == null) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFF4D8D)) }
            } else if (value != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AdminMetric("${value.online}", "Онлайн")
                    AdminMetric("${value.today.active}", "Сегодня")
                    AdminMetric("${value.last7Days.active}", "7 дней")
                    AdminMetric("${value.last30Days.active}", "30 дней")
                }
                Text(
                    "Онлайн сейчас: приложение ${value.onlineAndroid} · сайт ${value.onlineWeb}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Первые запуски: ${value.totalInstalls} · Скачивания APK: ${value.totalDownloads} · Аккаунты: ${value.registeredUsers}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "За неделю: ${value.last7Days.averageSessionMinutes} мин. в среднем · приложение ${value.last7Days.android} · сайт ${value.last7Days.web}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Статистика временно недоступна", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = Color(0xFFFF4D8D))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))) {
            Text(
                "‹",
                modifier = Modifier.padding(start = 20.dp, top = 54.dp).clickable(onClick = onDismiss).padding(8.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("A", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardDialog(
    state: ProfileState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF13131F),
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "👑 Таблица лидеров",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Рейтинг активных зрителей сообщества",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E2E),
                    modifier = Modifier.size(32.dp).clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main Content
            Box(Modifier.weight(1f)) {
                if (state.leaderboardBusy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF4D8D))
                    }
                } else if (state.leaderboard.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👑", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Пока нет активных зрителей",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Смотрите аниме, синхронизируйте просмотры и займите 1-е место в топе!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val top1 = state.leaderboard.getOrNull(0)
                    val top2 = state.leaderboard.getOrNull(1)
                    val top3 = state.leaderboard.getOrNull(2)
                    val rest = if (state.leaderboard.size > 3) state.leaderboard.drop(3) else emptyList()

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top-3 Podium Card
                        if (top1 != null) {
                            item {
                                PodiumSection(top1, top2, top3, state.userId)
                                Spacer(Modifier.height(6.dp))
                            }
                        }

                        // Remaining Ranks (#4+)
                        if (rest.isNotEmpty()) {
                            item {
                                Text(
                                    "Общий рейтинг",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(rest) { entry ->
                                LeaderboardRow(entry, isMe = state.userId > 0 && entry.userId == state.userId)
                            }
                        }
                    }
                }
            }

            // Pinned Bottom Bar: "Ваш статус"
            if (state.nick != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1B182B),
                    border = BorderStroke(1.dp, Color(0xFFFF4D8D).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF4D8D).copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                val rankText = if (state.rank > 0) "#${state.rank}" else "-"
                                Text(
                                    rankText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF4D8D)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        state.nick ?: "Вы",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "• ${state.levelTitle} ${state.badge}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                Text(
                                    "Ваш текущий результат в рейтинге",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${state.watchedEpisodes} эп.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF4D8D)
                            )
                            val hours = String.format(java.util.Locale.US, "%.1f ч", state.watchTimeMs / 3600000.0)
                            Text(
                                hours,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSection(
    top1: com.anipulse.app.data.LeaderboardEntry,
    top2: com.anipulse.app.data.LeaderboardEntry?,
    top3: com.anipulse.app.data.LeaderboardEntry?,
    currentUserId: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF181829),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ТОП-3 ЗРИТЕЛЯ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFB300),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place (Silver)
                if (top2 != null) {
                    PodiumColumn(
                        entry = top2,
                        place = 2,
                        placeIcon = "🥈",
                        accentColor = Color(0xFFC0C0C0),
                        podiumHeight = 90.dp,
                        modifier = Modifier.weight(1f),
                        isMe = currentUserId > 0 && top2.userId == currentUserId
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // 1st Place (Gold)
                PodiumColumn(
                    entry = top1,
                    place = 1,
                    placeIcon = "👑",
                    accentColor = Color(0xFFFFD700),
                    podiumHeight = 115.dp,
                    modifier = Modifier.weight(1.15f),
                    isMe = currentUserId > 0 && top1.userId == currentUserId
                )

                // 3rd Place (Bronze)
                if (top3 != null) {
                    PodiumColumn(
                        entry = top3,
                        place = 3,
                        placeIcon = "🥉",
                        accentColor = Color(0xFFCD7F32),
                        podiumHeight = 75.dp,
                        modifier = Modifier.weight(1f),
                        isMe = currentUserId > 0 && top3.userId == currentUserId
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    entry: com.anipulse.app.data.LeaderboardEntry,
    place: Int,
    placeIcon: String,
    accentColor: Color,
    podiumHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon above avatar (Crown / Medal)
        Text(placeIcon, style = if (place == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))

        // Avatar with colored border
        Box(
            Modifier
                .size(if (place == 1) 48.dp else 40.dp)
                .background(accentColor.copy(alpha = 0.2f), CircleShape)
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Avatar(
                entry.avatar,
                size = if (place == 1) 44.dp else 36.dp,
                nick = entry.nick,
                accountId = entry.userId
            )
        }

        Spacer(Modifier.height(6.dp))

        // Nickname
        Text(
            text = entry.nick,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isMe) Color(0xFFFF4D8D) else Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        // Episodes
        Text(
            text = "${entry.episodesWatched} эп.",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accentColor
        )

        Spacer(Modifier.height(6.dp))

        // Podium Block
        Surface(
            modifier = Modifier.fillMaxWidth().height(podiumHeight),
            color = accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "#$place",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                    Text(
                        text = entry.levelTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: com.anipulse.app.data.LeaderboardEntry,
    isMe: Boolean,
) {
    Surface(
        Modifier.fillMaxWidth(),
        color = if (isMe) Color(0xFF2A1F3D) else Color(0xFF1A1A2B),
        shape = RoundedCornerShape(14.dp),
        border = if (isMe) BorderStroke(1.dp, Color(0xFFFF4D8D).copy(alpha = 0.6f))
                 else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isMe) Color(0xFFFF4D8D).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                modifier = Modifier.width(36.dp).height(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "#${entry.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) Color(0xFFFF4D8D) else Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Avatar
            Avatar(
                entry.avatar,
                size = 38.dp,
                nick = entry.nick,
                accountId = entry.userId
            )

            Spacer(Modifier.width(12.dp))

            // Nick + Level
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.nick,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "(Вы)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF4D8D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "${entry.levelTitle} ${entry.badge}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Episodes and hours
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${entry.episodesWatched} эп.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isMe) Color(0xFFFF4D8D) else Color(0xFFFF6B9D)
                )
                Text(
                    "${entry.watchHours} ч",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
