package com.anipulse.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anipulse.app.data.video.Dub
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Полноэкранный ландшафт на время плеера; на выходе — вернуть портрет и системные панели.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                // вернуть авто-яркость
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val stream = state.stream
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            state.error != null -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: "", color = Color.White)
                TextButton(onClick = { state.selectedDub?.let(viewModel::selectDub) }) { Text("Повторить", color = Color.White) }
                TextButton(onClick = onBack) { Text("Назад", color = Color.White) }
            }
            stream != null && stream.isNative -> NativePlayer(state, viewModel, onBack)
            stream != null && stream.embedUrl != null -> {
                WebPlayer(stream.embedUrl!!)
                // Для fallback-плеера оставляем только кнопку «Назад»
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).background(Color(0x66000000), RoundedCornerShape(24.dp)),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun NativePlayer(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    val exo = remember {
        // Больше буфера → меньше рывков/перебуферизации на нестабильной сети и медленных CDN.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000,
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                setHandleAudioBecomingNoisy(true)
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var dubMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    var commentsMenu by remember { mutableStateOf(false) }
    // Выбранное качество; по умолчанию — до 720p (плавно; 1080p грузит сеть и лагает на старте).
    var selectedQuality by remember(state.stream) { mutableStateOf(state.stream?.defaultQuality()) }
    // Флаги авто-пропуска (сброс на каждой новой серии)
    var recapSkipped by remember(state.episode) { mutableStateOf(false) }
    var openingSkipped by remember(state.episode) { mutableStateOf(false) }

    // Загрузка/смена потока (озвучка/серия/качество) без пересоздания плеера, с сохранением позиции.
    LaunchedEffect(state.stream, selectedQuality) {
        val s = state.stream ?: return@LaunchedEffect
        val q = selectedQuality ?: s.bestQuality()
        val url = s.byQuality[q] ?: s.byQuality.values.firstOrNull() ?: return@LaunchedEffect
        // На новой серии — продолжить с места остановки; при смене качества — с текущей позиции.
        val resumeAt = maxOf(exo.currentPosition, state.resumePositionMs)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        if (resumeAt > 1000) exo.seekTo(resumeAt)
        exo.play()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress(exo.currentPosition, exo.duration.coerceAtLeast(0))
            exo.release()
        }
    }

    // Индикаторы жестов
    var seekFeedback by remember { mutableStateOf(0) }        // -1 / +1 / 0
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && viewModel.state.value.autoNextEpisode && viewModel.hasNext()) viewModel.nextEpisode()
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Тик позиции + периодическое сохранение прогресса + авто-пропуск
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            positionMs = exo.currentPosition
            durationMs = exo.duration.coerceAtLeast(0)
            isPlaying = exo.isPlaying
            if (++ticks % 10 == 0 && exo.isPlaying) viewModel.saveProgress(positionMs, durationMs)
            val sec = positionMs / 1000
            // Авто-пропуск повтора (в самом начале серии)
            if (state.autoSkipRecap && !recapSkipped && sec in 3..8 && durationMs > 200_000) {
                exo.seekTo(exo.currentPosition + 80_000); recapSkipped = true
            }
            // Авто-пропуск опенинга (по таймкодам или на первых 2 мин)
            if (state.autoSkipOpening && !openingSkipped && durationMs > 200_000) {
                val op = state.stream?.opening
                if (op != null && sec in op.start.toLong()..op.stop.toLong()) { exo.seekTo(op.stop * 1000L); openingSkipped = true }
                else if (op == null && sec in 5..90) { exo.seekTo(exo.currentPosition + 85_000); openingSkipped = true }
            }
            delay(500)
        }
    }
    // Автоскрытие панелей
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3500); controlsVisible = false }
    }
    // Кратковременные индикаторы
    LaunchedEffect(seekFeedback) { if (seekFeedback != 0) { delay(600); seekFeedback = 0 } }
    LaunchedEffect(brightnessOverlay) { if (brightnessOverlay >= 0) { delay(800); brightnessOverlay = -1f } }
    LaunchedEffect(volumeOverlay) { if (volumeOverlay >= 0) { delay(800); volumeOverlay = -1f } }

    Box(
        Modifier
            .fillMaxSize()
            // Тапы: одиночный — показать/скрыть панели; двойной — перемотка ±10с
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < screenW / 2) {
                            exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)); seekFeedback = -1
                        } else {
                            exo.seekTo(exo.currentPosition + 10_000); seekFeedback = 1
                        }
                    },
                )
            }
            // Свайпы по вертикали: слева — яркость, справа — громкость
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    if (change.position.x < screenW / 2) {
                        val win = activity?.window ?: return@detectVerticalDragGestures
                        val attrs = win.attributes
                        var b = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                        b = (b - dy / screenH).coerceIn(0.01f, 1f)
                        attrs.screenBrightness = b; win.attributes = attrs
                        brightnessOverlay = b
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val nv = (cur + (-dy / screenH * max * 1.5f)).toInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                        volumeOverlay = nv.toFloat() / max
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        // Индикатор перемотки
        if (seekFeedback != 0) {
            Row(
                Modifier
                    .align(if (seekFeedback < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (seekFeedback < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp),
                )
            }
        }
        // Индикаторы яркости/громкости
        if (brightnessOverlay >= 0) GestureBadge(Icons.Filled.Brightness6, brightnessOverlay)
        if (volumeOverlay >= 0) GestureBadge(Icons.Filled.VolumeUp, volumeOverlay)

        // Панели управления (с автоскрытием)
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // Верхняя строка
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Text(
                        "${state.title} · Серия ${state.episode}",
                        color = Color.White, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    // Выбор качества
                    val qualities = state.stream?.byQuality?.keys?.sortedDescending().orEmpty()
                    if (qualities.isNotEmpty()) {
                        TextButton(onClick = { qualityMenu = !qualityMenu; dubMenu = false }) {
                            Text("${selectedQuality ?: qualities.first()}p", color = Color.White)
                        }
                    }
                    state.selectedDub?.let { dub ->
                        TextButton(onClick = { dubMenu = !dubMenu; qualityMenu = false }) { Text(dub.title, color = Color.White) }
                    }
                    IconButton(onClick = { commentsMenu = !commentsMenu; settingsMenu = false; dubMenu = false; qualityMenu = false }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Комментарии к серии", tint = Color.White)
                    }
                    IconButton(onClick = { settingsMenu = !settingsMenu; dubMenu = false; qualityMenu = false; commentsMenu = false }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
                    }
                }

                // Центр: предыдущая серия / play-pause / следующая серия
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.prevEpisode(); controlsVisible = true },
                        enabled = viewModel.hasPrev(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious, contentDescription = "Предыдущая серия",
                            tint = if (viewModel.hasPrev()) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            isPlaying = exo.isPlaying
                            controlsVisible = true
                        },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.nextEpisode(); controlsVisible = true },
                        enabled = viewModel.hasNext(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, contentDescription = "Следующая серия",
                            tint = if (viewModel.hasNext()) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                // Нижняя строка: время, сикбар, следующая серия
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac ->
                                controlsVisible = true
                                if (durationMs > 0) { positionMs = (frac * durationMs).toLong() }
                            },
                            onValueChangeFinished = { exo.seekTo(positionMs) },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color(0x55FFFFFF),
                            ),
                        )
                        // Метки: конец опенинга (жёлтая), начало эндинга (оранжевая)
                        if (durationMs > 0) {
                            state.stream?.opening?.let { op ->
                                SeekMarker(op.stop * 1000L / durationMs.toFloat(), Color(0xFFFFD54F))
                            }
                            state.stream?.ending?.let { ed ->
                                SeekMarker(ed.start * 1000L / durationMs.toFloat(), Color(0xFFFF8A65))
                            }
                        }
                    }
                    Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Кнопка «Пропустить опенинг»: по таймкодам (AniLibria) либо на первых 2 мин (+85с) для любого источника.
        run {
            val op = state.stream?.opening
            val sec = positionMs / 1000
            val showByTimecode = op != null && sec in op.start.toLong()..op.stop.toLong()
            val showGeneric = op == null && sec in 3L..120L && durationMs > 200_000
            if (showByTimecode || showGeneric) {
                TextButton(
                    onClick = {
                        if (op != null) exo.seekTo(op.stop * 1000L)
                        else exo.seekTo(exo.currentPosition + 85_000)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp, end = 16.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp)),
                ) { Text("Пропустить опенинг ⏭", color = Color.White) }
            }
        }

        // Кнопка «Следующая серия» на эндинге (по таймкодам AniSkip)
        state.stream?.ending?.let { ed ->
            val sec = positionMs / 1000
            if (sec >= ed.start && viewModel.hasNext()) {
                TextButton(
                    onClick = { viewModel.nextEpisode() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp, end = 16.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                ) { Text("Следующая серия ⏭", color = Color.White) }
            }
        }

        // Слой-диссмисс: тап мимо закрывает открытое меню
        if (dubMenu || qualityMenu || settingsMenu || commentsMenu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(dubMenu, qualityMenu, settingsMenu, commentsMenu) {
                        detectTapGestures { dubMenu = false; qualityMenu = false; settingsMenu = false; commentsMenu = false }
                    }
            )
        }

        // Кнопка «Пропустить повтор» (в начале серии)
        run {
            val sec = positionMs / 1000
            if (sec in 3L..90L && durationMs > 200_000) {
                TextButton(
                    onClick = { exo.seekTo(exo.currentPosition + 80_000) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 120.dp, end = 16.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp)),
                ) { Text("Пропустить повтор ⏭", color = Color.White) }
            }
        }

        // Панель настроек плеера
        // Панель комментариев к текущей серии
        AnimatedVisibility(visible = commentsMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .background(Color(0xF2101018))
                    .padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Text(
                    "Серия ${state.episode} · обсуждение",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                "Пока нет комментариев — будь первым!",
                                color = Color(0xFF9D9AB0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(state.comments.size) { i ->
                        val cm = state.comments[i]
                        Row {
                            com.anipulse.app.ui.common.Avatar(cm.avatar, 26.dp)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    cm.nick,
                                    color = Color(0xFFFF4D8D),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(cm.text, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (state.isLoggedIn) {
                    var commentInput by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Комментарий…", color = Color(0xFF9D9AB0)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            maxLines = 2,
                        )
                        IconButton(
                            onClick = { viewModel.postComment(commentInput); commentInput = "" },
                            enabled = !state.commentSending && commentInput.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color(0xFFFF4D8D))
                        }
                    }
                } else {
                    Text(
                        "Войдите в Профиле, чтобы писать",
                        color = Color(0xFF9D9AB0),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = settingsMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier.fillMaxHeight().background(Color(0xE6000000)).padding(16.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Настройки плеера", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопропуск опенинга", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoSkipOpening, onCheckedChange = viewModel::setAutoSkipOpening)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопропуск повтора", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoSkipRecap, onCheckedChange = viewModel::setAutoSkipRecap)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопереход к след. серии", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoNextEpisode, onCheckedChange = viewModel::setAutoNextEpisode)
                }
            }
        }

        // Меню выбора озвучки
        AnimatedVisibility(visible = dubMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            DubMenu(state.dubs, state.selectedDub) { viewModel.selectDub(it); dubMenu = false }
        }

        // Меню выбора качества
        AnimatedVisibility(visible = qualityMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            val qualities = state.stream?.byQuality?.keys?.sortedDescending().orEmpty()
            Column(
                Modifier.fillMaxHeight().background(Color(0xE6000000)).padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Качество", color = Color.White, fontWeight = FontWeight.SemiBold)
                qualities.forEach { q ->
                    FilterChip(
                        selected = q == (selectedQuality ?: qualities.firstOrNull()),
                        onClick = { selectedQuality = q; qualityMenu = false },
                        label = { Text("${q}p") },
                    )
                }
            }
        }
    }
}

/** Вертикальная метка на дорожке перемотки в позиции fraction (0..1). */
@Composable
private fun BoxScope.SeekMarker(fraction: Float, color: Color) {
    Box(
        Modifier
            .align(androidx.compose.ui.BiasAlignment(horizontalBias = fraction.coerceIn(0f, 1f) * 2 - 1, verticalBias = 0f))
            .width(3.dp)
            .height(12.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun GestureBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier.width(120.dp),
                color = Color.White,
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}

@Composable
private fun DubMenu(dubs: List<Dub>, selected: Dub?, onSelect: (Dub) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(Color(0xE6000000))
            .padding(12.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Озвучка", color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(dubs) { dub ->
                FilterChip(
                    selected = dub.id == selected?.id,
                    onClick = { onSelect(dub) },
                    label = { Text(dub.title) },
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPlayer(url: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
